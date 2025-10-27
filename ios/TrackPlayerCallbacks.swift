import AVFoundation
import MediaPlayer

/// Callbacks for all player events.
///
/// This protocol defines the interface for receiving events from TrackPlayer.
/// TrackPlayerModule implements this protocol to bridge events to React Native.
public protocol TrackPlayerCallbacks: AnyObject {
  // MARK: - Playback State Events

  /// Called when the player state changes.
  func onPlaybackState(_ state: PlaybackState)

  /// Called when the active track changes.
  func onPlaybackActiveTrackChanged(_ event: PlaybackActiveTrackChangedEvent)

  /// Called periodically with playback progress updates.
  func onPlaybackProgressUpdated(_ event: PlaybackProgressUpdatedEvent)

  /// Called when playWhenReady changes.
  func onPlaybackPlayWhenReadyChanged(_ playWhenReady: Bool)

  /// Called when the playing state changes (playing or buffering flags).
  func onPlaybackPlayingState(_ event: PlaybackPlayingState)

  /// Called when the playback queue ends (player reaches the end of the last track).
  func onPlaybackQueueEnded(_ event: PlaybackQueueEndedEvent)

  /// Called when the player encounters an error.
  func onPlaybackError(_ error: Error?)

  // MARK: - Metadata Events

  /// Called when common metadata is received.
  func onMetadataCommonReceived(_ metadata: [AVMetadataItem])

  /// Called when timed metadata is received.
  func onMetadataTimedReceived(_ metadata: [AVTimedMetadataGroup])

  /// Called when chapter metadata is received.
  func onMetadataChapterReceived(_ metadata: [AVTimedMetadataGroup])

  // MARK: - Playback Events

  /// Called when a seek operation completes.
  func onSeekCompleted(position: Double, didFinish: Bool)

  /// Called when the duration is updated.
  func onDurationUpdated(_ duration: Double)

  // MARK: - Remote Control Events

  /// Called when play is triggered remotely.
  func onRemotePlay()

  /// Called when pause is triggered remotely.
  func onRemotePause()

  /// Called when stop is triggered remotely.
  func onRemoteStop()

  /// Called when toggle play/pause is triggered remotely.
  /// Implementation should check player state and emit appropriate event.
  func onRemotePlayPause()

  /// Called when next is triggered remotely.
  func onRemoteNext()

  /// Called when previous is triggered remotely.
  func onRemotePrevious()

  /// Called when jump forward is triggered remotely.
  func onRemoteJumpForward(interval: Double)

  /// Called when jump backward is triggered remotely.
  func onRemoteJumpBackward(interval: Double)

  /// Called when seek is triggered remotely.
  func onRemoteSeek(position: Double)

  /// Called when change playback position is triggered remotely.
  func onRemoteChangePlaybackPosition(position: Double)

  /// Called when set rating is triggered remotely.
  func onRemoteSetRating(rating: Any)

  /// Called when play from ID is triggered remotely.
  func onRemotePlayId(id: String, index: Int?)

  /// Called when play from search is triggered remotely.
  func onRemotePlaySearch(query: String)

  /// Called when like is triggered remotely.
  func onRemoteLike()

  /// Called when dislike is triggered remotely.
  func onRemoteDislike()

  /// Called when bookmark is triggered remotely.
  func onRemoteBookmark()

  // MARK: - Configuration Events

  /// Called when options are changed.
  func onOptionsChanged(_ options: PlayerUpdateOptions)
}
