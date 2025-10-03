package com.doublesymmetry.trackplayer

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.legacy.RatingCompat
import com.doublesymmetry.trackplayer.event.PlaybackActiveTrackChangedEvent
import com.doublesymmetry.trackplayer.event.PlaybackError
import com.doublesymmetry.trackplayer.model.PlaybackState
import com.doublesymmetry.trackplayer.model.State
import com.doublesymmetry.trackplayer.model.Track
import com.doublesymmetry.trackplayer.option.PlayerOptions
import com.doublesymmetry.trackplayer.option.PlayerRepeatMode
import com.doublesymmetry.trackplayer.player.ForwardingPlayer
import com.doublesymmetry.trackplayer.player.MediaFactory
import com.doublesymmetry.trackplayer.player.PlayerEvents
import com.doublesymmetry.trackplayer.player.PlayerListener
import com.doublesymmetry.trackplayer.event.PlaybackProgressUpdatedEvent
import com.doublesymmetry.trackplayer.extension.NumberExt.Companion.toSeconds
import com.doublesymmetry.trackplayer.player.PlaybackProgressUpdateManager
import com.doublesymmetry.trackplayer.util.PlayerCache
import java.util.concurrent.TimeUnit

@UnstableApi
class TrackPlayer(internal val context: Context, val options: PlayerOptions = PlayerOptions()) {

  val exoPlayer: ExoPlayer
  val forwardingPlayer: ForwardingPlayer
  val player: Player
    get() {
      return options.interceptPlayerActionsTriggeredExternally
        .takeIf { it }
        ?.let { forwardingPlayer } ?: exoPlayer
    }

  private var playerListener: PlayerListener
  private var cache: SimpleCache? = null
  val events = PlayerEvents()

  private val progressUpdateManager: PlaybackProgressUpdateManager by lazy {
    PlaybackProgressUpdateManager { handleProgressUpdate() }
  }

  val currentTrack: Track?
    get() = exoPlayer.currentMediaItem?.let { Track.fromMediaItem(it) }

  private var lastTrack: Track? = null
  private var lastIndex: Int? = null

  var playbackError: PlaybackError? = null
    internal set

  internal var playerState: State = State.NONE
    private set

  fun getPlaybackState(): PlaybackState {
    return PlaybackState(playerState, playbackError)
  }

  internal fun emitActiveTrackChanged(lastPosition: Double) {
    val event =
      PlaybackActiveTrackChangedEvent(
        lastIndex = lastIndex,
        lastTrack = lastTrack,
        lastPosition = lastPosition,
        index = currentIndex,
        track = currentTrack,
      )
    events.currentTrackChange.emit(event)

    // Update last track info for next transition
    lastTrack = currentTrack
    lastIndex = currentIndex
  }

  var playWhenReady: Boolean
    get() = exoPlayer.playWhenReady
    set(value) {
      exoPlayer.playWhenReady = value
    }

