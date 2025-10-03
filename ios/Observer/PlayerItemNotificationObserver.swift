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

  private(set) weak var observingItem: AVPlayerItem?
  weak var player: TrackPlayer?

  private(set) var isObserving: Bool = false

  deinit {
    stopObservingCurrentItem()
  }

  /**
   Will start observing notifications from an item.

   - parameter item: The item to observe.
   - important: Cannot observe more than one item at a time.
   */
  func startObserving(item: AVPlayerItem) {
    stopObservingCurrentItem()
    observingItem = item
    isObserving = true
    notificationCenter.addObserver(
      self,
      selector: #selector(itemDidPlayToEndTime),
      name: NSNotification.Name.AVPlayerItemDidPlayToEndTime,
      object: item
    )
    notificationCenter.addObserver(
      self,
      selector: #selector(itemFailedToPlayToEndTime),
      name: NSNotification.Name.AVPlayerItemFailedToPlayToEndTime,
      object: item
    )
    notificationCenter.addObserver(
      self,
      selector: #selector(itemPlaybackStalled),
      name: NSNotification.Name.AVPlayerItemPlaybackStalled,
      object: item
    )
  }

  /**
   Stop receiving notifications for the current item.
   */
  func stopObservingCurrentItem() {
    guard let observingItem, isObserving else {
      return
    }
    notificationCenter.removeObserver(
      self,
      name: NSNotification.Name.AVPlayerItemDidPlayToEndTime,
      object: observingItem
    )
    notificationCenter.removeObserver(
      self,
      name: NSNotification.Name.AVPlayerItemFailedToPlayToEndTime,
      object: observingItem
    )
    notificationCenter.removeObserver(
      self,
      name: NSNotification.Name.AVPlayerItemPlaybackStalled,
      object: observingItem
    )
    self.observingItem = nil
    isObserving = false
  }

  @objc private func itemDidPlayToEndTime() {
    player?.handleItemDidPlayToEndTime()
  }

  @objc private func itemFailedToPlayToEndTime() {
    player?.itemFailedToPlayToEndTime()
  }

  @objc private func itemPlaybackStalled() {
    player?.handleItemPlaybackStalled()
  }
}
