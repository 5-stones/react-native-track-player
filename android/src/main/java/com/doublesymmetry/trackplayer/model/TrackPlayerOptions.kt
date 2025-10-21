package com.doublesymmetry.trackplayer.model

import com.doublesymmetry.trackplayer.extension.NumberExt.Companion.toMilliseconds
import com.doublesymmetry.trackplayer.option.AudioContentType
import com.doublesymmetry.trackplayer.option.BufferOptions
import com.doublesymmetry.trackplayer.option.PlayerCapability
import com.doublesymmetry.trackplayer.option.PlayerOptions
import com.doublesymmetry.trackplayer.option.PlayerWakeMode
import com.facebook.react.bridge.ReadableMap

data class TrackPlayerOptions(
  var forwardJumpInterval: Double = 15.0,
  var backwardJumpInterval: Double = 15.0,
  var progressUpdateEventInterval: Double = -1.0,
  var ratingType: RatingType? = null,
  var capabilities: List<PlayerCapability>? = null,
  var notificationCapabilities: List<PlayerCapability>? = null,

  // Audio engine options
  var minBuffer: Double? = null,
  var maxBuffer: Double? = null,
  var playBuffer: Double? = null,
  var rebufferBuffer: Double? = null,
  var backBuffer: Double? = null,
  var maxCacheSize: Double = 0.0,
  var audioContentType: AudioContentType = AudioContentType.MUSIC,
  var handleAudioBecomingNoisy: Boolean = true,
  var wakeMode: PlayerWakeMode = PlayerWakeMode.NONE,

  // Android-specific options
  var audioOffload: Boolean? = null,
  var skipSilence: Boolean? = null,
  var appKilledPlaybackBehavior: String? = null,
  var shuffle: Boolean? = null,
) {
  companion object {
    fun fromBridge(map: ReadableMap?): TrackPlayerOptions {
      if (map == null) return TrackPlayerOptions()

      val capabilities =
        map.getArray("capabilities")?.let { arr ->
          (0 until arr.size()).mapNotNull { index ->
            val value = arr.getString(index) ?: return@mapNotNull null
            PlayerCapability.fromString(value)
              ?: throw IllegalArgumentException("Invalid capability value: $value")
          }
        }

      val notificationCapabilities =
        map.getArray("notificationCapabilities")?.let { arr ->
          (0 until arr.size()).mapNotNull { index ->
            val value = arr.getString(index) ?: return@mapNotNull null
            PlayerCapability.fromString(value)
              ?: throw IllegalArgumentException("Invalid notificationCapability value: $value")
          }
        }

      val androidMap = if (map.hasKey("android")) map.getMap("android") else null

      return TrackPlayerOptions(
        forwardJumpInterval =
          if (map.hasKey("forwardJumpInterval")) map.getDouble("forwardJumpInterval") else 15.0,
        backwardJumpInterval =
          if (map.hasKey("backwardJumpInterval")) map.getDouble("backwardJumpInterval") else 15.0,
        progressUpdateEventInterval =
          if (map.hasKey("progressUpdateEventInterval"))
            map.getDouble("progressUpdateEventInterval")
          else -1.0,
        ratingType = map.getString("ratingType")?.let { RatingType.fromString(it) },
        capabilities = capabilities,
        notificationCapabilities = notificationCapabilities,

        // Audio engine options
        minBuffer = if (map.hasKey("minBuffer")) map.getDouble("minBuffer") else null,
        maxBuffer = if (map.hasKey("maxBuffer")) map.getDouble("maxBuffer") else null,
        playBuffer = if (map.hasKey("playBuffer")) map.getDouble("playBuffer") else null,
        rebufferBuffer =
          if (map.hasKey("rebufferBuffer")) map.getDouble("rebufferBuffer") else null,
        backBuffer = if (map.hasKey("backBuffer")) map.getDouble("backBuffer") else null,
        maxCacheSize = if (map.hasKey("maxCacheSize")) map.getDouble("maxCacheSize") else 0.0,
        audioContentType =
          AudioContentType.fromString(map.getString("audioContentType") ?: "music"),
        handleAudioBecomingNoisy =
          if (map.hasKey("handleAudioBecomingNoisy")) map.getBoolean("handleAudioBecomingNoisy")
          else true,
        wakeMode =
          if (map.hasKey("wakeMode")) {
            val value = map.getInt("wakeMode")
            PlayerWakeMode.entries.getOrNull(value)
              ?: throw IllegalArgumentException(
                "Invalid wakeMode value: $value (valid range: 0-${PlayerWakeMode.entries.size - 1})"
              )
          } else PlayerWakeMode.NONE,

        // Android-specific options
        audioOffload =
          if (androidMap?.hasKey("audioOffload") == true) androidMap.getBoolean("audioOffload")
          else null,
        skipSilence =
          if (androidMap?.hasKey("skipSilence") == true) androidMap.getBoolean("skipSilence")
          else null,
        appKilledPlaybackBehavior =
          if (androidMap?.hasKey("appKilledPlaybackBehavior") == true)
            androidMap.getString("appKilledPlaybackBehavior")
          else null,
        shuffle =
          if (androidMap?.hasKey("shuffle") == true) androidMap.getBoolean("shuffle") else null,
      )
    }
  }

  fun updateFromBridge(map: ReadableMap?) {
    if (map == null) return

    if (map.hasKey("forwardJumpInterval")) {
      forwardJumpInterval = map.getDouble("forwardJumpInterval")
    }
    if (map.hasKey("backwardJumpInterval")) {
      backwardJumpInterval = map.getDouble("backwardJumpInterval")
    }
    if (map.hasKey("progressUpdateEventInterval")) {
      progressUpdateEventInterval = map.getDouble("progressUpdateEventInterval")
    }

    map.getString("ratingType")?.let { ratingType = RatingType.fromString(it) }

    map.getArray("capabilities")?.let { arr ->
      capabilities = (0 until arr.size()).mapNotNull { index ->
        val value = arr.getString(index) ?: return@mapNotNull null
        PlayerCapability.fromString(value)
          ?: throw IllegalArgumentException("Invalid capability value: $value")
      }
    }

    map.getArray("notificationCapabilities")?.let { arr ->
      notificationCapabilities = (0 until arr.size()).mapNotNull { index ->
        val value = arr.getString(index) ?: return@mapNotNull null
        PlayerCapability.fromString(value)
          ?: throw IllegalArgumentException("Invalid notificationCapability value: $value")
      }
    }

    // Audio engine options
    if (map.hasKey("minBuffer")) {
      minBuffer = map.getDouble("minBuffer")
    }
    if (map.hasKey("maxBuffer")) {
      maxBuffer = map.getDouble("maxBuffer")
    }
    if (map.hasKey("playBuffer")) {
      playBuffer = map.getDouble("playBuffer")
    }
    if (map.hasKey("rebufferBuffer")) {
      rebufferBuffer = map.getDouble("rebufferBuffer")
    }
    if (map.hasKey("backBuffer")) {
      backBuffer = map.getDouble("backBuffer")
    }
    if (map.hasKey("maxCacheSize")) {
      maxCacheSize = map.getDouble("maxCacheSize")
    }

    map.getString("audioContentType")?.let {
      audioContentType = AudioContentType.fromString(it)
    }

    if (map.hasKey("handleAudioBecomingNoisy")) {
      handleAudioBecomingNoisy = map.getBoolean("handleAudioBecomingNoisy")
    }

    if (map.hasKey("wakeMode")) {
      val value = map.getInt("wakeMode")
      wakeMode = PlayerWakeMode.entries.getOrNull(value)
        ?: throw IllegalArgumentException(
          "Invalid wakeMode value: $value (valid range: 0-${PlayerWakeMode.entries.size - 1})"
        )
    }

    // Android-specific options
    val androidMap = if (map.hasKey("android")) map.getMap("android") else null
    androidMap?.let { android ->
      if (android.hasKey("audioOffload")) {
        audioOffload = android.getBoolean("audioOffload")
      }
      if (android.hasKey("skipSilence")) {
        skipSilence = android.getBoolean("skipSilence")
      }
      if (android.hasKey("appKilledPlaybackBehavior")) {
        appKilledPlaybackBehavior = android.getString("appKilledPlaybackBehavior")
      }
      if (android.hasKey("shuffle")) {
        shuffle = android.getBoolean("shuffle")
      }
    }
  }

  fun toPlayerOptions(): PlayerOptions {
    return PlayerOptions(
      audioContentType = audioContentType,
      bufferOptions =
        BufferOptions(
          minBuffer?.toMilliseconds()?.toInt(),
          maxBuffer?.toMilliseconds()?.toInt(),
          playBuffer?.toMilliseconds()?.toInt(),
          rebufferBuffer?.toMilliseconds()?.toInt(),
          backBuffer?.toMilliseconds()?.toInt(),
        ),
      cacheSizeKb = maxCacheSize.toLong(),
      handleAudioBecomingNoisy = handleAudioBecomingNoisy,
      interceptPlayerActionsTriggeredExternally = true,
      skipSilence = skipSilence ?: false,
      wakeMode = wakeMode,
      forwardJumpInterval = forwardJumpInterval,
      backwardJumpInterval = backwardJumpInterval,
    )
  }
}
