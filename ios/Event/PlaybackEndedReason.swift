import Foundation

public enum PlaybackEndedReason: String {
  case playedUntilEnd
  case playerStopped
  case skippedToNext
  case skippedToPrevious
  case jumpedToIndex
  case cleared
  case error
}
