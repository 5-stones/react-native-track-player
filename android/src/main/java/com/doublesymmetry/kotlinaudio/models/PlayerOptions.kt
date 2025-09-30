@file: OptIn(UnstableApi::class)
package com.doublesymmetry.kotlinaudio.models

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi

data class PlayerOptions(
    val cacheSizeKb: Long = 0,
    val audioContentType: AudioContentType = AudioContentType.MUSIC,
    val wakeMode: WakeMode = WakeMode.NONE,
    val handleAudioBecomingNoisy: Boolean = true,
    val alwaysShowNext: Boolean = true,
    val handleAudioFocus: Boolean = true,
    var alwaysPauseOnInterruption: Boolean = true,
    var repeatMode: RepeatMode = RepeatMode.ALL,
    val bufferOptions: BufferOptions = BufferOptions(null, null, null, null),
    val parseEmbeddedArtwork: Boolean = false,
    val skipSilence: Boolean = false,
    val nativeExample: Boolean = false,
    val interceptPlayerActionsTriggeredExternally: Boolean = false
)

data class BufferOptions (
    val minBuffer: Int?,
    val maxBuffer: Int?,
    val playBuffer: Int?,
    val backBuffer: Int?,
)
