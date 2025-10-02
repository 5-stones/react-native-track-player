//
//  AudioPlayer.swift
//  SwiftAudio
//
//  Created by Jørgen Henrichsen on 15/03/2018.
//

import Foundation
import MediaPlayer

public class AudioPlayer {
  public let nowPlayingInfoController: NowPlayingInfoController
  public let remoteCommandController: RemoteCommandController
  public let event = EventHolder()

  private(set) var currentItem: AudioItem?

  // MARK: - AVPlayer Properties (from AVPlayerWrapper)

  /// Represents a seek operation that's pending while an item loads
  private struct PendingSeek {
    let time: TimeInterval
    let completion: ((Bool) -> Void)?

    func execute(on player: AVPlayer, delegate: AudioPlayer?) {
      let cmTime = CMTimeMakeWithSeconds(time, preferredTimescale: 1000)
      player
        .seek(to: cmTime, toleranceBefore: CMTime.zero, toleranceAfter: CMTime.zero) { finished in
          delegate?.handleSeekCompleted(to: Double(time), didFinish: finished)
          completion?(finished)
        }
    }

    func cancel() {
      completion?(false)
    }
  }

  private var avPlayer = AVPlayer()

  private lazy var playerObserver: PlayerStateObserver = {
    let observer = PlayerStateObserver()
    observer.player = self
    return observer
  }()

  private lazy var playerTimeObserver: PlayerTimeObserver = {
    let observer = PlayerTimeObserver(
      periodicObserverTimeInterval: _timeEventFrequency.getTime()
    )
    observer.player = self
    return observer
  }()

  private lazy var playerItemNotificationObserver: PlayerItemNotificationObserver = {
    let observer = PlayerItemNotificationObserver()
    observer.player = self
    return observer
  }()

  private lazy var playerItemObserver: PlayerItemPropertyObserver = {
    let observer = PlayerItemPropertyObserver()
    observer.player = self
    return observer
  }()
  private var pendingSeek: PendingSeek?
  private var asset: AVAsset?
  private var item: AVPlayerItem?
  private var url: URL?
  private var urlOptions: [String: Any]?
  private let stateQueue = DispatchQueue(
    label: "AudioPlayer.stateQueue",
    attributes: .concurrent
  )
  private(set) var playbackError: AudioPlayerError.PlaybackError?
  var _state: AudioPlayerState = .idle
  private(set) var lastPlayerTimeControlStatus: AVPlayer.TimeControlStatus = .paused
  private var _rate: Float = 1.0
  var _playWhenReady: Bool = false
  var _bufferDuration: TimeInterval = 0
  var _timeEventFrequency: TimeEventFrequency = .everySecond

  /**
   Set this to false to disable automatic updating of now playing info for control center and lock screen.
   */
  public var automaticallyUpdateNowPlayingInfo: Bool = true

  /**
   Controls the time pitch algorithm applied to each item loaded into the player.
   If the loaded `AudioItem` conforms to `TimePitcher`-protocol this will be overriden.
   */
  public var audioTimePitchAlgorithm: AVAudioTimePitchAlgorithm = .timeDomain

  /**
   Default remote commands to use for each playing item
   */
  public var remoteCommands: [RemoteCommand] = [] {
    didSet {
      if let item = currentItem {
        enableRemoteCommands(forItem: item)
      }
    }
  }

  /**
    Handles the `playWhenReady` setting while executing a given action.

    This method takes an optional `Bool` value and a closure representing an action to execute.
    If the `Bool` value is not `nil`, `self.playWhenReady` is set accordingly either before or
    after executing the action.

    - Parameters:
      - playWhenReady: Optional `Bool` to set `self.playWhenReady`.
                       - If `true`, `self.playWhenReady` will be set after executing the action.
                       - If `false`, `self.playWhenReady` will be set before executing the action.
                       - If `nil`, `self.playWhenReady` will not be changed.
      - action: A closure representing the action to execute. This closure can throw an error.

    - Throws: This function will propagate any errors thrown by the `action` closure.
   */
  func handlePlayWhenReady(_ playWhenReady: Bool?, action: () throws -> Void) rethrows {
    if playWhenReady == false {
      self.playWhenReady = false
    }

    try action()

    if playWhenReady == true {
      self.playWhenReady = true
    }
  }

  // MARK: - AVPlayer State and Computed Properties

