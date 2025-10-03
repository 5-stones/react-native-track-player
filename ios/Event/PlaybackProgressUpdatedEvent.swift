import Foundation

/**
 Event data for playback progress updates.
 */
public struct PlaybackProgressUpdatedEvent {
  /// The current playback position in seconds.
  public let position: Double

  /// The duration of the current track in seconds.
  public let duration: Double

  /// The buffered position in seconds.
  public let buffered: Double

  /// The index of the current track.
  public let track: Int

  public init(position: Double, duration: Double, buffered: Double, track: Int) {
    self.position = position
    self.duration = duration
    self.buffered = buffered
    self.track = track
  }

  public func toBridge() -> [String: Any] {
    return [
      "position": position,
      "duration": duration,
      "buffered": buffered,
      "track": track
    ]
  }
}
