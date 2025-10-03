import Foundation
import MediaPlayer

/**
 Event system for TrackPlayer.

 Provides type-safe events that listeners can subscribe to for playback state changes,
 metadata updates, and player lifecycle events. TrackPlayerModule uses these events to
 bridge player state to React Native.
 */
public extension TrackPlayer {
  typealias PlayWhenReadyChangeData = Bool
  typealias StateChangeEventData = PlaybackState
  typealias PlaybackEndEventData = PlaybackEndedReason
  typealias SecondElapseEventData = TimeInterval
  typealias FailEventData = Error?
  typealias SeekEventData = (seconds: Double, didFinish: Bool)
  typealias UpdateDurationEventData = Double
  typealias MetadataCommonEventData = [AVMetadataItem]
  typealias MetadataTimedEventData = [AVTimedMetadataGroup]
  typealias DidRecreateAVPlayerEventData = Void
  typealias CurrentTrackEventData = PlaybackActiveTrackChangedEvent

  struct EventHolder {
    /**
     Emitted when the `TrackPlayer`s state is changed
     - Important: Remember to dispatch to the main queue if any UI is updated in the event handler.
     */
    public let stateChange: TrackPlayer.Event<StateChangeEventData> = TrackPlayer.Event()

    /**
     Emitted when the `TrackPlayer#playWhenReady` has changed
     - Important: Remember to dispatch to the main queue if any UI is updated in the event handler.
     */
    public let playWhenReadyChange: TrackPlayer.Event<PlayWhenReadyChangeData> = TrackPlayer.Event()

    /**
     Emitted when the playback of the player, for some reason, has stopped.
     - Important: Remember to dispatch to the main queue if any UI is updated in the event handler.
     */
    public let playbackEnd: TrackPlayer.Event<PlaybackEndEventData> = TrackPlayer.Event()

    /**
     Emitted when a second is elapsed in the `TrackPlayer`.
     - Important: Remember to dispatch to the main queue if any UI is updated in the event handler.
     */
    public let secondElapse: TrackPlayer.Event<SecondElapseEventData> = TrackPlayer.Event()

    /**
     Emitted when the player encounters an error. This will ultimately result in the AVPlayer instance to be recreated.
     If this event is emitted, it means you will need to load a new track in some way. Calling play() will not resume playback.
     - Important: Remember to dispatch to the main queue if any UI is updated in the event handler.
     */
    public let fail: TrackPlayer.Event<FailEventData> = TrackPlayer.Event()

    /**
     Emitted when the player is done attempting to seek.
     - Important: Remember to dispatch to the main queue if any UI is updated in the event handler.
     */
    public let seek: TrackPlayer.Event<SeekEventData> = TrackPlayer.Event()

    /**
     Emitted when the player updates its duration.
     - Important: Remember to dispatch to the main queue if any UI is updated in the event handler.
     */
    public let updateDuration: TrackPlayer.Event<UpdateDurationEventData> = TrackPlayer.Event()

    /**
     Emitted when the player receives common metadata.
     - Important: Remember to dispatch to the main queue if any UI is updated in the event handler.
     */
    public let receiveCommonMetadata: TrackPlayer.Event<MetadataCommonEventData> = TrackPlayer
      .Event()

    /**
     Emitted when the player receives timed metadata.
     - Important: Remember to dispatch to the main queue if any UI is updated in the event handler.
     */
    public let receiveTimedMetadata: TrackPlayer.Event<MetadataTimedEventData> = TrackPlayer.Event()

    /**
     Emitted when the player receives chapter metadata.
     - Important: Remember to dispatch to the main queue if any UI is updated in the event handler.
     */
    public let receiveChapterMetadata: TrackPlayer.Event<MetadataTimedEventData> = TrackPlayer
      .Event()

    /**
     Emitted when the underlying AVPlayer instance is recreated. Recreation happens if the current player fails.
     - Important: Remember to dispatch to the main queue if any UI is updated in the event handler.
     - Note: It can be necessary to set the AVAudioSession's category again when this event is emitted.
     */
    public let didRecreateAVPlayer: TrackPlayer.Event<Void> = TrackPlayer.Event()

    /**
     Emitted when the current track has changed.
     - Important: Remember to dispatch to the main queue if any UI is updated in the event handler.
     */
    public let currentTrack: TrackPlayer.Event<CurrentTrackEventData> = TrackPlayer.Event()
  }

  typealias EventClosure<EventData> = (EventData) -> Void

  internal class Invoker<EventData> {
    // Signals false if the listener object is nil
    let invoke: (EventData) -> Bool
    weak var listener: AnyObject?

    init(listener: some AnyObject, closure: @escaping EventClosure<EventData>) {
      self.listener = listener
      invoke = { [weak listener] (data: EventData) in
        guard let _ = listener else {
          return false
        }
        closure(data)
        return true
      }
    }
  }

  class Event<EventData> {
    private let queue: DispatchQueue = .init(label: "com.swiftAudioEx.eventQueue")
    var invokers: [Invoker<EventData>] = []

    public func addListener(
      _ listener: some AnyObject,
      _ closure: @escaping EventClosure<EventData>
    ) {
      queue.async {
        self.invokers.append(Invoker(listener: listener, closure: closure))
      }
    }

    public func removeListener(_ listener: AnyObject) {
      queue.async {
        self.invokers = self.invokers.filter({ invoker -> Bool in
          return invoker.listener !== listener
        })
      }
    }

    func emit(data: EventData) {
      queue.async {
        self.invokers = self.invokers.filter { $0.invoke(data) }
      }
    }
  }
}