  var state: AudioPlayerState {
    get {
      var state: AudioPlayerState!
      stateQueue.sync {
        state = _state
      }
      return state
    }
    set {
      stateQueue.async(flags: .barrier) { [weak self] in
        guard let self else { return }
        let currentState = _state
        if currentState != newValue {
          _state = newValue
          handleStateChange(newValue)
        }
      }
    }
  }

  var currentAVPlayerItem: AVPlayerItem? {
    avPlayer.currentItem
  }

  var playbackActive: Bool {
    switch state {
    case .idle, .stopped, .ended, .failed:
      return false
    default: return true
    }
  }

  var reasonForWaitingToPlay: AVPlayer.WaitingReason? {
    avPlayer.reasonForWaitingToPlay
  }

  // MARK: - Getters from AVPlayerWrapper

  /**
   The elapsed playback time of the current item.
   */
  public var currentTime: Double {
    let seconds = avPlayer.currentTime().seconds
    return seconds.isNaN ? 0 : seconds
  }

  /**
   The duration of the current AudioItem.
   */
  public var duration: Double {
    if let seconds = currentAVPlayerItem?.asset.duration.seconds, !seconds.isNaN {
      return seconds
    } else if let seconds = currentAVPlayerItem?.duration.seconds, !seconds.isNaN {
      return seconds
    } else if let seconds = currentAVPlayerItem?.seekableTimeRanges.last?.timeRangeValue.duration
      .seconds,
      !seconds.isNaN
    {
      return seconds
    }
    return 0.0
  }

  /**
   The bufferedPosition of the current AudioItem.
   */
  public var bufferedPosition: Double {
    currentAVPlayerItem?.loadedTimeRanges.last?.timeRangeValue.end.seconds ?? 0
  }

  /**
   The current state of the underlying `AudioPlayer`.
   */
  public var playerState: AudioPlayerState {
    state
  }

  // MARK: - Setters for AVPlayerWrapper

  /**
   Whether the player should start playing automatically when the item is ready.
   */
  public var playWhenReady: Bool {
    get { _playWhenReady }
    set {
      let oldValue = _playWhenReady
      _playWhenReady = newValue
      if newValue == true, state == .failed || state == .stopped {
        reload(startFromCurrentTime: state == .failed)
      }
      applyAVPlayerRate()

      if oldValue != newValue {
        handlePlayWhenReadyChange(newValue)
      }
    }
  }

  /**
   The amount of seconds to be buffered by the player. Default value is 0 seconds, this means the AVPlayer will choose an appropriate level of buffering. Setting `bufferDuration` to larger than zero automatically disables `automaticallyWaitsToMinimizeStalling`. Setting it back to zero automatically enables `automaticallyWaitsToMinimizeStalling`.

   [Read more from Apple Documentation](https://developer.apple.com/documentation/avfoundation/avplayeritem/1643630-preferredforwardbufferduration)
   */
  public var bufferDuration: TimeInterval {
    get { _bufferDuration }
    set {
      _bufferDuration = newValue
      avPlayer.automaticallyWaitsToMinimizeStalling = _bufferDuration == 0
    }
  }

  /**
   Indicates whether the player should automatically delay playback in order to minimize stalling. Setting this to true will also set `bufferDuration` back to `0`.

   [Read more from Apple Documentation](https://developer.apple.com/documentation/avfoundation/avplayer/1643482-automaticallywaitstominimizestal)
   */
  public var automaticallyWaitsToMinimizeStalling: Bool {
    get { avPlayer.automaticallyWaitsToMinimizeStalling }
    set {
      if newValue {
        _bufferDuration = 0
      }
      avPlayer.automaticallyWaitsToMinimizeStalling = newValue
    }
  }

  /**
   Set this to decide how often the player should call the delegate with time progress events.
   */
  public var timeEventFrequency: TimeEventFrequency {
    get { _timeEventFrequency }
    set {
      _timeEventFrequency = newValue
      playerTimeObserver.periodicObserverTimeInterval = newValue.getTime()
    }
  }

  public var volume: Float {
    get { avPlayer.volume }
    set { avPlayer.volume = newValue }
  }

  public var isMuted: Bool {
    get { avPlayer.isMuted }
    set { avPlayer.isMuted = newValue }
  }

  public var rate: Float {
    get { _rate }
    set {
      _rate = newValue
      applyAVPlayerRate()
      if automaticallyUpdateNowPlayingInfo {
        updateNowPlayingPlaybackValues()
      }
    }
  }

  // MARK: - Init

