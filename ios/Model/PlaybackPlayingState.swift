import Foundation

/**
 Playback playing state data (playing and buffering flags).
 */
public struct PlaybackPlayingState {
  /// Whether the player is currently playing.
  public let playing: Bool
  /// Whether the player is buffering during playback.
  public let buffering: Bool

  public init(playing: Bool, buffering: Bool) {
    self.playing = playing
    self.buffering = buffering
  }

  public init(fromPlayingState playingState: [String: Bool]) {
    playing = playingState["playing"] ?? false
    buffering = playingState["buffering"] ?? false
  }

  public func toBridge() -> [String: Bool] {
    return [
      "playing": playing,
      "buffering": buffering,
    ]
  }
}
