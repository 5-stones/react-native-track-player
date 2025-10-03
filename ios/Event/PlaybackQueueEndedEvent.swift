//
//  PlaybackQueueEndedEvent.swift
//  SwiftAudio
//
//  Created by Jørgen Henrichsen on 10/03/2018.
//  Copyright © 2018 Jørgen Henrichsen. All rights reserved.
//

import Foundation

/**
 Event data for when the playback queue has ended.
 */
public struct PlaybackQueueEndedEvent {
  /// The index of the active track when the playback queue ended.
  public let track: Int

  /// The playback position in seconds of the active track when the playback queue ended.
  public let position: Double

  public init(track: Int, position: Double) {
    self.track = track
    self.position = position
  }

  public func toBridge() -> [String: Any] {
    return [
      "track": track,
      "position": position,
    ]
  }
}