  /**
   Create a new AudioPlayer.

   - parameter infoCenter: The InfoCenter to update. Default is `MPNowPlayingInfoCenter.default()`.
   */
  public init(
    nowPlayingInfoController: NowPlayingInfoController = NowPlayingInfoController(),
    remoteCommandController: RemoteCommandController = RemoteCommandController()
  ) {
    self.nowPlayingInfoController = nowPlayingInfoController
    self.remoteCommandController = remoteCommandController
    self.remoteCommandController.audioPlayer = self

    setupAVPlayer()
  }

  // MARK: - Player Actions

  /**
   Load an AudioItem into the manager.

   - parameter item: The AudioItem to load. The info given in this item is the one used for the InfoCenter.
   - parameter playWhenReady: Optional, whether to start playback when the item is ready.
   */
  public func load(item: AudioItem, playWhenReady: Bool? = nil) {
    handlePlayWhenReady(playWhenReady) {
      currentItem = item

      if automaticallyUpdateNowPlayingInfo {
        // Reset playback values without updating, because that will happen in
        // the loadNowPlayingMetaValues call straight after:
        nowPlayingInfoController.setWithoutUpdate(keyValues: [
          MediaItemProperty.duration(nil),
          NowPlayingInfoProperty.playbackRate(nil),
          NowPlayingInfoProperty.elapsedPlaybackTime(nil),
        ])
        loadNowPlayingMetaValues()
      }

      enableRemoteCommands(forItem: item)

      loadFromString(
        from: item.audioUrl,
        type: item.sourceType,
        playWhenReady: self.playWhenReady,
        initialTime: item.initialTime,
        options: item.assetOptions
      )
    }
  }

  /**
   Toggle playback status.
   */
  public func togglePlaying() {
    switch avPlayer.timeControlStatus {
    case .playing, .waitingToPlayAtSpecifiedRate:
      pause()
    case .paused:
      play()
    @unknown default:
      fatalError("Unknown AVPlayer.timeControlStatus")
    }
  }

  /**
   Start playback
   */
  public func play() {
    playWhenReady = true
  }

  /**
   Pause playback
   */
  public func pause() {
    playWhenReady = false
  }

  /**
   Stop playback
   */
  public func stop() {
    let wasActive = playbackActive
    state = .stopped
    clearCurrentItem()
    playWhenReady = false
    if wasActive {
      event.playbackEnd.emit(data: .playerStopped)
    }
  }

  /**
   Reload the current item.
   */
  public func reload(startFromCurrentTime: Bool) {
    var time: Double? = nil
    if startFromCurrentTime {
      if let currentItem = currentAVPlayerItem {
        if !currentItem.duration.isIndefinite {
          time = currentItem.currentTime().seconds
        }
      }
    }
    loadAVPlayer()
    if let time {
      seek(to: time)
    }
  }

  /**
   Seek to a specific time in the item.
   */
  public func seek(to seconds: TimeInterval) {
    seek(to: seconds, completion: { _ in })
  }

  /**
   Seek to a specific time in the item with a completion handler.

   - parameter seconds: The time to seek to.
   - parameter completion: Called when the seek operation completes. The Bool parameter indicates whether the seek finished successfully (true) or was interrupted/deferred (false).
   */
  public func seek(to seconds: TimeInterval, completion: @escaping (Bool) -> Void) {
    // If an item is currently being loaded asynchronously, defer the seek until it's ready.
    if state == .loading {
      // Cancel any previous pending seek before creating a new one
      pendingSeek?.cancel()
      pendingSeek = PendingSeek(time: seconds, completion: completion)
    } else if avPlayer.currentItem != nil {
      let time = CMTimeMakeWithSeconds(seconds, preferredTimescale: 1000)
      avPlayer
        .seek(to: time, toleranceBefore: CMTime.zero, toleranceAfter: CMTime.zero) { finished in
          self.handleSeekCompleted(to: Double(seconds), didFinish: finished)
          completion(finished)
        }
    } else {
      // No item loaded and not loading - seek fails immediately
      completion(false)
    }
  }

  /**
   Seek by relative a time offset in the item.
   */
  public func seek(by offset: TimeInterval) {
    // Calculate the target time based on current state
    let targetTime: TimeInterval
    if state == .loading {
      // If loading, offset from pending seek (or 0 if no pending seek)
      targetTime = (pendingSeek?.time ?? 0) + offset
    } else if let currentItem = avPlayer.currentItem {
      // If playing, offset from current position
      targetTime = currentItem.currentTime().seconds + offset
    } else {
      // No item and not loading - nothing to seek in
      return
    }

    // Delegate to absolute seek
    seek(to: targetTime)
  }

