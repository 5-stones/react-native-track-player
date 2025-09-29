package com.doublesymmetry.trackplayer.service

import android.annotation.SuppressLint
import android.app.*
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.view.KeyEvent
import androidx.annotation.MainThread
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CacheBitmapLoader
import androidx.media3.common.Rating
import androidx.media3.common.util.BitmapLoader
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import androidx.media3.session.SessionResult
import com.doublesymmetry.kotlinaudio.models.*
import com.doublesymmetry.kotlinaudio.players.QueuedAudioPlayer
import com.doublesymmetry.trackplayer.HeadlessJsMediaService
import com.doublesymmetry.trackplayer.extensions.NumberExt.Companion.toSeconds
import com.doublesymmetry.trackplayer.extensions.find
import com.doublesymmetry.trackplayer.model.Track
import com.doublesymmetry.trackplayer.model.PlayerOptionsData
import com.doublesymmetry.trackplayer.utils.CoilBitmapLoader
import com.facebook.react.bridge.Arguments
import com.facebook.react.jstasks.HeadlessJsTaskConfig
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.*
import timber.log.Timber
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

@OptIn(UnstableApi::class)
@MainThread
class MusicService : HeadlessJsMediaService() {
    lateinit var player: QueuedAudioPlayer
    private val binder = MusicBinder()
    private val scope = MainScope()
    private lateinit var fakePlayer: ExoPlayer
    private lateinit var mediaSession: MediaLibrarySession
    private var sessionCommands: SessionCommands? = null
    private var playerCommands: Player.Commands? = null
    private var customLayout: List<CommandButton> = listOf()
    private var lastWake: Long = 0
    var onStartCommandIntentValid: Boolean = true


    fun acquireWakeLock() {
        acquireWakeLockNow(this)
    }

    fun abandonWakeLock() {
        sWakeLock?.release()
    }

    fun getBitmapLoader(): BitmapLoader {
        return mediaSession.bitmapLoader
    }

    fun getCurrentBitmap(): ListenableFuture<Bitmap>? {
        return player.exoPlayer.currentMediaItem?.mediaMetadata?.let {
            mediaSession.bitmapLoader.loadBitmapFromMetadata(
                it
            )
        }
    }

