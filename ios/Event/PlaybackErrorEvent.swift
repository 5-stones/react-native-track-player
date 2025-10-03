import Foundation

/**
 Event data for playback error.
 */
public struct PlaybackErrorEvent {
  /// The error code.
  public let code: String

  /// The error message.
  public let message: String

  public init(code: String, message: String) {
    self.code = code
    self.message = message
  }

  public func toBridge() -> [String: Any] {
    return [
      "code": code,
      "message": message,
    ]
  }
}