  // MARK: - Remote Command Center

  func enableRemoteCommands(_ commands: [RemoteCommand]) {
    remoteCommandController.enable(commands: commands)
  }

  func enableRemoteCommands(forItem item: AudioItem) {
    if let commands = item.remoteCommands {
      enableRemoteCommands(commands)
    } else {
      enableRemoteCommands(remoteCommands)
    }
  }

  /**
   Syncs the current remoteCommands with the iOS command center.
   Can be used to update item states - e.g. like, dislike and bookmark.
   */
  @available(*, deprecated, message: "Directly set .remoteCommands instead")
  public func syncRemoteCommandsWithCommandCenter() {
    enableRemoteCommands(remoteCommands)
  }

  // MARK: - NowPlayingInfo

  /**
   Loads NowPlayingInfo-meta values with the values found in the current `AudioItem`. Use this if a change to the `AudioItem` is made and you want to update the `NowPlayingInfoController`s values.

   Reloads:
   - Artist
   - Title
   - Album title
   - Album artwork
   */
  public func loadNowPlayingMetaValues() {
    guard let item = currentItem else { return }

    nowPlayingInfoController.set(keyValues: [
      MediaItemProperty.artist(item.artist),
      MediaItemProperty.title(item.title),
      MediaItemProperty.albumTitle(item.album),
    ])
    loadArtwork(forItem: item)
  }

  /**
   Resyncs the playbackvalues of the currently playing `AudioItem`.

   Will resync:
   - Current time
   - Duration
   - Playback rate
   */
  func updateNowPlayingPlaybackValues() {
    nowPlayingInfoController.set(keyValues: [
      MediaItemProperty.duration(duration),
      NowPlayingInfoProperty.playbackRate(playWhenReady ? Double(rate) : 0),
      NowPlayingInfoProperty.elapsedPlaybackTime(currentTime),
    ])
  }

  public func clear() {
    let playbackWasActive = playbackActive
    currentItem = nil
    unloadAVPlayer()
    nowPlayingInfoController.clear()
    if playbackWasActive {
      event.playbackEnd.emit(data: .cleared)
    }
  }

  // MARK: - Private

  private func setNowPlayingCurrentTime(seconds: Double) {
    nowPlayingInfoController.set(
      keyValue: NowPlayingInfoProperty.elapsedPlaybackTime(seconds)
    )
  }

  private func loadArtwork(forItem item: AudioItem) {
    item.loadArtwork { image in
      if let image {
        let artwork = MPMediaItemArtwork(boundsSize: image.size, requestHandler: { _ in image })
        self.nowPlayingInfoController.set(keyValue: MediaItemProperty.artwork(artwork))
      } else {
        self.nowPlayingInfoController.set(keyValue: MediaItemProperty.artwork(nil))
      }
    }
  }

  private func setTimePitchingAlgorithmForCurrentItem() {
    if let algorithm = currentItem?.pitchAlgorithm {
      currentAVPlayerItem?.audioTimePitchAlgorithm = algorithm.avAlgorithm
    } else {
      currentAVPlayerItem?.audioTimePitchAlgorithm = audioTimePitchAlgorithm
    }
  }

  // MARK: - AVPlayer Management Methods (from AVPlayerWrapper)

  private func applyAVPlayerRate() {
    avPlayer.rate = _playWhenReady ? _rate : 0
  }

  private func clearCurrentItem() {
    guard let asset else { return }
    stopObservingAVPlayerItem()

    asset.cancelLoading()
    self.asset = nil

    // Clear any pending seek to prevent it from being applied to the next item that loads.
    // Without this, a seek called before any item was loaded could incorrectly apply to
    // an unrelated track that loads later.
    pendingSeek?.cancel()
    pendingSeek = nil

    avPlayer.replaceCurrentItem(with: nil)
  }

  private func startObservingAVPlayer(item: AVPlayerItem) {
    playerItemObserver.startObserving(item: item)
    playerItemNotificationObserver.startObserving(item: item)
  }

  private func stopObservingAVPlayerItem() {
    playerItemObserver.stopObservingCurrentItem()
    playerItemNotificationObserver.stopObservingCurrentItem()
  }

  private func recreateAVPlayer() {
    playbackError = nil
    playerTimeObserver.unregisterForBoundaryTimeEvents()
    playerTimeObserver.unregisterForPeriodicEvents()
    playerObserver.stopObserving()
    stopObservingAVPlayerItem()
    clearCurrentItem()

    avPlayer = AVPlayer()
    setupAVPlayer()

    handleAVPlayerRecreated()
  }

