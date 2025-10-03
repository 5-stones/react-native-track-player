package com.doublesymmetry.trackplayer

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.support.v4.media.RatingCompat
import androidx.media3.common.Metadata
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import com.doublesymmetry.trackplayer.event.ControllerConnectedEvent
import com.doublesymmetry.trackplayer.event.ControllerDisconnectedEvent
import com.doublesymmetry.trackplayer.event.PlaybackActiveTrackChangedEvent
import com.doublesymmetry.trackplayer.event.PlaybackErrorEvent
import com.doublesymmetry.trackplayer.event.PlaybackPlayWhenReadyChangedEvent
import com.doublesymmetry.trackplayer.event.PlaybackProgressUpdatedEvent
import com.doublesymmetry.trackplayer.event.PlaybackQueueEndedEvent
import com.doublesymmetry.trackplayer.event.RemoteJumpBackwardEvent
import com.doublesymmetry.trackplayer.event.RemoteJumpForwardEvent
import com.doublesymmetry.trackplayer.event.RemoteSeekEvent
import com.doublesymmetry.trackplayer.event.RemoteSetRatingEvent
import com.doublesymmetry.trackplayer.extension.NumberExt.Companion.toSeconds
import com.doublesymmetry.trackplayer.model.PlaybackMetadata
import com.doublesymmetry.trackplayer.model.PlaybackState
import com.doublesymmetry.trackplayer.model.RatingType
import com.doublesymmetry.trackplayer.model.State
import com.doublesymmetry.trackplayer.model.TrackFactory
import com.doublesymmetry.trackplayer.model.TrackPlayerOptions
import com.doublesymmetry.trackplayer.model.bridge
import com.doublesymmetry.trackplayer.option.PlayerCapability
import com.doublesymmetry.trackplayer.option.PlayerRepeatMode
import com.doublesymmetry.trackplayer.util.AppForegroundTracker
import com.doublesymmetry.trackplayer.util.BundleUtils
import com.doublesymmetry.trackplayer.util.MetadataAdapter
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.WritableArray
import com.facebook.react.bridge.WritableMap
import com.facebook.react.module.annotations.ReactModule
import java.util.HashMap
import java.util.concurrent.TimeUnit
import javax.annotation.Nonnull
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import timber.log.Timber

