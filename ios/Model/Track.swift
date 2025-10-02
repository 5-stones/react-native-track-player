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

class Track: AudioItem, TimePitching, AssetOptionsProviding {
  let url: MediaURL

  @objc var title: String?
  @objc var artist: String?

  var date: String?
  var desc: String?
  var genre: String?
  var duration: Double?
  var artworkURL: MediaURL?
  let headers: [String: Any]?
  var userAgent: String?
  let pitchAlgorithm: String?
  var isLiveStream: Bool?

  var album: String?
  var artwork: MPMediaItemArtwork?

  private var originalObject: [String: Any] = [:]

  init?(dictionary: [String: Any]) {
    guard let url = MediaURL(object: dictionary["url"]) else { return nil }
    self.url = url
    headers = dictionary["headers"] as? [String: Any]
    userAgent = dictionary["userAgent"] as? String
    pitchAlgorithm = dictionary["pitchAlgorithm"] as? String

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

    originalObject = originalObject.merging(dictionary) { _, new in new }
  }

  // MARK: - AudioItem Protocol

  func getSourceUrl() -> String {
    return url.isLocal ? url.value.path : url.value.absoluteString
  }

  func getArtist() -> String? {
    return artist
  }

  func getTitle() -> String? {
    return title
  }

  func getAlbumTitle() -> String? {
    return album
  }

  func getSourceType() -> SourceType {
    return url.isLocal ? .file : .stream
  }

  func getArtwork(_ handler: @escaping (UIImage?) -> Void) {
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

  // MARK: - TimePitching Protocol

  func getPitchAlgorithmType() -> AVAudioTimePitchAlgorithm {
    if let pitchAlgorithm {
      switch pitchAlgorithm {
      case PitchAlgorithm.linear.rawValue:
        return .varispeed
      case PitchAlgorithm.music.rawValue:
        return .spectral
      default: // voice
        return .timeDomain
      }
    }

    return .timeDomain
  }

  // MARK: - Authorizing Protocol

  func getAssetOptions() -> [String: Any] {
    var options: [String: Any] = [:]
    if let headers {
      options["AVURLAssetHTTPHeaderFieldsKey"] = headers
    }
    if #available(iOS 16, *) {
      if let userAgent {
        // there is now an official, working way to set the user-agent for every request
        // https://developer.apple.com/documentation/avfoundation/avurlassethttpuseragentkey
        options[AVURLAssetHTTPUserAgentKey] = userAgent
      }
    }
    return options
  }
}
