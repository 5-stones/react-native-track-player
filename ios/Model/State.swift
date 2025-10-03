import Foundation

/**
 The current playback state of the TrackPlayer.
 */
public enum State: String {
  /// An asset is being loaded for playback.
  case loading

  /// The current track is loaded, and the player is ready to start playing.
  case ready

  /// The current track is currently buffering and will start playing when
  /// buffering is complete.
  case buffering

  /// The player is paused.
  case paused

  /// The player is stopped.
  case stopped

  /// The player is playing.
  case playing

  /// No track loaded, the player is stopped.
  case none

  /// The player stopped playing due to an error.
  case error

  /// Playback has reached the end.
  case ended
}

extension State {
  var bridge: String {
    return rawValue
  }
}
