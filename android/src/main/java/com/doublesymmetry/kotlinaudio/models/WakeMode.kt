@file:OptIn(UnstableApi::class)
package com.doublesymmetry.kotlinaudio.models

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi

enum class WakeMode {
    NONE,
    LOCAL,
    NETWORK;

    fun toExoPlayer(): Int {
        return when (this) {
            NONE -> C.WAKE_MODE_NONE
            LOCAL -> C.WAKE_MODE_LOCAL
            NETWORK -> C.WAKE_MODE_NETWORK
        }
    }
}
