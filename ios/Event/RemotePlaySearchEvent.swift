import Foundation

/**
 Event data for remote play search command.
 */
public struct RemotePlaySearchEvent {
  /// The search query.
  public let query: String

  /// The search focus.
  public let focus: String?

  /// The title to search for.
  public let title: String?

  /// The artist to search for.
  public let artist: String?

  /// The album to search for.
  public let album: String?

  /// The date to search for.
  public let date: String?

  /// The playlist to search for.
  public let playlist: String?

  public init(
    query: String,
    focus: String? = nil,
    title: String? = nil,
    artist: String? = nil,
    album: String? = nil,
    date: String? = nil,
    playlist: String? = nil
  ) {
    self.query = query
    self.focus = focus
    self.title = title
    self.artist = artist
    self.album = album
    self.date = date
    self.playlist = playlist
  }

  public func toBridge() -> [String: Any] {
    var result: [String: Any] = ["query": query]

    if let focus = focus {
      result["focus"] = focus
    }

    if let title = title {
      result["title"] = title
    }

    if let artist = artist {
      result["artist"] = artist
    }

    if let album = album {
      result["album"] = album
    }

    if let date = date {
      result["date"] = date
    }

    if let playlist = playlist {
      result["playlist"] = playlist
    }

    return result
  }
}
