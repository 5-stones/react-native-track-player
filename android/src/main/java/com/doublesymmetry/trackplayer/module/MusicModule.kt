package com.doublesymmetry.trackplayer.module

import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.module.annotations.ReactModule
import android.annotation.SuppressLint
import android.content.*
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.support.v4.media.RatingCompat
import com.doublesymmetry.kotlinaudio.models.Capability
import com.doublesymmetry.kotlinaudio.models.RepeatMode
import com.doublesymmetry.trackplayer.model.State
import com.doublesymmetry.trackplayer.model.Track
import com.doublesymmetry.trackplayer.module.MusicEvents.Companion.EVENT_INTENT
import com.doublesymmetry.trackplayer.service.MusicService
import com.doublesymmetry.trackplayer.utils.AppForegroundTracker
import com.doublesymmetry.trackplayer.utils.RejectionException
import com.facebook.react.bridge.*
import androidx.media3.common.Player
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.*
import javax.annotation.Nonnull
import com.doublesymmetry.trackplayer.NativeTrackPlayerSpec
import kotlinx.coroutines.runBlocking

/**
 * @author Milen Pivchev @mpivchev
 */
@ReactModule(name = MusicModule.NAME)
class MusicModule(reactContext: ReactApplicationContext) : NativeTrackPlayerSpec(reactContext),
  ServiceConnection {
  private lateinit var browser: MediaBrowser
  private var playerOptions: Bundle? = null
  private var isServiceBound = false
  private var playerSetUpPromise: Promise? = null
  private val mainScope = MainScope()
  private lateinit var service: MusicService
  private val context = reactContext

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
      if (!::service.isInitialized) {
        val binder: MusicService.MusicBinder = serviceBinder as MusicService.MusicBinder
        service = binder.service
        service.setupPlayer(playerOptions)
        playerSetUpPromise?.resolve(null)
      }

      isServiceBound = true
    }
  }

  /**
   * Called when a connection to the Service has been lost.
   */
  override fun onServiceDisconnected(name: ComponentName) {
    launchInScope {
      isServiceBound = false
    }
  }


  private fun bundleToTrack(bundle: Bundle): Track {
    return Track(context, bundle, service.ratingType)
  }


  private fun readableArrayToTrackList(data: ReadableArray?): MutableList<Track> {
    val bundleList = Arguments.toList(data)
    if (bundleList !is ArrayList) {
      throw RejectionException("invalid_parameter", "Was not given an array of tracks")
    }
    return bundleList.map {
      if (it is Bundle) {
        bundleToTrack(it)
      } else {
        throw RejectionException(
          "invalid_track_object",
          "Track was not a dictionary type"
        )
      }
    }.toMutableList()
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
    if (isServiceBound) {
      promise.reject(
        "player_already_initialized",
        "The player has already been initialized via setupPlayer."
      )
      return
    }

    val bundledData = Arguments.toBundle(data)

    playerSetUpPromise = promise
    playerOptions = bundledData

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      context.registerReceiver(
        MusicEvents(context),
        IntentFilter(EVENT_INTENT), Context.RECEIVER_NOT_EXPORTED
      )
    } else {
      context.registerReceiver(
        MusicEvents(context),
        IntentFilter(EVENT_INTENT)
      )
    }

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
    if (!isServiceBound) return@runBlockingOnMain

    val options = Arguments.toBundle(data)

    options?.let {
      service.updateOptions(it)
    }
  }

  // override fun add(data: Double, y: Double): Double {
  //   return 1.0
  // }
  override fun add(data: ReadableArray, insertBeforeIndex: Double?): Double = runBlockingOnMain {
    if (!isServiceBound) throw Exception("Player not initialized")

    val insertBeforeIndexInt = insertBeforeIndex?.toInt() ?: 0
    val tracks = readableArrayToTrackList(data)
    if (insertBeforeIndexInt < -1 || insertBeforeIndexInt > service.tracks.size) {
      throw Exception("The track index is out of bounds")
    }
    val index = if (insertBeforeIndexInt == -1) service.tracks.size else insertBeforeIndexInt
    service.add(tracks, index)
    index.toDouble()
  }

  override fun load(data: ReadableMap?) = runBlockingOnMain {
    if (!isServiceBound) throw Exception("Player not initialized")
    if (data == null) return@runBlockingOnMain

    val bundle = Arguments.toBundle(data)
    if (bundle is Bundle) {
      service.load(bundleToTrack(bundle))
    } else {
      throw Exception("Track was not a dictionary type")
    }
  }

  override fun move(fromIndex: Double, toIndex: Double) = runBlockingOnMain {
    if (!isServiceBound) throw Exception("Player not initialized")
    service.move(fromIndex.toInt(), toIndex.toInt())
  }

  override fun remove(data: ReadableArray?) = runBlockingOnMain {
    if (!isServiceBound) throw Exception("Player not initialized")
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
    if (!isServiceBound) throw Exception("Player not initialized")

    if (index < 0 || index >= service.tracks.size) {
      throw Exception("The index is out of bounds")
    }

    Arguments.toBundle(map)?.let {
      service.updateMetadataForTrack(index.toInt(), it)
    }
  }

  override fun updateNowPlayingMetadata(map: ReadableMap?): Unit = runBlockingOnMain {
    if (!isServiceBound) throw Exception("Player not initialized")

    if (service.tracks.isEmpty()) {
      throw Exception("There is no current item in the player")
    }

    Arguments.toBundle(map)?.let {
      service.updateNowPlayingMetadata(it)
    }
  }

  override fun removeUpcomingTracks() = runBlockingOnMain {
    if (!isServiceBound) throw Exception("Player not initialized")
    service.removeUpcomingTracks()
  }

  override fun skip(index: Double, initialTime: Double?) = runBlockingOnMain {
    if (!isServiceBound) throw Exception("Player not initialized")

    service.skip(index.toInt())

    if (initialTime != null && initialTime >= 0) {
      service.seekTo(initialTime.toFloat())
    }
  }

  override fun skipToNext(initialTime: Double?) = runBlockingOnMain {
    if (!isServiceBound) throw Exception("Player not initialized")

    service.skipToNext()

    if (initialTime != null && initialTime >= 0) {
      service.seekTo(initialTime.toFloat())
    }
  }

  override fun skipToPrevious(initialTime: Double?) = runBlockingOnMain {
    if (!isServiceBound) throw Exception("Player not initialized")

    service.skipToPrevious()

    if (initialTime != null && initialTime >= 0) {
      service.seekTo(initialTime.toFloat())
    }
  }

  override fun reset() = runBlockingOnMain {
    if (!isServiceBound) throw Exception("Player not initialized")

    service.stop()
    delay(300) // Allow playback to stop
    service.clear()
  }

  override fun play() = runBlockingOnMain {
    if (!isServiceBound) return@runBlockingOnMain
    service.play()
  }

  override fun pause() = runBlockingOnMain {
    if (!isServiceBound) throw Exception("Player not initialized")
    service.pause()
  }

  override fun stop() = runBlockingOnMain {
    if (!isServiceBound) throw Exception("Player not initialized")
    service.stop()
  }

  override fun seekTo(seconds: Double) = runBlockingOnMain {
    if (!isServiceBound) throw Exception("Player not initialized")
    service.seekTo(seconds.toFloat())
  }

  override fun seekBy(offset: Double) = runBlockingOnMain {
    if (!isServiceBound) throw Exception("Player not initialized")
    service.seekBy(offset.toFloat())
  }

  override fun retry() = runBlockingOnMain {
    if (!isServiceBound) throw Exception("Player not initialized")
    service.retry()
  }

  override fun setVolume(volume: Double) = runBlockingOnMain {
    if (!isServiceBound) throw Exception("Player not initialized")
    service.setVolume(volume.toFloat())
  }

  override fun getVolume(): Double = runBlockingOnMain {
    if (!isServiceBound) throw Exception("Player not initialized")
    service.getVolume().toDouble()
  }

  override fun setRate(rate: Double) = runBlockingOnMain {
    if (!isServiceBound) throw Exception("Player not initialized")
    service.setRate(rate.toFloat())
  }

  override fun getRate(): Double = runBlockingOnMain {
    if (!isServiceBound) throw Exception("Player not initialized")
    service.getRate().toDouble()
  }

  override fun setRepeatMode(mode: Double) = runBlockingOnMain {
    if (!isServiceBound) throw Exception("Player not initialized")
    service.setRepeatMode(RepeatMode.fromOrdinal(mode.toInt()))
  }

  override fun getRepeatMode(): Double = runBlockingOnMain {
    if (!isServiceBound) throw Exception("Player not initialized")
    service.getRepeatMode().ordinal.toDouble()
  }

  override fun setPlayWhenReady(playWhenReady: Boolean) = runBlockingOnMain {
    if (!isServiceBound) throw Exception("Player not initialized")
    service.playWhenReady = playWhenReady
  }

  override fun getPlayWhenReady(): Boolean = runBlockingOnMain {
    if (!isServiceBound) throw Exception("Player not initialized")
    service.playWhenReady
  }

  override fun getTrack(index: Double): WritableMap? = runBlockingOnMain {
    if (!isServiceBound) throw Exception("Player not initialized")
    val indexInt = index.toInt()
    if (indexInt >= 0 && indexInt < service.tracks.size) {
      Arguments.fromBundle(service.tracks[indexInt].originalItem)
    } else {
      null
    }
  }

  override fun getQueue(): WritableArray = runBlockingOnMain {
    Arguments.fromList(service.tracks.map { it.originalItem })
  }

  override fun setQueue(data: ReadableArray?) = runBlockingOnMain {
    if (!isServiceBound) throw Exception("Player not initialized")

    service.clear()
    service.add(readableArrayToTrackList(data))
  }

  override fun getActiveTrackIndex(): Double? = runBlockingOnMain {
    if (!isServiceBound) throw Exception("Player not initialized")
    if (service.tracks.isEmpty()) null else service.getCurrentTrackIndex().toDouble()
  }

  override fun getActiveTrack(): WritableMap? = runBlockingOnMain {
    if (!isServiceBound) throw Exception("Player not initialized")
    service.currentTrack?.let {
      Arguments.fromBundle(it.originalItem)
    }
  }

  override fun getProgress(): WritableMap = runBlockingOnMain {
    if (!isServiceBound) throw Exception("Player not initialized")
    val bundle = Bundle()
    bundle.putDouble("duration", service.getDurationInSeconds())
    bundle.putDouble("position", service.getPositionInSeconds())
    bundle.putDouble("buffered", service.getBufferedPositionInSeconds())
    Arguments.fromBundle(bundle)
  }

  override fun getPlaybackState(): WritableMap = runBlockingOnMain {
    if (!isServiceBound) throw Exception("Player not initialized")
    Arguments.fromBundle(service.getPlayerStateBundle(service.state))
  }

  override fun acquireWakeLock() = runBlockingOnMain {
    if (!isServiceBound) throw Exception("Player not initialized")
    service.acquireWakeLock()
  }

  override fun abandonWakeLock() = runBlockingOnMain {
    if (!isServiceBound) throw Exception("Player not initialized")
    service.abandonWakeLock()
  }

  override fun validateOnStartCommandIntent(): Boolean = runBlockingOnMain {
    if (!isServiceBound) throw Exception("Player not initialized")
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
}
