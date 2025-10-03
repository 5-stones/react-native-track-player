//
//  PlayerItemNotificationObserver.swift
//  SwiftAudio
//
//  Created by Jørgen Henrichsen on 12/03/2018.
//

import AVFoundation
import Foundation

/**
 Observes player item notifications and calls TrackPlayer methods directly.
 */
class PlayerItemNotificationObserver {
  private let notificationCenter: NotificationCenter = .default

  private(set) weak var observingAVItem: AVPlayerItem?
  weak var player: TrackPlayer?

  private(set) var isObserving: Bool = false

  deinit {
    stopObservingCurrentItem()
  }

  /**
   Will start observing notifications from an AVPlayerItem.

   - parameter avItem: The AVPlayerItem to observe.
   - important: Cannot observe more than one item at a time.
   */
  func startObserving(item avItem: AVPlayerItem) {
    stopObservingCurrentItem()
    observingAVItem = avItem
    isObserving = true
    notificationCenter.addObserver(
      self,
      selector: #selector(avItemDidPlayToEndTime),
      name: NSNotification.Name.AVPlayerItemDidPlayToEndTime,
      object: avItem
    )
    notificationCenter.addObserver(
      self,
      selector: #selector(avItemFailedToPlayToEndTime),
      name: NSNotification.Name.AVPlayerItemFailedToPlayToEndTime,
      object: avItem
    )
    notificationCenter.addObserver(
      self,
      selector: #selector(avItemPlaybackStalled),
      name: NSNotification.Name.AVPlayerItemPlaybackStalled,
      object: avItem
    )
  }

  /**
   Stop receiving notifications for the current AVPlayerItem.
   */
  func stopObservingCurrentItem() {
    guard let observingAVItem, isObserving else {
      return
    }
    notificationCenter.removeObserver(
      self,
      name: NSNotification.Name.AVPlayerItemDidPlayToEndTime,
      object: observingAVItem
    )
    notificationCenter.removeObserver(
      self,
      name: NSNotification.Name.AVPlayerItemFailedToPlayToEndTime,
      object: observingAVItem
    )
    notificationCenter.removeObserver(
      self,
      name: NSNotification.Name.AVPlayerItemPlaybackStalled,
      object: observingAVItem
    )
    self.observingAVItem = nil
    isObserving = false
  }

  @objc private func avItemDidPlayToEndTime() {
    player?.handleTrackDidPlayToEndTime()
  }

  @objc private func avItemFailedToPlayToEndTime() {
    player?.handleTrackFailedToPlayToEndTime()
  }

  @objc private func avItemPlaybackStalled() {
    player?.handleTrackPlaybackStalled()
  }
}
