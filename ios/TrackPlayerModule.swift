// TrackPlayer.swift
import Foundation
import MediaPlayer
import React

@objc(NativeTrackPlayerImpl)
public class NativeTrackPlayerImpl: NSObject {
  // Add property for the Objective-C bridge
  @objc public weak var delegate: NativeTrackPlayerImplDelegate?

  // @objc
  // func constantsToExport() -> [String: Any]! {
  //   return ["someKey": "someValue"]
  // }

  // MARK: - Attributes

  private var hasInitialized = false
  private let player = TrackPlayer()
  private let audioSession = AVAudioSession.sharedInstance()
  private var audioSessionIsActive = false
  private var shouldEmitProgressEvent: Bool = false
  private var shouldResumePlaybackAfterInterruptionEnds: Bool = false
  private var forwardJumpInterval: NSNumber?
  private var backwardJumpInterval: NSNumber?
  private var sessionCategory: AVAudioSession.Category = .playback
  private var sessionCategoryMode: AVAudioSession.Mode = .default
  private var sessionCategoryPolicy: AVAudioSession.RouteSharingPolicy = .default
  private var sessionCategoryOptions: AVAudioSession.CategoryOptions = []
  private var currentImageTask: URLSessionDataTask?

  // MARK: - Lifecycle Methods

  override public init() {
    super.init()

    // Observe audio session interruptions
    NotificationCenter.default.addObserver(
      self,
      selector: #selector(handleAudioSessionInterruption),
      name: AVAudioSession.interruptionNotification,
      object: nil
    )

    player.playWhenReady = false
    player.event.receiveChapterMetadata.addListener(self, handleChapterMetadataReceived)
    player.event.receiveTimedMetadata.addListener(self, handleTimedMetadataReceived)
    player.event.receiveCommonMetadata.addListener(self, handleCommonMetadataReceived)
    player.event.stateChange.addListener(self, handleStateChange)
    player.event.fail.addListener(self, handleFailed)
    player.event.currentTrack.addListener(self, handleCurrentTrackChange)
    player.event.secondElapse.addListener(self, handleSecondElapse)
    player.event.playWhenReadyChange.addListener(self, handlePlayWhenReadyChange)
  }

  deinit {
    NotificationCenter.default.removeObserver(
      self,
      name: AVAudioSession.interruptionNotification,
      object: nil
    )
    reset()
  }

  // MARK: - Event Emission

  private func emit(event: EventType, body: Any? = nil) {
    let bodyDict = body as? [String: Any] ?? [:]

    switch event {
    case .PlaybackState:
      delegate?.emitPlaybackState(bodyDict)
    case .PlaybackActiveTrackChanged:
      delegate?.emitPlaybackActiveTrackChanged(bodyDict)
    case .PlaybackProgressUpdated:
      delegate?.emitPlaybackProgressUpdated(bodyDict)
    case .PlaybackPlayWhenReadyChanged:
      delegate?.emitPlaybackPlayWhenReadyChanged(bodyDict)
    case .PlaybackQueueEnded:
      delegate?.emitPlaybackQueueEnded(bodyDict)
    case .PlaybackError:
      delegate?.emitPlaybackError(bodyDict)
    case .PlaybackMetadata:
      delegate?.emitPlaybackMetadata(bodyDict)
    case .RemotePlay:
      delegate?.emitRemotePlay(bodyDict)
    case .RemotePause:
      delegate?.emitRemotePause(bodyDict)
    case .RemoteNext:
      delegate?.emitRemoteNext(bodyDict)
    case .RemotePrevious:
      delegate?.emitRemotePrevious(bodyDict)
    case .RemoteSeek:
      delegate?.emitRemoteSeek(bodyDict)
    case .RemoteJumpForward:
      delegate?.emitRemoteJumpForward(bodyDict)
    case .RemoteJumpBackward:
      delegate?.emitRemoteJumpBackward(bodyDict)
    case .RemoteStop:
      delegate?.emitRemoteStop(bodyDict)
    case .RemoteSetRating:
      delegate?.emitRemoteSetRating(bodyDict)
    case .RemotePlayId:
      delegate?.emitRemotePlayId(bodyDict)
    case .RemotePlaySearch:
      delegate?.emitRemotePlaySearch(bodyDict)
    case .RemoteSkip:
      delegate?.emitRemoteSkip(bodyDict)
    case .RemoteLike:
      delegate?.emitRemoteLike(bodyDict)
    case .RemoteDislike:
      delegate?.emitRemoteDislike(bodyDict)
    case .RemoteBookmark:
      delegate?.emitRemoteBookmark(bodyDict)
    case .MetadataChapterReceived:
      delegate?.emitMetadataChapterReceived(bodyDict)
    case .MetadataTimedReceived:
      delegate?.emitMetadataTimedReceived(bodyDict)
    case .MetadataCommonReceived:
      delegate?.emitMetadataCommonReceived(bodyDict)
    default:
      // Log unmapped events - these should be added to the switch statement
      print("[TrackPlayer] Unmapped event: \(event.rawValue)")
    }
  }