    @ExperimentalCoroutinesApi
    override fun onCreate() {
        Timber.plant(object : Timber.DebugTree() {
            override fun createStackElementTag(element: StackTraceElement): String? {
                return "RNTP-${element.className}:${element.methodName}"
            }
        })
        fakePlayer = ExoPlayer.Builder(this).build()
        val openAppIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            // Add the Uri data so apps can identify that it was a notification click
            data = Uri.parse("trackplayer://notification.click")
            action = Intent.ACTION_VIEW
        }
        mediaSession = MediaLibrarySession.Builder(this, fakePlayer,
            InnerMediaSessionCallback()
        )
            .setBitmapLoader(CacheBitmapLoader(CoilBitmapLoader(this)))
            // https://github.com/androidx/media/issues/1218
            .setSessionActivity(
                PendingIntent.getActivity(
                    this,
                    0,
                    openAppIntent,
                    getPendingIntentFlags()
                )
            )
            .build()
        super.onCreate()
    }

    enum class AppKilledPlaybackBehavior(val string: String) {
        CONTINUE_PLAYBACK("continue-playback"),
        PAUSE_PLAYBACK("pause-playback"),
        STOP_PLAYBACK_AND_REMOVE_NOTIFICATION("stop-playback-and-remove-notification")
    }

    private var appKilledPlaybackBehavior =
        AppKilledPlaybackBehavior.STOP_PLAYBACK_AND_REMOVE_NOTIFICATION
    private var stopForegroundGracePeriod: Int = DEFAULT_STOP_FOREGROUND_GRACE_PERIOD
    val tracks: List<Track>
        get() = player.items.mapNotNull { it.track }

    val currentTrack: Track?
        get() = player.currentItem?.track

    val state
        get() = player.playerState

    var ratingType: Int
        get() = player.ratingType
        set(value) {
            player.ratingType = value
        }

    val playbackError
        get() = player.playbackError

    val event
        get() = player.playerEventHolder

    var playWhenReady: Boolean
        get() = player.playWhenReady
        set(value) {
            player.playWhenReady = value
        }

    private var latestOptions: PlayerOptionsData? = null
    private var commandStarted = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        onStartCommandIntentValid = intent != null
        Timber.d("onStartCommand: ${intent?.action}, ${intent?.`package`}")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            // HACK: this is not supposed to be here. I definitely screwed up. but Why?
            onMediaKeyEvent(intent)
        }
        // HACK: Why is onPlay triggering onStartCommand??
        if (!commandStarted) {
            commandStarted = true
            super.onStartCommand(intent, flags, startId)
        }
        return START_STICKY
    }

    @MainThread
    fun setupPlayer(playerOptionsData: PlayerOptionsData) {
        if (this::player.isInitialized) {
            print("Player was initialized previously. Preventing reinitialization.")
            return
        }
        Timber.d("Setting up player")

        val options = playerOptionsData.toAudioPlayerOptions()
        player = QueuedAudioPlayer(this@MusicService, options)
        fakePlayer.release()
        mediaSession.player = player.forwardingPlayer
    }

    @MainThread
    fun updateOptions(options: PlayerOptionsData) {
        latestOptions = options
        val androidOptions = options.androidOptions

        androidOptions?.audioOffload?.let { audioOffload ->
            player.setAudioOffload(audioOffload)
        }

        androidOptions?.skipSilence?.let { skipSilence ->
            player.skipSilence = skipSilence
        }

        appKilledPlaybackBehavior =
            AppKilledPlaybackBehavior::string.find(androidOptions?.appKilledPlaybackBehavior) ?: AppKilledPlaybackBehavior.CONTINUE_PLAYBACK

        androidOptions?.stopForegroundGracePeriod?.let { stopForegroundGracePeriod = it }

        player.alwaysPauseOnInterruption = androidOptions?.pauseOnInterruption ?: false
        player.shuffleMode = androidOptions?.shuffle ?: false

        // Progress update events now handled by MusicModule
        val capabilities = options.capabilities?.map { Capability.entries[it] } ?: emptyList()
        var notificationCapabilities = options.notificationCapabilities?.map { Capability.entries[it] } ?: emptyList()
        if (notificationCapabilities.isEmpty()) notificationCapabilities = capabilities

        val playerCommandsBuilder = Player.Commands.Builder().addAll(
            // HACK: without COMMAND_GET_CURRENT_MEDIA_ITEM, notification cannot be created
            Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
            Player.COMMAND_GET_TRACKS,
            Player.COMMAND_GET_TIMELINE,
            Player.COMMAND_GET_METADATA,
            Player.COMMAND_GET_AUDIO_ATTRIBUTES,
            Player.COMMAND_GET_VOLUME,
            Player.COMMAND_GET_DEVICE_VOLUME,
            Player.COMMAND_GET_TEXT,
            Player.COMMAND_SEEK_TO_MEDIA_ITEM,
            Player.COMMAND_SET_MEDIA_ITEM,
            Player.COMMAND_PREPARE,
            Player.COMMAND_RELEASE,
        )
        notificationCapabilities.forEach {
            when (it) {
                Capability.PLAY, Capability.PAUSE -> {
                    playerCommandsBuilder.add(Player.COMMAND_PLAY_PAUSE)
                }

                Capability.STOP -> {
                    playerCommandsBuilder.add(Player.COMMAND_STOP)
                }

                Capability.SEEK_TO -> {
                    playerCommandsBuilder.add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                }

                else -> {}
            }
        }
        customLayout = CustomCommandButton.entries
            .filter { notificationCapabilities.contains(it.capability) }
            .map { c -> c.commandButton }
        val sessionCommandsBuilder =
            MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
        customLayout.forEach { v ->
            v.sessionCommand?.let { sessionCommandsBuilder.add(it) }
        }

        sessionCommands = sessionCommandsBuilder.build()
        playerCommands = playerCommandsBuilder.build()

        if (mediaSession.mediaNotificationControllerInfo != null) {
            // https://github.com/androidx/media/blob/c35a9d62baec57118ea898e271ac66819399649b/demos/session_service/src/main/java/androidx/media3/demo/session/DemoMediaLibrarySessionCallback.kt#L107
            mediaSession.setCustomLayout(
                mediaSession.mediaNotificationControllerInfo!!,
                customLayout
            )
            mediaSession.setAvailableCommands(
                mediaSession.mediaNotificationControllerInfo!!,
                sessionCommandsBuilder.build(),
                playerCommands!!
            )
        }
    }


    @MainThread
    fun add(track: Track) {
        add(listOf(track))
    }

    @MainThread
    fun add(tracks: List<Track>) {
        val items = tracks.map { it.toAudioItem() }
        player.add(items)
    }

    @MainThread
    fun add(tracks: List<Track>, atIndex: Int) {
        val items = tracks.map { it.toAudioItem() }
        player.add(items, atIndex)
    }

    @MainThread
    fun load(track: Track) {
        player.load(track.toAudioItem())
    }

    @MainThread
    fun move(fromIndex: Int, toIndex: Int) {
        player.move(fromIndex, toIndex)
    }

    @MainThread
    fun remove(index: Int) {
        remove(listOf(index))
    }

    @MainThread
    fun remove(indexes: List<Int>) {
        player.remove(indexes)
    }

    @MainThread
    fun clear() {
        player.clear()
    }

    @MainThread
    fun play() {
        player.play()
    }

    @MainThread
    fun pause() {
        player.pause()
    }

    @MainThread
    fun stop() {
        player.stop()
    }

    @MainThread
    fun removeUpcomingTracks() {
        player.removeUpcomingItems()
    }

    @MainThread
    fun removePreviousTracks() {
        player.removePreviousItems()
    }

    @MainThread
    fun skip(index: Int) {
        player.jumpToItem(index)
    }

    @MainThread
    fun skipToNext() {
        player.next()
    }

    @MainThread
    fun skipToPrevious() {
        player.previous()
    }

    @MainThread
    fun seekTo(seconds: Float) {
        player.seek((seconds * 1000).toLong(), TimeUnit.MILLISECONDS)
    }

    @MainThread
    fun seekBy(offset: Float) {
        player.seekBy((offset.toLong()), TimeUnit.SECONDS)
    }

    @MainThread
    fun retry() {
        player.prepare()
    }

    @MainThread
    fun getCurrentTrackIndex(): Int = player.currentIndex

    @MainThread
    fun getRate(): Float = player.playbackSpeed

    @MainThread
    fun setRate(value: Float) {
        player.playbackSpeed = value
    }

    @MainThread
    fun getRepeatMode(): RepeatMode = player.repeatMode

    @MainThread
    fun setRepeatMode(value: RepeatMode) {
        player.repeatMode = value
    }

    @MainThread
    fun getVolume(): Float = player.volume

    @MainThread
    fun setVolume(value: Float) {
        player.volume = value
    }

    @MainThread
    fun getDurationInSeconds(): Double = player.duration.toSeconds()

    @MainThread
    fun getPositionInSeconds(): Double = player.position.toSeconds()

    @MainThread
    fun getBufferedPositionInSeconds(): Double = player.bufferedPosition.toSeconds()

    @MainThread
    fun updateMetadataForTrack(index: Int, newTrack: Track) {
        player.replaceItem(index, newTrack.toAudioItem())
    }

    @MainThread
    fun updateNowPlayingMetadata(newTrack: Track) {
        updateMetadataForTrack(player.currentIndex, newTrack)
    }

    override fun getTaskConfig(intent: Intent?): HeadlessJsTaskConfig {
        return HeadlessJsTaskConfig(TASK_KEY, Arguments.createMap(), 0, true)
    }

    @MainThread
    override fun onBind(intent: Intent?): IBinder? {
        val intentAction = intent?.action
        Timber.d("intentAction = $intentAction")
        return if (intentAction != null) {
            super.onBind(intent)
        } else {
            binder
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        val intentAction = intent?.action
        Timber.d("intentAction = $intentAction")
        return super.onUnbind(intent)
    }

    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        // https://github.com/androidx/media/issues/843#issuecomment-1860555950
        super.onUpdateNotification(session, true)
    }

    @MainThread
    override fun onTaskRemoved(rootIntent: Intent?) {
        onUnbind(rootIntent)
        Timber.d("isInitialized = ${::player.isInitialized}, appKilledPlaybackBehavior = $appKilledPlaybackBehavior")
        if (!::player.isInitialized) {
            mediaSession.release()
            return
        }

        when (appKilledPlaybackBehavior) {
            AppKilledPlaybackBehavior.PAUSE_PLAYBACK -> {
                Timber.d("Pausing playback - appKilledPlaybackBehavior = $appKilledPlaybackBehavior")
                player.pause()
            }
            AppKilledPlaybackBehavior.STOP_PLAYBACK_AND_REMOVE_NOTIFICATION -> {
                Timber.d("Killing service - appKilledPlaybackBehavior = $appKilledPlaybackBehavior")
                mediaSession.release()
                player.clear()
                player.stop()
                // HACK: the service first stops, then starts, then call onTaskRemove. Why system
                // registers the service being restarted?
                player.destroy()
                scope.cancel()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                onDestroy()
                // https://github.com/androidx/media/issues/27#issuecomment-1456042326
                stopSelf()
                exitProcess(0)
            }

            else -> {}
        }
    }

    @SuppressLint("VisibleForTests")
    private fun selfWake(clientPackageName: String): Boolean {
        val reactActivity = reactContext?.currentActivity
        if (
        // HACK: validate reactActivity is present; if not, send wake intent
            (reactActivity == null || reactActivity.isDestroyed)
            && Settings.canDrawOverlays(this)
        ) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastWake < 100000) {
                return false
            }
            lastWake = currentTime
            val activityIntent = packageManager.getLaunchIntentForPackage(packageName)
            activityIntent!!.data = Uri.parse("trackplayer://service-bound")
            activityIntent.action = Intent.ACTION_VIEW
            activityIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            var activityOptions = ActivityOptions.makeBasic()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                activityOptions = activityOptions.setPendingIntentBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                )
            }
            this.startActivity(activityIntent, activityOptions.toBundle())
            return true
        }
        return false
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession {
        Timber.d("${controllerInfo.packageName}")
        return mediaSession
    }

    @MainThread
    override fun onHeadlessJsTaskFinish(taskId: Int) {
        // This is empty so ReactNative doesn't kill this service
    }

    @MainThread
    override fun onDestroy() {
        if (::player.isInitialized) {
            Timber.d("Releasing media session and destroying player")
            mediaSession.release()
            player.destroy()
        }

        super.onDestroy()
    }

    fun onMediaKeyEvent(intent: Intent?): Boolean? {
        val keyEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
        } else {
            intent?.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
        }

        if (keyEvent?.action == KeyEvent.ACTION_DOWN) {
            return when (keyEvent.keyCode) {
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                    player.togglePlay()
                    true
                }

                KeyEvent.KEYCODE_MEDIA_STOP -> {
                    player.forwardingPlayer.stop()
                    true
                }

                KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                    player.forwardingPlayer.pause()
                    true
                }

                KeyEvent.KEYCODE_MEDIA_PLAY -> {
                    player.forwardingPlayer.play()
                    true
                }

                KeyEvent.KEYCODE_MEDIA_NEXT -> {
                    player.forwardingPlayer.seekToNext()
                    true
                }

                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                    player.forwardingPlayer.seekToPrevious()
                    true
                }

                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD, KeyEvent.KEYCODE_MEDIA_STEP_FORWARD -> {
                    player.forwardingPlayer.seekForward()
                    true
                }

                KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD, KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD -> {
                    player.forwardingPlayer.seekBack()
                    true
                }

                else -> null
            }
        }
        return null
    }

    @MainThread
    inner class MusicBinder : Binder() {
        val service = this@MusicService
    }

    private inner class InnerMediaSessionCallback : MediaLibrarySession.Callback {
        // HACK: I'm sure most of the callbacks were not implemented correctly.
        // ATM I only care that andorid auto still functions.

        override fun onDisconnected(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ) {
            if (::player.isInitialized) {
                player.playerEventHolder.updateOnControllerDisconnected(controller.packageName)
            }
            super.onDisconnected(session, controller)
        }

        // Configure commands available to the controller in onConnect()
        @OptIn(UnstableApi::class)
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            Timber.d("${controller.packageName}")
            val isMediaNotificationController = session.isMediaNotificationController(controller)
            val isAutomotiveController = session.isAutomotiveController(controller)
            val isAutoCompanionController = session.isAutoCompanionController(controller)
            if (::player.isInitialized) {
                player.playerEventHolder.updateOnControllerConnected(
                    EventControllerConnectionData(
                        packageName = controller.packageName,
                        isMediaNotificationController = isMediaNotificationController,
                        isAutomotiveController = isAutomotiveController,
                        isAutoCompanionController = isAutoCompanionController
                    )
                )
            }
            if (controller.packageName in arrayOf(
                    "com.android.systemui",
                    // https://github.com/googlesamples/android-media-controller
                    "com.example.android.mediacontroller",
                    // Android Auto
                    "com.google.android.projection.gearhead"
                )
            ) {
                // HACK: attempt to wake up activity (for legacy APM). if not, start headless.
                if (!selfWake(controller.packageName)) {
                    onStartCommand(null, 0, 0)
                }
            }
            return if (
                isMediaNotificationController ||
                isAutomotiveController ||
                isAutoCompanionController
            ) {
                MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setCustomLayout(customLayout)
                    .setAvailableSessionCommands(
                        sessionCommands
                            ?: MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                    )
                    .setAvailablePlayerCommands(
                        playerCommands ?: MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS
                    )
                    .build()
            } else {
                super.onConnect(session, controller)
            }
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            command: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            player.forwardingPlayer.let {
                when (command.customAction) {
                    CustomCommandButton.JUMP_BACKWARD.customAction -> { it.seekBack() }
                    CustomCommandButton.JUMP_FORWARD.customAction -> { it.seekForward() }
                    CustomCommandButton.NEXT.customAction -> { it.seekToNext() }
                    CustomCommandButton.PREVIOUS.customAction -> { it.seekToPrevious() }
                }
            }
            return super.onCustomCommand(session, controller, command, args)
        }


        override fun onMediaButtonEvent(
            session: MediaSession,
            controllerInfo: MediaSession.ControllerInfo,
            intent: Intent
        ): Boolean {
            return onMediaKeyEvent(intent) ?: super.onMediaButtonEvent(
                session,
                controllerInfo,
                intent
            )
        }

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            player.playerEventHolder.updateOnPlaybackResume(controller.packageName)
            return super.onPlaybackResumption(mediaSession, controller)
        }

        override fun onSetRating(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            rating: Rating
        ): ListenableFuture<SessionResult> {
            player.playerEventHolder.updateOnRatingChanged(rating)
            return super.onSetRating(session, controller, rating)
        }
    }

    private fun getPendingIntentFlags(): Int {
        return PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT
    }

    companion object {
        const val STATE_KEY = "state"
        const val ERROR_KEY = "error"

        const val TASK_KEY = "TrackPlayer"

        const val PLAYER_OPTIONS_MIN_BUFFER = "minBuffer"
        const val PLAYER_OPTIONS_MAX_BUFFER = "maxBuffer"
        const val PLAYER_OPTIONS_PLAY_BUFFER = "playBuffer"
        const val PLAYER_OPTIONS_BACK_BUFFER = "backBuffer"
        const val PLAYER_OPTIONS_MAX_CACHE_SIZE_KEY = "maxCacheSize"

        const val ANDROID_OPTIONS_KEY = "android"

        const val PLAYER_OPTIONS_ANDROID_APP_KILLED_PLAYBACK_BEHAVIOR_KEY = "appKilledPlaybackBehavior"
        const val PLAYER_OPTIONS_ANDROID_AUDIO_OFFLOAD_KEY = "audioOffload"
        const val PLAYER_OPTIONS_ANDROID_SHUFFLE_KEY = "shuffle"
        const val PLAYER_OPTIONS_ANDROID_STOP_FOREGROUND_GRACE_PERIOD_KEY = "stopForegroundGracePeriod"
        const val PLAYER_OPTIONS_ANDROID_PAUSE_ON_INTERRUPTION = "alwaysPauseOnInterruption"
        const val PLAYER_OPTIONS_AUTO_HANDLE_INTERRUPTIONS = "autoHandleInterruptions"
        const val PLAYER_OPTIONS_ANDROID_AUDIO_CONTENT_TYPE = "androidAudioContentType"
        const val PLAYER_OPTIONS_HANDLE_NOISY = "androidHandleAudioBecomingNoisy"
        const val PLAYER_OPTIONS_ALWAYS_SHOW_NEXT = "androidAlwaysShowNext"
        const val PLAYER_OPTIONS_SKIP_SILENCE = "androidSkipSilence"
        const val PLAYER_OPTIONS_WAKE_MODE = "androidWakeMode"
       const val DEFAULT_STOP_FOREGROUND_GRACE_PERIOD = 5
    }
}
