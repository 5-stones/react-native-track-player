//
//  PitchAlgorithms.swift
//  RNTrackPlayer
//
//  Created by Anders Lemke on 28/02/2018.
//  Copyright © 2018 David Chavez. All rights reserved.
//

import AVFoundation
import Foundation

enum PitchAlgorithm: String {
  case linear
  case music
  case voice

  /// The corresponding AVAudioTimePitchAlgorithm for use with AVPlayer
  var avAlgorithm: AVAudioTimePitchAlgorithm {
    switch self {
    case .linear: return .varispeed
    case .music: return .spectral
    case .voice: return .timeDomain
    }
  }
}
