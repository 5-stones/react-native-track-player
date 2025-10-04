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
  private lazy var player: TrackPlayer = .init(callbacks: self)

  private let audioSession = AVAudioSession.sharedInstance()
  private var audioSessionIsActive = false
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
  }

  deinit {
    NotificationCenter.default.removeObserver(
      self,
      name: AVAudioSession.interruptionNotification,
      object: nil
    )
    reset()
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

      // Initialize playWhenReady
      self.player.playWhenReady = false

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

      let interval = ((options["progressUpdateEventInterval"] as? NSNumber) ?? 0).doubleValue
      self.player.setProgressUpdateInterval(interval > 0 ? interval : nil)
    }
  }

  @objc
  public func add(tracks: [[String: Any]], before trackIndex: NSNumber) {
    onMainThread {
      guard self.hasInitialized else { return }
      // -1 means no index was passed and therefore should be inserted at the end.
      let index = trackIndex.intValue == -1 ? player.tracks.count : trackIndex.intValue
      guard index >= 0, index <= player.tracks.count else { return }

      var trackObjects = [Track]()
      for trackDict in tracks {
        guard let track = Track.fromBridge(dictionary: trackDict) else { return }
        trackObjects.append(track)
      }

      try? player.add(trackObjects, at: index)
    }
  }

  @objc
  public func load(track: [String: Any]) {
    guard let trackObject = Track.fromBridge(dictionary: track) else { return }
    ensureMainThread {
      guard self.hasInitialized else { return }
      self.player.load(trackObject)
    }
  }

  @objc
  public func remove(tracks indexes: [Int]) {
    ensureMainThread {
      guard self.hasInitialized else { return }

      // Check for duplicates
      guard Set(indexes).count == indexes.count else {
        print("Error: Duplicate indexes provided to remove()")
        return
      }

      // Validate all indexes first
      for index in indexes {
        guard index >= 0, index < self.player.tracks.count else { return }
      }

      // Sort the indexes in descending order so we can safely remove them one by one
      // without having the next index possibly newly pointing to another track than intended:
      for index in indexes.sorted().reversed() {
        try? self.player.remove(index)
      }
    }
  }

  @objc
  public func move(fromIndex: Int, toIndex: Int) {
    ensureMainThread {
      guard self.hasInitialized else { return }
      guard fromIndex >= 0, fromIndex < self.player.tracks.count else { return }
      guard toIndex >= 0 else { return }
      try? self.player.move(fromIndex: fromIndex, toIndex: toIndex)
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

      try? self.player.skipTo(
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
  public func setQueue(tracks: [[String: Any]]) {
    ensureMainThread {
      guard self.hasInitialized else { return }
      var trackObjects = [Track]()
      for trackDict in tracks {
        guard let track = Track.fromBridge(dictionary: trackDict) else { return }
        trackObjects.append(track)
      }
      self.player.clear()
      try? self.player.add(trackObjects)
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
      return player.getPlaybackState().toBridge()
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

  // MARK: - Player Event Handlers

  func handleStateChange(state: PlaybackState) {
    ensureMainThread {
      self.delegate?.emitPlaybackState(state.toBridge())
    }
  }

  func handleCommonMetadataReceived(metadata: [AVMetadataItem]) {
    let commonMetadata = MetadataAdapter.convertToCommonMetadata(metadata: metadata, skipRaw: true)
    delegate?.emitMetadataCommonReceived(["metadata": commonMetadata])
  }

  func handleChapterMetadataReceived(metadata: [AVTimedMetadataGroup]) {
    let metadataItems = MetadataAdapter.convertToGroupedMetadata(metadataGroups: metadata)
    delegate?.emitMetadataChapterReceived(["metadata": metadataItems])
  }

  func handleTimedMetadataReceived(metadata: [AVTimedMetadataGroup]) {
    let metadataItems = MetadataAdapter.convertToGroupedMetadata(metadataGroups: metadata)
    delegate?.emitMetadataTimedReceived(["metadata": metadataItems])
  }

  func handleFailed(error: Error?) {
    let eventData = if let error {
      PlaybackErrorEvent(
        code: "playback-error",
        message: error.localizedDescription
      )
    } else {
      PlaybackErrorEvent()
    }
    delegate?.emitPlaybackError(eventData.toBridge())
  }

  func handleActiveTrackChanged(_ event: PlaybackActiveTrackChangedEvent) {
    ensureMainThread {
      if let track = event.track {
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

      if (event.track != nil && event.lastTrack == nil) || event.track == nil {
        self.configureAudioSession()
      }

      self.delegate?.emitPlaybackActiveTrackChanged(event.toBridge())
    }
  }

  func handleProgressUpdate(event: PlaybackProgressUpdatedEvent) {
    ensureMainThread {
      self.delegate?.emitPlaybackProgressUpdated(event.toBridge())
    }
  }

  func handlePlayWhenReadyChange(playWhenReady: Bool) {
    configureAudioSession()
    let event = PlaybackPlayWhenReadyChangedEvent(playWhenReady: playWhenReady)
    delegate?.emitPlaybackPlayWhenReadyChanged(event.toBridge())
  }
}

@objc public protocol NativeTrackPlayerImplDelegate {
  func emitPlaybackState(_ body: [String: Any])
  func emitPlaybackActiveTrackChanged(_ body: [String: Any])
  func emitPlaybackProgressUpdated(_ body: [String: Any])
  func emitPlaybackPlayWhenReadyChanged(_ body: [String: Any])
  func emitPlaybackQueueEnded(_ body: [String: Any])
  func emitPlaybackError(_ body: [String: Any])
  func emitRemotePlay()
  func emitRemotePause()
  func emitRemoteNext()
  func emitRemotePrevious()
  func emitRemoteSeek(_ body: [String: Any])
  func emitRemoteJumpForward(_ body: [String: Any])
  func emitRemoteJumpBackward(_ body: [String: Any])
  func emitRemoteStop()
  func emitRemoteSetRating(_ body: [String: Any])
  func emitRemotePlayId(_ body: [String: Any])
  func emitRemotePlaySearch(_ body: [String: Any])
  func emitRemoteSkip(_ body: [String: Any])
  func emitRemoteLike()
  func emitRemoteDislike()
  func emitRemoteBookmark()
  func emitMetadataTimedReceived(_ body: [String: Any])
  func emitMetadataCommonReceived(_ body: [String: Any])
  func emitMetadataChapterReceived(_ body: [String: Any])
  func emitPlaybackMetadata(_ body: [String: Any])
}

public extension NativeTrackPlayerImpl {
  @objc(constantsToExport)
  static var constantsToExport: [AnyHashable: Any] {
    return [
      "STATE_NONE": State.none.bridge,
      "STATE_READY": State.ready.bridge,
      "STATE_PLAYING": State.playing.bridge,
      "STATE_PAUSED": State.paused.bridge,
      "STATE_STOPPED": State.stopped.bridge,
      "STATE_BUFFERING": State.buffering.bridge,
      "STATE_LOADING": State.loading.bridge,
      "STATE_ERROR": State.error.bridge,

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

// MARK: - TrackPlayerCallbacks Implementation

extension NativeTrackPlayerImpl: TrackPlayerCallbacks {
  public func onPlaybackState(_ state: PlaybackState) {
    handleStateChange(state: state)
  }

  public func onPlaybackActiveTrackChanged(_ event: PlaybackActiveTrackChangedEvent) {
    handleActiveTrackChanged(event)
  }

  public func onPlaybackProgressUpdated(_ event: PlaybackProgressUpdatedEvent) {
    handleProgressUpdate(event: event)
  }

  public func onPlaybackPlayWhenReadyChanged(_ playWhenReady: Bool) {
    handlePlayWhenReadyChange(playWhenReady: playWhenReady)
  }

  public func onPlaybackQueueEnded(_ event: PlaybackQueueEndedEvent) {
    delegate?.emitPlaybackQueueEnded(event.toBridge())
  }

  public func onPlaybackError(_ error: Error?) {
    handleFailed(error: error)
  }

  public func onMetadataCommonReceived(_ metadata: [AVMetadataItem]) {
    handleCommonMetadataReceived(metadata: metadata)
  }

  public func onMetadataTimedReceived(_ metadata: [AVTimedMetadataGroup]) {
    handleTimedMetadataReceived(metadata: metadata)
  }

  public func onMetadataChapterReceived(_ metadata: [AVTimedMetadataGroup]) {
    handleChapterMetadataReceived(metadata: metadata)
  }

  public func onSeekCompleted(position: Double, didFinish: Bool) {
    // Seek events are not currently emitted to React Native
    // They could be added if needed
  }

  public func onDurationUpdated(_ duration: Double) {
    // Duration updates are not currently emitted to React Native
    // They could be added if needed
  }

  // MARK: - Remote Control Callbacks

  public func onRemotePlay() {
    delegate?.emitRemotePlay()
  }

  public func onRemotePause() {
    delegate?.emitRemotePause()
  }

  public func onRemoteStop() {
    delegate?.emitRemoteStop()
  }

  public func onRemotePlayPause() {
    // Check playWhenReady (user intent) and emit appropriate event
    // If user wants to play -> emit pause, otherwise emit play
    if player.playWhenReady {
      delegate?.emitRemotePause()
    } else {
      delegate?.emitRemotePlay()
    }
  }

  public func onRemoteNext() {
    delegate?.emitRemoteNext()
  }

  public func onRemotePrevious() {
    delegate?.emitRemotePrevious()
  }

  public func onRemoteJumpForward(interval: Double) {
    let event = RemoteJumpForwardEvent(interval: interval)
    delegate?.emitRemoteJumpForward(event.toBridge())
  }

  public func onRemoteJumpBackward(interval: Double) {
    let event = RemoteJumpBackwardEvent(interval: interval)
    delegate?.emitRemoteJumpBackward(event.toBridge())
  }

  public func onRemoteSeek(position: Double) {
    let event = RemoteSeekEvent(position: position)
    delegate?.emitRemoteSeek(event.toBridge())
  }

  public func onRemoteChangePlaybackPosition(position: Double) {
    let event = RemoteSeekEvent(position: position)
    delegate?.emitRemoteSeek(event.toBridge())
  }

  public func onRemoteSetRating(rating: Any) {
    // Convert rating to appropriate format and emit
    delegate?.emitRemoteSetRating(["rating": String(describing: rating)])
  }

  public func onRemotePlayId(id: String, index: Int?) {
    let event = RemotePlayIdEvent(id: id)
    delegate?.emitRemotePlayId(event.toBridge())
  }

  public func onRemotePlaySearch(query: String) {
    let event = RemotePlaySearchEvent(query: query)
    delegate?.emitRemotePlaySearch(event.toBridge())
  }

  public func onRemoteLike() {
    delegate?.emitRemoteLike()
  }

  public func onRemoteDislike() {
    delegate?.emitRemoteDislike()
  }

  public func onRemoteBookmark() {
    delegate?.emitRemoteBookmark()
  }
}
