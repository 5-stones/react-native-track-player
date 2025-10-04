import Foundation

/**
 Event data for playback error.
 Matches TypeScript interface: { error?: { code: string, message: string } }
 */
public struct PlaybackErrorEvent {
  /// Optional error details. Nil when error is resolved.
  public let error: ErrorDetails?

  public struct ErrorDetails {
    public let code: String
    public let message: String
  }

  public init(code: String, message: String) {
    error = ErrorDetails(code: code, message: message)
  }

  public init() {
    error = nil
  }

  public func toBridge() -> [String: Any] {
    if let error {
      return [
        "error": [
          "code": error.code,
          "message": error.message,
        ],
      ]
    }
    return [:]
  }
}
