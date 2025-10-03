//
//  TrackPlayer.swift
//  SwiftAudio
//
//  Created by Jørgen Henrichsen on 15/03/2018.
//

import Foundation
import MediaPlayer

public class TrackPlayer {
  public let nowPlayingInfoController: NowPlayingInfoController
  public let remoteCommandController: RemoteCommandController
  public let event = EventHolder()

  fileprivate var lastIndex: Int = -1
  fileprivate var lastTrack: Track?

  /// The repeat mode for the queue player.
  public var repeatMode: RepeatMode = .off

  // MARK: - Queue Properties

  private func assertMainThread() {
    assert(Thread.isMainThread, "TrackPlayer queue must be accessed from the main thread")
  }

  /**
   The index of the current track. `-1` when there is no current track
   */
  private(set) public var currentIndex: Int = -1

  /**
   All tracks held by the queue.
   */
  private(set) public var tracks: [Track] = []

  public var currentTrack: Track? {
    assertMainThread()
    guard currentIndex >= 0, currentIndex < tracks.count else { return nil }
    return tracks[currentIndex]
  }

  /**
   The upcoming tracks in the queue.
   */
  public var nextTracks: [Track] {
    assertMainThread()
    guard currentIndex >= 0, currentIndex < tracks.count - 1 else { return [] }
    return Array(tracks[currentIndex + 1 ..< tracks.count])
  }

  /**
   The previous tracks held by the queue.
   */
  public var previousTracks: [Track] {
    assertMainThread()
    guard currentIndex > 0 else { return [] }
    return Array(tracks[0 ..< currentIndex])
  }

  /**
   Whether there are more tracks after the current track in the queue.
   */
  private var hasNextTrack: Bool {
    currentIndex < tracks.count - 1
  }

  // MARK: - AVPlayer Properties (from AVPlayerWrapper)

  /// Represents a seek operation that's pending while a track loads
  private struct PendingSeek {
    let time: TimeInterval
    let completion: ((Bool) -> Void)?

