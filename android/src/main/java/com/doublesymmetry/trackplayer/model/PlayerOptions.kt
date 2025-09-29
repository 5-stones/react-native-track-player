package com.doublesymmetry.trackplayer.model

import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.ReadableArray
import androidx.media3.common.C
import com.doublesymmetry.kotlinaudio.models.PlayerOptions
import com.doublesymmetry.kotlinaudio.models.BufferOptions
import com.doublesymmetry.trackplayer.extensions.NumberExt.Companion.toMilliseconds

data class PlayerOptionsData(
    val forwardJumpInterval: Double = 15.0,
    val backwardJumpInterval: Double = 15.0,
    val progressUpdateEventInterval: Double = -1.0,
    val capabilities: List<Int>? = null,
    val notificationCapabilities: List<Int>? = null,
    val androidOptions: AndroidOptions? = null,

    // Audio engine options
    val minBuffer: Double? = null,
    val maxBuffer: Double? = null,
    val playBuffer: Double? = null,
    val backBuffer: Double? = null,
    val maxCacheSize: Double = 0.0,
    val audioContentType: String = "music",
    val handleAudioBecomingNoisy: Boolean = true,
    val autoHandleInterruptions: Boolean = true,
    val alwaysShowNext: Boolean = true,
    val wakeMode: Int = 0
) {
    companion object {
        fun fromBridge(map: ReadableMap?): PlayerOptionsData {
            if (map == null) return PlayerOptionsData()

            val capabilities = map.getArray("capabilities")?.let { arr ->
                (0 until arr.size()).map { arr.getInt(it) }
            }

            val notificationCapabilities = map.getArray("notificationCapabilities")?.let { arr ->
                (0 until arr.size()).map { arr.getInt(it) }
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
                audioContentType = map.getString("audioContentType") ?: "music",
                handleAudioBecomingNoisy = if (map.hasKey("handleAudioBecomingNoisy")) map.getBoolean("handleAudioBecomingNoisy") else true,
                autoHandleInterruptions = if (map.hasKey("autoHandleInterruptions")) map.getBoolean("autoHandleInterruptions") else true,
                alwaysShowNext = if (map.hasKey("alwaysShowNext")) map.getBoolean("alwaysShowNext") else true,
                wakeMode = if (map.hasKey("wakeMode")) map.getInt("wakeMode") else 0
            )
        }
    }

    fun toAudioPlayerOptions(): PlayerOptions {
        return PlayerOptions(
            alwaysShowNext = alwaysShowNext,
            audioContentType = when (audioContentType) {
                "music" -> C.AUDIO_CONTENT_TYPE_MUSIC
                "speech" -> C.AUDIO_CONTENT_TYPE_SPEECH
                "sonification" -> C.AUDIO_CONTENT_TYPE_SONIFICATION
                "movie" -> C.AUDIO_CONTENT_TYPE_MOVIE
                "unknown" -> C.AUDIO_CONTENT_TYPE_UNKNOWN
                else -> C.AUDIO_CONTENT_TYPE_MUSIC
            },
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