import Foundation

/// Manages the playing state (playing and buffering flags) and notifies when they change.
class PlayingState {
  private(set) var playing: Bool = false
  private(set) var buffering: Bool = false

  private let onChange: (PlaybackPlayingState) -> Void

  init(onChange: @escaping (PlaybackPlayingState) -> Void) {
    self.onChange = onChange
  }

  func update(playWhenReady: Bool, state: State) {
    let newPlaying = playWhenReady && !(state == .error || state == .ended || state == .none)
    let newBuffering = playWhenReady && (state == .loading || state == .buffering)

    if newPlaying != playing || newBuffering != buffering {
      playing = newPlaying
      buffering = newBuffering
      onChange(PlaybackPlayingState(playing: playing, buffering: buffering))
    }
  }

  func toEvent() -> PlaybackPlayingState {
    return PlaybackPlayingState(playing: playing, buffering: buffering)
  }
}
