//
//  AudioItem.swift
//  SwiftAudio
//
//  Created by Jørgen Henrichsen on 18/03/2018.
//

import AVFoundation
import Foundation
import UIKit

public class AudioItem {
  public var audioUrl: String
  public var artist: String?
  public var title: String?
  public var album: String?
  public var sourceType: SourceType
  public var artwork: UIImage?

  /// Optional time pitch algorithm for this item. If nil, the player's default will be used.
  public var pitchAlgorithm: AVAudioTimePitchAlgorithm?

  /// Optional initial playback time for this item.
  public var initialTime: TimeInterval?

  /// Optional asset initialization options.
  public var assetOptions: [String: Any]?

  /// Optional remote commands for this item. If nil, the player's default commands will be used.
  public var remoteCommands: [RemoteCommand]?

  public init(
    audioUrl: String,
    artist: String? = nil,
    title: String? = nil,
    album: String? = nil,
    sourceType: SourceType,
    artwork: UIImage? = nil,
    pitchAlgorithm: AVAudioTimePitchAlgorithm? = nil,
    initialTime: TimeInterval? = nil,
    assetOptions: [String: Any]? = nil,
    remoteCommands: [RemoteCommand]? = nil
  ) {
    self.audioUrl = audioUrl
    self.artist = artist
    self.title = title
    self.album = album
    self.sourceType = sourceType
    self.artwork = artwork
    self.pitchAlgorithm = pitchAlgorithm
    self.initialTime = initialTime
    self.assetOptions = assetOptions
    self.remoteCommands = remoteCommands
  }

  public func loadArtwork(_ handler: @escaping (UIImage?) -> Void) {
    handler(artwork)
  }
}
