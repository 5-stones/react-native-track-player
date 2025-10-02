//
//  AudioItem.swift
//  SwiftAudio
//
//  Created by Jørgen Henrichsen on 18/03/2018.
//

import AVFoundation
import Foundation
import MediaPlayer
import UIKit

public class AudioItem {
  // MARK: - Public Properties

  /// The URL string used by AVPlayer to load the audio resource
  public var audioUrl: String

  public var artist: String?
  public var title: String?
  public var album: String?
  public var sourceType: SourceType

  /// Optional time pitch algorithm for this item. If nil, the player's default will be used.
  public var pitchAlgorithm: PitchAlgorithm?

  /// Optional initial playback time for this item.
  public var initialTime: TimeInterval?

  /// Optional asset initialization options.
  public var assetOptions: [String: Any]?

  /// Optional remote commands for this item. If nil, the player's default commands will be used.
  public var remoteCommands: [RemoteCommand]?

  // MARK: - Internal Bridge Properties

  /// The original MediaURL from the bridge (for serialization)
  internal let url: MediaURL?

  /// URL to artwork image (local file or remote URL)
  internal var artworkURL: MediaURL?

  internal var date: String?
  internal var desc: String?
  internal var genre: String?
  internal var duration: Double?
  internal let headers: [String: Any]?
  internal var userAgent: String?
  internal var isLiveStream: Bool?

  // MARK: - Initialization

  /// Internal initializer - use `fromBridge()` to create items from React Native
  internal init(
    audioUrl: String,
    artist: String? = nil,
    title: String? = nil,
    album: String? = nil,
    sourceType: SourceType,
    pitchAlgorithm: PitchAlgorithm? = nil,
    initialTime: TimeInterval? = nil,
    assetOptions: [String: Any]? = nil,
    remoteCommands: [RemoteCommand]? = nil,
    url: MediaURL? = nil,
    artworkURL: MediaURL? = nil,
    date: String? = nil,
    desc: String? = nil,
    genre: String? = nil,
    duration: Double? = nil,
    headers: [String: Any]? = nil,
    userAgent: String? = nil,
    isLiveStream: Bool? = nil
  ) {
    self.audioUrl = audioUrl
    self.artist = artist
    self.title = title
    self.album = album
    self.sourceType = sourceType
    self.pitchAlgorithm = pitchAlgorithm
    self.initialTime = initialTime
    self.assetOptions = assetOptions
    self.remoteCommands = remoteCommands
    self.url = url
    self.artworkURL = artworkURL
    self.date = date
    self.desc = desc
    self.genre = genre
    self.duration = duration
    self.headers = headers
    self.userAgent = userAgent
    self.isLiveStream = isLiveStream
  }

  // MARK: - Bridge Communication

  /// Creates an AudioItem from a React Native bridge dictionary
  public static func fromBridge(dictionary: [String: Any]) -> AudioItem? {
    guard let url = MediaURL(object: dictionary["url"]) else {
      print("AudioItem.fromBridge: Failed to create track - invalid or missing URL. Dictionary: \(dictionary)")
      return nil
    }

    let headers = dictionary["headers"] as? [String: Any]
    let userAgent = dictionary["userAgent"] as? String
    let pitchAlgorithm = (dictionary["pitchAlgorithm"] as? String).flatMap { PitchAlgorithm(rawValue: $0) }

    let item = AudioItem(
      audioUrl: url.isLocal ? url.value.path : url.value.absoluteString,
      artist: dictionary["artist"] as? String,
      title: dictionary["title"] as? String,
      album: dictionary["album"] as? String,
      sourceType: url.isLocal ? .file : .stream,
      pitchAlgorithm: pitchAlgorithm,
      url: url,
      artworkURL: MediaURL(object: dictionary["artwork"]),
      headers: headers,
      userAgent: userAgent
    )

    item.updateMetadata(dictionary: dictionary)
    return item
  }

  /// Serializes the AudioItem back to a React Native bridge dictionary
  /// Rebuilds the dictionary from current property values (matches Android behavior)
  public func toBridge() -> [String: Any] {
    var map: [String: Any] = [:]

    // URL is required
    if let url = url {
      map["url"] = url.value.absoluteString
    }

    // Source type
    switch sourceType {
    case .file:
      map["type"] = "file"
    case .stream:
      map["type"] = "stream"
    }

    // Headers
    if let headers = headers {
      map["headers"] = headers
    }

    // User agent
    if let userAgent = userAgent {
      map["userAgent"] = userAgent
    }

    // Pitch algorithm
    if let pitchAlgorithm = pitchAlgorithm {
      map["pitchAlgorithm"] = pitchAlgorithm.rawValue
    }

    // Metadata
    if let title = title {
      map["title"] = title
    }

    if let artist = artist {
      map["artist"] = artist
    }

    if let album = album {
      map["album"] = album
    }

    if let artwork = artworkURL?.value.absoluteString {
      map["artwork"] = artwork
    }

    if let date = date {
      map["date"] = date
    }

    if let genre = genre {
      map["genre"] = genre
    }

    if let desc = desc {
      map["description"] = desc
    }

    if let duration = duration {
      map["duration"] = duration
    }

    if let isLiveStream = isLiveStream {
      map["isLiveStream"] = isLiveStream
    }

    return map
  }

  /// Updates metadata from a React Native bridge dictionary
  public func updateMetadata(dictionary: [String: Any]) {
    title = (dictionary["title"] as? String) ?? title
    artist = (dictionary["artist"] as? String) ?? artist
    date = dictionary["date"] as? String
    album = dictionary["album"] as? String
    genre = dictionary["genre"] as? String
    desc = dictionary["description"] as? String
    duration = dictionary["duration"] as? Double
    artworkURL = MediaURL(object: dictionary["artwork"])
    isLiveStream = dictionary["isLiveStream"] as? Bool

    // Update pitch algorithm if provided
    if let algoString = dictionary["pitchAlgorithm"] as? String {
      pitchAlgorithm = PitchAlgorithm(rawValue: algoString)
    }

    // Set asset options
    var options: [String: Any] = [:]
    if let headers {
      options["AVURLAssetHTTPHeaderFieldsKey"] = headers
    }
    if #available(iOS 16, *) {
      if let userAgent {
        options[AVURLAssetHTTPUserAgentKey] = userAgent
      }
    }
    assetOptions = options.isEmpty ? nil : options
  }

  // MARK: - Artwork

  /// Loads artwork from the artworkURL (local file or remote URL)
  public func loadArtwork(_ handler: @escaping (UIImage?) -> Void) {
    guard let artworkURL = artworkURL?.value else {
      handler(nil)
      return
    }

    if self.artworkURL?.isLocal ?? false {
      // Load from local file
      let image = UIImage(contentsOfFile: artworkURL.path)
      handler(image)
    } else {
      // Load from remote URL
      URLSession.shared.dataTask(with: artworkURL, completionHandler: { data, _, error in
        if let data, let artwork = UIImage(data: data), error == nil {
          handler(artwork)
        } else {
          if let error = error {
            print("AudioItem.loadArtwork: Failed to load from \(artworkURL) - \(error)")
          }
          handler(nil)
        }
      }).resume()
    }
  }
}
