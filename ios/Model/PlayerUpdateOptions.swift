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
  public var capabilities: [Capability] = [
    .play,
    .pause,
    .next,
    .previous,
    .seek,
  ]

  /// Repeat mode
  public var repeatMode: RepeatMode = .off

  /// iOS-specific options
  public var likeOptions: FeedbackOptions = .init(title: "Like")
  public var dislikeOptions: FeedbackOptions = .init(title: "Dislike")
  public var bookmarkOptions: FeedbackOptions = .init(title: "Bookmark")

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
    if options.keys.contains("progressUpdateEventInterval") {
      if options["progressUpdateEventInterval"] is NSNull {
        progressUpdateEventInterval = nil
      } else if let interval = options["progressUpdateEventInterval"] as? NSNumber {
        progressUpdateEventInterval = interval.doubleValue
      }
    }

    // Update capabilities
    if let caps = options["capabilities"] as? [String] {
      var updatedCapabilities = caps.compactMap { Capability(rawValue: $0) }
      // Add toggle-play-pause if both play and pause are present
      if updatedCapabilities.contains(.play), updatedCapabilities.contains(.pause) {
        updatedCapabilities.append(.togglePlayPause)
      }
      capabilities = updatedCapabilities
    }

    // Update repeat mode
    if options.keys.contains("repeatMode") {
      if options["repeatMode"] is NSNull {
        repeatMode = .off // Reset to default when explicitly set to null
      } else if let modeString = options["repeatMode"] as? String {
        repeatMode = RepeatMode(rawValue: modeString) ?? .off
      }
    }

    // Update iOS-specific options
    if let iosOptions = options["ios"] as? [String: Any] {
      if let likeDict = iosOptions["likeOptions"] as? [String: Any],
         let like = FeedbackOptions.fromBridge(likeDict)
      {
        likeOptions = like
      }
      if let dislikeDict = iosOptions["dislikeOptions"] as? [String: Any],
         let dislike = FeedbackOptions.fromBridge(dislikeDict)
      {
        dislikeOptions = dislike
      }
      if let bookmarkDict = iosOptions["bookmarkOptions"] as? [String: Any],
         let bookmark = FeedbackOptions.fromBridge(bookmarkDict)
      {
        bookmarkOptions = bookmark
      }
    }
  }

  public func toBridge() -> [String: Any] {
    var result: [String: Any] = [:]

    // Add jump intervals (always include these core values)
    result["forwardJumpInterval"] = NSNumber(value: forwardJumpInterval)
    result["backwardJumpInterval"] = NSNumber(value: backwardJumpInterval)

    // Add progress update interval (always include, nil means disabled)
    if let interval = progressUpdateEventInterval {
      result["progressUpdateEventInterval"] = NSNumber(value: interval)
    } else {
      result["progressUpdateEventInterval"] = NSNull()
    }

    // Add capabilities (always include, filter out auto-added toggle-play-pause)
    let filteredCapabilities = capabilities.filter { $0 != .togglePlayPause }.map(\.rawValue)
    result["capabilities"] = filteredCapabilities

    // Add repeat mode (always include)
    result["repeatMode"] = repeatMode.rawValue

    // Add iOS-specific options (always include with defaults)
    result["ios"] = [
      "likeOptions": likeOptions.toBridge(),
      "dislikeOptions": dislikeOptions.toBridge(),
      "bookmarkOptions": bookmarkOptions.toBridge(),
    ]

    return result
  }

  // MARK: - Convenience Methods
}
