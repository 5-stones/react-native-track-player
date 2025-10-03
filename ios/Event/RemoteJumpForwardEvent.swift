import Foundation

/**
 Event data for remote jump forward command.
 */
public struct RemoteJumpForwardEvent {
  /// The number of seconds to jump forward.
  public let interval: Double

  public init(interval: Double) {
    self.interval = interval
  }

  public func toBridge() -> [String: Any] {
    return ["interval": interval]
  }
}
