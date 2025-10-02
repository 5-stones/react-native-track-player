//
//  PlayerItemPropertyObserver.swift
//  SwiftAudio
//
//  Created by Jørgen Henrichsen on 28/07/2018.
//

import Foundation
import AVFoundation

/**
 Observes player item property changes and calls AudioPlayer methods directly.
 */
class PlayerItemPropertyObserver: NSObject {

    private static var context = 0
    private var currentMetadataOutput: AVPlayerItemMetadataOutput?

    private struct AVPlayerItemKeyPath {
        static let duration = #keyPath(AVPlayerItem.duration)
        static let loadedTimeRanges = #keyPath(AVPlayerItem.loadedTimeRanges)
        static let playbackLikelyToKeepUp = #keyPath(AVPlayerItem.isPlaybackLikelyToKeepUp)
    }

    private(set) var isObserving: Bool = false

    private(set) weak var observingItem: AVPlayerItem?
    weak var player: AudioPlayer?

    init(player: AudioPlayer) {
        self.player = player
        super.init()
    }
    
    deinit {
        stopObservingCurrentItem()
    }
    
    /**
     Start observing an item. Will remove self as observer from old item, if any.
     
     - parameter item: The player item to observe.
     */
    func startObserving(item: AVPlayerItem) {
        stopObservingCurrentItem()
        
        self.isObserving = true
        self.observingItem = item
        item.addObserver(self, forKeyPath: AVPlayerItemKeyPath.duration, options: [.new], context: &PlayerItemPropertyObserver.context)
        item.addObserver(self, forKeyPath: AVPlayerItemKeyPath.loadedTimeRanges, options: [.new], context: &PlayerItemPropertyObserver.context)
        item.addObserver(self, forKeyPath: AVPlayerItemKeyPath.playbackLikelyToKeepUp, options: [.new], context: &PlayerItemPropertyObserver.context)
        
        // Create and add a new metadata output to the item.
        let metadataOutput = AVPlayerItemMetadataOutput()
        metadataOutput.setDelegate(self, queue: .main)
        item.add(metadataOutput)
        self.currentMetadataOutput = metadataOutput
    }
    
    func stopObservingCurrentItem() {
        guard let observingItem = observingItem, isObserving else {
            return
        }
        
        observingItem.removeObserver(self, forKeyPath: AVPlayerItemKeyPath.duration, context: &PlayerItemPropertyObserver.context)
        observingItem.removeObserver(self, forKeyPath: AVPlayerItemKeyPath.loadedTimeRanges, context: &PlayerItemPropertyObserver.context)
        observingItem.removeObserver(self, forKeyPath: AVPlayerItemKeyPath.playbackLikelyToKeepUp, context: &PlayerItemPropertyObserver.context)
        
        // Remove all metadata outputs from the item.
        observingItem.removeAllMetadataOutputs()
        
        isObserving = false
        self.observingItem = nil
        self.currentMetadataOutput = nil
    }
    
    override func observeValue(forKeyPath keyPath: String?, of object: Any?, change: [NSKeyValueChangeKey : Any]?, context: UnsafeMutableRawPointer?) {
        guard context == &PlayerItemPropertyObserver.context, let observedKeyPath = keyPath else {
            super.observeValue(forKeyPath: keyPath, of: object, change: change, context: context)
            return
        }

        switch observedKeyPath {
        case AVPlayerItemKeyPath.duration:
            if let duration = change?[.newKey] as? CMTime {
                player?.handleDurationUpdate(duration.seconds)
            }

        case AVPlayerItemKeyPath.loadedTimeRanges:
            if let ranges = change?[.newKey] as? [NSValue], let duration = ranges.first?.timeRangeValue.duration {
                player?.handleDurationUpdate(duration.seconds)
            }

        case AVPlayerItemKeyPath.playbackLikelyToKeepUp:
            if let playbackLikelyToKeepUp = change?[.newKey] as? Bool {
                player?.itemDidUpdatePlaybackLikelyToKeepUp(playbackLikelyToKeepUp)
            }

        default: break

        }
    }
}

extension PlayerItemPropertyObserver: AVPlayerItemMetadataOutputPushDelegate {
    func metadataOutput(_ output: AVPlayerItemMetadataOutput, didOutputTimedMetadataGroups groups: [AVTimedMetadataGroup], from track: AVPlayerItemTrack?) {
        if output == currentMetadataOutput {
            player?.handleTimedMetadataReceived(groups)
        }
    }
}

extension AVPlayerItem {
    func removeAllMetadataOutputs() {
        for output in self.outputs.filter({ $0 is AVPlayerItemMetadataOutput }) {
            self.remove(output)
        }
    }
}