  // MARK: - Audio Session Interruption Handling

  @objc private func handleAudioSessionInterruption(notification: Notification) {
    guard let userInfo = notification.userInfo,
          let typeValue = userInfo[AVAudioSessionInterruptionTypeKey] as? UInt,
          let type = AVAudioSession.InterruptionType(rawValue: typeValue)
    else {
      return
    }

    switch type {
    case .began:
      break
    case .ended:
      guard let optionsValue = userInfo[AVAudioSessionInterruptionOptionKey] as? UInt else {
        return
      }
      let options = AVAudioSession.InterruptionOptions(rawValue: optionsValue)
      let shouldResume = options.contains(.shouldResume)

      if shouldResume, shouldResumePlaybackAfterInterruptionEnds {
        player.play()
      }
    @unknown default:
      break
    }
  }

  // MARK: - Bridged Methods

  private func ensureMainThread(_ block: @escaping () -> Void) {
    if Thread.isMainThread {
      block()
    } else {
      DispatchQueue.main.async(execute: block)
    }
  }

  private func onMainThread<T>(_ block: () -> T) -> T {
    if Thread.isMainThread {
      return block()
    } else {
      return DispatchQueue.main.sync(execute: block)
    }
  }

  private func rejectWhenNotInitialized(reject: RCTPromiseRejectBlock) -> Bool {
    let rejected = !hasInitialized
    if rejected {
      reject(
        "player_not_initialized",
        "The player is not initialized. Call setupPlayer first.",
        nil
      )
    }
    return rejected
  }

