//
//  Track.swift
//  RNTrackPlayer
//
//  Created by David Chavez on 12.08.17.
//  Copyright © 2017 David Chavez. All rights reserved.
//

import AVFoundation
import Foundation
import MediaPlayer

class Track: AudioItem {
  let url: MediaURL

  var date: String?
  var desc: String?
  var genre: String?
  var duration: Double?
  var artworkURL: MediaURL?
  let headers: [String: Any]?
  var userAgent: String?
  let pitchAlgorithmString: String?
  var isLiveStream: Bool?

  private var originalObject: [String: Any] = [:]

  init?(dictionary: [String: Any]) {
    guard let url = MediaURL(object: dictionary["url"]) else { return nil }
    self.url = url
    headers = dictionary["headers"] as? [String: Any]
    userAgent = dictionary["userAgent"] as? String
    pitchAlgorithmString = dictionary["pitchAlgorithm"] as? String

    super.init(
      audioUrl: url.isLocal ? url.value.path : url.value.absoluteString,
      artist: dictionary["artist"] as? String,
      title: dictionary["title"] as? String,
      album: dictionary["album"] as? String,
      sourceType: url.isLocal ? .file : .stream
    )

    updateMetadata(dictionary: dictionary)
  }

  // MARK: - Public Interface

  func toObject() -> [String: Any] {
    return originalObject
  }

  func updateMetadata(dictionary: [String: Any]) {
    title = (dictionary["title"] as? String) ?? title
    artist = (dictionary["artist"] as? String) ?? artist
    date = dictionary["date"] as? String
    album = dictionary["album"] as? String
    genre = dictionary["genre"] as? String
    desc = dictionary["description"] as? String
    duration = dictionary["duration"] as? Double
    artworkURL = MediaURL(object: dictionary["artwork"])
    isLiveStream = dictionary["isLiveStream"] as? Bool

    // Set pitch algorithm based on string value
    if let pitchAlgorithmString {
      switch pitchAlgorithmString {
      case PitchAlgorithm.linear.rawValue:
        pitchAlgorithm = .varispeed
      case PitchAlgorithm.music.rawValue:
        pitchAlgorithm = .spectral
      default: // voice
        pitchAlgorithm = .timeDomain
      }
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

    originalObject = originalObject.merging(dictionary) { _, new in new }
  }

  // MARK: - AudioItem Overrides

  override func loadArtwork(_ handler: @escaping (UIImage?) -> Void) {
    if let artworkURL = artworkURL?.value {
      if self.artworkURL?.isLocal ?? false {
        let image = UIImage(contentsOfFile: artworkURL.path)
        handler(image)
      } else {
        URLSession.shared.dataTask(with: artworkURL, completionHandler: { data, _, error in
          if let data, let artwork = UIImage(data: data), error == nil {
            handler(artwork)
          } else {
            handler(nil)
          }
        }).resume()
      }
    } else {
      handler(nil)
    }
  }
}
