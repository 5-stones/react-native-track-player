import { useEffect, useRef } from 'react';

import { Event } from '../constants';
import TrackPlayer from '../NativeTrackPlayer';
import type { PlaybackActiveTrackChangedEvent } from './activeTrack';
import type { PlaybackErrorEvent } from './errors';
import type {
  AudioCommonMetadataReceivedEvent,
  AudioMetadataReceivedEvent,
} from './metadata';
import type { PlaybackState } from './playbackState';
import type { PlayingState } from './playingState';
import type { PlaybackPlayWhenReadyChangedEvent } from './playWhenReady';
import type { PlaybackProgressUpdatedEvent } from './progress';
import type { PlaybackQueueEndedEvent } from './queue';
import type {
  AndroidControllerConnectedEvent,
  AndroidControllerDisconnectedEvent,
  RemoteJumpBackwardEvent,
  RemoteJumpForwardEvent,
  RemotePlayIdEvent,
  RemotePlaySearchEvent,
  RemoteSeekEvent,
  RemoteSetRatingEvent,
  RemoteSkipEvent,
} from './remoteControls';

// MARK: - Event Type Mappings

/**
 * Maps event constants to their payload types.
 */
export type EventPayloadByEvent = {
  [Event.PlaybackState]: PlaybackState;
  [Event.PlaybackPlayingState]: PlayingState;
  [Event.PlaybackError]: PlaybackErrorEvent | undefined;
  [Event.PlaybackQueueEnded]: PlaybackQueueEndedEvent;
  [Event.PlaybackActiveTrackChanged]: PlaybackActiveTrackChangedEvent;
  [Event.PlaybackPlayWhenReadyChanged]: PlaybackPlayWhenReadyChangedEvent;
  [Event.PlaybackProgressUpdated]: PlaybackProgressUpdatedEvent;
  [Event.RemotePlay]: never;
  [Event.RemotePlayId]: RemotePlayIdEvent;
  [Event.RemotePlaySearch]: RemotePlaySearchEvent;
  [Event.RemotePause]: never;
  [Event.RemoteStop]: never;
  [Event.RemoteSkip]: RemoteSkipEvent;
  [Event.RemoteNext]: never;
  [Event.RemotePrevious]: never;
  [Event.RemoteJumpForward]: RemoteJumpForwardEvent;
  [Event.RemoteJumpBackward]: RemoteJumpBackwardEvent;
  [Event.RemoteSeek]: RemoteSeekEvent;
  [Event.RemoteSetRating]: RemoteSetRatingEvent;
  [Event.RemoteLike]: never;
  [Event.RemoteDislike]: never;
  [Event.RemoteBookmark]: never;
  [Event.MetadataChapterReceived]: AudioMetadataReceivedEvent;
  [Event.MetadataTimedReceived]: AudioMetadataReceivedEvent;
  [Event.MetadataCommonReceived]: AudioCommonMetadataReceivedEvent;
  [Event.AndroidConnectorConnected]: AndroidControllerConnectedEvent;
  [Event.AndroidConnectorDisconnected]: AndroidControllerDisconnectedEvent;
};

type Simplify<T> = { [KeyType in keyof T]: T[KeyType] } & {};

/**
 * Event payloads with their type constant attached.
 */
export type EventPayloadByEventWithType = {
  [K in keyof EventPayloadByEvent]: EventPayloadByEvent[K] extends never
    ? { type: K }
    : Simplify<EventPayloadByEvent[K] & { type: K }>;
};

// MARK: - Event Subscription

