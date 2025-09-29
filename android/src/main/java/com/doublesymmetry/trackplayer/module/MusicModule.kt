package com.doublesymmetry.trackplayer.module

import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.module.annotations.ReactModule
import android.annotation.SuppressLint
import android.content.*
import com.facebook.react.bridge.WritableMap
import android.os.IBinder
import android.support.v4.media.RatingCompat
import com.doublesymmetry.kotlinaudio.models.Capability
import com.doublesymmetry.kotlinaudio.models.RepeatMode
import com.doublesymmetry.trackplayer.model.State
import com.doublesymmetry.trackplayer.model.TrackFactory
import com.doublesymmetry.trackplayer.service.MusicService
import com.doublesymmetry.trackplayer.utils.AppForegroundTracker
import com.doublesymmetry.trackplayer.model.PlayerOptionsData
import com.doublesymmetry.trackplayer.utils.BundleUtils
import com.facebook.react.bridge.*
import androidx.media3.common.Player
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import com.doublesymmetry.kotlinaudio.models.MediaSessionCallback
import com.doublesymmetry.kotlinaudio.models.AudioPlayerState
import com.doublesymmetry.kotlinaudio.models.PlaybackError
import com.doublesymmetry.trackplayer.model.PlaybackMetadata
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelChildren
import timber.log.Timber
import java.util.*
import javax.annotation.Nonnull
import com.doublesymmetry.trackplayer.NativeTrackPlayerSpec
import com.doublesymmetry.trackplayer.extensions.NumberExt.Companion.toSeconds
import com.doublesymmetry.trackplayer.extensions.asLibState
import com.doublesymmetry.trackplayer.model.MetadataAdapter
import com.doublesymmetry.trackplayer.service.MusicService.Companion.ERROR_KEY
import com.doublesymmetry.trackplayer.service.MusicService.Companion.STATE_KEY
import kotlinx.coroutines.runBlocking


