//
//  PlaybackActiveTrackChangedEvent.swift
//  SwiftAudio
//
//  Created by Jørgen Henrichsen on 10/03/2018.
//  Copyright © 2018 Jørgen Henrichsen. All rights reserved.
//

import Foundation

/**
 Event data for when the active track changes.
 */
public struct PlaybackActiveTrackChangedEvent {
  /// The index of previously active track.
  public let lastIndex: Int?

  /// The previously active track or nil when there wasn't a previously active track.
  public let lastTrack: Track?

  /// The position of the previously active track in seconds.
  public let lastPosition: Double

  /// The newly active track index or nil if there is no longer an active track.
  public let index: Int?

  /// The newly active track or nil if there is no longer an active track.
  public let track: Track?

  public init(
    lastIndex: Int?,
    lastTrack: Track?,
    lastPosition: Double,
    index: Int?,
    track: Track?
  ) {
    self.lastIndex = lastIndex
    self.lastTrack = lastTrack
    self.lastPosition = lastPosition
    self.index = index
    self.track = track
  }

  public func toBridge() -> [String: Any] {
    var result: [String: Any] = ["lastPosition": lastPosition]

    if let lastIndex = lastIndex {
      result["lastIndex"] = lastIndex
    }

    if let lastTrack = lastTrack {
      result["lastTrack"] = lastTrack.toBridge()
    }

    if let index = index {
      result["index"] = index
    }

    if let track = track {
      result["track"] = track.toBridge()
    }

    return result
  }
}
