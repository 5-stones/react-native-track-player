import Foundation

/**
 Event data for when audio metadata is received.
 */
public struct AudioMetadataReceivedEvent {
  /// The metadata items received.
  public let metadata: [AudioMetadata]

  public init(metadata: [AudioMetadata]) {
    self.metadata = metadata
  }

  public func toBridge() -> [String: Any] {
    return [
      "metadata": metadata.map { $0.toBridge() },
    ]
  }
}

/**
 Represents audio metadata with common fields and raw entries.
 */
public struct AudioMetadata {
  public let title: String?
  public let artist: String?
  public let albumTitle: String?
  public let subtitle: String?
  public let description: String?
  public let artworkUri: String?
  public let trackNumber: String?
  public let composer: String?
  public let conductor: String?
  public let genre: String?
  public let compilation: String?
  public let station: String?
  public let mediaType: String?
  public let creationDate: String?
  public let creationYear: String?
  public let raw: [RawMetadataEntry]

  public init(
    title: String? = nil,
    artist: String? = nil,
    albumTitle: String? = nil,
    subtitle: String? = nil,
    description: String? = nil,
    artworkUri: String? = nil,
    trackNumber: String? = nil,
    composer: String? = nil,
    conductor: String? = nil,
    genre: String? = nil,
    compilation: String? = nil,
    station: String? = nil,
    mediaType: String? = nil,
    creationDate: String? = nil,
    creationYear: String? = nil,
    raw: [RawMetadataEntry] = []
  ) {
    self.title = title
    self.artist = artist
    self.albumTitle = albumTitle
    self.subtitle = subtitle
    self.description = description
    self.artworkUri = artworkUri
    self.trackNumber = trackNumber
    self.composer = composer
    self.conductor = conductor
    self.genre = genre
    self.compilation = compilation
    self.station = station
    self.mediaType = mediaType
    self.creationDate = creationDate
    self.creationYear = creationYear
    self.raw = raw
  }

  public func toBridge() -> [String: Any] {
    var result: [String: Any] = [:]

    if let title { result["title"] = title }
    if let artist { result["artist"] = artist }
    if let albumTitle { result["albumTitle"] = albumTitle }
    if let subtitle { result["subtitle"] = subtitle }
    if let description { result["description"] = description }
    if let artworkUri { result["artworkUri"] = artworkUri }
    if let trackNumber { result["trackNumber"] = trackNumber }
    if let composer { result["composer"] = composer }
    if let conductor { result["conductor"] = conductor }
    if let genre { result["genre"] = genre }
    if let compilation { result["compilation"] = compilation }
    if let station { result["station"] = station }
    if let mediaType { result["mediaType"] = mediaType }
    if let creationDate { result["creationDate"] = creationDate }
    if let creationYear { result["creationYear"] = creationYear }

    if !raw.isEmpty {
      result["raw"] = raw.map { $0.toBridge() }
    }

    return result
  }
}

/**
 Represents a raw metadata entry.
 */
public struct RawMetadataEntry {
  public let commonKey: String?
  public let keySpace: String?
  public let time: Double?
  public let value: Any?
  public let key: String

  public init(
    commonKey: String? = nil,
    keySpace: String? = nil,
    time: Double? = nil,
    value: Any? = nil,
    key: String
  ) {
    self.commonKey = commonKey
    self.keySpace = keySpace
    self.time = time
    self.value = value
    self.key = key
  }

  public func toBridge() -> [String: Any] {
    var result: [String: Any] = ["key": key]

    if let commonKey { result["commonKey"] = commonKey }
    if let keySpace { result["keySpace"] = keySpace }
    if let time { result["time"] = time }
    if let value { result["value"] = value }

    return result
  }
}