  private func setupAVPlayer() {
    // disabled since we're not making use of video playback
    avPlayer.allowsExternalPlayback = false

    playerObserver.avPlayer = avPlayer
    playerObserver.startObserving()

    playerTimeObserver.avPlayer = avPlayer
    playerTimeObserver.registerForBoundaryTimeEvents()
    playerTimeObserver.registerForPeriodicTimeEvents()

    applyAVPlayerRate()
  }

  private func playbackFailed(error: AudioPlayerError.PlaybackError) {
    state = .failed
    playbackError = error
    handlePlaybackError(error)
  }

  func loadAVPlayer() {
    if state == .failed {
      recreateAVPlayer()
    } else {
      clearCurrentItem()
    }
    if let url {
      let pendingAsset = AVURLAsset(url: url, options: urlOptions)
      asset = pendingAsset
      state = .loading

      // Load metadata keys asynchronously and separate from playable, to allow that to execute as
      // quickly as it can
      let metdataKeys = ["commonMetadata", "availableChapterLocales", "availableMetadataFormats"]
      pendingAsset.loadValuesAsynchronously(
        forKeys: metdataKeys,
        completionHandler: { [weak self] in
          guard let self else { return }
          if pendingAsset != asset { return }

          let commonData = pendingAsset.commonMetadata
          if !commonData.isEmpty {
            handleCommonMetadataReceived(commonData)
          }

          if !pendingAsset.availableChapterLocales.isEmpty {
            for locale in pendingAsset.availableChapterLocales {
              let chapters = pendingAsset.chapterMetadataGroups(
                withTitleLocale: locale,
                containingItemsWithCommonKeys: nil
              )
              handleChapterMetadataReceived(chapters)
            }
          } else {
            for format in pendingAsset.availableMetadataFormats {
              let timeRange = CMTimeRange(
                start: CMTime(seconds: 0, preferredTimescale: 1000),
                end: pendingAsset.duration
              )
              let group = AVTimedMetadataGroup(
                items: pendingAsset.metadata(forFormat: format),
                timeRange: timeRange
              )
              handleTimedMetadataReceived([group])
            }
          }
        }
      )

      // Load playable portion of the track and commence when ready
      let playableKeys = ["playable"]
      pendingAsset.loadValuesAsynchronously(
        forKeys: playableKeys,
        completionHandler: { [weak self] in
          guard let self else { return }

          DispatchQueue.main.async {
            if pendingAsset != self.asset { return }

            for key in playableKeys {
              var error: NSError?
              let keyStatus = pendingAsset.statusOfValue(forKey: key, error: &error)
              switch keyStatus {
              case .failed:
                self.playbackFailed(error: AudioPlayerError.PlaybackError.failedToLoadKeyValue)
                return
              case .cancelled, .loading, .unknown:
                return
              case .loaded:
                break
              default: break
              }
            }

            if !pendingAsset.isPlayable {
              self.playbackFailed(error: AudioPlayerError.PlaybackError.itemWasUnplayable)
              return
            }

            let item = AVPlayerItem(
              asset: pendingAsset,
              automaticallyLoadedAssetKeys: playableKeys
            )
            self.item = item
            item.preferredForwardBufferDuration = self._bufferDuration
            self.avPlayer.replaceCurrentItem(with: item)
            self.startObservingAVPlayer(item: item)
            self.applyAVPlayerRate()

            // Execute any pending seek operation
            if let pending = self.pendingSeek {
              self.pendingSeek = nil
              pending.execute(on: self.avPlayer, delegate: self)
            }
          }
        }
      )
    }
  }

  func loadFromURL(
    from url: URL,
    playWhenReady: Bool,
    initialTime: TimeInterval? = nil,
    options: [String: Any]? = nil
  ) {
    self.playWhenReady = playWhenReady
    self.url = url
    urlOptions = options
    loadAVPlayer()
    if let initialTime {
      seek(to: initialTime)
    }
  }

  func loadFromString(
    from url: String,
    type: SourceType = .stream,
    playWhenReady: Bool = false,
    initialTime: TimeInterval? = nil,
    options: [String: Any]? = nil
  ) {
    if let itemUrl = type == .file
      ? URL(fileURLWithPath: url)
      : URL(string: url)
    {
      loadFromURL(
        from: itemUrl,
        playWhenReady: playWhenReady,
        initialTime: initialTime,
        options: options
      )
    } else {
      clearCurrentItem()
      playbackFailed(error: AudioPlayerError.PlaybackError.invalidSourceUrl(url))
    }
  }