@ReactModule(name = TrackPlayerModule.NAME)
class TrackPlayerModule(reactContext: ReactApplicationContext) :
  NativeTrackPlayerSpec(reactContext), ServiceConnection, TrackPlayerCallbacks {
  private lateinit var browser: MediaBrowser
  private var playerOptions: TrackPlayerOptions = TrackPlayerOptions()
  private var playerSetUpPromise: Promise? = null
  private val mainScope = MainScope()
  private var connectedService: TrackPlayerService? = null
  private val context = reactContext
  private val trackFactory =
    TrackFactory(context) { connectedService?.player?.ratingType ?: RatingCompat.RATING_NONE }

  @Nonnull
  override fun getName(): String {
    return NAME
  }

  companion object {
    const val NAME = "TrackPlayer"
  }

  override fun addListener(eventType: String) {
    // No implementation needed for TurboModule
    // This implements the abstract method required by NativeTrackPlayerSpec
  }

  override fun removeListeners(count: Double) {
    // No implementation needed for TurboModule
    // This implements the abstract method required by NativeTrackPlayerSpec
  }

  override fun initialize() {
    AppForegroundTracker.start()
  }

  override fun onServiceConnected(name: ComponentName, serviceBinder: IBinder) {
    launchInScope {
      // If a binder already exists, don't get a new one
      if (connectedService == null) {
        val binder: TrackPlayerService.MusicBinder = serviceBinder as TrackPlayerService.MusicBinder
        connectedService = binder.service
        connectedService?.setupPlayer(playerOptions, this@TrackPlayerModule)
        playerSetUpPromise?.resolve(null)
      }
    }
  }

  /** Called when a connection to the Service has been lost. */
  override fun onServiceDisconnected(name: ComponentName) {
    // Cancel all event observation coroutines when service disconnects
    mainScope.coroutineContext.cancelChildren()
    connectedService = null
  }

  /* ****************************** API ****************************** */
  override fun getTypedExportedConstants(): Map<String, Any> {
    return HashMap<String, Any>().apply {
      // Capabilities
      this["CAPABILITY_PLAY"] = PlayerCapability.PLAY.string
      this["CAPABILITY_PLAY_FROM_ID"] = PlayerCapability.PLAY_FROM_ID.string
      this["CAPABILITY_PLAY_FROM_SEARCH"] = PlayerCapability.PLAY_FROM_SEARCH.string
      this["CAPABILITY_PAUSE"] = PlayerCapability.PAUSE.string
      this["CAPABILITY_STOP"] = PlayerCapability.STOP.string
      this["CAPABILITY_SEEK_TO"] = PlayerCapability.SEEK_TO.string
      this["CAPABILITY_SKIP"] = PlayerCapability.SKIP.string
      this["CAPABILITY_SKIP_TO_NEXT"] = PlayerCapability.SKIP_TO_NEXT.string
      this["CAPABILITY_SKIP_TO_PREVIOUS"] = PlayerCapability.SKIP_TO_PREVIOUS.string
      this["CAPABILITY_SET_RATING"] = PlayerCapability.SET_RATING.string
      this["CAPABILITY_JUMP_FORWARD"] = PlayerCapability.JUMP_FORWARD.string
      this["CAPABILITY_JUMP_BACKWARD"] = PlayerCapability.JUMP_BACKWARD.string

      // States
      this["STATE_NONE"] = State.NONE.bridge
      this["STATE_READY"] = State.READY.bridge
      this["STATE_PLAYING"] = State.PLAYING.bridge
      this["STATE_PAUSED"] = State.PAUSED.bridge
      this["STATE_STOPPED"] = State.STOPPED.bridge
      this["STATE_BUFFERING"] = State.BUFFERING.bridge
      this["STATE_LOADING"] = State.LOADING.bridge

      // Rating Types
      this["RATING_HEART"] = RatingType.HEART.string
      this["RATING_THUMBS_UP_DOWN"] = RatingType.THUMBS_UP_DOWN.string
      this["RATING_3_STARS"] = RatingType.THREE_STARS.string
      this["RATING_4_STARS"] = RatingType.FOUR_STARS.string
      this["RATING_5_STARS"] = RatingType.FIVE_STARS.string
      this["RATING_PERCENTAGE"] = RatingType.PERCENTAGE.string

      // Repeat Modes
      this["REPEAT_OFF"] = PlayerRepeatMode.OFF.string
      this["REPEAT_TRACK"] = PlayerRepeatMode.ONE.string
      this["REPEAT_QUEUE"] = PlayerRepeatMode.ALL.string

      // Pitch Algorithm: No-op on android
      this["PITCH_ALGORITHM_LINEAR"] = "linear"
      this["PITCH_ALGORITHM_MUSIC"] = "music"
      this["PITCH_ALGORITHM_VOICE"] = "voice"
    }
  }

  @SuppressLint("UnspecifiedRegisterReceiverFlag")
  override fun setupPlayer(data: ReadableMap?, promise: Promise) {
    if (connectedService != null) {
      promise.reject(
        "player_already_initialized",
        "The player has already been initialized via setupPlayer.",
      )
      return
    }

    playerSetUpPromise = promise
    playerOptions = TrackPlayerOptions.fromBridge(data)

    val musicModule = this
    try {
      Intent(context, TrackPlayerService::class.java).also { intent ->
        context.bindService(intent, musicModule, Context.BIND_AUTO_CREATE)
        val sessionToken =
          SessionToken(context, ComponentName(context, TrackPlayerService::class.java))
        val browserFuture = MediaBrowser.Builder(context, sessionToken).buildAsync()
        // browser = browserFuture.get()
      }
    } catch (exception: Exception) {
      Timber.Forest.w(exception, "Could not initialize service")
      throw exception
    }
  }

  override fun updateOptions(data: ReadableMap?): Unit = runBlockingOnMain {
    val options = TrackPlayerOptions.fromBridge(data)

    // Store progress update interval for use during playback
    player.setProgressUpdateInterval(
      if (options.progressUpdateEventInterval > 0) options.progressUpdateEventInterval else null
    )

    service.updateOptions(options)
  }

  override fun add(data: ReadableArray, insertBeforeIndex: Double?): Unit = runBlockingOnMain {
    val inputIndex = insertBeforeIndex?.toInt() ?: -1
    val tracks = trackFactory.tracksFromBridge(data)
    player.add(tracks, inputIndex)
  }

  override fun load(data: ReadableMap?): Unit = runBlockingOnMain {
    data?.let { player.load(trackFactory.fromBridge(it)) }
  }

  override fun move(fromIndex: Double, toIndex: Double): Unit = runBlockingOnMain {
    player.move(fromIndex.toInt(), toIndex.toInt())
  }

  override fun remove(data: ReadableArray?): Unit = runBlockingOnMain {
    Arguments.toList(data)?.map { (it as Number).toInt() }?.let { player.remove(it) }
  }

  override fun updateMetadataForTrack(index: Double, map: ReadableMap?): Unit = runBlockingOnMain {
    map?.let {
      val currentTrack = player.getTrack(index.toInt())
      val updatedTrack =
        currentTrack.updateMetadata(
          title = it.getString("title") ?: currentTrack.title,
          artist = it.getString("artist") ?: currentTrack.artist,
          album = it.getString("album") ?: currentTrack.album,
          artwork = it.getString("artwork") ?: currentTrack.artwork,
          date = it.getString("date") ?: currentTrack.date,
          genre = it.getString("genre") ?: currentTrack.genre,
          duration = if (it.hasKey("duration")) it.getDouble("duration") else currentTrack.duration,
          rating = BundleUtils.getRating(it, "rating", player.ratingType) ?: currentTrack.rating,
          mediaId = it.getString("mediaId") ?: currentTrack.mediaId,
        )
      player.replaceTrack(index.toInt(), updatedTrack)
    }
  }

  override fun updateNowPlayingMetadata(map: ReadableMap?): Unit = runBlockingOnMain {
    if (player.isEmpty) {
      throw Exception("There is no current item in the player")
    }

    map?.let {
      val currentIndex = player.currentIndex ?: throw Exception("There is no current track")
      val currentTrack = player.currentTrack ?: throw Exception("There is no current track")
      val updatedTrack =
        currentTrack.updateMetadata(
          title = it.getString("title") ?: currentTrack.title,
          artist = it.getString("artist") ?: currentTrack.artist,
          album = it.getString("album") ?: currentTrack.album,
          artwork = it.getString("artwork") ?: currentTrack.artwork,
          date = it.getString("date") ?: currentTrack.date,
          genre = it.getString("genre") ?: currentTrack.genre,
          duration = if (it.hasKey("duration")) it.getDouble("duration") else currentTrack.duration,
          rating = BundleUtils.getRating(it, "rating", player.ratingType) ?: currentTrack.rating,
          mediaId = it.getString("mediaId") ?: currentTrack.mediaId,
        )
      player.replaceTrack(currentIndex, updatedTrack)
    }
  }

  override fun removeUpcomingTracks() = runBlockingOnMain { player.removeUpcomingTracks() }

  override fun skip(index: Double, initialTime: Double?) = runBlockingOnMain {
    player.skipTo(index.toInt())

    if (initialTime != null && initialTime >= 0) {
      player.seekTo((initialTime * 1000).toLong(), TimeUnit.MILLISECONDS)
    }
  }

  override fun skipToNext(initialTime: Double?) = runBlockingOnMain {
    player.next()

    if (initialTime != null && initialTime >= 0) {
      player.seekTo((initialTime * 1000).toLong(), TimeUnit.MILLISECONDS)
    }
  }

  override fun skipToPrevious(initialTime: Double?) = runBlockingOnMain {
    player.previous()

    if (initialTime != null && initialTime >= 0) {
      player.seekTo((initialTime * 1000).toLong(), TimeUnit.MILLISECONDS)
    }
  }

  override fun reset() = runBlockingOnMain {
    player.stop()
    delay(300) // Allow playback to stop
    player.clear()
  }

  override fun play() = runBlockingOnMain { player.play() }

  override fun pause() = runBlockingOnMain { player.pause() }

  override fun stop() = runBlockingOnMain { player.stop() }

  override fun seekTo(seconds: Double) = runBlockingOnMain {
    player.seekTo((seconds * 1000).toLong(), TimeUnit.MILLISECONDS)
  }

  override fun seekBy(offset: Double) = runBlockingOnMain {
    player.seekBy((offset * 1000).toLong(), TimeUnit.MILLISECONDS)
  }

  override fun retry() = runBlockingOnMain { player.prepare() }

  override fun setVolume(volume: Double) = runBlockingOnMain { player.volume = volume.toFloat() }

  override fun getVolume(): Double = runBlockingOnMain { player.volume.toDouble() }

  override fun setRate(rate: Double) = runBlockingOnMain { player.playbackSpeed = rate.toFloat() }

  override fun getRate(): Double = runBlockingOnMain { player.playbackSpeed.toDouble() }

  override fun setRepeatMode(mode: String) = runBlockingOnMain {
    player.repeatMode =
      PlayerRepeatMode.fromString(mode)
        ?: throw IllegalArgumentException("Invalid repeat mode value: $mode")
  }

  override fun getRepeatMode(): String = runBlockingOnMain { player.repeatMode.string }

  override fun setPlayWhenReady(playWhenReady: Boolean) = runBlockingOnMain {
    player.playWhenReady = playWhenReady
  }

  override fun getPlayWhenReady(): Boolean = runBlockingOnMain { player.playWhenReady }

  override fun getTrack(index: Double): WritableMap? = runBlockingOnMain {
    try {
      player.getTrack(index.toInt()).toBridge()
    } catch (e: IllegalArgumentException) {
      null
    }
  }

  override fun getQueue(): WritableArray = runBlockingOnMain {
    Arguments.fromList(player.tracks.map { it.toBridge() })
  }

  override fun setQueue(data: ReadableArray?): Unit = runBlockingOnMain {
    data?.let {
      player.clear()
      player.add(trackFactory.tracksFromBridge(data))
    }
  }

  override fun getActiveTrackIndex(): Double? = runBlockingOnMain {
    player.currentIndex?.toDouble()
  }

  override fun getActiveTrack(): WritableMap? = runBlockingOnMain {
    player.currentTrack?.toBridge()
  }

  override fun getProgress(): WritableMap = runBlockingOnMain {
    Arguments.createMap().let {
      it.putDouble("duration", player.duration.toSeconds())
      it.putDouble("position", player.position.toSeconds())
      it.putDouble("buffered", player.bufferedPosition.toSeconds())
      it
    }
  }

  override fun getPlaybackState(): WritableMap = runBlockingOnMain {
    player.getPlaybackState().toBridge()
  }

  override fun acquireWakeLock() = runBlockingOnMain { service.acquireWakeLock() }

  override fun abandonWakeLock() = runBlockingOnMain { service.abandonWakeLock() }

  override fun validateOnStartCommandIntent(): Boolean = runBlockingOnMain {
    service.onStartCommandIntentValid
  }

  // Bridgeless interop layer tries to pass the `Job` from `scope.launch` to the JS side
  // which causes an exception. We can work around this using a wrapper.
  private fun launchInScope(block: suspend () -> Unit) {
    mainScope.launch { block() }
  }

  private fun <T> runBlockingOnMain(block: suspend () -> T): T {
    return runBlocking(mainScope.coroutineContext) { block() }
  }

  private val service: TrackPlayerService
    get() = connectedService ?: throw Exception("Player not initialized")

  private val player
    get() = service.player

  // TrackPlayerCallbacks implementation
  override fun onPlaybackState(state: PlaybackState) {
    emitOnPlaybackState(state.toBridge())
  }

  override fun onPlaybackActiveTrackChanged(event: PlaybackActiveTrackChangedEvent) {
    emitOnPlaybackActiveTrackChanged(event.toBridge())
  }

  override fun onPlaybackProgressUpdated(event: PlaybackProgressUpdatedEvent) {
    emitOnPlaybackProgressUpdated(event.toBridge())
  }

  override fun onPlaybackPlayWhenReadyChanged(event: PlaybackPlayWhenReadyChangedEvent) {
    emitOnPlaybackPlayWhenReadyChanged(event.toBridge())
  }

  override fun onPlaybackQueueEnded(event: PlaybackQueueEndedEvent) {
    emitOnPlaybackQueueEnded(event.toBridge())
  }

  override fun onPlaybackError(event: PlaybackErrorEvent) {
    emitOnPlaybackError(event.toBridge())
  }

  override fun onMetadataCommonReceived(metadata: WritableMap) {
    emitOnMetadataCommonReceived(Arguments.createMap().apply { putMap("metadata", metadata) })
  }

  override fun onMetadataTimedReceived(metadata: Metadata) {
    emitOnMetadataTimedReceived(
      Arguments.createMap().let {
        it.putArray(
          "metadata",
          Arguments.createArray().apply {
            MetadataAdapter.Companion.fromMetadata(metadata).forEach { item -> pushMap(item) }
          },
        )
        it
      }
    )
  }

  override fun onPlaybackMetadata(metadata: PlaybackMetadata?) {
    metadata?.let {
      emitOnPlaybackMetadata(
        Arguments.createMap().apply {
          putString("source", it.source)
          putString("title", it.title)
          putString("url", it.url)
          putString("artist", it.artist)
          putString("album", it.album)
          putString("date", it.date)
          putString("genre", it.genre)
        }
      )
    }
  }

  override fun onRemotePlay() {
    emitOnRemotePlay(Arguments.createMap())
  }

  override fun onRemotePause() {
    emitOnRemotePause(Arguments.createMap())
  }

  override fun onRemoteStop() {
    emitOnRemoteStop(Arguments.createMap())
  }

  override fun onRemoteNext() {
    emitOnRemoteNext(Arguments.createMap())
  }

  override fun onRemotePrevious() {
    emitOnRemotePrevious(Arguments.createMap())
  }

  override fun onRemoteJumpForward(event: RemoteJumpForwardEvent) {
    emitOnRemoteJumpForward(event.toBridge())
  }

  override fun onRemoteJumpBackward(event: RemoteJumpBackwardEvent) {
    emitOnRemoteJumpBackward(event.toBridge())
  }

  override fun onRemoteSeek(event: RemoteSeekEvent) {
    emitOnRemoteSeek(event.toBridge())
  }

  override fun onRemoteSetRating(event: RemoteSetRatingEvent) {
    emitOnRemoteSetRating(event.toBridge())
  }

  override fun onControllerConnected(event: ControllerConnectedEvent) {
    emitOnAndroidControllerConnected(event.toBridge())
  }

  override fun onControllerDisconnected(event: ControllerDisconnectedEvent) {
    emitOnAndroidControllerDisconnected(event.toBridge())
  }
}