@ReactModule(name = MusicModule.NAME)
class MusicModule(reactContext: ReactApplicationContext) : NativeTrackPlayerSpec(reactContext),
  ServiceConnection {
  private lateinit var browser: MediaBrowser
  private var playerOptions: PlayerOptionsData = PlayerOptionsData()
  private var playerSetUpPromise: Promise? = null
  private val mainScope = MainScope()
  private var connectedService: MusicService? = null
  private val context = reactContext
  private var progressUpdateManager: ProgressUpdateManager? = null
  private val trackFactory = TrackFactory(context) { connectedService?.ratingType ?: RatingCompat.RATING_NONE }

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
        val binder: MusicService.MusicBinder = serviceBinder as MusicService.MusicBinder
        connectedService = binder.service
        progressUpdateManager = ProgressUpdateManager() {
          val service = connectedService ?: return@ProgressUpdateManager
          emitOnPlaybackProgressUpdated(Arguments.createMap().apply {
            putDouble("position", service.getPositionInSeconds())
            putDouble("duration", service.getDurationInSeconds())
            putDouble("buffered", service.getBufferedPositionInSeconds())
            putInt("track", service.getCurrentTrackIndex())
          })
        }
        connectedService?.setupPlayer(playerOptions)
        playerSetUpPromise?.resolve(null)
        observePlayerEvents()
      }

    }
  }

  /**
   * Called when a connection to the Service has been lost.
   */
  override fun onServiceDisconnected(name: ComponentName) {
    // Cancel all event observation coroutines when service disconnects
    progressUpdateManager?.stop()
    mainScope.coroutineContext.cancelChildren()
    connectedService = null
  }



  private fun getPlaybackErrorMap(error: PlaybackError?): WritableMap {
    return Arguments.createMap().let {
      if (error?.message != null) {
        it.putString("message", error.message)
      }
      if (error?.code != null) {
        it.putString("code", "android-" + error.code)
      }
      it
    }
  }

  private fun getPlayerStateMap(state: AudioPlayerState): WritableMap {
    return Arguments.createMap().let {
      it.putString(STATE_KEY, state.asLibState.state)
      if (state == AudioPlayerState.ERROR) {
        it.putMap(ERROR_KEY, getPlaybackErrorMap(connectedService?.player?.playbackError))
      }
      it
    }
  }

  /* ****************************** API ****************************** */
  override fun getTypedExportedConstants(): Map<String, Any> {
    return HashMap<String, Any>().apply {
      // Capabilities
      this["CAPABILITY_PLAY"] = Capability.PLAY.ordinal
      this["CAPABILITY_PLAY_FROM_ID"] = Capability.PLAY_FROM_ID.ordinal
      this["CAPABILITY_PLAY_FROM_SEARCH"] = Capability.PLAY_FROM_SEARCH.ordinal
      this["CAPABILITY_PAUSE"] = Capability.PAUSE.ordinal
      this["CAPABILITY_STOP"] = Capability.STOP.ordinal
      this["CAPABILITY_SEEK_TO"] = Capability.SEEK_TO.ordinal
      this["CAPABILITY_SKIP"] = OnErrorAction.SKIP.ordinal
      this["CAPABILITY_SKIP_TO_NEXT"] = Capability.SKIP_TO_NEXT.ordinal
      this["CAPABILITY_SKIP_TO_PREVIOUS"] = Capability.SKIP_TO_PREVIOUS.ordinal
      this["CAPABILITY_SET_RATING"] = Capability.SET_RATING.ordinal
      this["CAPABILITY_JUMP_FORWARD"] = Capability.JUMP_FORWARD.ordinal
      this["CAPABILITY_JUMP_BACKWARD"] = Capability.JUMP_BACKWARD.ordinal

      // States
      this["STATE_NONE"] = State.None.state
      this["STATE_READY"] = State.Ready.state
      this["STATE_PLAYING"] = State.Playing.state
      this["STATE_PAUSED"] = State.Paused.state
      this["STATE_STOPPED"] = State.Stopped.state
      this["STATE_BUFFERING"] = State.Buffering.state
      this["STATE_LOADING"] = State.Loading.state

      // Rating Types
      this["RATING_HEART"] = RatingCompat.RATING_HEART
      this["RATING_THUMBS_UP_DOWN"] = RatingCompat.RATING_THUMB_UP_DOWN
      this["RATING_3_STARS"] = RatingCompat.RATING_3_STARS
      this["RATING_4_STARS"] = RatingCompat.RATING_4_STARS
      this["RATING_5_STARS"] = RatingCompat.RATING_5_STARS
      this["RATING_PERCENTAGE"] = RatingCompat.RATING_PERCENTAGE

      // Repeat Modes
      this["REPEAT_OFF"] = Player.REPEAT_MODE_OFF
      this["REPEAT_TRACK"] = Player.REPEAT_MODE_ONE
      this["REPEAT_QUEUE"] = Player.REPEAT_MODE_ALL

      // Pitch Algorithm: No-op on android
      this["PITCH_ALGORITHM_LINEAR"] = -1
      this["PITCH_ALGORITHM_MUSIC"] = -2
      this["PITCH_ALGORITHM_VOICE"] = -3
    }
  }

  @SuppressLint("UnspecifiedRegisterReceiverFlag")
  override fun setupPlayer(data: ReadableMap?, promise: Promise) {
    if (connectedService != null) {
      promise.reject(
        "player_already_initialized",
        "The player has already been initialized via setupPlayer."
      )
      return
    }

    playerSetUpPromise = promise
    playerOptions = PlayerOptionsData.fromBridge(data)


    val musicModule = this
    try {
      Intent(context, MusicService::class.java).also { intent ->
        context.bindService(intent, musicModule, Context.BIND_AUTO_CREATE)
        val sessionToken =
          SessionToken(context, ComponentName(context, MusicService::class.java))
        val browserFuture = MediaBrowser.Builder(context, sessionToken).buildAsync()
        // browser = browserFuture.get()
      }
    } catch (exception: Exception) {
      Timber.w(exception, "Could not initialize service")
      throw exception
    }
  }

  override fun updateOptions(data: ReadableMap?): Unit = runBlockingOnMain {
    val service = requireService()

    val options = PlayerOptionsData.fromBridge(data)

    // Store progress update interval for use during playback
    progressUpdateManager?.setUpdateInterval(if (options.progressUpdateEventInterval > 0) options.progressUpdateEventInterval else null)

    service.updateOptions(options)
  }

  override fun add(data: ReadableArray, insertBeforeIndex: Double?): Double = runBlockingOnMain {
    val service = requireService()

    val insertBeforeIndexInt = insertBeforeIndex?.toInt() ?: 0
    val tracks = trackFactory.tracksFromBridge(data)
    if (insertBeforeIndexInt < -1 || insertBeforeIndexInt > service.tracks.size) {
      throw Exception("The track index is out of bounds")
    }
    val index = if (insertBeforeIndexInt == -1) service.tracks.size else insertBeforeIndexInt
    service.add(tracks, index)
    index.toDouble()
  }

  override fun load(data: ReadableMap?) = runBlockingOnMain {
    val service = requireService()
    if (data == null) return@runBlockingOnMain

    service.load(trackFactory.fromBridge(data))
  }

  override fun move(fromIndex: Double, toIndex: Double) = runBlockingOnMain {
    val service = requireService()
    service.move(fromIndex.toInt(), toIndex.toInt())
  }

  override fun remove(data: ReadableArray?) = runBlockingOnMain {
    val service = requireService()
    val inputIndexes = Arguments.toList(data)
    if (inputIndexes != null) {
      val size = service.tracks.size
      val indexes: ArrayList<Int> = ArrayList()
      for (inputIndex in inputIndexes) {
        val index = if (inputIndex is Int) inputIndex else inputIndex.toString().toInt()
        if (index < 0 || index >= size) {
          throw Exception("One or more indexes was out of bounds")
        }
        indexes.add(index)
      }
      service.remove(indexes)
    }
  }

  override fun updateMetadataForTrack(index: Double, map: ReadableMap?): Unit = runBlockingOnMain {
    val service = requireService()

    if (index < 0 || index >= service.tracks.size) {
      throw Exception("The index is out of bounds")
    }

    map?.let {
      val currentTrack = service.tracks[index.toInt()]
      val updatedTrack = currentTrack.updateMetadata(
        title = it.getString("title") ?: currentTrack.title,
        artist = it.getString("artist") ?: currentTrack.artist,
        album = it.getString("album") ?: currentTrack.album,
        artwork = it.getString("artwork") ?: currentTrack.artwork,
        date = it.getString("date") ?: currentTrack.date,
        genre = it.getString("genre") ?: currentTrack.genre,
        duration = if (it.hasKey("duration")) it.getDouble("duration") else currentTrack.duration,
        rating = BundleUtils.getRating(it, "rating", service.ratingType)
          ?: currentTrack.rating,
        mediaId = it.getString("mediaId") ?: currentTrack.mediaId
      )
      service.updateMetadataForTrack(index.toInt(), updatedTrack)
    }
  }

  override fun updateNowPlayingMetadata(map: ReadableMap?): Unit = runBlockingOnMain {
    val service = requireService()

    if (service.tracks.isEmpty()) {
      throw Exception("There is no current item in the player")
    }

    map?.let {
      val currentTrack = service.currentTrack ?: throw Exception("There is no current track")
      val updatedTrack = currentTrack.updateMetadata(
        title = it.getString("title") ?: currentTrack.title,
        artist = it.getString("artist") ?: currentTrack.artist,
        album = it.getString("album") ?: currentTrack.album,
        artwork = it.getString("artwork") ?: currentTrack.artwork,
        date = it.getString("date") ?: currentTrack.date,
        genre = it.getString("genre") ?: currentTrack.genre,
        duration = if (it.hasKey("duration")) it.getDouble("duration") else currentTrack.duration,
        rating = BundleUtils.getRating(it, "rating", service.ratingType)
          ?: currentTrack.rating,
        mediaId = it.getString("mediaId") ?: currentTrack.mediaId
      )
      service.updateNowPlayingMetadata(updatedTrack)
    }
  }

  override fun removeUpcomingTracks() = runBlockingOnMain {
    val service = requireService()
    service.removeUpcomingTracks()
  }

  override fun skip(index: Double, initialTime: Double?) = runBlockingOnMain {
    val service = requireService()

    service.skip(index.toInt())

    if (initialTime != null && initialTime >= 0) {
      service.seekTo(initialTime.toFloat())
    }
  }

  override fun skipToNext(initialTime: Double?) = runBlockingOnMain {
    val service = requireService()

    service.skipToNext()

    if (initialTime != null && initialTime >= 0) {
      service.seekTo(initialTime.toFloat())
    }
  }

  override fun skipToPrevious(initialTime: Double?) = runBlockingOnMain {
    val service = requireService()

    service.skipToPrevious()

    if (initialTime != null && initialTime >= 0) {
      service.seekTo(initialTime.toFloat())
    }
  }

  override fun reset() = runBlockingOnMain {
    val service = requireService()

    service.stop()
    delay(300) // Allow playback to stop
    service.clear()
  }

  override fun play() = runBlockingOnMain {
    val service = requireService()
    service.play()
  }

  override fun pause() = runBlockingOnMain {
    val service = requireService()
    service.pause()
  }

  override fun stop() = runBlockingOnMain {
    val service = requireService()
    service.stop()
  }

  override fun seekTo(seconds: Double) = runBlockingOnMain {
    val service = requireService()
    service.seekTo(seconds.toFloat())
  }

  override fun seekBy(offset: Double) = runBlockingOnMain {
    val service = requireService()
    service.seekBy(offset.toFloat())
  }

  override fun retry() = runBlockingOnMain {
    val service = requireService()
    service.retry()
  }

  override fun setVolume(volume: Double) = runBlockingOnMain {
    val service = requireService()
    service.setVolume(volume.toFloat())
  }

  override fun getVolume(): Double = runBlockingOnMain {
    val service = requireService()
    service.getVolume().toDouble()
  }

  override fun setRate(rate: Double) = runBlockingOnMain {
    val service = requireService()
    service.setRate(rate.toFloat())
  }

  override fun getRate(): Double = runBlockingOnMain {
    val service = requireService()
    service.getRate().toDouble()
  }

  override fun setRepeatMode(mode: Double) = runBlockingOnMain {
    val service = requireService()
    service.setRepeatMode(RepeatMode.fromOrdinal(mode.toInt()))
  }

  override fun getRepeatMode(): Double = runBlockingOnMain {
    val service = requireService()
    service.getRepeatMode().ordinal.toDouble()
  }

  override fun setPlayWhenReady(playWhenReady: Boolean) = runBlockingOnMain {
    val service = requireService()
    service.playWhenReady = playWhenReady
  }

  override fun getPlayWhenReady(): Boolean = runBlockingOnMain {
    val service = requireService()
    service.playWhenReady
  }

  override fun getTrack(index: Double): WritableMap? = runBlockingOnMain {
    val service = requireService()
    val indexInt = index.toInt()
    if (indexInt >= 0 && indexInt < service.tracks.size) {
      service.tracks[indexInt].toBridge()
    } else {
      null
    }
  }

  override fun getQueue(): WritableArray = runBlockingOnMain {
    val service = requireService()
    Arguments.fromList(service.tracks.map { it.toBridge() })
  }

  override fun setQueue(data: ReadableArray?): Unit = runBlockingOnMain {
    val service = requireService()
    data?.let {
      service.clear()
      service.add(trackFactory.tracksFromBridge(data))
    }
  }

  override fun getActiveTrackIndex(): Double? = runBlockingOnMain {
    val service = requireService()
    if (service.tracks.isEmpty()) null else service.getCurrentTrackIndex().toDouble()
  }

  override fun getActiveTrack(): WritableMap? = runBlockingOnMain {
    val service = requireService()
    service.currentTrack?.toBridge()
  }

  override fun getProgress(): WritableMap = runBlockingOnMain {
    val service = requireService()
    Arguments.createMap().let {
      it.putDouble("duration", service.getDurationInSeconds())
      it.putDouble("position", service.getPositionInSeconds())
      it.putDouble("buffered", service.getBufferedPositionInSeconds())
      it
    }
  }

  override fun getPlaybackState(): WritableMap = runBlockingOnMain {
    val service = requireService()
    getPlayerStateMap(service.state)
  }

  override fun acquireWakeLock() = runBlockingOnMain {
    val service = requireService()
    service.acquireWakeLock()
  }

  override fun abandonWakeLock() = runBlockingOnMain {
    val service = requireService()
    service.abandonWakeLock()
  }

  override fun validateOnStartCommandIntent(): Boolean = runBlockingOnMain {
    val service = requireService()
    service.onStartCommandIntentValid
  }

  // Bridgeless interop layer tries to pass the `Job` from `scope.launch` to the JS side
  // which causes an exception. We can work around this using a wrapper.
  private fun launchInScope(block: suspend () -> Unit) {
    mainScope.launch {
      block()
    }
  }

  private fun <T> runBlockingOnMain(block: suspend () -> T): T {
    return runBlocking(mainScope.coroutineContext) {
      block()
    }
  }

  private fun requireService(): MusicService {
    return connectedService ?: throw Exception("Player not initialized")
  }

  private fun observePlayerEvents() {
    mainScope.launch {
      val service = connectedService ?: return@launch
      service.event.stateChange.collect { state ->
        emitOnPlaybackState(getPlayerStateMap(state))

        // Let progress manager handle playback state changes
        progressUpdateManager?.onPlaybackStateChanged(state)
      }
    }

    mainScope.launch {
      val service = connectedService ?: return@launch
      service.event.audioItemTransition.collect { transition ->
        if (transition != null) {
          emitOnPlaybackActiveTrackChanged(Arguments.createMap().apply {
            putMap("track", service.player.currentItem?.track?.toBridge())
            putDouble("position", transition.oldPosition.toSeconds())
          })
        }
      }
    }

    mainScope.launch {
      val service = connectedService ?: return@launch
      service.event.playWhenReadyChange.collect { playWhenReadyData ->
        emitOnPlaybackPlayWhenReadyChanged(Arguments.createMap().apply {
          putBoolean("playWhenReady", playWhenReadyData.playWhenReady)
        })
      }
    }

    mainScope.launch {
      val service = connectedService ?: return@launch
      service.event.onPlayerActionTriggeredExternally.collect { mediaSessionAction ->
        when (mediaSessionAction) {
          MediaSessionCallback.PLAY -> {
            val readableMap = Arguments.createMap()
            emitOnRemotePlay(readableMap)
          }

          MediaSessionCallback.PAUSE -> {
            val readableMap = Arguments.createMap()
            emitOnRemotePause(readableMap)
          }

          MediaSessionCallback.NEXT -> {
            val readableMap = Arguments.createMap()
            emitOnRemoteNext(readableMap)
          }

          MediaSessionCallback.PREVIOUS -> {
            val readableMap = Arguments.createMap()
            emitOnRemotePrevious(readableMap)
          }

          MediaSessionCallback.STOP -> {
            val readableMap = Arguments.createMap()
            emitOnRemoteStop(readableMap)
          }

          MediaSessionCallback.FORWARD -> {
            val readableMap = Arguments.createMap().apply {
              putInt("interval", playerOptions.forwardJumpInterval.toInt())
            }
            emitOnRemoteJumpForward(readableMap)
          }

          MediaSessionCallback.REWIND -> {
            val readableMap = Arguments.createMap().apply {
              putInt("interval", playerOptions.backwardJumpInterval.toInt())
            }
            emitOnRemoteJumpBackward(readableMap)
          }

          is MediaSessionCallback.RATING -> {
            val readableMap = Arguments.createMap().apply {
              putString("rating", mediaSessionAction.rating.toString())
            }
            emitOnRemoteSetRating(readableMap)
          }

          is MediaSessionCallback.SEEK -> {
            val readableMap = Arguments.createMap().apply {
              putDouble("position", mediaSessionAction.positionMs.toDouble() / 1000.0)
            }
            emitOnRemoteSeek(readableMap)
          }

          else -> {} // Handle other actions as needed
        }
      }
    }

    // Progress updates and remote seek detection
    mainScope.launch {
      val service = connectedService ?: return@launch
      service.event.positionChanged.collect { positionChangedReason ->
        emitOnPlaybackProgressUpdated(Arguments.createMap().apply {
          putDouble("position", service.getPositionInSeconds())
          putDouble("buffered", service.getBufferedPositionInSeconds())
          putDouble("duration", service.getDurationInSeconds())
        })
      }
    }

    // Queue ended events
    mainScope.launch {
      val service = connectedService ?: return@launch
      service.event.stateChange.collect { state ->
        if (state == AudioPlayerState.ENDED && service.player.nextItem == null) {
          emitOnPlaybackQueueEnded(Arguments.createMap().apply {
            putInt("track", service.player.currentIndex)
            putDouble("position", service.getPositionInSeconds())
          })
        }
      }
    }

    // Player errors
    mainScope.launch {
      val service = connectedService ?: return@launch
      service.event.playbackError.collect { error ->
        emitOnPlaybackError(getPlaybackErrorMap(error))
      }
    }

    // Audio focus changes (duck events)
    mainScope.launch {
      val service = connectedService ?: return@launch
      service.event.onAudioFocusChanged.collect { focusChangeData ->
        emitOnRemoteDuck(Arguments.createMap().apply {
          putBoolean(
            "permanent",
            focusChangeData.isFocusLostPermanently
          )
          putBoolean("paused", focusChangeData.isPaused)
        })
      }
    }


    // Metadata events
    mainScope.launch {
      val service = connectedService ?: return@launch
      service.event.onCommonMetadata.collect { metadata ->
        emitOnMetadataCommonReceived(Arguments.createMap().apply {
          putMap("metadata", MetadataAdapter.mapFromMediaMetadata(metadata))
        })
      }
    }

    mainScope.launch {
      val service = connectedService ?: return@launch
      service.event.onTimedMetadata.collect { metadata ->
        emitOnMetadataTimedReceived(Arguments.createMap().let {
          it.putArray("metadata", Arguments.createArray().apply {
            MetadataAdapter.fromMetadata(metadata)
              .forEach { item -> pushMap(item) }
          })
          it
        })

        // TODO: Handle the different types of metadata and publish to new events
        val playbackMetadata = PlaybackMetadata.fromId3Metadata(metadata)
          ?: PlaybackMetadata.fromIcy(metadata)
          ?: PlaybackMetadata.fromVorbisComment(metadata)
          ?: PlaybackMetadata.fromQuickTime(metadata)

        if (playbackMetadata != null) {
          emitOnPlaybackMetadata(Arguments.createMap().apply {
            putString("source", playbackMetadata.source)
            putString("title", playbackMetadata.title)
            putString("url", playbackMetadata.url)
            putString("artist", playbackMetadata.artist)
            putString("album", playbackMetadata.album)
            putString("date", playbackMetadata.date)
            putString("genre", playbackMetadata.genre)
          })
        }
      }
    }

    // Rating events
    mainScope.launch {
      val service = connectedService ?: return@launch
      service.event.onRatingChanged.collect { rating ->
        emitOnRemoteSetRating(Arguments.createMap().apply {
          putString("rating", rating.toString())
        })
      }
    }

    // Controller connection events
    mainScope.launch {
      val service = connectedService ?: return@launch
      service.event.onControllerConnected.collect { controllerData ->
        emitOnAndroidControllerConnected(Arguments.createMap().apply {
          putString("package", controllerData.packageName)
          putBoolean("isMediaNotificationController", controllerData.isMediaNotificationController)
          putBoolean("isAutomotiveController", controllerData.isAutomotiveController)
          putBoolean("isAutoCompanionController", controllerData.isAutoCompanionController)
        })
      }
    }

    mainScope.launch {
      val service = connectedService ?: return@launch
      service.event.onControllerDisconnected.collect { controllerName ->
        emitOnAndroidControllerDisconnected(Arguments.createMap().apply {
          putString("package", controllerName)
        })
      }
    }

    // Playback resume events
    mainScope.launch {
      val service = connectedService ?: return@launch
      service.event.onPlaybackResume.collect { packageName ->
        emitOnAndroidPlaybackResume(Arguments.createMap().apply {
          putString("package", packageName)
        })
      }
    }
  }

}
