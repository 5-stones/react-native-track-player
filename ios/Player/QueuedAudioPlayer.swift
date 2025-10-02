//
//  QueuedAudioPlayer.swift
//  SwiftAudio
//
//  Created by Jørgen Henrichsen on 24/03/2018.
//

import Foundation
import MediaPlayer

/**
 An audio player that can keep track of a queue of AudioItems.
 */
public class QueuedAudioPlayer: AudioPlayer {
  fileprivate var lastIndex: Int = -1
  fileprivate var lastItem: AudioItem?

  override public init(
    nowPlayingInfoController: NowPlayingInfoControllerProtocol = NowPlayingInfoController(),
    remoteCommandController: RemoteCommandController = RemoteCommandController()
  ) {
    super.init(
      nowPlayingInfoController: nowPlayingInfoController,
      remoteCommandController: remoteCommandController
    )
  }

  /// The repeat mode for the queue player.
  public var repeatMode: RepeatMode = .off

  // MARK: - Queue Properties

  private func assertMainThread() {
    assert(Thread.isMainThread, "QueuedAudioPlayer queue must be accessed from the main thread")
  }

  /**
   The index of the current item. `-1` when there is no current item
   */
  private(set) var currentIndex: Int = -1

  /**
   All items held by the queue.
   */
  private(set) var items: [AudioItem] = []

  override public var currentItem: AudioItem? {
    assertMainThread()
    guard currentIndex >= 0, currentIndex < items.count else { return nil }
    return items[currentIndex]
  }

  /**
   The upcoming items in the queue.
   */
  public var nextItems: [AudioItem] {
    assertMainThread()
    guard currentIndex >= 0, currentIndex < items.count - 1 else { return [] }
    return Array(items[currentIndex + 1 ..< items.count])
  }

  /**
   The previous items held by the queue.
   */
  public var previousItems: [AudioItem] {
    assertMainThread()
    guard currentIndex > 0 else { return [] }
    return Array(items[0 ..< currentIndex])
  }

  override public func clear() {
    clearQueue()
    super.clear()
  }

  /**
   Whether there are more items after the current item in the queue.
   */
  private var hasNextItem: Bool {
    currentIndex < items.count - 1
  }

  // MARK: - Queue Validation

  private func throwIfQueueEmpty() throws {
    if items.isEmpty {
      throw AudioPlayerError.QueueError.empty
    }
  }

  private func throwIfIndexInvalid(
    index: Int,
    name: String = "index",
    min: Int? = nil,
    max: Int? = nil
  ) throws {
    guard index >= (min ?? 0), (max ?? items.count) > index else {
      throw AudioPlayerError.QueueError.invalidIndex(
        index: index,
        message: "\(name) must be non-negative and less than \(items.count)"
      )
    }
  }

  // MARK: - Queue Methods

  /**
   Will replace the current item with a new one and load it into the player.

   - parameter item: The AudioItem to replace the current item.
   - parameter playWhenReady: Optional, whether to start playback when the item is ready.
   */
  override public func load(item: AudioItem, playWhenReady: Bool? = nil) {
    handlePlayWhenReady(playWhenReady) {
      replaceCurrentItem(with: item)
    }
  }

  /**
   Replace the current item with a new one. If there is no current item, it is equivalent to calling `add(item:)`, `jump(to: itemIndex)`.

   - parameter item: The item to set as the new current item.
   */
  private func replaceCurrentItem(with item: AudioItem) {
    assertMainThread()
    if currentIndex == -1 {
      items.append(item)
      currentIndex = items.count - 1
      try! jump(to: 0)
    } else {
      items[currentIndex] = item
      handleCurrentItemChanged()
    }
  }

  /**
   Add items to the queue.

   - parameter items: The items to add to the queue.
   - parameter playWhenReady: Optional, whether to start playback when the item is ready.
   */
  public func add(items: [AudioItem], playWhenReady: Bool? = nil) {
    handlePlayWhenReady(playWhenReady) {
      try! addItems(items)
    }
  }

  private func addItems(_ newItems: [AudioItem]) {
    assertMainThread()
    guard !newItems.isEmpty else { return }
    let wasEmpty = items.isEmpty
    items.append(contentsOf: newItems)
    if wasEmpty {
      try! jump(to: 0)
    }
  }

  public func add(items: [AudioItem], at index: Int) throws {
    assertMainThread()
    guard !items.isEmpty else { return }
    guard index >= 0, self.items.count >= index else {
      throw AudioPlayerError.QueueError.invalidIndex(
        index: index,
        message: "Index to insert at has to be non-negative and equal to or smaller than the number of items: (\(self.items.count))"
      )
    }
    let wasEmpty = self.items.isEmpty
    // Correct index when items were inserted in front of it:
    if self.items.count > 1, currentIndex >= index {
      currentIndex += items.count
    }
    self.items.insert(contentsOf: items, at: index)
    if wasEmpty {
      try! jump(to: 0)
    }
  }