  @objc(setupPlayer:resolver:rejecter:)
  public func setupPlayer(
    config: [String: Any],
    resolve: @escaping RCTPromiseResolveBlock,
    reject: @escaping RCTPromiseRejectBlock
  ) {
    ensureMainThread {
      if self.hasInitialized {
        reject(
          "player_already_initialized",
          "The player has already been initialized via setupPlayer.",
          nil
        )
        return
      }
      // configure buffer size
      if let bufferDuration = config["minBuffer"] as? TimeInterval {
        self.player.bufferDuration = bufferDuration
      }

      if let autoHandleInterruptions = config["autoHandleInterruptions"] as? Bool {
        self.shouldResumePlaybackAfterInterruptionEnds = autoHandleInterruptions
      }

      // configure wether control center metdata should auto update
      self.player.automaticallyUpdateNowPlayingInfo = config["autoUpdateMetadata"] as? Bool ?? true

      // configure audio session - category, options & mode
      if
        let sessionCategoryStr = config["iosCategory"] as? String,
        let mappedCategory = SessionCategory(rawValue: sessionCategoryStr)
      {
        self.sessionCategory = mappedCategory.mapConfigToAVAudioSessionCategory()
      }

      if
        let sessionCategoryModeStr = config["iosCategoryMode"] as? String,
        let mappedCategoryMode = SessionCategoryMode(rawValue: sessionCategoryModeStr)
      {
        self.sessionCategoryMode = mappedCategoryMode.mapConfigToAVAudioSessionCategoryMode()
      }

      if
        let sessionCategoryPolicyStr = config["iosCategoryPolicy"] as? String,
        let mappedCategoryPolicy = SessionCategoryPolicy(rawValue: sessionCategoryPolicyStr)
      {
        self.sessionCategoryPolicy = mappedCategoryPolicy.mapConfigToAVAudioSessionCategoryPolicy()
      }

      let sessionCategoryOptsStr = config["iosCategoryOptions"] as? [String]
      let mappedCategoryOpts = sessionCategoryOptsStr?
        .compactMap {
          SessionCategoryOptions(rawValue: $0)?.mapConfigToAVAudioSessionCategoryOptions()
        } ?? []
      self.sessionCategoryOptions = AVAudioSession.CategoryOptions(mappedCategoryOpts)

      self.configureAudioSession()

      // setup event listeners
      self.player.remoteCommandController
        .handleChangePlaybackPositionCommand = { [weak self] event in
          if let event = event as? MPChangePlaybackPositionCommandEvent {
            self?.emit(event: EventType.RemoteSeek, body: ["position": event.positionTime])
            return MPRemoteCommandHandlerStatus.success
          }

          return MPRemoteCommandHandlerStatus.commandFailed
        }

      self.player.remoteCommandController.handleNextTrackCommand = { [weak self] _ in
        self?.emit(event: EventType.RemoteNext)
        return MPRemoteCommandHandlerStatus.success
      }

      self.player.remoteCommandController.handlePauseCommand = { [weak self] _ in
        self?.emit(event: EventType.RemotePause)
        return MPRemoteCommandHandlerStatus.success
      }

      self.player.remoteCommandController.handlePlayCommand = { [weak self] _ in
        self?.emit(event: EventType.RemotePlay)
        return MPRemoteCommandHandlerStatus.success
      }

      self.player.remoteCommandController.handlePreviousTrackCommand = { [weak self] _ in
        self?.emit(event: EventType.RemotePrevious)
        return MPRemoteCommandHandlerStatus.success
      }

      self.player.remoteCommandController.handleSkipBackwardCommand = { [weak self] event in
        if let command = event.command as? MPSkipIntervalCommand,
           let interval = command.preferredIntervals.first
        {
          self?.emit(event: EventType.RemoteJumpBackward, body: ["interval": interval])
          return MPRemoteCommandHandlerStatus.success
        }

        return MPRemoteCommandHandlerStatus.commandFailed
      }

      self.player.remoteCommandController.handleSkipForwardCommand = { [weak self] event in
        if let command = event.command as? MPSkipIntervalCommand,
           let interval = command.preferredIntervals.first
        {
          self?.emit(event: EventType.RemoteJumpForward, body: ["interval": interval])
          return MPRemoteCommandHandlerStatus.success
        }

        return MPRemoteCommandHandlerStatus.commandFailed
      }

      self.player.remoteCommandController.handleStopCommand = { [weak self] _ in
        self?.emit(event: EventType.RemoteStop)
        return MPRemoteCommandHandlerStatus.success
      }

      self.player.remoteCommandController.handleTogglePlayPauseCommand = { [weak self] _ in
        self?.emit(event: self?.player.playerState == .paused
          ? EventType.RemotePlay
          : EventType.RemotePause
        )

        return MPRemoteCommandHandlerStatus.success
      }

      self.player.remoteCommandController.handleLikeCommand = { [weak self] _ in
        self?.emit(event: EventType.RemoteLike)
        return MPRemoteCommandHandlerStatus.success
      }

      self.player.remoteCommandController.handleDislikeCommand = { [weak self] _ in
        self?.emit(event: EventType.RemoteDislike)
        return MPRemoteCommandHandlerStatus.success
      }

      self.player.remoteCommandController.handleBookmarkCommand = { [weak self] _ in
        self?.emit(event: EventType.RemoteBookmark)
        return MPRemoteCommandHandlerStatus.success
      }

      self.hasInitialized = true
      resolve(NSNull())
    }
  }

