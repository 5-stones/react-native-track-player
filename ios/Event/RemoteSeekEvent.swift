import Foundation

/**
 Event data for remote seek command.
 */
public struct RemoteSeekEvent {
  /// The position to seek to in seconds.
  public let position: Double

  public init(position: Double) {
    self.position = position
  }

  public func toBridge() -> [String: Any] {
    return ["position": position]
  }
}