  func unloadAVPlayer() {
    clearCurrentItem()
    state = .idle
  }

  // MARK: - Internal Event Handlers

  private func handleStateChange(_ state: AudioPlayerState) {
    DispatchQueue.main.async { [weak self] in
      guard let self else { return }
      switch state {
      case .ready, .loading:
        setTimePitchingAlgorithmForCurrentItem()
      default: break
      }

      switch state {
      case .ready, .loading, .playing, .paused:
        if automaticallyUpdateNowPlayingInfo {
          updateNowPlayingPlaybackValues()
        }
      default: break
      }
      event.stateChange.emit(data: state)
    }
  }

  func handleSecondElapsed(_ seconds: Double) {
    event.secondElapse.emit(data: seconds)
  }

  private func handlePlaybackError(_ error: Error?) {
    event.fail.emit(data: error)
    event.playbackEnd.emit(data: .failed)
  }

  private func handleSeekCompleted(to seconds: Double, didFinish: Bool) {
    if automaticallyUpdateNowPlayingInfo {
      setNowPlayingCurrentTime(seconds: Double(seconds))
    }
    event.seek.emit(data: (seconds, didFinish))
  }

  func handleDurationUpdate(_ duration: Double) {
    event.updateDuration.emit(data: duration)
  }

  private func handleCommonMetadataReceived(_ metadata: [AVMetadataItem]) {
    event.receiveCommonMetadata.emit(data: metadata)
  }

  private func handleChapterMetadataReceived(_ metadata: [AVTimedMetadataGroup]) {
    event.receiveChapterMetadata.emit(data: metadata)
  }

  func handleTimedMetadataReceived(_ metadata: [AVTimedMetadataGroup]) {
    event.receiveTimedMetadata.emit(data: metadata)
  }

  private func handlePlayWhenReadyChange(_ playWhenReady: Bool) {
    event.playWhenReadyChange.emit(data: playWhenReady)
  }

  func handleItemDidPlayToEndTime() {
    event.playbackEnd.emit(data: .playedUntilEnd)
    state = .ended
  }

  func handleItemFailedToPlayToEndTime() {
    handlePlaybackError(AudioPlayerError.PlaybackError.playbackFailed)
  }

  func handleItemPlaybackStalled() {}

  private func handleAVPlayerRecreated() {
    event.didRecreateAVPlayer.emit(data: ())
  }

  // MARK: - Observer Callbacks

  func playerDidChangeTimeControlStatus(_ status: AVPlayer.TimeControlStatus) {
    switch status {
    case .paused:
      let currentState = state
      if asset == nil, currentState != .stopped {
        state = .idle
      } else if currentState != .failed, currentState != .stopped {
        // Distinguish between external pauses (bluetooth disconnect, interruption) and natural
        // track completion:
        if playWhenReady {
          // If playback pauses unexpectedly, this is likely an external interruption (bluetooth
          // disconnect, system interruption, etc). Set playWhenReady to false to acknowledge the
          // pause.
          // However, if we're near the end of the track (within 0.5s of duration), this is likely
          // a natural pause from track completion. Let itemDidPlayToEndTime handle this case to
          // preserve auto-advance behavior between tracks in a queue/playlist.
          if currentTime < duration - 0.5 {
            playWhenReady = false
          }
        } else {
          state = .paused
        }
      }
    case .waitingToPlayAtSpecifiedRate:
      if asset != nil {
        state = .buffering
      }
    case .playing:
      state = .playing
    @unknown default:
      break
    }
  }

  func playerStatusDidChange(_ status: AVPlayer.Status) {
    if status == .failed {
      let error = item!.error as NSError?
      playbackFailed(error: error?.code == URLError.notConnectedToInternet.rawValue
        ? AudioPlayerError.PlaybackError.notConnectedToInternet
        : AudioPlayerError.PlaybackError.playbackFailed
      )
    }
  }

  func audioDidStart() {
    state = .playing
  }

  func itemFailedToPlayToEndTime() {
    playbackFailed(error: AudioPlayerError.PlaybackError.playbackFailed)
    handleItemFailedToPlayToEndTime()
  }

  func itemDidUpdatePlaybackLikelyToKeepUp(_ playbackLikelyToKeepUp: Bool) {
    if playbackLikelyToKeepUp, state != .playing {
      state = .ready
    }
  }
}
