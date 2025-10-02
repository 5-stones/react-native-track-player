//
//  NowPlayingInfoKeyValue.swift
//  SwiftAudio
//
//  Created by Jørgen Henrichsen on 28/02/2019.
//

import Foundation

public protocol NowPlayingInfoKeyValue {
  var key: String { get }
  var value: Any? { get }
}
