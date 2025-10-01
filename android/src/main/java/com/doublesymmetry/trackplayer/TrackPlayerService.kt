package com.doublesymmetry.trackplayer

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import androidx.annotation.MainThread
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.Player
import androidx.media3.common.Rating
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import androidx.media3.session.SessionResult
import com.doublesymmetry.trackplayer.option.Capability
import com.doublesymmetry.trackplayer.model.CustomCommandButton
import com.doublesymmetry.trackplayer.event.EventControllerConnection
import com.doublesymmetry.trackplayer.extension.find
import com.doublesymmetry.trackplayer.model.AudioPlayerOptionsData
import com.facebook.react.bridge.Arguments
import com.facebook.react.jstasks.HeadlessJsTaskConfig
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import timber.log.Timber
import kotlin.system.exitProcess

@OptIn(UnstableApi::class)
@MainThread
class TrackPlayerService : HeadlessJsMediaService() {
    lateinit var player: TrackPlayer
    private val binder = MusicBinder()
    private val scope = MainScope()
    // Temporary reference to the initial player's ExoPlayer, used for MediaSession initialization
    // before the player is configured with options from JavaScript
    private var temporaryPlayer: ExoPlayer? = null
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

    override fun onCreate() {
        Timber.Forest.plant(object : Timber.DebugTree() {
            override fun createStackElementTag(element: StackTraceElement): String? {
                return "RNTP-${element.className}:${element.methodName}"
            }
        })
        // Create initial player with default options. This will be replaced in setupPlayer()
        // when JavaScript provides the actual configuration
        player = TrackPlayer(this)
        temporaryPlayer = player.exoPlayer
        val openAppIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            // Add the Uri data so apps can identify that it was a notification click
            data = "trackplayer://notification.click".toUri()
            action = Intent.ACTION_VIEW
        }
        mediaSession = MediaLibrarySession.Builder(this, player.exoPlayer,
            InnerMediaSessionCallback()
        )
            // https://github.com/androidx/media/issues/1218
            .setSessionActivity(
                PendingIntent.getActivity(
                    this,
                    0,
                    openAppIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        onStartCommandIntentValid = intent != null
        Timber.Forest.d("onStartCommand: ${intent?.action}, ${intent?.`package`}")
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    fun setupPlayer(playerOptionsData: AudioPlayerOptionsData) {
        // Check if player has already been configured (not the temporary initial player)
        if (temporaryPlayer == null) {
            print("Player setup already completed. Preventing reinitialization.")
            return
        }
        Timber.Forest.d("Setting up player")

        val options = playerOptionsData.toAudioPlayerOptions()
        val oldPlayer = player
        // Replace temporary player with properly configured one
        player = TrackPlayer(this@TrackPlayerService, options)
        oldPlayer.destroy()
        temporaryPlayer = null
        mediaSession.player = player.forwardingPlayer
    }

    fun updateOptions(options: AudioPlayerOptionsData) {
        val androidOptions = options.androidOptions

        androidOptions?.audioOffload?.let { audioOffload ->
            player.setAudioOffload(audioOffload)
        }

        androidOptions?.skipSilence?.let { skipSilence ->
            player.skipSilence = skipSilence
        }

        appKilledPlaybackBehavior =
            AppKilledPlaybackBehavior::string.find(androidOptions?.appKilledPlaybackBehavior) ?: AppKilledPlaybackBehavior.CONTINUE_PLAYBACK

        player.shuffleMode = androidOptions?.shuffle ?: false

        // Progress update events now handled by MusicModule
        val capabilities = options.capabilities ?: emptyList()
        val notificationCapabilities = options.notificationCapabilities?.takeIf { it.isNotEmpty() } ?: capabilities

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

    override fun getTaskConfig(intent: Intent?): HeadlessJsTaskConfig {
        return HeadlessJsTaskConfig(TASK_KEY, Arguments.createMap(), 0, true)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return if (intent?.action != null) {
            super.onBind(intent)
        } else {
            binder
        }
    }


    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        // https://github.com/androidx/media/issues/843#issuecomment-1860555950
        super.onUpdateNotification(session, true)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        onUnbind(rootIntent)
        Timber.Forest.d("player = $player, appKilledPlaybackBehavior = $appKilledPlaybackBehavior")

        when (appKilledPlaybackBehavior) {
            AppKilledPlaybackBehavior.PAUSE_PLAYBACK -> {
                Timber.Forest.d("Pausing playback - appKilledPlaybackBehavior = $appKilledPlaybackBehavior")
                player.pause()
            }
            AppKilledPlaybackBehavior.STOP_PLAYBACK_AND_REMOVE_NOTIFICATION -> {
                Timber.Forest.d("Killing service - appKilledPlaybackBehavior = $appKilledPlaybackBehavior")
                mediaSession.release()
                player.clear()
                player.stop()
                // HACK: the service first stops, then starts, then call onTaskRemove. Why system
                // registers the service being restarted?
                player.destroy()
                scope.cancel()
                stopForeground(STOP_FOREGROUND_REMOVE)
                onDestroy()
                // https://github.com/androidx/media/issues/27#issuecomment-1456042326
                stopSelf()
                exitProcess(0)
            }
            AppKilledPlaybackBehavior.CONTINUE_PLAYBACK -> {
                // No action needed - just continue playing
            }
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
            activityIntent!!.data = "trackplayer://service-bound".toUri()
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
        Timber.Forest.d(controllerInfo.packageName)
        return mediaSession
    }

    override fun onHeadlessJsTaskFinish(taskId: Int) {
        // This is empty so ReactNative doesn't kill this service
    }

    override fun onDestroy() {
        if (::player.isInitialized) {
            Timber.Forest.d("Releasing media session and destroying player")
            mediaSession.release()
            player.destroy()
        }

        super.onDestroy()
    }

    inner class MusicBinder : Binder() {
        val service = this@TrackPlayerService
    }

    private inner class InnerMediaSessionCallback : MediaLibrarySession.Callback {
        // HACK: I'm sure most of the callbacks were not implemented correctly.
        // ATM I only care that andorid auto still functions.

        override fun onDisconnected(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ) {
            player.events.onControllerDisconnected.emit(controller.packageName)
            super.onDisconnected(session, controller)
        }

        // Configure commands available to the controller in onConnect()
        @OptIn(UnstableApi::class)
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            Timber.Forest.d(controller.packageName)
            val isMediaNotificationController = session.isMediaNotificationController(controller)
            val isAutomotiveController = session.isAutomotiveController(controller)
            val isAutoCompanionController = session.isAutoCompanionController(controller)
            player.events.onControllerConnected.emit(
                EventControllerConnection(
                    packageName = controller.packageName,
                    isMediaNotificationController = isMediaNotificationController,
                    isAutomotiveController = isAutomotiveController,
                    isAutoCompanionController = isAutoCompanionController
                )
            )
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
            when (command.customAction) {
                CustomCommandButton.JUMP_BACKWARD.customAction -> { player.forwardingPlayer.seekBack() }
                CustomCommandButton.JUMP_FORWARD.customAction -> { player.forwardingPlayer.seekForward() }
                CustomCommandButton.NEXT.customAction -> { player.forwardingPlayer.seekToNext() }
                CustomCommandButton.PREVIOUS.customAction -> { player.forwardingPlayer.seekToPrevious() }
            }
            return super.onCustomCommand(session, controller, command, args)
        }



        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            player.events.onPlaybackResume.emit(controller.packageName)
            return super.onPlaybackResumption(mediaSession, controller)
        }

        override fun onSetRating(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            rating: Rating
        ): ListenableFuture<SessionResult> {
            player.events.onRatingChanged.emit(rating)
            return super.onSetRating(session, controller, rating)
        }
    }

    companion object {
        const val TASK_KEY = "TrackPlayer"
    }
}