  val duration: Long
    get() {
      return if (exoPlayer.duration == C.TIME_UNSET) 0 else exoPlayer.duration
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

  var volume: Float
    get() = exoPlayer.volume
    set(value) {
      exoPlayer.volume = value
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
    get() =
      if (exoPlayer.currentMediaItemIndex == C.INDEX_UNSET) null
      else exoPlayer.currentMediaItemIndex

  var shuffleMode
    get() = exoPlayer.shuffleModeEnabled
    set(v) {
      exoPlayer.shuffleModeEnabled = v
    }

  val trackCount: Int
    get() = exoPlayer.mediaItemCount

  val isEmpty: Boolean
    get() = exoPlayer.mediaItemCount == 0

  val tracks: List<Track>
    get() =
      (0 until exoPlayer.mediaItemCount).map { index ->
        Track.fromMediaItem(exoPlayer.getMediaItemAt(index))
      }

  val isLastTrack: Boolean
    get() = exoPlayer.currentMediaItemIndex == exoPlayer.mediaItemCount - 1

  /**
   * Get track at index with bounds checking.
   *
   * @param index The index of the track to retrieve.
   * @throws IllegalArgumentException if index is out of bounds.
   */
  fun getTrack(index: Int): Track {
    if (index < 0 || index >= exoPlayer.mediaItemCount) {
      throw IllegalArgumentException(
        "Track index $index is out of bounds (size: ${exoPlayer.mediaItemCount})"
      )
    }
    return Track.fromMediaItem(exoPlayer.getMediaItemAt(index))
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
          else TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
        )
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
    events.stateChange.emit(PlaybackState(State.NONE))

    val renderer = DefaultRenderersFactory(context)
    renderer.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

    val loadControl = run {
      val bufferConfig = options.bufferOptions
      val multiplier =
        DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS /
          DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS
      val minBuffer =
        bufferConfig.minBuffer?.takeIf { it != 0 } ?: DefaultLoadControl.DEFAULT_MIN_BUFFER_MS
      val maxBuffer =
        bufferConfig.maxBuffer?.takeIf { it != 0 } ?: DefaultLoadControl.DEFAULT_MAX_BUFFER_MS
      val playBuffer =
        bufferConfig.playBuffer?.takeIf { it != 0 }
          ?: DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS
      val backBuffer =
        bufferConfig.backBuffer?.takeIf { it != 0 }
          ?: DefaultLoadControl.DEFAULT_BACK_BUFFER_DURATION_MS
      DefaultLoadControl.Builder()
        .setBufferDurationsMs(minBuffer, maxBuffer, playBuffer, playBuffer * multiplier)
        .setBackBuffer(backBuffer, false)
        .build()
    }

    exoPlayer =
      ExoPlayer.Builder(context)
        .setRenderersFactory(renderer)
        .setHandleAudioBecomingNoisy(options.handleAudioBecomingNoisy)
        .setMediaSourceFactory(MediaFactory(context, cache))
        .setWakeMode(options.wakeMode.toExoPlayer())
        .setLoadControl(loadControl)
        .setSkipSilenceEnabled(options.skipSilence)
        .setName("kotlin-audio-player")
        .build()

    val audioAttributes =
      AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(options.audioContentType.toExoPlayer())
        .build()
    exoPlayer.setAudioAttributes(audioAttributes, true)
    forwardingPlayer = ForwardingPlayer(exoPlayer, events)
    playerListener = PlayerListener(this)
    player.addListener(playerListener)
  }

  /**
   * Loads a track into the player. If there is a current track, it will be replaced. If the queue
   * is empty, the track will be added.
   *
   * @param track The [Track] to load.
   */
  fun load(track: Track) {
    if (exoPlayer.mediaItemCount == 0) {
      add(track)
    } else {
      val index = exoPlayer.currentMediaItemIndex
      replaceTrack(index, track)
      exoPlayer.seekTo(index, C.TIME_UNSET)
      exoPlayer.prepare()
    }
  }

  /**
   * Add a single track to the queue. If the AudioPlayer has no track loaded, it will load the
   * `track`.
   *
   * @param track The [Track] to add.
   */
  fun add(track: Track) {
    val mediaSource = track.toMediaItem()
    exoPlayer.addMediaItem(mediaSource)
    exoPlayer.prepare()
  }

  /**
   * Add multiple tracks to the queue. If the AudioPlayer has no track loaded, it will load the
   * first track in the list.
   *
   * @param tracks The [Track]s to add.
   */
  fun add(tracks: List<Track>) {
    val mediaItems = tracks.map { it.toMediaItem() }
    exoPlayer.addMediaItems(mediaItems)
    exoPlayer.prepare()
  }

  /**
   * Add multiple tracks to the queue.
   *
   * @param tracks The [Track]s to add.
   * @param atIndex Index to insert tracks at. Use -1 to append to the end of the queue.
   * @throws IllegalArgumentException if index is out of bounds.
   */
  fun add(tracks: List<Track>, atIndex: Int) {
    validateInsertIndex(atIndex)
    val index = if (atIndex == -1) exoPlayer.mediaItemCount else atIndex
    val mediaItems = tracks.map { it.toMediaItem() }
    exoPlayer.addMediaItems(index, mediaItems)
    exoPlayer.prepare()
  }

  /**
   * Remove a track from the queue.
   *
   * @param index The index of the track to remove.
   * @throws IllegalArgumentException if index is out of bounds.
   */
  fun remove(index: Int) {
    validateIndex(index)
    exoPlayer.removeMediaItem(index)
  }

  /**
   * Remove tracks from the queue.
   *
   * @param indexes The indexes of the tracks to remove.
   * @throws IllegalArgumentException if any index is out of bounds or if duplicate indexes are
   *   provided.
   */
  fun remove(indexes: List<Int>) {
    if (indexes.toSet().size != indexes.size) {
      throw IllegalArgumentException("Duplicate indexes provided")
    }
    indexes.forEach { validateIndex(it) }
    val sorted = indexes.sortedDescending()
    sorted.forEach { exoPlayer.removeMediaItem(it) }
  }

  /**
   * Skip to the next track in the queue, which may depend on the current repeat mode. Does nothing
   * if there is no next track to skip to.
   */
  fun next() {
    exoPlayer.seekToNextMediaItem()
    exoPlayer.prepare()
  }

  /**
   * Skip to the previous track in the queue, which may depend on the current repeat mode. Does
   * nothing if there is no previous track to skip to.
   */
  fun previous() {
    exoPlayer.seekToPreviousMediaItem()
    exoPlayer.prepare()
  }

  /**
   * Move an track in the queue from one position to another.
   *
   * @param fromIndex The index of the track to move.
   * @param toIndex The index to move the track to. If the index is larger than the size of the
   *   queue, the track is moved to the end of the queue instead.
   * @throws IllegalArgumentException if fromIndex is out of bounds.
   */
  fun move(fromIndex: Int, toIndex: Int) {
    validateIndex(fromIndex)
    exoPlayer.moveMediaItem(fromIndex, toIndex)
  }

  /**
   * Skips to a track in the queue.
   *
   * @param index the index to skip to
   * @throws IllegalArgumentException if index is out of bounds.
   */
  fun skipTo(index: Int) {
    validateIndex(index)
    exoPlayer.seekTo(index, C.TIME_UNSET)
    exoPlayer.prepare()
  }

  /**
   * Replaces track at index in queue.
   *
   * @throws IllegalArgumentException if index is out of bounds.
   */
  fun replaceTrack(index: Int, track: Track) {
    validateIndex(index)
    val mediaItem = track.toMediaItem()
    exoPlayer.replaceMediaItem(index, mediaItem)
  }

  /** Removes all the upcoming tracks, if any (the ones returned by [next]). */
  fun removeUpcomingTracks() {
    val index = exoPlayer.currentMediaItemIndex
    if (index == C.INDEX_UNSET) return
    val lastIndex = exoPlayer.mediaItemCount
    val fromIndex = index + 1

    exoPlayer.removeMediaItems(fromIndex, lastIndex)
  }

  fun play() {
    exoPlayer.play()
    if (currentTrack != null) {
      exoPlayer.prepare()
    }
  }

  fun prepare() {
    if (currentTrack != null) {
      exoPlayer.prepare()
    }
  }

  fun pause() {
    exoPlayer.pause()
  }

  /**
   * Stops playback, without clearing the active track. Calling this method will cause the playback
   * state to transition to State.NONE and the player will release the loaded media and resources
   * required for playback.
   */
  fun stop() {
    playerState = State.STOPPED
    exoPlayer.playWhenReady = false
    exoPlayer.stop()
  }

  fun clear() {
    exoPlayer.clearMediaItems()
  }

  /**
   * Stops and destroys the player. Only call this when you are finished using the player, otherwise
   * use [pause].
   */
  fun destroy() {
    stop()
    player.removeListener(playerListener)
    exoPlayer.release()
    cache?.release()
    cache = null
  }

  fun seekTo(duration: Long, unit: TimeUnit) {
    val positionMs = TimeUnit.MILLISECONDS.convert(duration, unit)
    exoPlayer.seekTo(positionMs)
  }

  fun seekBy(offset: Long, unit: TimeUnit) {
    val positionMs = exoPlayer.currentPosition + TimeUnit.MILLISECONDS.convert(offset, unit)
    exoPlayer.seekTo(positionMs)
  }

  /**
   * Updates the player state and emits a state change event if the state has changed. Only emits an
   * event if the new state differs from the current state.
   *
   * @param state The new player state to set
   */
  internal fun setPlayerState(state: State) {
    if (state != playerState) {
      playerState = state
      events.stateChange.emit(PlaybackState(state, playbackError))
      progressUpdateManager.onPlaybackStateChanged(state)
    }
  }

  /**
   * Sets the progress update interval.
   *
   * @param interval The interval in seconds, or null to disable progress updates
   */
  fun setProgressUpdateInterval(interval: Double?) {
    progressUpdateManager.setUpdateInterval(interval)
  }

  /**
   * Handles progress updates by emitting a progress event.
   */
  private fun handleProgressUpdate() {
    val index = currentIndex ?: return
    val event =
      PlaybackProgressUpdatedEvent(
        position = position.toSeconds(),
        duration = duration.toSeconds(),
        buffered = bufferedPosition.toSeconds(),
        track = index,
      )
    events.progressUpdate.emit(event)
  }

  /**
   * Validates that an index is within bounds [0, trackCount).
   *
   * @param index The index to validate.
   * @throws IllegalArgumentException if index is out of bounds.
   */
  private fun validateIndex(index: Int) {
    if (index < 0 || index >= exoPlayer.mediaItemCount) {
      throw IllegalArgumentException(
        "Track index $index is out of bounds (size: ${exoPlayer.mediaItemCount})"
      )
    }
  }

  /**
   * Validates that an insertion index is within bounds [0, trackCount] or -1 (append).
   *
   * @param index The index to validate.
   * @throws IllegalArgumentException if index is out of bounds.
   */
  private fun validateInsertIndex(index: Int) {
    if (index < -1 || index > exoPlayer.mediaItemCount) {
      throw IllegalArgumentException(
        "Insert index $index is out of bounds (size: ${exoPlayer.mediaItemCount}, use -1 to append)"
      )
    }
  }
}
