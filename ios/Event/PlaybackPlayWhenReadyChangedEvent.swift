//
//  PlaybackPlayWhenReadyChangedEvent.swift
//  SwiftAudio
//
//  Created by Jørgen Henrichsen on 10/03/2018.
//  Copyright © 2018 Jørgen Henrichsen. All rights reserved.
//

import Foundation

/**
 Event data for when playWhenReady changes.
 */
public struct PlaybackPlayWhenReadyChangedEvent {
  /// Whether the player will play when it is ready to do so.
  public let playWhenReady: Bool

  public init(playWhenReady: Bool) {
    self.playWhenReady = playWhenReady
  }

  public func toBridge() -> [String: Any] {
    return [
      "playWhenReady": playWhenReady
    ]
  }
}
