import Foundation

public enum TrackPlayerError: Error {
  public enum PlaybackError: Error {
    case failedToLoadKeyValue
    case invalidSourceUrl(String)
    case notConnectedToInternet
    case playbackFailed
    case trackWasUnplayable
  }

  public enum QueueError: Error {
    case noCurrentItem
    case invalidIndex(index: Int, message: String)
    case empty
  }
}

extension TrackPlayerError.PlaybackError: LocalizedError {
  public var errorDescription: String? {
    switch self {
    case .failedToLoadKeyValue:
      return "Failed to load audio track"
    case .invalidSourceUrl(let url):
      return "Invalid audio source URL: \(url)"
    case .notConnectedToInternet:
      return "No internet connection"
    case .playbackFailed:
      return "Playback failed"
    case .trackWasUnplayable:
      return "Track is not playable"
    }
  }
}

extension TrackPlayerError.QueueError: LocalizedError {
  public var errorDescription: String? {
    switch self {
    case .noCurrentItem:
      return "No current track"
    case .invalidIndex(let index, let message):
      return "Invalid track index \(index): \(message)"
    case .empty:
      return "Queue is empty"
    }
  }
}
