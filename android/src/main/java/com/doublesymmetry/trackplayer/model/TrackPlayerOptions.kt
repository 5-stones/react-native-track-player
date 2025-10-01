package com.doublesymmetry.trackplayer.model

import com.doublesymmetry.trackplayer.extension.NumberExt.Companion.toMilliseconds
import com.doublesymmetry.trackplayer.option.AudioContentType
import com.doublesymmetry.trackplayer.option.BufferOptions
import com.doublesymmetry.trackplayer.option.Capability
import com.doublesymmetry.trackplayer.option.PlayerOptions
import com.doublesymmetry.trackplayer.option.PlayerWakeMode
import com.facebook.react.bridge.ReadableMap

data class AudioPlayerOptionsData(
  val forwardJumpInterval: Double = 15.0,
  val backwardJumpInterval: Double = 15.0,
  val progressUpdateEventInterval: Double = -1.0,
  val capabilities: List<Capability>? = null,
  val notificationCapabilities: List<Capability>? = null,
  val androidOptions: AndroidAudioPlayerOptions? = null,

  // Audio engine options
  val minBuffer: Double? = null,
  val maxBuffer: Double? = null,
  val playBuffer: Double? = null,
  val backBuffer: Double? = null,
  val maxCacheSize: Double = 0.0,
  val audioContentType: AudioContentType = AudioContentType.MUSIC,
  val handleAudioBecomingNoisy: Boolean = true,
  val autoHandleInterruptions: Boolean = true,
  val wakeMode: PlayerWakeMode = PlayerWakeMode.NONE,
) {
  companion object {
    fun fromBridge(map: ReadableMap?): AudioPlayerOptionsData {
      if (map == null) return AudioPlayerOptionsData()

      val capabilities =
        map.getArray("capabilities")?.let { arr ->
          (0 until arr.size()).map { index ->
            val value = arr.getInt(index)
            Capability.entries.getOrNull(value)
              ?: throw IllegalArgumentException(
                "Invalid capability value: $value (valid range: 0-${Capability.entries.size - 1})"
              )
          }
        }

      val notificationCapabilities =
        map.getArray("notificationCapabilities")?.let { arr ->
          (0 until arr.size()).map { index ->
            val value = arr.getInt(index)
            Capability.entries.getOrNull(value)
              ?: throw IllegalArgumentException(
                "Invalid notificationCapability value: $value (valid range: 0-${Capability.entries.size - 1})"
              )
          }
        }

      return AudioPlayerOptionsData(
        forwardJumpInterval =
          if (map.hasKey("forwardJumpInterval")) map.getDouble("forwardJumpInterval") else 15.0,
        backwardJumpInterval =
          if (map.hasKey("backwardJumpInterval")) map.getDouble("backwardJumpInterval") else 15.0,
        progressUpdateEventInterval =
          if (map.hasKey("progressUpdateEventInterval"))
            map.getDouble("progressUpdateEventInterval")
          else -1.0,
        capabilities = capabilities,
        notificationCapabilities = notificationCapabilities,
        androidOptions =
          if (map.hasKey("android")) AndroidAudioPlayerOptions.fromBridge(map.getMap("android"))
          else null,

        // Audio engine options
        minBuffer = if (map.hasKey("minBuffer")) map.getDouble("minBuffer") else null,
        maxBuffer = if (map.hasKey("maxBuffer")) map.getDouble("maxBuffer") else null,
        playBuffer = if (map.hasKey("playBuffer")) map.getDouble("playBuffer") else null,
        backBuffer = if (map.hasKey("backBuffer")) map.getDouble("backBuffer") else null,
        maxCacheSize = if (map.hasKey("maxCacheSize")) map.getDouble("maxCacheSize") else 0.0,
        audioContentType =
          AudioContentType.fromString(map.getString("audioContentType") ?: "music"),
        handleAudioBecomingNoisy =
          if (map.hasKey("handleAudioBecomingNoisy")) map.getBoolean("handleAudioBecomingNoisy")
          else true,
        autoHandleInterruptions =
          if (map.hasKey("autoHandleInterruptions")) map.getBoolean("autoHandleInterruptions")
          else true,
        wakeMode =
          if (map.hasKey("wakeMode")) {
            val value = map.getInt("wakeMode")
            PlayerWakeMode.entries.getOrNull(value)
              ?: throw IllegalArgumentException(
                "Invalid wakeMode value: $value (valid range: 0-${PlayerWakeMode.entries.size - 1})"
              )
          } else PlayerWakeMode.NONE,
      )
    }
  }

  fun toAudioPlayerOptions(): PlayerOptions {
    return PlayerOptions(
      audioContentType = audioContentType,
      bufferOptions =
        BufferOptions(
          minBuffer?.toMilliseconds()?.toInt(),
          maxBuffer?.toMilliseconds()?.toInt(),
          playBuffer?.toMilliseconds()?.toInt(),
          backBuffer?.toMilliseconds()?.toInt(),
        ),
      cacheSizeKb = maxCacheSize.toLong(),
      handleAudioBecomingNoisy = handleAudioBecomingNoisy,
      interceptPlayerActionsTriggeredExternally = true,
      skipSilence = androidOptions?.skipSilence ?: false,
      wakeMode = wakeMode,
    )
  }
}

data class AndroidAudioPlayerOptions(
  val audioOffload: Boolean? = null,
  val skipSilence: Boolean? = null,
  val appKilledPlaybackBehavior: String? = null,
  val pauseOnInterruption: Boolean? = null,
  val shuffle: Boolean? = null,
) {
  companion object {
    fun fromBridge(map: ReadableMap?): AndroidAudioPlayerOptions {
      if (map == null) return AndroidAudioPlayerOptions()

      return AndroidAudioPlayerOptions(
        audioOffload = if (map.hasKey("audioOffload")) map.getBoolean("audioOffload") else null,
        skipSilence = if (map.hasKey("skipSilence")) map.getBoolean("skipSilence") else null,
        appKilledPlaybackBehavior =
          if (map.hasKey("appKilledPlaybackBehavior")) map.getString("appKilledPlaybackBehavior")
          else null,
        pauseOnInterruption =
          if (map.hasKey("pauseOnInterruption")) map.getBoolean("pauseOnInterruption") else null,
        shuffle = if (map.hasKey("shuffle")) map.getBoolean("shuffle") else null,
      )
    }
  }
}
