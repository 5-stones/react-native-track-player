package com.doublesymmetry.trackplayer.model

import com.doublesymmetry.trackplayer.option.PlayerCapability
import com.doublesymmetry.trackplayer.option.RepeatMode
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
  var progressUpdateEventInterval: Double? = null,

  // Rating and capabilities
  var capabilities: List<PlayerCapability> =
    listOf(
      PlayerCapability.PLAY,
      PlayerCapability.PAUSE,
      PlayerCapability.SKIP_TO_NEXT,
      PlayerCapability.SKIP_TO_PREVIOUS,
    ),
  var notificationCapabilities: List<PlayerCapability>? = null,

  // Repeat mode
  var repeatMode: RepeatMode = RepeatMode.OFF,

  // Android-specific runtime options (all under android.* in JS)
  var ratingType: RatingType? = null,
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
      progressUpdateEventInterval =
        if (map.isNull("progressUpdateEventInterval")) {
          null
        } else {
          map.getDouble("progressUpdateEventInterval")
        }
    }

    map.getArray("capabilities")?.let { arr ->
      capabilities =
        (0 until arr.size()).mapNotNull { index ->
          val value = arr.getString(index) ?: return@mapNotNull null
          PlayerCapability.fromString(value)
            ?: throw IllegalArgumentException("Invalid capability value: $value")
        }
    }

    if (map.hasKey("notificationCapabilities")) {
      notificationCapabilities =
        if (map.isNull("notificationCapabilities")) {
          null // Explicitly set to null - reset to default behavior
        } else {
          map.getArray("notificationCapabilities")?.let { arr ->
            (0 until arr.size()).mapNotNull { index ->
              val value = arr.getString(index) ?: return@mapNotNull null
              PlayerCapability.fromString(value)
                ?: throw IllegalArgumentException("Invalid notificationCapability value: $value")
            }
          }
        }
    }

    if (map.hasKey("repeatMode")) {
      repeatMode = map.getString("repeatMode")?.let { RepeatMode.fromString(it) } ?: RepeatMode.OFF
    }

    // Android-specific runtime options (all under android.*)
    val androidMap = if (map.hasKey("android")) map.getMap("android") else null
    androidMap?.let { android ->
      if (android.hasKey("ratingType")) {
        ratingType = android.getString("ratingType")?.let { RatingType.fromString(it) }
      }
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

    // Add progress update interval (always include, null means disabled)
    if (progressUpdateEventInterval != null) {
      result.putDouble("progressUpdateEventInterval", progressUpdateEventInterval)
    } else {
      result.putNull("progressUpdateEventInterval")
    }

    // Add capabilities (always include, even if empty)
    val capabilitiesArray = Arguments.createArray()
    capabilities.forEach { cap -> capabilitiesArray.pushString(cap.string) }
    result.putArray("capabilities", capabilitiesArray)

    // Add notification capabilities if set (null means not set, different from empty)
    notificationCapabilities?.let { caps ->
      val notificationCapsArray = Arguments.createArray()
      caps.forEach { cap -> notificationCapsArray.pushString(cap.string) }
      result.putArray("notificationCapabilities", notificationCapsArray)
    }

    // Add repeat mode (always include)
    result.putString("repeatMode", repeatMode.string)

    // Add Android-specific options if any are set
    val hasAndroidOptions =
      ratingType != null ||
        appKilledPlaybackBehavior != null ||
        skipSilence != null ||
        shuffle != null
    if (hasAndroidOptions) {
      val androidOptions = Arguments.createMap()

      ratingType?.let { androidOptions.putString("ratingType", it.string) }
      appKilledPlaybackBehavior?.let { androidOptions.putString("appKilledPlaybackBehavior", it) }
      skipSilence?.let { androidOptions.putBoolean("skipSilence", it) }
      shuffle?.let { androidOptions.putBoolean("shuffle", it) }

      result.putMap("android", androidOptions)
    }

    return result
  }
}
