package com.doublesymmetry.trackplayer.model

import com.doublesymmetry.trackplayer.option.PlayerCapability
import com.facebook.react.bridge.ReadableMap

/**
 * Update options for the TrackPlayer that can be changed at runtime. These options control player
 * behavior and capabilities that can be modified during playback.
 */
data class PlayerUpdateOptions(
  // Jump intervals
  var forwardJumpInterval: Double = 15.0,
  var backwardJumpInterval: Double = 15.0,
  var progressUpdateEventInterval: Double = -1.0,

  // Rating and capabilities
  var ratingType: RatingType? = null,
  var capabilities: List<PlayerCapability>? = null,
  var notificationCapabilities: List<PlayerCapability>? = null,

  // Android-specific runtime options (all under android.* in JS)
  var appKilledPlaybackBehavior: String? = null,
  var skipSilence: Boolean? = null,
  var shuffle: Boolean? = null,
) {

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
      capabilities =
        (0 until arr.size()).mapNotNull { index ->
          val value = arr.getString(index) ?: return@mapNotNull null
          PlayerCapability.fromString(value)
            ?: throw IllegalArgumentException("Invalid capability value: $value")
        }
    }

    map.getArray("notificationCapabilities")?.let { arr ->
      notificationCapabilities =
        (0 until arr.size()).mapNotNull { index ->
          val value = arr.getString(index) ?: return@mapNotNull null
          PlayerCapability.fromString(value)
            ?: throw IllegalArgumentException("Invalid notificationCapability value: $value")
        }
    }

    // Android-specific runtime options (all under android.*)
    val androidMap = if (map.hasKey("android")) map.getMap("android") else null
    androidMap?.let { android ->
      if (android.hasKey("appKilledPlaybackBehavior")) {
        appKilledPlaybackBehavior = android.getString("appKilledPlaybackBehavior")
      }
      if (android.hasKey("skipSilence")) {
        skipSilence = android.getBoolean("skipSilence")
      }
      if (android.hasKey("shuffle")) {
        shuffle = android.getBoolean("shuffle")
      }
    }
  }
}