    func execute(on player: AVPlayer, delegate: TrackPlayer?) {
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
  private var url: URL?
  private var urlOptions: [String: Any]?
  private let stateQueue = DispatchQueue(
    label: "TrackPlayer.stateQueue",
    attributes: .concurrent
  )
  private(set) var playbackError: TrackPlayerError.PlaybackError?
  var _state: PlaybackState = .idle
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
   Controls the time pitch algorithm applied to each track loaded into the player.
   If the loaded `AudioItem` conforms to `TimePitcher`-protocol this will be overriden.
   */
  public var audioTimePitchAlgorithm: AVAudioTimePitchAlgorithm = .timeDomain

  /**
   Default remote commands to use for each playing track
   */
  public var remoteCommands: [RemoteCommand] = [] {
    didSet {
      if let track = currentTrack {
        enableRemoteCommands(track.remoteCommands ?? remoteCommands)
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

  var state: PlaybackState {
    get {
      var state: PlaybackState!
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
   The elapsed playback time of the current track.
   */
  public var currentTime: Double {
    let seconds = avPlayer.currentTime().seconds
    return seconds.isNaN ? 0 : seconds
  }

  /**
   The duration of the current track.
   */
  public var duration: Double {
    guard let item = avPlayer.currentItem else { return 0.0 }

    if !item.asset.duration.seconds.isNaN {
      return item.asset.duration.seconds
    }
    if !item.duration.seconds.isNaN {
      return item.duration.seconds
    }
    if let seekable = item.seekableTimeRanges.last?.timeRangeValue.duration.seconds,
       !seekable.isNaN
    {
      return seekable
    }
    return 0.0
  }

  /**
   The bufferedPosition of the active track
   */
  public var bufferedPosition: Double {
    avPlayer.currentItem?.loadedTimeRanges.last?.timeRangeValue.end.seconds ?? 0
  }

  /**
   The current state of the underlying `TrackPlayer`.
   */
  public var playerState: PlaybackState {
    state
  }

  // MARK: - Setters for AVPlayerWrapper

  /**
   Whether the player should start playing automatically when the track is ready.
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

  public init(
    nowPlayingInfoController: NowPlayingInfoController = NowPlayingInfoController(),
    remoteCommandController: RemoteCommandController = RemoteCommandController()
  ) {
    self.nowPlayingInfoController = nowPlayingInfoController
    self.remoteCommandController = remoteCommandController
    self.remoteCommandController.player = self

    setupAVPlayer()
  }

  // MARK: - Player Actions

  /**
   Will replace the current track with a new one and load it into the player.

   - parameter track: The Track to replace the current track.
   - parameter playWhenReady: Optional, whether to start playback when the track is ready.
   */
  public func load(_ track: Track, playWhenReady: Bool? = nil) {
    handlePlayWhenReady(playWhenReady) {
      replaceCurrentTrackWith(track)
    }
  }

  /**
   Internal load method that loads a track directly without queue management.
   Used by queue operations after updating the queue state.
   */
  private func loadTrack(_ track: Track) {
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

    enableRemoteCommands(track.remoteCommands ?? remoteCommands)

    loadFromString(
      from: track.audioUrl,
      type: track.sourceType,
      playWhenReady: self.playWhenReady,
      initialTime: track.initialTime,
      options: track.assetOptions
    )
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
    clearCurrentAVItem()
    playWhenReady = false
    if wasActive {
      event.playbackEnd.emit(data: .playerStopped)
    }
  }

  /**
   Reload the current track.
   */
  public func reload(startFromCurrentTime: Bool) {
    var time: Double? = nil
    if startFromCurrentTime {
      if let currentItem = avPlayer.currentItem {
        if !currentItem.duration.isIndefinite {
          time = currentItem.currentTime().seconds
        }
      }
    }
    loadAVPlayer()
    if let time {
      seekTo(time)
    }
  }

  /**
   Seek to a specific time in the track.
   */
  public func seekTo(_ seconds: TimeInterval) {
    seekTo(seconds, completion: { _ in })
  }

  /**
   Seek to a specific time in the track with a completion handler.

   - parameter seconds: The time to seek to.
   - parameter completion: Called when the seek operation completes. The Bool parameter indicates whether the seek finished successfully (true) or was interrupted/deferred (false).
   */
  public func seekTo(_ seconds: TimeInterval, completion: @escaping (Bool) -> Void) {
    // If an track is currently being loaded asynchronously, defer the seek until it's ready.
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
      // No track loaded and not loading - seek fails immediately
      completion(false)
    }
  }

  /**
   Seek by relative a time offset in the track.
   */
  public func seekBy(_ offset: TimeInterval) {
    // Calculate the target time based on current state
    let targetTime: TimeInterval
    if state == .loading {
      // If loading, offset from pending seek (or 0 if no pending seek)
      targetTime = (pendingSeek?.time ?? 0) + offset
    } else if let currentItem = avPlayer.currentItem {
      // If playing, offset from current position
      targetTime = currentItem.currentTime().seconds + offset
    } else {
      // No track and not loading - nothing to seek in
      return
    }

    // Delegate to absolute seek
    seekTo(targetTime)
  }

  // MARK: - Remote Command Center

  func enableRemoteCommands(_ commands: [RemoteCommand]) {
    remoteCommandController.enable(commands: commands)
  }

  // MARK: - NowPlayingInfo

  /**
   Loads NowPlayingInfo-meta values with the values found in the current track. Use this if a change to the track is made and you want to update the `NowPlayingInfoController`s values.

   Reloads:
   - Artist
   - Title
   - Album title
   - Album artwork
   */
  public func loadNowPlayingMetaValues() {
    guard let track = currentTrack else { return }

    nowPlayingInfoController.set(keyValues: [
      MediaItemProperty.artist(track.artist),
      MediaItemProperty.title(track.title),
      MediaItemProperty.albumTitle(track.album),
    ])
    loadArtworkForTrack(track)
  }

  /**
   Resyncs the playbackvalues of the currently playing track.

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
    clearTracks()
    let playbackWasActive = playbackActive
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

  private func loadArtworkForTrack(_ track: Track) {
    track.loadArtwork { image in
      if let image {
        let artwork = MPMediaItemArtwork(boundsSize: image.size, requestHandler: { _ in image })
        self.nowPlayingInfoController.set(keyValue: MediaItemProperty.artwork(artwork))
      } else {
        self.nowPlayingInfoController.set(keyValue: MediaItemProperty.artwork(nil))
      }
    }
  }

  private func setTimePitchingAlgorithmForCurrentItem() {
    if let algorithm = currentTrack?.pitchAlgorithm {
      avPlayer.currentItem?.audioTimePitchAlgorithm = algorithm.avAlgorithm
    } else {
      avPlayer.currentItem?.audioTimePitchAlgorithm = audioTimePitchAlgorithm
    }
  }

  // MARK: - AVPlayer Management Methods (from AVPlayerWrapper)

  private func applyAVPlayerRate() {
    avPlayer.rate = _playWhenReady ? _rate : 0
  }

  private func clearCurrentAVItem() {
    guard let asset else { return }
    stopObservingAVPlayerItem()

    asset.cancelLoading()
    self.asset = nil

    // Clear any pending seek to prevent it from being applied to the next track that loads.
    // Without this, a seek called before any track was loaded could incorrectly apply to
    // an unrelated track that loads later.
    pendingSeek?.cancel()
    pendingSeek = nil

    avPlayer.replaceCurrentItem(with: nil)
  }

  private func startObservingAVPlayerItem(_ avItem: AVPlayerItem) {
    playerItemObserver.startObserving(item: avItem)
    playerItemNotificationObserver.startObserving(item: avItem)
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
    clearCurrentAVItem()

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

  private func playbackFailed(error: TrackPlayerError.PlaybackError) {
    state = .failed
    playbackError = error
    handlePlaybackError(error)
  }

  func loadAVPlayer() {
    if state == .failed {
      recreateAVPlayer()
    } else {
      clearCurrentAVItem()
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
                self.playbackFailed(error: TrackPlayerError.PlaybackError.failedToLoadKeyValue)
                return
              case .cancelled, .loading, .unknown:
                return
              case .loaded:
                break
              default: break
              }
            }

            if !pendingAsset.isPlayable {
              self.playbackFailed(error: TrackPlayerError.PlaybackError.trackWasUnplayable)
              return
            }

            let avItem = AVPlayerItem(
              asset: pendingAsset,
              automaticallyLoadedAssetKeys: playableKeys
            )
            avItem.preferredForwardBufferDuration = self._bufferDuration
            self.avPlayer.replaceCurrentItem(with: avItem)
            self.startObservingAVPlayerItem(avItem)
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
      seekTo(initialTime)
    }
  }

  func loadFromString(
    from url: String,
    type: SourceType = .stream,
    playWhenReady: Bool = false,
    initialTime: TimeInterval? = nil,
    options: [String: Any]? = nil
  ) {
    if let trackUrl = type == .file
      ? URL(fileURLWithPath: url)
      : URL(string: url)
    {
      loadFromURL(
        from: trackUrl,
        playWhenReady: playWhenReady,
        initialTime: initialTime,
        options: options
      )
    } else {
      clearCurrentAVItem()
      playbackFailed(error: TrackPlayerError.PlaybackError.invalidSourceUrl(url))
    }
  }

  func unloadAVPlayer() {
    clearCurrentAVItem()
    state = .idle
  }

  // MARK: - Internal Event Handlers

  private func handleStateChange(_ state: PlaybackState) {
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

  func handleTrackDidPlayToEndTime() {
    DispatchQueue.main.async { [weak self] in
      guard let self else { return }
      event.playbackEnd.emit(data: .playedUntilEnd)
      if repeatMode == .track {
        replay()
      } else if repeatMode == .queue || hasNextTrack {
        next()
      } else {
        state = .ended
      }
    }
  }

  func handleTrackFailedToPlayToEndTime() {
    handlePlaybackError(TrackPlayerError.PlaybackError.playbackFailed)
  }

  func handleTrackPlaybackStalled() {}

  private func handleAVPlayerRecreated() {
    event.didRecreateAVPlayer.emit(data: ())
  }

  // MARK: - Observer Callbacks

  func avPlayerDidChangeTimeControlStatus(_ status: AVPlayer.TimeControlStatus) {
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
          // a natural pause from track completion. Let handleTrackDidPlayToEndTime handle this case to
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

  func avPlayerStatusDidChange(_ status: AVPlayer.Status) {
    if status == .failed {
      let error = avPlayer.currentItem?.error as NSError?
      playbackFailed(error: error?.code == URLError.notConnectedToInternet.rawValue
        ? TrackPlayerError.PlaybackError.notConnectedToInternet
        : TrackPlayerError.PlaybackError.playbackFailed
      )
    }
  }

  func audioDidStart() {
    state = .playing
  }

  func avItemFailedToPlayToEndTime() {
    playbackFailed(error: TrackPlayerError.PlaybackError.playbackFailed)
    handleTrackFailedToPlayToEndTime()
  }

  func avItemDidUpdatePlaybackLikelyToKeepUp(_ playbackLikelyToKeepUp: Bool) {
    if playbackLikelyToKeepUp, state != .playing {
      state = .ready
    }
  }

  // MARK: - Queue Validation

  private func throwIfQueueEmpty() throws {
    if tracks.isEmpty {
      throw TrackPlayerError.QueueError.empty
    }
  }

  private func throwIfIndexInvalid(
    index: Int,
    name: String = "index",
    min: Int? = nil,
    max: Int? = nil
  ) throws {
    guard index >= (min ?? 0), (max ?? tracks.count) > index else {
      throw TrackPlayerError.QueueError.invalidIndex(
        index: index,
        message: "\(name) must be non-negative and less than \(tracks.count)"
      )
    }
  }

  // MARK: - Queue Methods

  /**
   Replace the current track with a new one. If there is no current track, it is equivalent to calling `add(track:)`, `skipTo(trackIndex)`.

   - parameter track: The track to set as the new current track.
   */
  private func replaceCurrentTrackWith(_ track: Track) {
    assertMainThread()
    if currentIndex == -1 {
      tracks.append(track)
      currentIndex = 0
      handleCurrentTrackChanged()
    } else {
      tracks[currentIndex] = track
      handleCurrentTrackChanged()
    }
  }

  /**
   Add tracks to the queue.

   - parameter tracks: The tracks to add to the queue.
   - parameter playWhenReady: Optional, whether to start playback when the track is ready.
   */
  public func add(_ tracks: [Track], playWhenReady: Bool? = nil) {
    handlePlayWhenReady(playWhenReady) {
      addTracks(tracks)
    }
  }

  private func addTracks(_ newTracks: [Track]) {
    assertMainThread()
    guard !newTracks.isEmpty else { return }
    let wasEmpty = tracks.isEmpty
    tracks.append(contentsOf: newTracks)
    if wasEmpty {
      currentIndex = 0
      handleCurrentTrackChanged()
    }
  }

  public func add(_ tracks: [Track], at index: Int) throws {
    assertMainThread()
    guard !tracks.isEmpty else { return }
    guard index >= 0, self.tracks.count >= index else {
      throw TrackPlayerError.QueueError.invalidIndex(
        index: index,
        message: "Index to insert at has to be non-negative and equal to or smaller than the number of tracks: (\(self.tracks.count))"
      )
    }
    let wasEmpty = self.tracks.isEmpty
    // Correct index when tracks were inserted in front of it:
    if self.tracks.count > 1, currentIndex >= index {
      currentIndex += tracks.count
    }
    self.tracks.insert(contentsOf: tracks, at: index)
    if wasEmpty {
      currentIndex = 0
      handleCurrentTrackChanged()
    }
  }

  /**
   Step to the next track in the queue.
   */
  public func next() {
    let lastIndex = currentIndex
    let playbackWasActive = playbackActive
    _ = skipBy(1, wrap: repeatMode == .queue)
    if playbackWasActive && lastIndex != currentIndex || repeatMode == .queue {
      event.playbackEnd.emit(data: .skippedToNext)
    }
  }

  /**
   Step to the previous track in the queue.
   */
  public func previous() {
    let lastIndex = currentIndex
    let playbackWasActive = playbackActive
    _ = skipBy(-1, wrap: repeatMode == .queue)
    if playbackWasActive && lastIndex != currentIndex || repeatMode == .queue {
      event.playbackEnd.emit(data: .skippedToPrevious)
    }
  }

  private func skipBy(_ delta: Int, wrap: Bool) -> Track? {
    assertMainThread()
    guard currentTrack != nil, !tracks.isEmpty else { return nil }

    if tracks.count == 1 {
      if wrap, playWhenReady {
        replay()
      }
      return currentTrack
    }

    var index = currentIndex + delta
    if wrap {
      index = (index + tracks.count) % tracks.count
    }
    let newIndex = max(0, min(tracks.count - 1, index))

    if newIndex != currentIndex {
      currentIndex = newIndex
      handleCurrentTrackChanged()
    }
    return currentTrack
  }

  /**
   Remove a track from the queue.

   - parameter index: The index of the track to remove.
   - throws: `TrackPlayerError.QueueError`
   */
  public func removeTrack(_ index: Int) throws {
    assertMainThread()
    try throwIfQueueEmpty()
    try throwIfIndexInvalid(index: index)
    let result = tracks.remove(at: index)
    if index == currentIndex {
      currentIndex = tracks.count > 0 ? currentIndex % tracks.count : -1
      handleCurrentTrackChanged()
    } else if index < currentIndex {
      currentIndex -= 1
    }
  }

  /**
   Skip to a certain track in the queue.

   - parameter index: The index of the track to skip to.
   - parameter playWhenReady: Optional, whether to start playback when the track is ready.
   - throws: `TrackPlayerError`
   */
  public func skipToTrack(_ index: Int, playWhenReady: Bool? = nil) throws {
    try handlePlayWhenReady(playWhenReady) {
      if index == currentIndex {
        seekTo(0)
      } else {
        _ = try skipTo(index)
      }
      event.playbackEnd.emit(data: .jumpedToIndex)
    }
  }

  private func skipTo(_ index: Int) throws -> Track {
    assertMainThread()
    try throwIfQueueEmpty()
    try throwIfIndexInvalid(index: index)

    if index == currentIndex {
      if playWhenReady {
        replay()
      }
    } else {
      currentIndex = index
      handleCurrentTrackChanged()
    }

    guard let track = currentTrack else {
      throw TrackPlayerError.QueueError.invalidIndex(
        index: index,
        message: "Failed to get current track after jumping to index \(index)"
      )
    }
    return track
  }

  /**
   Move a track in the queue from one position to another.

   - parameter fromIndex: The index of the track to move.
   - parameter toIndex: The index to move the track to.
   - throws: `TrackPlayerError.QueueError`
   */
  public func moveTrack(fromIndex: Int, toIndex: Int) throws {
    assertMainThread()
    try throwIfQueueEmpty()
    try throwIfIndexInvalid(index: fromIndex, name: "fromIndex")
    try throwIfIndexInvalid(index: toIndex, name: "toIndex", max: Int.max)

    let track = tracks.remove(at: fromIndex)
    tracks.insert(track, at: min(tracks.count, toIndex))
    if fromIndex == currentIndex {
      currentIndex = toIndex
      handleCurrentTrackChanged()
    }
  }

  /**
   Remove all upcoming tracks, those returned by `next()`
   */
  public func removeUpcomingTracks() {
    assertMainThread()
    guard !tracks.isEmpty else { return }
    let nextIndex = currentIndex + 1
    guard nextIndex < tracks.count else { return }
    tracks.removeSubrange(nextIndex ..< tracks.count)
  }

  /**
   Removes all tracks from queue
   */
  private func clearTracks() {
    assertMainThread()
    guard currentIndex != -1 else { return }
    currentIndex = -1
    tracks.removeAll()
    handleCurrentTrackChanged()
  }

  func replay() {
    seekTo(0) { [weak self] succeeded in
      if succeeded {
        self?.play()
      }
    }
  }

  func handleCurrentTrackChanged() {
    let lastPosition = currentTime
    let shouldContinuePlayback = playWhenReady
    if let currentTrack {
      // Ensure playWhenReady is set before loading to preserve playback state
      playWhenReady = shouldContinuePlayback
      loadTrack(currentTrack)
    } else {
      let playbackWasActive = playbackActive
      unloadAVPlayer()
      nowPlayingInfoController.clear()
      if playbackWasActive {
        event.playbackEnd.emit(data: .cleared)
      }
    }
    event.currentTrack.emit(
      data: (
        track: currentTrack,
        index: currentIndex == -1 ? nil : currentIndex,
        lastTrack: lastTrack,
        lastIndex: lastIndex == -1 ? nil : lastIndex,
        lastPosition: lastPosition
      )
    )
    lastTrack = currentTrack
    lastIndex = currentIndex
  }
}
