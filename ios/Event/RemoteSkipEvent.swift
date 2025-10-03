import Foundation

/**
 Event data for remote skip command.
 */
public struct RemoteSkipEvent {
  /// The index to skip to.
  public let index: Int

  public init(index: Int) {
    self.index = index
  }

  public func toBridge() -> [String: Any] {
    return ["index": index]
  }
}
