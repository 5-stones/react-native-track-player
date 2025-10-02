//
//  NowPlayingInfoController.swift
//  SwiftAudio
//
//  Created by Jørgen Henrichsen on 15/03/2018.
//

import Foundation
import MediaPlayer

public class NowPlayingInfoController {
  private var infoQueue = DispatchQueue(
    label: "NowPlayingInfoController.infoQueue",
    attributes: .concurrent
  )

  private(set) var infoCenter: NowPlayingInfoCenter
  private(set) var info: [String: Any] = [:]

  public required init() {
    infoCenter = MPNowPlayingInfoCenter.default()
  }

  public required init(infoCenter: NowPlayingInfoCenter) {
    self.infoCenter = infoCenter
  }

  public func set(keyValues: [NowPlayingInfoKeyValue]) {
    infoQueue.async(flags: .barrier) { [weak self] in
      guard let self else { return }
      for keyValue in keyValues {
        info[keyValue.getKey()] = keyValue.getValue()
      }
      update()
    }
  }

  public func setWithoutUpdate(keyValues: [NowPlayingInfoKeyValue]) {
    infoQueue.async(flags: .barrier) { [weak self] in
      guard let self else { return }
      for keyValue in keyValues {
        info[keyValue.getKey()] = keyValue.getValue()
      }
    }
  }

  public func set(keyValue: NowPlayingInfoKeyValue) {
    infoQueue.async(flags: .barrier) { [weak self] in
      guard let self else { return }
      info[keyValue.getKey()] = keyValue.getValue()
      update()
    }
  }

  private func update() {
    infoCenter.nowPlayingInfo = info
  }

  public func clear() {
    infoQueue.async(flags: .barrier) { [weak self] in
      guard let self else { return }
      info = [:]
      infoCenter.nowPlayingInfo = nil
    }
  }
}
