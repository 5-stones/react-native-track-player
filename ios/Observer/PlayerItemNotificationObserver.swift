//
//  PlayerItemNotificationObserver.swift
//  SwiftAudio
//
//  Created by Jørgen Henrichsen on 12/03/2018.
//

import Foundation
import AVFoundation

/**
 Observes player item notifications and calls AudioPlayer methods directly.
 */
class PlayerItemNotificationObserver {

    private let notificationCenter: NotificationCenter = NotificationCenter.default

    private(set) weak var observingItem: AVPlayerItem?
    weak var audioPlayer: AudioPlayer?

    private(set) var isObserving: Bool = false

    init(audioPlayer: AudioPlayer) {
        self.audioPlayer = audioPlayer
    }

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
        guard let observingItem = observingItem, isObserving else {
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
        audioPlayer?.handleItemDidPlayToEndTime()
    }

    @objc private func itemFailedToPlayToEndTime() {
        audioPlayer?.itemFailedToPlayToEndTime()
    }

    @objc private func itemPlaybackStalled() {
        audioPlayer?.handleItemPlaybackStalled()
    }
}
