package com.doublesymmetry.trackplayer.model

import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.ReadableArray
import com.doublesymmetry.kotlinaudio.models.PlayerOptions
import com.doublesymmetry.kotlinaudio.models.BufferOptions
import com.doublesymmetry.kotlinaudio.models.WakeMode
import com.doublesymmetry.kotlinaudio.models.AudioContentType
import com.doublesymmetry.kotlinaudio.models.Capability
import com.doublesymmetry.trackplayer.extensions.NumberExt.Companion.toMilliseconds

data class PlayerOptionsData(
    val forwardJumpInterval: Double = 15.0,
    val backwardJumpInterval: Double = 15.0,
    val progressUpdateEventInterval: Double = -1.0,
    val capabilities: List<Capability>? = null,
    val notificationCapabilities: List<Capability>? = null,
    val androidOptions: AndroidOptions? = null,

    // Audio engine options
    val minBuffer: Double? = null,
    val maxBuffer: Double? = null,
    val playBuffer: Double? = null,
    val backBuffer: Double? = null,
    val maxCacheSize: Double = 0.0,
    val audioContentType: AudioContentType = AudioContentType.MUSIC,
    val handleAudioBecomingNoisy: Boolean = true,
    val autoHandleInterruptions: Boolean = true,
    val alwaysShowNext: Boolean = true,
    val wakeMode: WakeMode = WakeMode.NONE
) {
    companion object {
        fun fromBridge(map: ReadableMap?): PlayerOptionsData {
            if (map == null) return PlayerOptionsData()

            val capabilities = map.getArray("capabilities")?.let { arr ->
                (0 until arr.size()).map { index ->
                    val value = arr.getInt(index)
                    Capability.entries.getOrNull(value)
                        ?: throw IllegalArgumentException("Invalid capability value: $value (valid range: 0-${Capability.entries.size - 1})")
                }
            }

            val notificationCapabilities = map.getArray("notificationCapabilities")?.let { arr ->
                (0 until arr.size()).map { index ->
                    val value = arr.getInt(index)
                    Capability.entries.getOrNull(value)
                        ?: throw IllegalArgumentException("Invalid notificationCapability value: $value (valid range: 0-${Capability.entries.size - 1})")
                }
            }

            return PlayerOptionsData(
                forwardJumpInterval = if (map.hasKey("forwardJumpInterval")) map.getDouble("forwardJumpInterval") else 15.0,
                backwardJumpInterval = if (map.hasKey("backwardJumpInterval")) map.getDouble("backwardJumpInterval") else 15.0,
                progressUpdateEventInterval = if (map.hasKey("progressUpdateEventInterval")) map.getDouble("progressUpdateEventInterval") else -1.0,
                capabilities = capabilities,
                notificationCapabilities = notificationCapabilities,
                androidOptions = if (map.hasKey("android")) AndroidOptions.fromBridge(map.getMap("android")) else null,

                // Audio engine options
                minBuffer = if (map.hasKey("minBuffer")) map.getDouble("minBuffer") else null,
                maxBuffer = if (map.hasKey("maxBuffer")) map.getDouble("maxBuffer") else null,
                playBuffer = if (map.hasKey("playBuffer")) map.getDouble("playBuffer") else null,
                backBuffer = if (map.hasKey("backBuffer")) map.getDouble("backBuffer") else null,
                maxCacheSize = if (map.hasKey("maxCacheSize")) map.getDouble("maxCacheSize") else 0.0,
                audioContentType = AudioContentType.fromString(map.getString("audioContentType") ?: "music"),
                handleAudioBecomingNoisy = if (map.hasKey("handleAudioBecomingNoisy")) map.getBoolean("handleAudioBecomingNoisy") else true,
                autoHandleInterruptions = if (map.hasKey("autoHandleInterruptions")) map.getBoolean("autoHandleInterruptions") else true,
                alwaysShowNext = if (map.hasKey("alwaysShowNext")) map.getBoolean("alwaysShowNext") else true,
                wakeMode = if (map.hasKey("wakeMode")) {
                    val value = map.getInt("wakeMode")
                    WakeMode.entries.getOrNull(value)
                        ?: throw IllegalArgumentException("Invalid wakeMode value: $value (valid range: 0-${WakeMode.entries.size - 1})")
                } else WakeMode.NONE
            )
        }
    }

    fun toAudioPlayerOptions(): PlayerOptions {
        return PlayerOptions(
            alwaysShowNext = alwaysShowNext,
            audioContentType = audioContentType,
            bufferOptions = BufferOptions(
                minBuffer?.toMilliseconds()?.toInt(),
                maxBuffer?.toMilliseconds()?.toInt(),
                playBuffer?.toMilliseconds()?.toInt(),
                backBuffer?.toMilliseconds()?.toInt(),
            ),
            cacheSizeKb = maxCacheSize.toLong(),
            handleAudioBecomingNoisy = handleAudioBecomingNoisy,
            handleAudioFocus = autoHandleInterruptions,
            interceptPlayerActionsTriggeredExternally = true,
            skipSilence = androidOptions?.skipSilence ?: false,
            wakeMode = wakeMode
        )
    }
}

data class AndroidOptions(
    val audioOffload: Boolean? = null,
    val stopForegroundGracePeriod: Int? = null,
    val skipSilence: Boolean? = null,
    val appKilledPlaybackBehavior: String? = null,
    val pauseOnInterruption: Boolean? = null,
    val shuffle: Boolean? = null
) {
    companion object {
        fun fromBridge(map: ReadableMap?): AndroidOptions {
            if (map == null) return AndroidOptions()

            return AndroidOptions(
                audioOffload = if (map.hasKey("audioOffload")) map.getBoolean("audioOffload") else null,
                stopForegroundGracePeriod = if (map.hasKey("stopForegroundGracePeriod")) map.getInt("stopForegroundGracePeriod") else null,
                skipSilence = if (map.hasKey("skipSilence")) map.getBoolean("skipSilence") else null,
                appKilledPlaybackBehavior = if (map.hasKey("appKilledPlaybackBehavior")) map.getString("appKilledPlaybackBehavior") else null,
                pauseOnInterruption = if (map.hasKey("pauseOnInterruption")) map.getBoolean("pauseOnInterruption") else null,
                shuffle = if (map.hasKey("shuffle")) map.getBoolean("shuffle") else null
            )
        }
    }
}