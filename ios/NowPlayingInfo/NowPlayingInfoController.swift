//
//  NowPlayingInfoController.swift
//  SwiftAudio
//
//  Created by Jørgen Henrichsen on 15/03/2018.
//

import Foundation
import MediaPlayer

public class NowPlayingInfoController: NowPlayingInfoControllerProtocol {
  private var infoQueue: DispatchQueueType = DispatchQueue(
    label: "NowPlayingInfoController.infoQueue",
    attributes: .concurrent
  )

  private(set) var infoCenter: NowPlayingInfoCenter
  private(set) var info: [String: Any] = [:]

  public required init() {
    infoCenter = MPNowPlayingInfoCenter.default()
  }

  /// Used for testing purposes.
  public required init(dispatchQueue: DispatchQueueType, infoCenter: NowPlayingInfoCenter) {
    infoQueue = dispatchQueue
    self.infoCenter = infoCenter
  }

  public required init(infoCenter: NowPlayingInfoCenter = MPNowPlayingInfoCenter.default()) {
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
