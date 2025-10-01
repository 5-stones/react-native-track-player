package com.doublesymmetry.trackplayer

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.support.v4.media.RatingCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import com.doublesymmetry.trackplayer.extension.NumberExt.Companion.toSeconds
import com.doublesymmetry.trackplayer.event.AudioPlayerState
import com.doublesymmetry.trackplayer.option.Capability
import com.doublesymmetry.trackplayer.event.MediaSessionCallback
import com.doublesymmetry.trackplayer.event.PlaybackState
import com.doublesymmetry.trackplayer.option.PlayerRepeatMode
import com.doublesymmetry.trackplayer.event.bridge
import com.doublesymmetry.trackplayer.model.AudioPlayerOptionsData
import com.doublesymmetry.trackplayer.util.MetadataAdapter
import com.doublesymmetry.trackplayer.model.PlaybackMetadata
import com.doublesymmetry.trackplayer.model.TrackFactory
import com.doublesymmetry.trackplayer.player.PlaybackProgressUpdateManager
import com.doublesymmetry.trackplayer.util.AppForegroundTracker
import com.doublesymmetry.trackplayer.util.BundleUtils
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.WritableArray
import com.facebook.react.bridge.WritableMap
import com.facebook.react.module.annotations.ReactModule
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.util.ArrayList
import java.util.HashMap
import java.util.concurrent.TimeUnit
import javax.annotation.Nonnull

