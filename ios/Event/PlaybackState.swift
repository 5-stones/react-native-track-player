//
//  PlaybackState.swift
//  SwiftAudio
//
//  Created by Jørgen Henrichsen on 10/03/2018.
//  Copyright © 2018 Jørgen Henrichsen. All rights reserved.
//

import Foundation

/**
 The current playback state of the TrackPlayer.
 */
public enum PlaybackState: String {
  /// An asset is being loaded for playback.
  case loading

  /// The current track is loaded, and the player is ready to start playing.
  case ready

  /// The current track is currently buffering and will start playing when
  /// buffering is complete.
  case buffering

  /// The player is paused.
  case paused

  /// The player is stopped.
  case stopped

  /// The player is playing.
  case playing

  /// No track loaded, the player is stopped.
  case idle

  /// Failed
  case failed

  /// Playback has reached the end.
  case ended
}
