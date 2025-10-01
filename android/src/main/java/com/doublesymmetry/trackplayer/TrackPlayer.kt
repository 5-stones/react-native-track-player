package com.doublesymmetry.trackplayer

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.IllegalSeekPositionException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.legacy.RatingCompat
import com.doublesymmetry.trackplayer.model.AudioItem
import com.doublesymmetry.trackplayer.event.AudioPlayerState
import com.doublesymmetry.trackplayer.event.PlaybackError
import com.doublesymmetry.trackplayer.event.PlaybackState
import com.doublesymmetry.trackplayer.option.PlayerOptions
import com.doublesymmetry.trackplayer.option.PlayerRepeatMode
import com.doublesymmetry.trackplayer.player.AudioFocusManager
import com.doublesymmetry.trackplayer.player.PlayerEvents
import com.doublesymmetry.trackplayer.player.ForwardingPlayer
import com.doublesymmetry.trackplayer.player.PlayerListener
import com.doublesymmetry.trackplayer.player.MediaFactory
import com.doublesymmetry.trackplayer.util.PlayerCache
import timber.log.Timber
import java.util.concurrent.TimeUnit

@UnstableApi
class TrackPlayer(
    internal val context: Context,
    val options: PlayerOptions = PlayerOptions()
) {

    val exoPlayer: ExoPlayer
    val forwardingPlayer: ForwardingPlayer
    val player: Player
        get() {
            return options.interceptPlayerActionsTriggeredExternally
                .takeIf { it }
                ?.let { forwardingPlayer }
                ?: exoPlayer
        }
    private var playerListener: PlayerListener
    private var cache: SimpleCache? = null
    val events = PlayerEvents()

    var wasDucking = false
        internal set
    private val focusManager: AudioFocusManager = AudioFocusManager(this)

    var alwaysPauseOnInterruption: Boolean
        get() = options.alwaysPauseOnInterruption
        set(v) { options.alwaysPauseOnInterruption = v }

    val currentItem: AudioItem?
        get() = exoPlayer.currentMediaItem?.let { AudioItem.fromMediaItem(it) }

    var playbackError: PlaybackError? = null
        internal set

    internal var playerState: AudioPlayerState = AudioPlayerState.IDLE
        private set

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

    internal var oldPosition = 0L

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

    var repeatMode: PlayerRepeatMode
        get() {
            return when (exoPlayer.repeatMode) {
                Player.REPEAT_MODE_ALL -> PlayerRepeatMode.ALL
                Player.REPEAT_MODE_ONE -> PlayerRepeatMode.ONE
                else -> PlayerRepeatMode.OFF
            }
        }
        set(value) {
            when (value) {
                PlayerRepeatMode.ALL -> exoPlayer.repeatMode = Player.REPEAT_MODE_ALL
                PlayerRepeatMode.ONE -> exoPlayer.repeatMode = Player.REPEAT_MODE_ONE
                PlayerRepeatMode.OFF -> exoPlayer.repeatMode = Player.REPEAT_MODE_OFF
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
            cache = PlayerCache.initCache(context, options.cacheSizeKb)
        }
        events.stateChange.emit(PlaybackState(AudioPlayerState.IDLE))

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
        forwardingPlayer = ForwardingPlayer(exoPlayer, events)
        playerListener = PlayerListener(this)
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

    // Helper methods for TrackPlayerListener
    internal fun updateOldPosition(position: Long) {
        oldPosition = position
    }

    internal fun setPlayerState(state: AudioPlayerState) {
        if (state != playerState) {
            playerState = state
            events.stateChange.emit(PlaybackState(state, playbackError))
            if (!options.handleAudioFocus) {
                when (state) {
                    AudioPlayerState.IDLE,
                    AudioPlayerState.ERROR -> focusManager.abandonAudioFocusIfHeld()
                    AudioPlayerState.READY -> focusManager.requestAudioFocus()
                    else -> {}
                }
            }
        }
    }

    internal fun clearPlaybackError() {
        playbackError = null
    }

    // Helper method for AudioFocusManager to update volume multiplier
    internal fun setVolumeMultiplier(multiplier: Float) {
        volumeMultiplier = multiplier
        volume = volume  // Trigger volume recalculation
    }
}