  private func configureAudioSession() {
    ensureMainThread {
      // deactivate the session when there is no current track to be played
      if self.player.currentTrack == nil {
        try? self.audioSession.setActive(false, options: [])
        self.audioSessionIsActive = false
        return
      }

      // activate the audio session when there is a track to be played
      // and the player has been configured to start when it is ready loading:
      if self.player.playWhenReady {
        try? self.audioSession.setActive(true, options: [])
        self.audioSessionIsActive = true
        if #available(iOS 11.0, *) {
          try? AVAudioSession.sharedInstance().setCategory(
            self.sessionCategory,
            mode: self.sessionCategoryMode,
            policy: self.sessionCategoryPolicy,
            options: self.sessionCategoryOptions
          )
        } else {
          try? AVAudioSession.sharedInstance().setCategory(
            self.sessionCategory,
            mode: self.sessionCategoryMode,
            options: self.sessionCategoryOptions
          )
        }
      }
    }
  }

  @objc(isServiceRunning:rejecter:)
  public func isServiceRunning(resolve: RCTPromiseResolveBlock, reject _: RCTPromiseRejectBlock) {
    // TODO: That is probably always true
    resolve(player != nil)
  }

  @objc
  public func updateOptions(options: [String: Any]) {
    ensureMainThread {
      guard self.hasInitialized else { return }
      var capabilitiesStr = options["capabilities"] as? [String] ?? []
      if capabilitiesStr.contains("play"), capabilitiesStr.contains("pause") {
        capabilitiesStr.append("toggle-play-pause")
      }

      self.forwardJumpInterval = options["forwardJumpInterval"] as? NSNumber ?? self
        .forwardJumpInterval
      self.backwardJumpInterval = options["backwardJumpInterval"] as? NSNumber ?? self
        .backwardJumpInterval

      self.player.remoteCommands = capabilitiesStr
        .compactMap { Capability(rawValue: $0) }
        .map { capability in
          capability.mapToPlayerCommand(
            forwardJumpInterval: self.forwardJumpInterval,
            backwardJumpInterval: self.backwardJumpInterval,
            likeOptions: options["likeOptions"] as? [String: Any],
            dislikeOptions: options["dislikeOptions"] as? [String: Any],
            bookmarkOptions: options["bookmarkOptions"] as? [String: Any]
          )
        }

      self.configureProgressUpdateEvent(
        interval: ((options["progressUpdateEventInterval"] as? NSNumber) ?? 0).doubleValue
      )
    }
  }

  private func configureProgressUpdateEvent(interval: Double) {
    shouldEmitProgressEvent = interval > 0
    player.timeEventFrequency = shouldEmitProgressEvent
      ? .custom(time: CMTime(seconds: interval, preferredTimescale: 1000))
      : .everySecond
  }

  @objc
  public func add(trackDicts: [[String: Any]], before trackIndex: NSNumber) -> Int {
    return onMainThread {
      guard self.hasInitialized else { return -1 }
      // -1 means no index was passed and therefore should be inserted at the end.
      let index = trackIndex.intValue == -1 ? player.tracks.count : trackIndex.intValue
      guard index >= 0, index <= player.tracks.count else { return -1 }

      var tracks = [Track]()
      for trackDict in trackDicts {
        guard let track = Track.fromBridge(dictionary: trackDict) else { return -1 }
        tracks.append(track)
      }

      try? player.add(tracks, at: index)
      return index
    }
  }

  @objc
  public func load(trackDict: [String: Any]) {
    guard let track = Track.fromBridge(dictionary: trackDict) else { return }
    ensureMainThread {
      guard self.hasInitialized else { return }
      self.player.load(track)
    }
  }

  @objc
  public func remove(tracks indexes: [Int]) {
    ensureMainThread {
      guard self.hasInitialized else { return }
      // Validate all indexes first
      for index in indexes {
        guard index >= 0, index < self.player.tracks.count else { return }
      }

      // Sort the indexes in descending order so we can safely remove them one by one
      // without having the next index possibly newly pointing to another track than intended:
      for index in indexes.sorted().reversed() {
        try? self.player.removeTrack(index)
      }
    }
  }

  @objc
  public func move(fromIndex: Int, toIndex: Int) {
    ensureMainThread {
      guard self.hasInitialized else { return }
      guard fromIndex >= 0, fromIndex < self.player.tracks.count else { return }
      guard toIndex >= 0 else { return }
      try? self.player.moveTrack(fromIndex: fromIndex, toIndex: toIndex)
    }
  }

  @objc
  public func removeUpcomingTracks() {
    ensureMainThread {
      guard self.hasInitialized else { return }
      self.player.removeUpcomingTracks()
    }
  }

  @objc
  public func skip(to trackIndex: Int, initialTime: Double) {
    ensureMainThread {
      guard self.hasInitialized else { return }
      guard trackIndex >= 0, trackIndex < self.player.tracks.count else { return }

      try? self.player.skipToTrack(
        trackIndex,
        playWhenReady: self.player.playerState == .playing
      )

      // if an initialTime is passed then seek to it
      if initialTime >= 0 {
        self.seekTo(time: initialTime)
      }
    }
  }

  @objc
  public func skipToNext(initialTime: Double) {
    ensureMainThread {
      guard self.hasInitialized else { return }
      self.player.next()

      // if an initialTime is passed then seek to it
      if initialTime >= 0 {
        self.seekTo(time: initialTime)
      }
    }
  }

  @objc
  public func skipToPrevious(initialTime: Double) {
    ensureMainThread {
      guard self.hasInitialized else { return }
      self.player.previous()

      // if an initialTime is passed then seek to it
      if initialTime >= 0 {
        self.seekTo(time: initialTime)
      }
    }
  }

  @objc
  public func reset() {
    ensureMainThread {
      guard self.hasInitialized else { return }
      self.player.stop()
      self.player.clear()
    }
  }

  @objc
  public func play() {
    ensureMainThread {
      guard self.hasInitialized else { return }
      self.player.play()
    }
  }

  @objc
  public func pause() {
    ensureMainThread {
      guard self.hasInitialized else { return }
      self.player.pause()
    }
  }

  @objc
  public func setPlayWhenReady(playWhenReady: Bool) {
    ensureMainThread {
      guard self.hasInitialized else { return }
      self.player.playWhenReady = playWhenReady
    }
  }

  @objc
  public func getPlayWhenReady() -> Bool {
    return onMainThread {
      guard self.hasInitialized else { return false }
      return player.playWhenReady
    }
  }

  @objc
  public func stop() {
    ensureMainThread {
      guard self.hasInitialized else { return }
      self.player.stop()
    }
  }

  @objc
  public func seekTo(time: Double) {
    ensureMainThread {
      guard self.hasInitialized else { return }
      self.player.seekTo(time)
    }
  }

  @objc
  public func seekBy(offset: Double) {
    ensureMainThread {
      guard self.hasInitialized else { return }
      self.player.seekBy(offset)
    }
  }

  @objc
  public func retry() {
    ensureMainThread {
      guard self.hasInitialized else { return }
      self.player.reload(startFromCurrentTime: true)
    }
  }

  @objc
  public func setRepeatMode(repeatMode: NSString) {
    ensureMainThread {
      guard self.hasInitialized else { return }
      self.player.repeatMode = RepeatMode(rawValue: repeatMode as String) ?? .off
    }
  }

  @objc
  public func getRepeatMode() -> String {
    return onMainThread {
      guard self.hasInitialized else { return "off" }
      return player.repeatMode.rawValue
    }
  }

  @objc
  public func setVolume(level: Float) {
    ensureMainThread {
      guard self.hasInitialized else { return }
      self.player.volume = level
    }
  }

  @objc
  public func getVolume() -> Float {
    return onMainThread {
      guard self.hasInitialized else { return 1.0 }
      return player.volume
    }
  }

  @objc
  public func setRate(rate: Float) {
    ensureMainThread {
      guard self.hasInitialized else { return }
      self.player.rate = rate
    }
  }

  @objc
  public func getRate() -> Float {
    return onMainThread {
      guard self.hasInitialized else { return 1.0 }
      return player.rate
    }
  }

  @objc
  public func getTrack(index: Double) -> [String: Any]? {
    return onMainThread {
      guard self.hasInitialized else { return nil }
      let indexInt = Int(index)
      if indexInt >= 0, indexInt < player.tracks.count {
        let track = player.tracks[indexInt]
        return track.toBridge()
      }
      return nil
    }
  }

  @objc
  public func getQueue() -> [[String: Any]] {
    return onMainThread {
      guard self.hasInitialized else { return [] }
      return player.tracks.map { $0.toBridge() }
    }
  }

  @objc
  public func setQueue(trackDicts: [[String: Any]]) {
    ensureMainThread {
      guard self.hasInitialized else { return }
      var tracks = [Track]()
      for trackDict in trackDicts {
        guard let track = Track.fromBridge(dictionary: trackDict) else { return }
        tracks.append(track)
      }
      self.player.clear()
      try? self.player.add(tracks)
    }
  }

  @objc
  public func getActiveTrack() -> [String: Any]? {
    return onMainThread {
      guard self.hasInitialized else { return nil }
      let index = player.currentIndex
      if index >= 0, index < player.tracks.count {
        let track = player.tracks[index]
        return track.toBridge()
      }
      return nil
    }
  }

  @objc
  public func getActiveTrackIndex() -> NSNumber? {
    return onMainThread {
      guard self.hasInitialized else { return nil }
      let index = player.currentIndex
      if index < 0 || index >= player.tracks.count {
        return nil
      }
      return NSNumber(value: index)
    }
  }

  @objc
  public func getProgress() -> [String: Any] {
    return onMainThread {
      guard self.hasInitialized else { return [:] }
      return [
        "position": player.currentTime,
        "duration": player.duration,
        "buffered": player.bufferedPosition,
      ]
    }
  }

  @objc
  public func getPlaybackState() -> [String: Any] {
    return onMainThread {
      guard self.hasInitialized else { return [:] }
      return getPlaybackStateBodyKeyValues(state: player.playerState)
    }
  }

  @objc
  public func updateMetadata(for trackIndex: Int, metadata: [String: Any]) {
    ensureMainThread {
      guard self.hasInitialized else { return }
      guard trackIndex >= 0, trackIndex < self.player.tracks.count else { return }
      let track = self.player.tracks[trackIndex]

      track.updateMetadata(dictionary: metadata)

      if self.player.currentIndex == trackIndex {
        self.updateNowPlayingInfo(with: metadata)
      }
    }
  }

  @objc
  public func updateNowPlayingMetadata(metadata: [String: Any]) {
    ensureMainThread {
      guard self.hasInitialized else { return }
      self.updateNowPlayingInfo(with: metadata)
    }
  }

  private func updateNowPlayingInfo(with metadata: [String: Any]) {
    currentImageTask?.cancel()
    var ret: [NowPlayingInfoKeyValue] = []

    if let title = metadata["title"] as? String {
      ret.append(MediaItemProperty.title(title))
    }

    if let artist = metadata["artist"] as? String {
      ret.append(MediaItemProperty.artist(artist))
    }

    if let album = metadata["album"] as? String {
      ret.append(MediaItemProperty.albumTitle(album))
    }

    if let duration = metadata["duration"] as? Double {
      ret.append(MediaItemProperty.duration(duration))
    }

    if let elapsedTime = metadata["elapsedTime"] as? Double {
      ret.append(NowPlayingInfoProperty.elapsedPlaybackTime(elapsedTime))
    }

    if let isLiveStream = metadata["isLiveStream"] as? Bool {
      ret.append(NowPlayingInfoProperty.isLiveStream(isLiveStream))
    }

    player.nowPlayingInfoController.set(keyValues: ret)

    if let artworkURL = MediaURL(object: metadata["artwork"]) {
      currentImageTask = URLSession.shared.dataTask(
        with: artworkURL.value,
        completionHandler: { [weak self] data, _, error in
          if let data, let image = UIImage(data: data), error == nil {
            let artwork = MPMediaItemArtwork(
              boundsSize: image.size,
              requestHandler: { _ -> UIImage in
                return image
              }
            )
            self?.player.nowPlayingInfoController.set(keyValue: MediaItemProperty.artwork(artwork))
          }
        }
      )

      currentImageTask?.resume()
    } else {
      player.nowPlayingInfoController.set(keyValue: MediaItemProperty.artwork(nil))
    }
  }

  private func getPlaybackStateErrorKeyValues() -> [String: Any] {
    switch player.playbackError {
    case .failedToLoadKeyValue: return [
        "message": "Failed to load resource",
        "code": "ios_failed_to_load_resource",
      ]
    case .invalidSourceUrl: return [
        "message": "The source url was invalid",
        "code": "ios_invalid_source_url",
      ]
    case .notConnectedToInternet: return [
        "message": "A network resource was requested, but an internet connection has not been established and can’t be established automatically.",
        "code": "ios_not_connected_to_internet",
      ]
    case .playbackFailed: return [
        "message": "Playback of the track failed",
        "code": "ios_playback_failed",
      ]
    case .trackWasUnplayable: return [
        "message": "The track could not be played",
        "code": "ios_track_unplayable",
      ]
    default: return [
        "message": "A playback error occurred",
        "code": "ios_playback_error",
      ]
    }
  }

  private func getPlaybackStateBodyKeyValues(state: PlaybackState) -> [String: Any] {
    var body: [String: Any] = ["state": State.fromPlayerState(state: state).rawValue]
    if state == PlaybackState.failed {
      body["error"] = getPlaybackStateErrorKeyValues()
    }
    return body
  }

  // MARK: - Player Event Handlers

  func handleStateChange(state: PlaybackState) {
    ensureMainThread {
      self.emit(
        event: EventType.PlaybackState,
        body: self.getPlaybackStateBodyKeyValues(state: state)
      )
      if state == .ended {
        self.emit(event: EventType.PlaybackQueueEnded, body: [
          "track": self.player.currentIndex,
          "position": self.player.currentTime,
        ] as [String: Any])
      }
    }
  }

  func handleCommonMetadataReceived(metadata: [AVMetadataItem]) {
    let commonMetadata = MetadataAdapter.convertToCommonMetadata(metadata: metadata, skipRaw: true)
    emit(event: EventType.MetadataCommonReceived, body: ["metadata": commonMetadata])
  }

  func handleChapterMetadataReceived(metadata: [AVTimedMetadataGroup]) {
    let metadataItems = MetadataAdapter.convertToGroupedMetadata(metadataGroups: metadata)
    emit(event: EventType.MetadataChapterReceived, body: ["metadata": metadataItems])
  }

  func handleTimedMetadataReceived(metadata: [AVTimedMetadataGroup]) {
    let metadataItems = MetadataAdapter.convertToGroupedMetadata(metadataGroups: metadata)
    emit(event: EventType.MetadataTimedReceived, body: ["metadata": metadataItems])
  }

  func handleFailed(error: Error?) {
    emit(event: EventType.PlaybackError, body: ["error": error?.localizedDescription])
  }

  func handleCurrentTrackChange(
    track: Track?,
    index: Int?,
    lastTrack: Track?,
    lastIndex: Int?,
    lastPosition: Double?
  ) {
    ensureMainThread {
      if let track {
        UIApplication.shared.beginReceivingRemoteControlEvents()
        // Update now playing controller with isLiveStream option from track
        if self.player.automaticallyUpdateNowPlayingInfo {
          let isTrackLiveStream = track.isLiveStream ?? false
          self.player.nowPlayingInfoController
            .set(keyValue: NowPlayingInfoProperty.isLiveStream(isTrackLiveStream))
        }
      } else {
        UIApplication.shared.endReceivingRemoteControlEvents()
      }

      if (track != nil && lastTrack == nil) || track == nil {
        self.configureAudioSession()
      }

      var a: [String: Any] = ["lastPosition": lastPosition ?? 0]
      if let lastIndex {
        a["lastIndex"] = lastIndex
      }

      if let lastTrack {
        a["lastTrack"] = lastTrack.toBridge()
      }

      if let index {
        a["index"] = index
      }

      if let track {
        a["track"] = track.toBridge()
      }
      self.emit(event: EventType.PlaybackActiveTrackChanged, body: a)
    }
  }

  func handleSecondElapse(seconds _: Double) {
    // because you cannot prevent the `event.secondElapse` from firing
    // do not emit an event if `progressUpdateEventInterval` is nil
    // additionally, there are certain instances in which this event is emitted
    // _after_ a manipulation to the queu causing no currentTrack to exist (see reset)
    // in which case we shouldn't emit anything or we'll get an exception.
    guard shouldEmitProgressEvent else { return }
    ensureMainThread {
      guard self.player.currentTrack != nil else { return }
      self.emit(
        event: EventType.PlaybackProgressUpdated,
        body: [
          "position": self.player.currentTime,
          "duration": self.player.duration,
          "buffered": self.player.bufferedPosition,
          "track": self.player.currentIndex,
        ]
      )
    }
  }

  func handlePlayWhenReadyChange(playWhenReady: Bool) {
    configureAudioSession()
    emit(
      event: EventType.PlaybackPlayWhenReadyChanged,
      body: [
        "playWhenReady": playWhenReady,
      ]
    )
  }
}

