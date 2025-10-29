import Foundation

public enum Capability: String {
  case play
  case pause
  case togglePlayPause = "toggle-play-pause"
  case stop
  case next = "skip-to-next"
  case previous = "skip-to-previous"
  case jumpForward = "jump-forward"
  case jumpBackward = "jump-backward"
  case seek = "seek-to"
  case like
  case dislike
  case bookmark

  func mapToPlayerCommand(
    forwardJumpInterval: NSNumber?,
    backwardJumpInterval: NSNumber?,
    likeOptions: FeedbackOptions,
    dislikeOptions: FeedbackOptions,
    bookmarkOptions: FeedbackOptions
  ) -> RemoteCommand {
    switch self {
    case .stop:
      return .stop
    case .play:
      return .play
    case .pause:
      return .pause
    case .togglePlayPause:
      return .togglePlayPause
    case .next:
      return .next
    case .previous:
      return .previous
    case .seek:
      return .changePlaybackPosition
    case .jumpForward:
      return .skipForward(preferredIntervals: [(forwardJumpInterval ?? backwardJumpInterval) ?? 15])
    case .jumpBackward:
      return .skipBackward(preferredIntervals: [
        (backwardJumpInterval ?? forwardJumpInterval) ??
          15,
      ])
    case .like:
      return .like(
        isActive: likeOptions.isActive,
        localizedTitle: likeOptions.title,
        localizedShortTitle: likeOptions.title
      )
    case .dislike:
      return .dislike(
        isActive: dislikeOptions.isActive,
        localizedTitle: dislikeOptions.title,
        localizedShortTitle: dislikeOptions.title
      )
    case .bookmark:
      return .bookmark(
        isActive: bookmarkOptions.isActive,
        localizedTitle: bookmarkOptions.title,
        localizedShortTitle: bookmarkOptions.title
      )
    }
  }
}