  /**
   Step to the next item in the queue.
   */
  public func next() {
    let lastIndex = currentIndex
    let playbackWasActive = playbackActive
    _ = skip(by: 1, wrap: repeatMode == .queue)
    if playbackWasActive && lastIndex != currentIndex || repeatMode == .queue {
      event.playbackEnd.emit(data: .skippedToNext)
    }
  }

  /**
   Step to the previous item in the queue.
   */
  public func previous() {
    let lastIndex = currentIndex
    let playbackWasActive = playbackActive
    _ = skip(by: -1, wrap: repeatMode == .queue)
    if playbackWasActive && lastIndex != currentIndex || repeatMode == .queue {
      event.playbackEnd.emit(data: .skippedToPrevious)
    }
  }

  private func skip(by delta: Int, wrap: Bool) -> AudioItem? {
    assertMainThread()
    guard currentItem != nil, !items.isEmpty else { return nil }

    if items.count == 1 {
      if wrap, playWhenReady {
        replay()
      }
      return currentItem
    }

    var index = currentIndex + delta
    if wrap {
      index = (index + items.count) % items.count
    }
    let newIndex = max(0, min(items.count - 1, index))

    if newIndex != currentIndex {
      currentIndex = newIndex
      handleCurrentItemChanged()
    }
    return currentItem
  }

  /**
   Remove an item from the queue.

   - parameter index: The index of the item to remove.
   - throws: `AudioPlayerError.QueueError`
   */
  public func removeItem(at index: Int) throws {
    assertMainThread()
    try throwIfQueueEmpty()
    try throwIfIndexInvalid(index: index)
    let result = items.remove(at: index)
    if index == currentIndex {
      currentIndex = items.count > 0 ? currentIndex % items.count : -1
      handleCurrentItemChanged()
    } else if index < currentIndex {
      currentIndex -= 1
    }
  }

  /**
   Jump to a certain item in the queue.

   - parameter index: The index of the item to jump to.
   - parameter playWhenReady: Optional, whether to start playback when the item is ready.
   - throws: `AudioPlayerError`
   */
  public func jumpToItem(atIndex index: Int, playWhenReady: Bool? = nil) throws {
    try handlePlayWhenReady(playWhenReady) {
      if index == currentIndex {
        seek(to: 0)
      } else {
        _ = try jump(to: index)
      }
      event.playbackEnd.emit(data: .jumpedToIndex)
    }
  }

  private func jump(to index: Int) throws -> AudioItem {
    assertMainThread()
    try throwIfQueueEmpty()
    try throwIfIndexInvalid(index: index)

    if index == currentIndex {
      if playWhenReady {
        replay()
      }
    } else {
      currentIndex = index
      handleCurrentItemChanged()
    }
    return currentItem!
  }

  /**
   Move an item in the queue from one position to another.

   - parameter fromIndex: The index of the item to move.
   - parameter toIndex: The index to move the item to.
   - throws: `AudioPlayerError.QueueError`
   */
  public func moveItem(fromIndex: Int, toIndex: Int) throws {
    assertMainThread()
    try throwIfQueueEmpty()
    try throwIfIndexInvalid(index: fromIndex, name: "fromIndex")
    try throwIfIndexInvalid(index: toIndex, name: "toIndex", max: Int.max)

    let item = items.remove(at: fromIndex)
    items.insert(item, at: min(items.count, toIndex))
    if fromIndex == currentIndex {
      currentIndex = toIndex
      handleCurrentItemChanged()
    }
  }

  /**
   Remove all upcoming items, those returned by `next()`
   */
  public func removeUpcomingItems() {
    assertMainThread()
    guard !items.isEmpty else { return }
    let nextIndex = currentIndex + 1
    guard nextIndex < items.count else { return }
    items.removeSubrange(nextIndex ..< items.count)
  }

  /**
   Removes all items from queue
   */
  private func clearQueue() {
    assertMainThread()
    let itemWasNil = currentIndex == -1
    currentIndex = -1
    items.removeAll()
    if !itemWasNil {
      handleCurrentItemChanged()
    }
  }

  func replay() {
    seek(to: 0) { [weak self] succeeded in
      if succeeded {
        self?.play()
      }
    }
  }

  // MARK: - AudioPlayer Event Overrides

  override func handleItemDidPlayToEndTime() {
    DispatchQueue.main.async { [weak self] in
      guard let self else { return }
      event.playbackEnd.emit(data: .playedUntilEnd)
      if repeatMode == .track {
        replay()
      } else if repeatMode == .queue || hasNextItem {
        next()
      } else {
        state = .ended
      }
    }
  }

  func handleCurrentItemChanged() {
    let lastPosition = currentTime
    let shouldContinuePlayback = playWhenReady
    if let currentItem {
      // Ensure playWhenReady is set before loading to preserve playback state
      playWhenReady = shouldContinuePlayback
      super.load(item: currentItem, playWhenReady: nil)
    } else {
      super.clear()
    }
    event.currentItem.emit(
      data: (
        item: currentItem,
        index: currentIndex == -1 ? nil : currentIndex,
        lastItem: lastItem,
        lastIndex: lastIndex == -1 ? nil : lastIndex,
        lastPosition: lastPosition
      )
    )
    lastItem = currentItem
    lastIndex = currentIndex
  }
}