@objc public protocol NativeTrackPlayerImplDelegate {
  func emitPlaybackState(_ body: [String: Any])
  func emitPlaybackActiveTrackChanged(_ body: [String: Any])
  func emitPlaybackProgressUpdated(_ body: [String: Any])
  func emitPlaybackPlayWhenReadyChanged(_ body: [String: Any])
  func emitPlaybackQueueEnded(_ body: [String: Any])
  func emitPlaybackError(_ body: [String: Any])
  func emitRemotePlay(_ body: [String: Any])
  func emitRemotePause(_ body: [String: Any])
  func emitRemoteNext(_ body: [String: Any])
  func emitRemotePrevious(_ body: [String: Any])
  func emitRemoteSeek(_ body: [String: Any])
  func emitRemoteJumpForward(_ body: [String: Any])
  func emitRemoteJumpBackward(_ body: [String: Any])
  func emitRemoteStop(_ body: [String: Any])
  func emitRemoteSetRating(_ body: [String: Any])
  func emitRemotePlayId(_ body: [String: Any])
  func emitRemotePlaySearch(_ body: [String: Any])
  func emitRemoteSkip(_ body: [String: Any])
  func emitRemoteLike(_ body: [String: Any])
  func emitRemoteDislike(_ body: [String: Any])
  func emitRemoteBookmark(_ body: [String: Any])
  func emitMetadataTimedReceived(_ body: [String: Any])
  func emitMetadataCommonReceived(_ body: [String: Any])
  func emitMetadataChapterReceived(_ body: [String: Any])
  func emitPlaybackMetadata(_ body: [String: Any])
}

