import Foundation

/**
 * Update options for the TrackPlayer that can be changed at runtime.
 * These options control player behavior and capabilities that can be modified during playback.
 */
public class PlayerUpdateOptions {
  // MARK: - Core Properties

  /// Jump intervals
  public var forwardJumpInterval: Double = 15.0
  public var backwardJumpInterval: Double = 15.0
  public var progressUpdateEventInterval: Double?

  /// Rating and capabilities
  public var ratingType: String?
  public var capabilities: [String] = []
  public var notificationCapabilities: [String] = []

  /// iOS-specific options
  public var likeOptions: [String: Any]?
  public var dislikeOptions: [String: Any]?
  public var bookmarkOptions: [String: Any]?

  // MARK: - Initialization

  public init() {}

  // MARK: - Bridge Methods

  public func updateFromBridge(_ options: [String: Any]) {
    // Update jump intervals
    if let interval = options["forwardJumpInterval"] as? NSNumber {
      forwardJumpInterval = interval.doubleValue
    }
    if let interval = options["backwardJumpInterval"] as? NSNumber {
      backwardJumpInterval = interval.doubleValue
    }
    if let interval = options["progressUpdateEventInterval"] as? NSNumber {
      progressUpdateEventInterval = interval.doubleValue > 0 ? interval.doubleValue : nil
    }

    // Update rating type
    if let rating = options["ratingType"] as? String {
      ratingType = rating
    }

    // Update capabilities
    if let caps = options["capabilities"] as? [String] {
      var updatedCapabilities = caps
      // Add toggle-play-pause if both play and pause are present
      if caps.contains("play"), caps.contains("pause") {
        updatedCapabilities.append("toggle-play-pause")
      }
      capabilities = updatedCapabilities
    }

    // Update notification capabilities
    if let notificationCaps = options["notificationCapabilities"] as? [String] {
      notificationCapabilities = notificationCaps
    }

    // Update iOS-specific options
    if let iosOptions = options["ios"] as? [String: Any] {
      likeOptions = iosOptions["likeOptions"] as? [String: Any]
      dislikeOptions = iosOptions["dislikeOptions"] as? [String: Any]
      bookmarkOptions = iosOptions["bookmarkOptions"] as? [String: Any]
    }
  }

  public func toBridge() -> [String: Any] {
    var result: [String: Any] = [:]

    // Add jump intervals (always include these core values)
    result["forwardJumpInterval"] = NSNumber(value: forwardJumpInterval)
    result["backwardJumpInterval"] = NSNumber(value: backwardJumpInterval)

    // Add progress update interval if set
    if let interval = progressUpdateEventInterval {
      result["progressUpdateEventInterval"] = NSNumber(value: interval)
    }

    // Add rating type if set
    if let rating = ratingType {
      result["ratingType"] = rating
    }

    // Add capabilities if set (filter out auto-added toggle-play-pause)
    if !capabilities.isEmpty {
      let filteredCapabilities = capabilities.filter { $0 != "toggle-play-pause" }
      if !filteredCapabilities.isEmpty {
        result["capabilities"] = filteredCapabilities
      }
    }

    // Add notification capabilities if set
    if !notificationCapabilities.isEmpty {
      result["notificationCapabilities"] = notificationCapabilities
    }

    // Add iOS-specific options if any are set
    let hasIOSOptions = likeOptions != nil || dislikeOptions != nil || bookmarkOptions != nil
    if hasIOSOptions {
      var iosOptions: [String: Any] = [:]

      if let like = likeOptions { iosOptions["likeOptions"] = like }
      if let dislike = dislikeOptions { iosOptions["dislikeOptions"] = dislike }
      if let bookmark = bookmarkOptions { iosOptions["bookmarkOptions"] = bookmark }

      result["ios"] = iosOptions
    }

    return result
  }

  // MARK: - Convenience Methods

  /// Get forward jump interval as NSNumber for compatibility
  public var forwardJumpIntervalNumber: NSNumber {
    return NSNumber(value: forwardJumpInterval)
  }

  /// Get backward jump interval as NSNumber for compatibility
  public var backwardJumpIntervalNumber: NSNumber {
    return NSNumber(value: backwardJumpInterval)
  }

  /// Get the mapped capabilities as Capability enums
  public var mappedCapabilities: [Capability] {
    return capabilities.compactMap { Capability(rawValue: $0) }
  }
}
