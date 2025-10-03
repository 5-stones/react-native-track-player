import Foundation

/**
 Event data for remote jump backward command.
 */
public struct RemoteJumpBackwardEvent {
  /// The number of seconds to jump backward.
  public let interval: Double

  public init(interval: Double) {
    self.interval = interval
  }

  public func toBridge() -> [String: Any] {
    return ["interval": interval]
  }
}
