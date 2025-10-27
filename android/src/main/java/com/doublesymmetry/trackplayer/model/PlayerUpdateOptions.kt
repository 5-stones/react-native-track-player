package com.doublesymmetry.trackplayer.model

import com.doublesymmetry.trackplayer.option.PlayerCapability
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.WritableMap

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

  fun toBridge(): WritableMap {
    val result = Arguments.createMap()

    // Add jump intervals (always include these core values)
    result.putDouble("forwardJumpInterval", forwardJumpInterval)
    result.putDouble("backwardJumpInterval", backwardJumpInterval)

    // Add progress update interval if set (don't include if disabled)
    if (progressUpdateEventInterval > 0) {
      result.putDouble("progressUpdateEventInterval", progressUpdateEventInterval)
    }

    // Add rating type if set
    ratingType?.let { result.putString("ratingType", it.string) }

    // Add capabilities if set
    capabilities?.let { caps ->
      val capabilitiesArray = Arguments.createArray()
      caps.forEach { cap -> capabilitiesArray.pushString(cap.string) }
      result.putArray("capabilities", capabilitiesArray)
    }

    // Add notification capabilities if set
    notificationCapabilities?.let { caps ->
      val notificationCapsArray = Arguments.createArray()
      caps.forEach { cap -> notificationCapsArray.pushString(cap.string) }
      result.putArray("notificationCapabilities", notificationCapsArray)
    }

    // Add Android-specific options if any are set
    val hasAndroidOptions =
      appKilledPlaybackBehavior != null || skipSilence != null || shuffle != null
    if (hasAndroidOptions) {
      val androidOptions = Arguments.createMap()

      appKilledPlaybackBehavior?.let { androidOptions.putString("appKilledPlaybackBehavior", it) }
      skipSilence?.let { androidOptions.putBoolean("skipSilence", it) }
      shuffle?.let { androidOptions.putBoolean("shuffle", it) }

      result.putMap("android", androidOptions)
    }

    return result
  }
}
