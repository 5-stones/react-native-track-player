import Foundation

/**
 Event data for remote play id command.
 */
public struct RemotePlayIdEvent {
  /// The track id.
  public let id: String

  public init(id: String) {
    self.id = id
  }

  public func toBridge() -> [String: Any] {
    return ["id": id]
  }
}
