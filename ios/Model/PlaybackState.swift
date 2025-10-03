import Foundation

/**
 Represents the current state of the TrackPlayer. Includes the playback state and any associated error information.
 */
public struct PlaybackState {
  public let state: State
  public let error: TrackPlayerError.PlaybackError?

  public init(state: State, error: TrackPlayerError.PlaybackError? = nil) {
    self.state = state
    self.error = error
  }

  public func toBridge() -> [String: Any] {
    var body: [String: Any] = ["state": state.bridge]
    if let error {
      body["error"] = error.toBridge()
    }
    return body
  }
}

extension TrackPlayerError.PlaybackError {
  func toBridge() -> [String: Any] {
    switch self {
    case .failedToLoadKeyValue: return [
        "message": "Failed to load resource",
        "code": "ios_failed_to_load_resource",
      ]
    case .invalidSourceUrl: return [
        "message": "The source url was invalid",
        "code": "ios_invalid_source_url",
      ]
    case .notConnectedToInternet: return [
        "message": "A network resource was requested, but an internet connection has not been established and can't be established automatically.",
        "code": "ios_not_connected_to_internet",
      ]
    case .playbackFailed: return [
        "message": "Playback of the track failed",
        "code": "ios_playback_failed",
      ]
    case .trackWasUnplayable: return [
        "message": "The track could not be played",
        "code": "ios_track_unplayable",
      ]
    }
  }
}