public extension NativeTrackPlayerImpl {
  @objc(constantsToExport)
  static var constantsToExport: [AnyHashable: Any] {
    return [
      "STATE_NONE": State.none.rawValue,
      "STATE_READY": State.ready.rawValue,
      "STATE_PLAYING": State.playing.rawValue,
      "STATE_PAUSED": State.paused.rawValue,
      "STATE_STOPPED": State.stopped.rawValue,
      "STATE_BUFFERING": State.buffering.rawValue,
      "STATE_LOADING": State.loading.rawValue,
      "STATE_ERROR": State.error.rawValue,

      "TRACK_PLAYBACK_ENDED_REASON_END": PlaybackEndedReason.playedUntilEnd.rawValue,
      "TRACK_PLAYBACK_ENDED_REASON_JUMPED": PlaybackEndedReason.jumpedToIndex.rawValue,
      "TRACK_PLAYBACK_ENDED_REASON_NEXT": PlaybackEndedReason.skippedToNext.rawValue,
      "TRACK_PLAYBACK_ENDED_REASON_PREVIOUS": PlaybackEndedReason.skippedToPrevious.rawValue,
      "TRACK_PLAYBACK_ENDED_REASON_STOPPED": PlaybackEndedReason.playerStopped.rawValue,

      "PITCH_ALGORITHM_LINEAR": PitchAlgorithm.linear.rawValue,
      "PITCH_ALGORITHM_MUSIC": PitchAlgorithm.music.rawValue,
      "PITCH_ALGORITHM_VOICE": PitchAlgorithm.voice.rawValue,

      "CAPABILITY_PLAY": Capability.play.rawValue,
      "CAPABILITY_PLAY_FROM_ID": "NOOP",
      "CAPABILITY_PLAY_FROM_SEARCH": "NOOP",
      "CAPABILITY_PAUSE": Capability.pause.rawValue,
      "CAPABILITY_STOP": Capability.stop.rawValue,
      "CAPABILITY_SEEK_TO": Capability.seek.rawValue,
      "CAPABILITY_SKIP": "NOOP",
      "CAPABILITY_SKIP_TO_NEXT": Capability.next.rawValue,
      "CAPABILITY_SKIP_TO_PREVIOUS": Capability.previous.rawValue,
      "CAPABILITY_SET_RATING": "NOOP",
      "CAPABILITY_JUMP_FORWARD": Capability.jumpForward.rawValue,
      "CAPABILITY_JUMP_BACKWARD": Capability.jumpBackward.rawValue,
      "CAPABILITY_LIKE": Capability.like.rawValue,
      "CAPABILITY_DISLIKE": Capability.dislike.rawValue,
      "CAPABILITY_BOOKMARK": Capability.bookmark.rawValue,

      "REPEAT_OFF": RepeatMode.off.rawValue,
      "REPEAT_TRACK": RepeatMode.track.rawValue,
      "REPEAT_QUEUE": RepeatMode.queue.rawValue,

      "RATING_HEART": RatingType.heart.rawValue,
      "RATING_THUMBS_UP_DOWN": RatingType.thumbsUpDown.rawValue,
      "RATING_3_STARS": RatingType.threeStars.rawValue,
      "RATING_4_STARS": RatingType.fourStars.rawValue,
      "RATING_5_STARS": RatingType.fiveStars.rawValue,
      "RATING_PERCENTAGE": RatingType.percentage.rawValue,
    ]
  }

  @objc(supportedEvents)
  static var supportedEvents: [String] {
    return EventType.allRawValues()
  }
}
