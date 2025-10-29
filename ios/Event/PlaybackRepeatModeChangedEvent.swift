import Foundation

/**
 Event data for when repeat mode changes.
 */
public struct PlaybackRepeatModeChangedEvent {
  /// The new repeat mode.
  public let repeatMode: RepeatMode

  public init(repeatMode: RepeatMode) {
    self.repeatMode = repeatMode
  }

  public func toBridge() -> [String: Any] {
    return [
      "repeatMode": repeatMode.rawValue,
    ]
  }
}