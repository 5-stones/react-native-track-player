import Foundation

/**
 Event data for when playWhenReady changes.
 */
public struct PlaybackPlayWhenReadyChangedEvent {
  /// Whether the player will play when it is ready to do so.
  public let playWhenReady: Bool

  public init(playWhenReady: Bool) {
    self.playWhenReady = playWhenReady
  }

  public func toBridge() -> [String: Any] {
    return [
      "playWhenReady": playWhenReady,
    ]
  }
}