const callbackByEvent = {
  'android-controller-connected': TrackPlayer.onAndroidControllerConnected,
  'android-controller-disconnected':
    TrackPlayer.onAndroidControllerDisconnected,
  'metadata-chapter-received': TrackPlayer.onMetadataChapterReceived,
  'metadata-common-received': TrackPlayer.onMetadataCommonReceived,
  'metadata-timed-received': TrackPlayer.onMetadataTimedReceived,
  'playback-state': TrackPlayer.onPlaybackState,
  'playback-playing-state': TrackPlayer.onPlaybackPlayingState,
  // 'playback-active-track': TrackPlayer.onPlaybackActiveTrack,
  'playback-active-track-changed': TrackPlayer.onPlaybackActiveTrackChanged,
  'playback-progress-updated': TrackPlayer.onPlaybackProgressUpdated,
  'playback-play-when-ready-changed':
    TrackPlayer.onPlaybackPlayWhenReadyChanged,
  'playback-queue-ended': TrackPlayer.onPlaybackQueueEnded,
  'playback-error': TrackPlayer.onPlaybackError,
  // 'playback-metadata': TrackPlayer.onPlaybackMetadata,
  'remote-play': TrackPlayer.onRemotePlay,
  'remote-play-search': TrackPlayer.onRemotePlaySearch,
  'remote-play-id': TrackPlayer.onRemotePlayId,
  'remote-pause': TrackPlayer.onRemotePause,
  'remote-stop': TrackPlayer.onRemoteStop,
  'remote-next': TrackPlayer.onRemoteNext,
  'remote-previous': TrackPlayer.onRemotePrevious,
  'remote-seek': TrackPlayer.onRemoteSeek,
  'remote-jump-forward': TrackPlayer.onRemoteJumpForward,
  'remote-jump-backward': TrackPlayer.onRemoteJumpBackward,
  'remote-set-rating': TrackPlayer.onRemoteSetRating,
  'remote-like': TrackPlayer.onRemoteLike,
  'remote-dislike': TrackPlayer.onRemoteDislike,
  'remote-bookmark': TrackPlayer.onRemoteBookmark,
  'remote-skip': TrackPlayer.onRemoteSkip,
} satisfies Record<Event, (callback: any) => { remove: () => void }>;

/**
 * Subscribes to a TrackPlayer event.
 * @param event - The event to subscribe to
 * @param listener - The callback to invoke when the event fires
 * @returns Subscription object with remove() method
 */
export function addEventListener<T extends Event>(
  event: T,
  listener: EventPayloadByEvent[T] extends never
    ? () => void
    : (event: EventPayloadByEvent[T]) => void
): {
  remove: () => void;
} {
  return callbackByEvent[event](listener as never);
}

// MARK: - React Hook

/**
 * Attaches a handler to the given TrackPlayer events and performs cleanup on unmount.
 * @param events - TrackPlayer events to subscribe to
 * @param handler - Callback invoked when the event fires
 */
export function useTrackPlayerEvents<
  T extends Event[],
  H extends (data: EventPayloadByEventWithType[T[number]]) => void,
>(events: T, handler: H) {
  const savedHandler = useRef(handler);
  savedHandler.current = handler;

  /* eslint-disable react-hooks/exhaustive-deps */
  useEffect(() => {
    if (__DEV__) {
      const allowedTypes = Object.values(Event);
      const invalidTypes = events.filter(
        (type) => !allowedTypes.includes(type)
      );
      if (invalidTypes.length) {
        console.warn(
          'One or more of the events provided to useTrackPlayerEvents is ' +
            `not a valid TrackPlayer event: ${invalidTypes.join("', '")}. ` +
            'A list of available events can be found at ' +
            'https://rntp.dev/docs/api/events'
        );
      }
    }

    const subs = events.map((type) =>
      addEventListener(type, (payload) => {
        // @ts-expect-error - we know the type is correct
        savedHandler.current({ ...payload, type });
      })
    );

    return () => subs.forEach((sub) => sub.remove());
  }, events);
}

/**
 * Logs all TrackPlayer events to the console for debugging purposes.
 */
export function useDebugPlayerEvents() {
  useTrackPlayerEvents(Object.values(Event), ({ type, ...event }) => {
    console.debug(`Event: ${type}`, event);
  });
}