@ReactModule(name = TrackPlayerModule.NAME)
class TrackPlayerModule(reactContext: ReactApplicationContext) :
  NativeTrackPlayerSpec(reactContext),
    ServiceConnection {
  private lateinit var browser: MediaBrowser
  private var playerOptions: AudioPlayerOptionsData = AudioPlayerOptionsData()
  private var playerSetUpPromise: Promise? = null
  private val mainScope = MainScope()
  private var connectedService: TrackPlayerService? = null
  private val context = reactContext
  private var progressUpdateManager: PlaybackProgressUpdateManager? = null
  private val trackFactory =
      TrackFactory(context) { connectedService?.player?.ratingType ?: RatingCompat.RATING_NONE }
  private var eventObserver: PlayerEventObserver? = null

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
        progressUpdateManager = PlaybackProgressUpdateManager() {
            val service = connectedService ?: return@PlaybackProgressUpdateManager
            val currentIndex = service.player.currentIndex ?: return@PlaybackProgressUpdateManager
            emitOnPlaybackProgressUpdated(Arguments.createMap().apply {
                putDouble("position", service.player.position.toSeconds())
                putDouble("duration", service.player.duration.toSeconds())
                putDouble("buffered", service.player.bufferedPosition.toSeconds())
                putInt("track", currentIndex)
            })
        }
        connectedService?.setupPlayer(playerOptions)
        playerSetUpPromise?.resolve(null)
        setupEventObserver()
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
    eventObserver = null
    connectedService = null
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
      this["STATE_NONE"] = AudioPlayerState.IDLE.bridge
      this["STATE_READY"] = AudioPlayerState.READY.bridge
      this["STATE_PLAYING"] = AudioPlayerState.PLAYING.bridge
      this["STATE_PAUSED"] = AudioPlayerState.PAUSED.bridge
      this["STATE_STOPPED"] = AudioPlayerState.STOPPED.bridge
      this["STATE_BUFFERING"] = AudioPlayerState.BUFFERING.bridge
      this["STATE_LOADING"] = AudioPlayerState.LOADING.bridge

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
    playerOptions = AudioPlayerOptionsData.Companion.fromBridge(data)


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
    val options = AudioPlayerOptionsData.Companion.fromBridge(data)

    // Store progress update interval for use during playback
    progressUpdateManager?.setUpdateInterval(if (options.progressUpdateEventInterval > 0) options.progressUpdateEventInterval else null)

    service.updateOptions(options)
  }

  override fun add(data: ReadableArray, insertBeforeIndex: Double?): Double = runBlockingOnMain {
    val insertBeforeIndexInt = insertBeforeIndex?.toInt() ?: 0
    val tracks = trackFactory.tracksFromBridge(data)
    if (insertBeforeIndexInt < -1 || insertBeforeIndexInt > player.items.size) {
      throw Exception("The track index is out of bounds")
    }
    val index = if (insertBeforeIndexInt == -1) player.items.size else insertBeforeIndexInt
    player.add(tracks.map { it.toAudioItem() }, index)
    index.toDouble()
  }

  override fun load(data: ReadableMap?): Unit = runBlockingOnMain {
    data?.let {
      player.load(trackFactory.fromBridge(it).toAudioItem())
    }
  }

  override fun move(fromIndex: Double, toIndex: Double): Unit = runBlockingOnMain {
    player.move(fromIndex.toInt(), toIndex.toInt())
  }

  override fun remove(data: ReadableArray?) = runBlockingOnMain {
    val inputIndexes = Arguments.toList(data)
    if (inputIndexes != null) {
      val size = player.items.size
      val indexes: ArrayList<Int> = ArrayList()
      for (inputIndex in inputIndexes) {
        val index = if (inputIndex is Int) inputIndex else inputIndex.toString().toInt()
        if (index < 0 || index >= size) {
          throw Exception("One or more indexes was out of bounds")
        }
        indexes.add(index)
      }
      player.remove(indexes)
    }
  }

  override fun updateMetadataForTrack(index: Double, map: ReadableMap?): Unit = runBlockingOnMain {
    if (index < 0 || index >= player.items.size) {
      throw Exception("The index is out of bounds")
    }

    map?.let {
      val currentTrack = player.items[index.toInt()].track
        ?: throw Exception("Track not found at index ${index.toInt()}")
      val updatedTrack = currentTrack.updateMetadata(
        title = it.getString("title") ?: currentTrack.title,
        artist = it.getString("artist") ?: currentTrack.artist,
        album = it.getString("album") ?: currentTrack.album,
        artwork = it.getString("artwork") ?: currentTrack.artwork,
        date = it.getString("date") ?: currentTrack.date,
        genre = it.getString("genre") ?: currentTrack.genre,
        duration = if (it.hasKey("duration")) it.getDouble("duration") else currentTrack.duration,
        rating = BundleUtils.getRating(it, "rating", player.ratingType)
          ?: currentTrack.rating,
        mediaId = it.getString("mediaId") ?: currentTrack.mediaId
      )
      player.replaceItem(index.toInt(), updatedTrack.toAudioItem())
    }
  }

  override fun updateNowPlayingMetadata(map: ReadableMap?): Unit = runBlockingOnMain {
    if (player.items.isEmpty()) {
      throw Exception("There is no current item in the player")
    }

    map?.let {
      val currentIndex = player.currentIndex ?: throw Exception("There is no current track")
      val currentTrack = player.currentItem?.track ?: throw Exception("There is no current track")
      val updatedTrack = currentTrack.updateMetadata(
        title = it.getString("title") ?: currentTrack.title,
        artist = it.getString("artist") ?: currentTrack.artist,
        album = it.getString("album") ?: currentTrack.album,
        artwork = it.getString("artwork") ?: currentTrack.artwork,
        date = it.getString("date") ?: currentTrack.date,
        genre = it.getString("genre") ?: currentTrack.genre,
        duration = if (it.hasKey("duration")) it.getDouble("duration") else currentTrack.duration,
        rating = BundleUtils.getRating(it, "rating", player.ratingType)
          ?: currentTrack.rating,
        mediaId = it.getString("mediaId") ?: currentTrack.mediaId
      )
      player.replaceItem(currentIndex, updatedTrack.toAudioItem())
    }
  }

  override fun removeUpcomingTracks() = runBlockingOnMain {
    player.removeUpcomingItems()
  }

  override fun skip(index: Double, initialTime: Double?) = runBlockingOnMain {
    player.jumpToItem(index.toInt())

    if (initialTime != null && initialTime >= 0) {
      player.seek((initialTime * 1000).toLong(), TimeUnit.MILLISECONDS)
    }
  }

  override fun skipToNext(initialTime: Double?) = runBlockingOnMain {
    player.next()

    if (initialTime != null && initialTime >= 0) {
      player.seek((initialTime * 1000).toLong(), TimeUnit.MILLISECONDS)
    }
  }

  override fun skipToPrevious(initialTime: Double?) = runBlockingOnMain {
    player.previous()

    if (initialTime != null && initialTime >= 0) {
      player.seek((initialTime * 1000).toLong(), TimeUnit.MILLISECONDS)
    }
  }

  override fun reset() = runBlockingOnMain {
    player.stop()
      delay(300) // Allow playback to stop
    player.clear()
  }

  override fun play() = runBlockingOnMain {
    player.play()
  }

  override fun pause() = runBlockingOnMain {
    player.pause()
  }

  override fun stop() = runBlockingOnMain {
    player.stop()
  }

  override fun seekTo(seconds: Double) = runBlockingOnMain {
    player.seek((seconds * 1000).toLong(), TimeUnit.MILLISECONDS)
  }

  override fun seekBy(offset: Double) = runBlockingOnMain {
    player.seekBy((offset * 1000).toLong(), TimeUnit.MILLISECONDS)
  }

  override fun retry() = runBlockingOnMain {
    player.prepare()
  }

  override fun setVolume(volume: Double) = runBlockingOnMain {
    player.volume = volume.toFloat()
  }

  override fun getVolume(): Double = runBlockingOnMain {
    player.volume.toDouble()
  }

  override fun setRate(rate: Double) = runBlockingOnMain {
    player.playbackSpeed = rate.toFloat()
  }

  override fun getRate(): Double = runBlockingOnMain {
    player.playbackSpeed.toDouble()
  }

  override fun setRepeatMode(mode: Double) = runBlockingOnMain {
    val value = mode.toInt()
    player.repeatMode = PlayerRepeatMode.entries.getOrNull(value)
      ?: throw IllegalArgumentException("Invalid repeat mode value: $value (valid range: 0-${PlayerRepeatMode.entries.size - 1})")
  }

  override fun getRepeatMode(): Double = runBlockingOnMain {
    player.repeatMode.ordinal.toDouble()
  }

  override fun setPlayWhenReady(playWhenReady: Boolean) = runBlockingOnMain {
    player.playWhenReady = playWhenReady
  }

  override fun getPlayWhenReady(): Boolean = runBlockingOnMain {
    player.playWhenReady
  }

  override fun getTrack(index: Double): WritableMap? = runBlockingOnMain {
    val indexInt = index.toInt()
    if (indexInt >= 0 && indexInt < player.items.size) {
      player.items[indexInt].track?.toBridge()
    } else {
      null
    }
  }

  override fun getQueue(): WritableArray = runBlockingOnMain {
    Arguments.fromList(player.items.mapNotNull { it.track?.toBridge() })
  }

  override fun setQueue(data: ReadableArray?): Unit = runBlockingOnMain {
    data?.let {
      player.clear()
      player.add(trackFactory.tracksFromBridge(data).map { it.toAudioItem() })
    }
  }

  override fun getActiveTrackIndex(): Double? = runBlockingOnMain {
    player.currentIndex?.toDouble()
  }

  override fun getActiveTrack(): WritableMap? = runBlockingOnMain {
    player.currentItem?.track?.toBridge()
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
    PlaybackState(player.playerState, player.playbackError).toBridge()
  }

  override fun acquireWakeLock() = runBlockingOnMain {
    service.acquireWakeLock()
  }

  override fun abandonWakeLock() = runBlockingOnMain {
    service.abandonWakeLock()
  }

  override fun validateOnStartCommandIntent(): Boolean = runBlockingOnMain {
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

  private val service: TrackPlayerService
    get() = connectedService ?: throw Exception("Player not initialized")

  private val player
    get() = service.player

  private fun setupEventObserver() {
    connectedService ?: return

    eventObserver = PlayerEventObserver()

    eventObserver?.observeAll()
  }

  private inner class PlayerEventObserver {
    private var lastTrackIndex: Int? = null
    private var lastTrack: WritableMap? = null

    fun observeAll() {
      observeStateChange()
      observeAudioItemTransition()
      observePlayWhenReadyChange()
      observePlayerActionTriggeredExternally()
      observePositionChanged()
      observeQueueEnded()
      observePlaybackError()
      observeAudioFocusChanged()
      observeCommonMetadata()
      observeTimedMetadata()
      observeRatingChanged()
      observeControllerConnected()
      observeControllerDisconnected()
      observePlaybackResume()
    }

    private fun observeStateChange() {
      mainScope.launch {
        player.events.stateChange.collect { playbackState ->
          emitOnPlaybackState(playbackState.toBridge())
          progressUpdateManager?.onPlaybackStateChanged(playbackState.state)
        }
      }
    }

    private fun observeAudioItemTransition() {
      mainScope.launch {
        player.events.audioItemTransition.collect { transition ->
          emitOnPlaybackActiveTrackChanged(Arguments.createMap().apply {
            putDouble("lastPosition", transition.oldPosition.toSeconds())

            // Add last track info if available
            lastTrackIndex?.let { putInt("lastIndex", it) }
            lastTrack?.let { putMap("lastTrack", it) }

            // Add current track info
            player.currentIndex?.let { currentIndex ->
              putInt("index", currentIndex)
              player.currentItem?.track?.toBridge()?.let { putMap("track", it) }
            }
          })

          // Update last track info for next transition
          lastTrackIndex = player.currentIndex
          lastTrack = player.currentItem?.track?.toBridge()
        }
      }
    }

    private fun observePlayWhenReadyChange() {
      mainScope.launch {
        player.events.playWhenReadyChange.collect { playWhenReadyData ->
          emitOnPlaybackPlayWhenReadyChanged(Arguments.createMap().apply {
            putBoolean("playWhenReady", playWhenReadyData.playWhenReady)
          })
        }
      }
    }

    private fun observePlayerActionTriggeredExternally() {
      mainScope.launch {
        player.events.onPlayerActionTriggeredExternally.collect { mediaSessionAction ->
          when (mediaSessionAction) {
            MediaSessionCallback.PLAY -> {
              emitOnRemotePlay(Arguments.createMap())
            }

            MediaSessionCallback.PAUSE -> {
              emitOnRemotePause(Arguments.createMap())
            }

            MediaSessionCallback.NEXT -> {
              emitOnRemoteNext(Arguments.createMap())
            }

            MediaSessionCallback.PREVIOUS -> {
              emitOnRemotePrevious(Arguments.createMap())
            }

            MediaSessionCallback.STOP -> {
              emitOnRemoteStop(Arguments.createMap())
            }

            MediaSessionCallback.FORWARD -> {
              emitOnRemoteJumpForward(Arguments.createMap().apply {
                putInt("interval", playerOptions.forwardJumpInterval.toInt())
              })
            }

            MediaSessionCallback.REWIND -> {
              emitOnRemoteJumpBackward(Arguments.createMap().apply {
                putInt("interval", playerOptions.backwardJumpInterval.toInt())
              })
            }

            is MediaSessionCallback.RATING -> {
              emitOnRemoteSetRating(Arguments.createMap().apply {
                putString("rating", mediaSessionAction.rating.toString())
              })
            }

            is MediaSessionCallback.SEEK -> {
              emitOnRemoteSeek(Arguments.createMap().apply {
                putDouble("position", mediaSessionAction.positionMs.toDouble() / 1000.0)
              })
            }

            else -> {} // Handle other actions as needed
          }
        }
      }
    }

    private fun observePositionChanged() {
      mainScope.launch {
        player.events.positionChanged.collect {
          emitOnPlaybackProgressUpdated(Arguments.createMap().apply {
            putDouble("position", player.position.toSeconds())
            putDouble("buffered", player.bufferedPosition.toSeconds())
            putDouble("duration", player.duration.toSeconds())
          })
        }
      }
    }

    private fun observeQueueEnded() {
      mainScope.launch {
        player.events.stateChange.collect { playbackState ->
          if (playbackState.state == AudioPlayerState.ENDED && player.nextItem == null) {
            player.currentIndex?.let { currentIndex ->
              emitOnPlaybackQueueEnded(Arguments.createMap().apply {
                putInt("track", currentIndex)
                putDouble("position", player.position.toSeconds())
              })
            }
          }
        }
      }
    }

    private fun observePlaybackError() {
      mainScope.launch {
        player.events.playbackError.collect { error ->
          emitOnPlaybackError(error.toBridge())
        }
      }
    }

    private fun observeAudioFocusChanged() {
      mainScope.launch {
        player.events.onAudioFocusChanged.collect { focusChangeData ->
          emitOnRemoteDuck(Arguments.createMap().apply {
            putBoolean("permanent", focusChangeData.isFocusLostPermanently)
            putBoolean("paused", focusChangeData.isPaused)
          })
        }
      }
    }

    private fun observeCommonMetadata() {
      mainScope.launch {
        player.events.onCommonMetadata.collect { metadata ->
          emitOnMetadataCommonReceived(Arguments.createMap().apply {
            putMap("metadata", MetadataAdapter.Companion.mapFromMediaMetadata(metadata))
          })
        }
      }
    }

    private fun observeTimedMetadata() {
      mainScope.launch {
        player.events.onTimedMetadata.collect { metadata ->
          emitOnMetadataTimedReceived(Arguments.createMap().let {
            it.putArray("metadata", Arguments.createArray().apply {
              MetadataAdapter.Companion.fromMetadata(metadata)
                .forEach { item -> pushMap(item) }
            })
            it
          })

          // TODO: Handle the different types of metadata and publish to new events
          val playbackMetadata = PlaybackMetadata.Companion.fromId3Metadata(metadata)
            ?: PlaybackMetadata.Companion.fromIcy(metadata)
            ?: PlaybackMetadata.Companion.fromVorbisComment(metadata)
            ?: PlaybackMetadata.Companion.fromQuickTime(metadata)

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
    }

    private fun observeRatingChanged() {
      mainScope.launch {
        player.events.onRatingChanged.collect { rating ->
          emitOnRemoteSetRating(Arguments.createMap().apply {
            putString("rating", rating.toString())
          })
        }
      }
    }

    private fun observeControllerConnected() {
      mainScope.launch {
        player.events.onControllerConnected.collect { controllerData ->
          emitOnAndroidControllerConnected(Arguments.createMap().apply {
            putString("package", controllerData.packageName)
            putBoolean(
              "isMediaNotificationController",
              controllerData.isMediaNotificationController
            )
            putBoolean("isAutomotiveController", controllerData.isAutomotiveController)
            putBoolean("isAutoCompanionController", controllerData.isAutoCompanionController)
          })
        }
      }
    }

    private fun observeControllerDisconnected() {
      mainScope.launch {
        player.events.onControllerDisconnected.collect { controllerName ->
          emitOnAndroidControllerDisconnected(Arguments.createMap().apply {
            putString("package", controllerName)
          })
        }
      }
    }

    private fun observePlaybackResume() {
      mainScope.launch {
        player.events.onPlaybackResume.collect { packageName ->
          emitOnAndroidPlaybackResume(Arguments.createMap().apply {
            putString("package", packageName)
          })
        }
      }
    }
  }

}
