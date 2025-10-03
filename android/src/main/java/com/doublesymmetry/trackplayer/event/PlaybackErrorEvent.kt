package com.doublesymmetry.trackplayer.event

import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableMap

/** Event data for playback error. */
data class PlaybackErrorEvent(
  /** The error code. */
  val code: String,
  /** The error message. */
  val message: String,
) {
  fun toBridge(): WritableMap {
    return Arguments.createMap().apply {
      putString("code", code)
      putString("message", message)
    }
  }
}
