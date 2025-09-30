package com.doublesymmetry.kotlinaudio.players

import android.content.Context
import android.media.AudioManager
import androidx.core.content.ContextCompat
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.IllegalSeekPositionException
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Player.Listener
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.legacy.RatingCompat
import com.doublesymmetry.kotlinaudio.event.PlayerEvents
import com.doublesymmetry.kotlinaudio.models.AudioItem
import com.doublesymmetry.kotlinaudio.models.AudioItemTransition
import com.doublesymmetry.kotlinaudio.models.AudioItemTransitionReason
import com.doublesymmetry.kotlinaudio.models.AudioPlayerState
import com.doublesymmetry.kotlinaudio.models.FocusChangeData
import com.doublesymmetry.kotlinaudio.models.MediaSessionCallback
import com.doublesymmetry.kotlinaudio.models.PlayWhenReadyChangeData
import com.doublesymmetry.kotlinaudio.models.PlaybackError
import com.doublesymmetry.kotlinaudio.models.PlayerOptions
import com.doublesymmetry.kotlinaudio.models.PositionChangedReason
import com.doublesymmetry.kotlinaudio.models.RepeatMode
import com.doublesymmetry.kotlinaudio.players.components.Cache
import com.doublesymmetry.kotlinaudio.players.components.MediaFactory
import timber.log.Timber
import java.util.Locale
import java.util.concurrent.TimeUnit

@UnstableApi
class AudioPlayer(
    private val context: Context,
    val options: PlayerOptions = PlayerOptions()
) {

    val exoPlayer: ExoPlayer
    val forwardingPlayer: InnerForwardingPlayer
    val player: Player
        get() {
            return options.interceptPlayerActionsTriggeredExternally
                .takeIf { it }
                ?.let { forwardingPlayer }
                ?: exoPlayer
        }
    private var playerListener = InnerPlayerListener()
    private var cache: SimpleCache? = null
    val events = PlayerEvents()

    private var wasDucking = false
    private val focusManager: FocusManager = FocusManager()

    var alwaysPauseOnInterruption: Boolean
        get() = options.alwaysPauseOnInterruption
        set(v) { options.alwaysPauseOnInterruption = v }

    val currentItem: AudioItem?
        get() = exoPlayer.currentMediaItem?.let { AudioItem.fromMediaItem(it) }

    var playbackError: PlaybackError? = null
    var playerState: AudioPlayerState = AudioPlayerState.IDLE
        private set(value) {
            if (value != field) {
                field = value
                events.stateChange.emit(value)
                if (!options.handleAudioFocus) {
                    when (value) {
                        AudioPlayerState.IDLE,
                        AudioPlayerState.ERROR -> focusManager.abandonAudioFocusIfHeld()
                        AudioPlayerState.READY -> focusManager.requestAudioFocus()
                        else -> {}
                    }
                }
            }
        }

    var playWhenReady: Boolean
        get() = exoPlayer.playWhenReady
        set(value) {
            exoPlayer.playWhenReady = value
        }

    val duration: Long
        get() {
            return if (exoPlayer.duration == C.TIME_UNSET) 0
            else exoPlayer.duration
        }

    private var oldPosition = 0L

    val position: Long
        get() {
            return if (exoPlayer.currentPosition == C.INDEX_UNSET.toLong()) 0
            else exoPlayer.currentPosition
        }

    val bufferedPosition: Long
        get() {
            return if (exoPlayer.bufferedPosition == C.INDEX_UNSET.toLong()) 0
            else exoPlayer.bufferedPosition
        }

    private var volumeMultiplier = 1f
        private set(value) {
            field = value
            volume = volume
        }

    var volume: Float
        get() = exoPlayer.volume
        set(value) {
            exoPlayer.volume = value * volumeMultiplier
        }

    var playbackSpeed: Float
        get() = exoPlayer.playbackParameters.speed
        set(value) {
            exoPlayer.setPlaybackSpeed(value)
        }

    val isPlaying
        get() = exoPlayer.isPlaying

    var ratingType: Int = RatingCompat.RATING_NONE

    var repeatMode: RepeatMode
        get() {
            return when (exoPlayer.repeatMode) {
                Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                else -> RepeatMode.OFF
            }
        }
        set(value) {
            when (value) {
                RepeatMode.ALL -> exoPlayer.repeatMode = Player.REPEAT_MODE_ALL
                RepeatMode.ONE -> exoPlayer.repeatMode = Player.REPEAT_MODE_ONE
                RepeatMode.OFF -> exoPlayer.repeatMode = Player.REPEAT_MODE_OFF
            }
        }

    val currentIndex: Int?
        get() = if (exoPlayer.currentMediaItemIndex == C.INDEX_UNSET) null
            else exoPlayer.currentMediaItemIndex

    val isEmpty: Boolean
        get() = exoPlayer.mediaItemCount == 0

    var shuffleMode
        get() = exoPlayer.shuffleModeEnabled
        set(v) {
            exoPlayer.shuffleModeEnabled = v
        }

    val nextIndex: Int?
        get() {
            return if (exoPlayer.nextMediaItemIndex == C.INDEX_UNSET) null
            else exoPlayer.nextMediaItemIndex
        }

    val items: List<AudioItem>
        get() = (0 until exoPlayer.mediaItemCount).map { index ->
            AudioItem.fromMediaItem(exoPlayer.getMediaItemAt(index))
        }

    val nextItem: AudioItem?
        get() {
            val nextIndex = exoPlayer.currentMediaItemIndex + 1
            return if (nextIndex < exoPlayer.mediaItemCount)
                AudioItem.fromMediaItem(exoPlayer.getMediaItemAt(nextIndex))
            else null
        }

    var skipSilence: Boolean
        get() = exoPlayer.skipSilenceEnabled
        set(value) {
            exoPlayer.skipSilenceEnabled = value
        }

    fun setAudioOffload(offload: Boolean = true) {
        val audioOffloadPreferences =
            TrackSelectionParameters.AudioOffloadPreferences.Builder()
                .setAudioOffloadMode(
                    if (offload) TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED
                    else TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED)
                // Add additional options as needed
                .setIsGaplessSupportRequired(true)
                .setIsSpeedChangeSupportRequired(true)
                .build()
        exoPlayer.trackSelectionParameters =
            exoPlayer.trackSelectionParameters
                .buildUpon()
                .setAudioOffloadPreferences(audioOffloadPreferences)
                .build()
    }

    init {
        if (options.cacheSizeKb > 0) {
            cache = Cache.initCache(context, options.cacheSizeKb)
        }
        events.stateChange.emit(AudioPlayerState.IDLE)

        val renderer = DefaultRenderersFactory(context)
        renderer.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

        val loadControl = run {
            val bufferConfig = options.bufferOptions
            val multiplier = DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS / DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS
            val minBuffer = bufferConfig.minBuffer?.takeIf { it != 0 } ?: DefaultLoadControl.DEFAULT_MIN_BUFFER_MS
            val maxBuffer = bufferConfig.maxBuffer?.takeIf { it != 0 } ?: DefaultLoadControl.DEFAULT_MAX_BUFFER_MS
            val playBuffer = bufferConfig.playBuffer?.takeIf { it != 0 } ?: DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS
            val backBuffer = bufferConfig.backBuffer?.takeIf { it != 0 } ?: DefaultLoadControl.DEFAULT_BACK_BUFFER_DURATION_MS
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(minBuffer, maxBuffer, playBuffer, playBuffer * multiplier)
                .setBackBuffer(backBuffer, false)
                .build()
        }

        exoPlayer = ExoPlayer
            .Builder(context)
            .setRenderersFactory(renderer)
            .setHandleAudioBecomingNoisy(options.handleAudioBecomingNoisy)
            .setMediaSourceFactory(MediaFactory(context, cache))
            .setWakeMode(options.wakeMode.toExoPlayer())
            .setLoadControl(loadControl)
            .setSkipSilenceEnabled(options.skipSilence)
            .setName("kotlin-audio-player")
            .build()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(options.audioContentType.toExoPlayer())
            .build()
        exoPlayer.setAudioAttributes(audioAttributes, options.handleAudioFocus)
        forwardingPlayer = InnerForwardingPlayer(exoPlayer)
        player.addListener(playerListener)
    }

    /**
     * Will replace the current item with a new one and load it into the player.
     * @param item The [AudioItem] to replace the current one.
     */
    fun load(item: AudioItem) {
        if (isEmpty) {
            add(item)
        } else {
            val index = exoPlayer.currentMediaItemIndex
            replaceItem(index, item)
            exoPlayer.seekTo(index, C.TIME_UNSET)
            exoPlayer.prepare()
        }
    }

    /**
     * Add a single item to the queue. If the AudioPlayer has no item loaded, it will load the `item`.
     * @param item The [AudioItem] to add.
     */
    fun add(item: AudioItem) {
        val mediaSource = item.toMediaItem()
        exoPlayer.addMediaItem(mediaSource)
        exoPlayer.prepare()
    }

    /**
     * Add multiple items to the queue. If the AudioPlayer has no item loaded, it will load the first item in the list.
     * @param items The [AudioItem]s to add.
     */
    fun add(items: List<AudioItem>) {
        val mediaItems = items.map { it.toMediaItem() }
        exoPlayer.addMediaItems(mediaItems)
        exoPlayer.prepare()
    }

    /**
     * Add multiple items to the queue.
     * @param items The [AudioItem]s to add.
     * @param atIndex  Index to insert items at, if no items loaded this will not automatically start playback.
     */
    fun add(items: List<AudioItem>, atIndex: Int) {
        val mediaItems = items.map { (it).toMediaItem() }
        exoPlayer.addMediaItems(atIndex, mediaItems)
        exoPlayer.prepare()
    }

    /**
     * Remove an item from the queue.
     * @param index The index of the item to remove.
     */
    fun remove(index: Int) {
        exoPlayer.removeMediaItem(index)
    }

    /**
     * Remove items from the queue.
     * @param indexes The indexes of the items to remove.
     */
    fun remove(indexes: List<Int>) {
        val sorted = indexes.toMutableList()
        // Sort the indexes in descending order so we can safely remove them one by one
        // without having the next index possibly newly pointing to another item than intended:
        sorted.sortDescending()
        sorted.forEach {
            remove(it)
        }
    }

    /**
     * Skip to the next item in the queue, which may depend on the current repeat mode.
     * Does nothing if there is no next item to skip to.
     */
    fun next() {
        exoPlayer.seekToNextMediaItem()
        exoPlayer.prepare()
    }

    /**
     * Skip to the previous item in the queue, which may depend on the current repeat mode.
     * Does nothing if there is no previous item to skip to.
     */
    fun previous() {
        exoPlayer.seekToPreviousMediaItem()
        exoPlayer.prepare()
    }

    /**
     * Move an item in the queue from one position to another.
     * @param fromIndex The index of the item to move.
     * @param toIndex The index to move the item to. If the index is larger than the size of the queue, the item is moved to the end of the queue instead.
     */
    fun move(fromIndex: Int, toIndex: Int) {
        exoPlayer.moveMediaItem(fromIndex, toIndex)
    }

    /**
     * Jump to an item in the queue.
     * @param index the index to jump to
     */
    fun jumpToItem(index: Int) {
        try {
            exoPlayer.seekTo(index, C.TIME_UNSET)
            exoPlayer.prepare()
        } catch (e: IllegalSeekPositionException) {
            throw Error("This item index $index does not exist. The size of the queue is ${exoPlayer.mediaItemCount} items.")
        }
    }

    /**
     * Replaces item at index in queue.
     */
    fun replaceItem(index: Int, item: AudioItem) {
        val mediaItem = item.toMediaItem()
        exoPlayer.replaceMediaItem(index, mediaItem)
    }

    /**
     * Removes all the upcoming items, if any (the ones returned by [next]).
     */
    fun removeUpcomingItems() {
        val index = exoPlayer.currentMediaItemIndex
        if (index == C.INDEX_UNSET) return
        val lastIndex = exoPlayer.mediaItemCount
        val fromIndex = index + 1

        exoPlayer.removeMediaItems(fromIndex, lastIndex)
    }

    fun play() {
        exoPlayer.play()
        if (currentItem != null) {
            exoPlayer.prepare()
        }
    }

    fun prepare() {
        if (currentItem != null) {
            exoPlayer.prepare()
        }
    }

    fun pause() {
        exoPlayer.pause()
    }

    /**
     * Stops playback, without clearing the active item. Calling this method will cause the playback
     * state to transition to AudioPlayerState.IDLE and the player will release the loaded media and
     * resources required for playback.
     */
    fun stop() {
        playerState = AudioPlayerState.STOPPED
        exoPlayer.playWhenReady = false
        exoPlayer.stop()
    }

    fun clear() {
        exoPlayer.clearMediaItems()
    }

    /**
     * Stops and destroys the player. Only call this when you are finished using the player, otherwise use [pause].
     */
    fun destroy() {
        focusManager.abandonAudioFocusIfHeld()
        stop()
        player.removeListener(playerListener)
        exoPlayer.release()
        cache?.release()
        cache = null
    }

    fun seek(duration: Long, unit: TimeUnit) {
        val positionMs = TimeUnit.MILLISECONDS.convert(duration, unit)
        exoPlayer.seekTo(positionMs)
    }

    fun seekBy(offset: Long, unit: TimeUnit) {
        val positionMs = exoPlayer.currentPosition + TimeUnit.MILLISECONDS.convert(offset, unit)
        exoPlayer.seekTo(positionMs)
    }

    inner class InnerPlayerListener : Listener {
        /**
         * Called when there is metadata associated with the current playback time.
         */
        override fun onMetadata(metadata: Metadata) {
            events.onTimedMetadata.emit(metadata)
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            events.onCommonMetadata.emit(mediaMetadata)
        }

        /**
         * A position discontinuity occurs when the playing period changes, the playback position
         * jumps within the period currently being played, or when the playing period has been
         * skipped or removed.
         */
        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            this@AudioPlayer.oldPosition = oldPosition.positionMs

            when (reason) {
                Player.DISCONTINUITY_REASON_AUTO_TRANSITION -> events.positionChanged.emit(
                    PositionChangedReason.AUTO(oldPosition.positionMs, newPosition.positionMs)
                )
                Player.DISCONTINUITY_REASON_SEEK -> events.positionChanged.emit(
                    PositionChangedReason.SEEK(oldPosition.positionMs, newPosition.positionMs)
                )
                Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT -> events.positionChanged.emit(
                    PositionChangedReason.SEEK_FAILED(
                        oldPosition.positionMs,
                        newPosition.positionMs
                    )
                )
                Player.DISCONTINUITY_REASON_REMOVE -> events.positionChanged.emit(
                    PositionChangedReason.QUEUE_CHANGED(
                        oldPosition.positionMs,
                        newPosition.positionMs
                    )
                )
                Player.DISCONTINUITY_REASON_SKIP -> events.positionChanged.emit(
                    PositionChangedReason.SKIPPED_PERIOD(
                        oldPosition.positionMs,
                        newPosition.positionMs
                    )
                )
                Player.DISCONTINUITY_REASON_INTERNAL -> events.positionChanged.emit(
                    PositionChangedReason.UNKNOWN(oldPosition.positionMs, newPosition.positionMs)
                )

                Player.DISCONTINUITY_REASON_SILENCE_SKIP -> events.positionChanged.emit(
                    PositionChangedReason.UNKNOWN(oldPosition.positionMs, newPosition.positionMs)
                )
            }
        }

        /**
         * Called when playback transitions to a media item or starts repeating a media item
         * according to the current repeat mode. Note that this callback is also called when the
         * playlist becomes non-empty or empty as a consequence of a playlist change.
         */
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            when (reason) {
                Player.MEDIA_ITEM_TRANSITION_REASON_AUTO -> events.audioItemTransition.emit(
                    AudioItemTransition(AudioItemTransitionReason.AUTO, oldPosition)
                )
                Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED -> events.audioItemTransition.emit(
                    AudioItemTransition(AudioItemTransitionReason.QUEUE_CHANGED, oldPosition)
                )
                Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT -> events.audioItemTransition.emit(
                    AudioItemTransition(AudioItemTransitionReason.REPEAT, oldPosition)
                )
                Player.MEDIA_ITEM_TRANSITION_REASON_SEEK -> events.audioItemTransition.emit(
                    AudioItemTransition(AudioItemTransitionReason.SEEK_TO_ANOTHER_AUDIO_ITEM, oldPosition)
                )
            }
        }

        /**
         * Called when the value returned from Player.getPlayWhenReady() changes.
         */
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            val pausedBecauseReachedEnd = reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM
            events.playWhenReadyChange.emit(PlayWhenReadyChangeData(playWhenReady, pausedBecauseReachedEnd))
        }

        /**
         * The generic onEvents callback provides access to the Player object and specifies the set
         * of events that occurred together. It's always called after the callbacks that correspond
         * to the individual events.
         */
        override fun onEvents(player: Player, events: Player.Events) {
            // Note that it is necessary to set `playerState` in order, since each mutation fires an
            // event.
            for (i in 0 until events.size()) {
                when (events[i]) {
                    Player.EVENT_PLAYBACK_STATE_CHANGED -> {
                        val state = when (player.playbackState) {
                            Player.STATE_BUFFERING -> AudioPlayerState.BUFFERING
                            Player.STATE_READY -> AudioPlayerState.READY
                            Player.STATE_IDLE ->
                                // Avoid transitioning to idle from error or stopped
                                if (
                                    playerState == AudioPlayerState.ERROR ||
                                    playerState == AudioPlayerState.STOPPED
                                )
                                    null
                                else
                                    AudioPlayerState.IDLE
                            Player.STATE_ENDED ->
                                if (player.mediaItemCount > 0) AudioPlayerState.ENDED
                                else AudioPlayerState.IDLE
                            else -> null // noop
                        }
                        if (state != null && state != playerState) {
                            playerState = state
                        }
                    }
                    Player.EVENT_MEDIA_ITEM_TRANSITION -> {
                        playbackError = null
                        if (currentItem != null) {
                            playerState = AudioPlayerState.LOADING
                            if (isPlaying) {
                                playerState = AudioPlayerState.READY
                                playerState = AudioPlayerState.PLAYING
                            }
                        }
                    }
                    Player.EVENT_PLAY_WHEN_READY_CHANGED -> {
                        if (!player.playWhenReady && playerState != AudioPlayerState.STOPPED) {
                            playerState = AudioPlayerState.PAUSED
                        }
                    }
                    Player.EVENT_IS_PLAYING_CHANGED -> {
                        if (player.isPlaying) {
                            playerState = AudioPlayerState.PLAYING
                        }
                    }
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val _playbackError = PlaybackError(
                error.errorCodeName
                    .replace("ERROR_CODE_", "")
                    .lowercase(Locale.getDefault())
                    .replace("_", "-"),
                error.message
            )
            events.playbackError.emit(_playbackError)
            playbackError = _playbackError
            playerState = AudioPlayerState.ERROR
        }
    }

    inner class InnerForwardingPlayer(player: ExoPlayer): ForwardingPlayer(player) {
        // Block all external media item modifications to delegate control to RNTP.
        // These overrides prevent media controllers (like Android Auto, notifications)
        // from directly modifying the queue, ensuring all queue changes go through
        // the RNTP API for proper state management and event handling.

        override fun setMediaItems(mediaItems: MutableList<MediaItem>, resetPosition: Boolean) {
            return
        }

        override fun addMediaItems(mediaItems: MutableList<MediaItem>) {
            return
        }

        override fun addMediaItems(index: Int, mediaItems: MutableList<MediaItem>) {
            return
        }

        override fun setMediaItems(
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long
        ) {
            return
        }

        override fun setMediaItems(mediaItems: MutableList<MediaItem>) {
            return
        }

        override fun play() {
            events.onPlayerActionTriggeredExternally.emit(MediaSessionCallback.PLAY)
        }

        override fun pause() {
            events.onPlayerActionTriggeredExternally.emit(MediaSessionCallback.PAUSE)
        }

        override fun seekToNext() {
            events.onPlayerActionTriggeredExternally.emit(MediaSessionCallback.NEXT)
        }

        override fun seekToNextMediaItem() {
            events.onPlayerActionTriggeredExternally.emit(MediaSessionCallback.NEXT)
        }

        override fun seekToPrevious() {
            events.onPlayerActionTriggeredExternally.emit(MediaSessionCallback.PREVIOUS)
        }

        override fun seekToPreviousMediaItem() {
            events.onPlayerActionTriggeredExternally.emit(MediaSessionCallback.PREVIOUS)
        }

        override fun seekForward() {
            events.onPlayerActionTriggeredExternally.emit(MediaSessionCallback.FORWARD)
        }

        override fun seekBack() {
            events.onPlayerActionTriggeredExternally.emit(MediaSessionCallback.REWIND)
        }

        override fun stop() {
            events.onPlayerActionTriggeredExternally.emit(MediaSessionCallback.STOP)
        }

        override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
            events.onPlayerActionTriggeredExternally.emit(
                MediaSessionCallback.SEEK(
                    positionMs
                )
            )
        }

        override fun seekTo(positionMs: Long) {
            events.onPlayerActionTriggeredExternally.emit(
                MediaSessionCallback.SEEK(
                    positionMs
                )
            )
        }
    }

    inner class FocusManager {
        private var hasAudioFocus = false
        private var focus: AudioFocusRequestCompat? = null

        fun requestAudioFocus() {
            if (hasAudioFocus) return

            val manager = ContextCompat.getSystemService(context, AudioManager::class.java)

            focus = AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN)
                .setOnAudioFocusChangeListener(
                    { focusChange ->
                        Timber.d("Audio focus changed")
                        val isPermanent = focusChange == AudioManager.AUDIOFOCUS_LOSS
                        val isPaused = when (focusChange) {
                            AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> true
                            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> options.alwaysPauseOnInterruption
                            else -> false
                        }
                        if (!options.handleAudioFocus) {
                            if (isPermanent) focusManager.abandonAudioFocusIfHeld()

                            val isDucking = focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK
                                    && !options.alwaysPauseOnInterruption
                            if (isDucking) {
                                volumeMultiplier = 0.5f
                                wasDucking = true
                            } else if (wasDucking) {
                                volumeMultiplier = 1f
                                wasDucking = false
                            }
                        }
                        events.onAudioFocusChanged.emit(FocusChangeData(isPaused, isPermanent))
                    }
                )
                .setAudioAttributes(
                    AudioAttributesCompat.Builder()
                        .setUsage(AudioAttributesCompat.USAGE_MEDIA)
                        .setContentType(AudioAttributesCompat.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setWillPauseWhenDucked(options.alwaysPauseOnInterruption)
                .build()

            val result: Int = if (manager != null && focus != null) {
                AudioManagerCompat.requestAudioFocus(manager, focus!!)
            } else {
                AudioManager.AUDIOFOCUS_REQUEST_FAILED
            }

            hasAudioFocus = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        }

        fun abandonAudioFocusIfHeld() {
            if (!hasAudioFocus) return

            val manager = ContextCompat.getSystemService(context, AudioManager::class.java)

            val result: Int = if (manager != null && focus != null) {
                AudioManagerCompat.abandonAudioFocusRequest(manager, focus!!)
            } else {
                AudioManager.AUDIOFOCUS_REQUEST_FAILED
            }

            hasAudioFocus = (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        }
    }
}
