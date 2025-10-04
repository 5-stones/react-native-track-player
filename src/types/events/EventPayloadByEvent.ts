import { Event } from '../../constants';
import type { PlaybackState } from '../PlaybackState';
import type {
  AudioCommonMetadataReceivedEvent,
  AudioMetadataReceivedEvent,
} from './AudioMetadataReceivedEvent';
import type {
  AndroidControllerConnectedEvent,
  AndroidControllerDisconnectedEvent,
} from './ControllerConnectedEvent';
import type { PlaybackActiveTrackChangedEvent } from './PlaybackActiveTrackChangedEvent';
import type { PlaybackErrorEvent } from './PlaybackErrorEvent';
import type { PlaybackPlayWhenReadyChangedEvent } from './PlaybackPlayWhenReadyChangedEvent';
import type { PlaybackProgressUpdatedEvent } from './PlaybackProgressUpdatedEvent';
import type { PlaybackQueueEndedEvent } from './PlaybackQueueEndedEvent';
import type { RemoteJumpBackwardEvent } from './RemoteJumpBackwardEvent';
import type { RemoteJumpForwardEvent } from './RemoteJumpForwardEvent';
import type { RemotePlayIdEvent } from './RemotePlayIdEvent';
import type { RemotePlaySearchEvent } from './RemotePlaySearchEvent';
import type { RemoteSeekEvent } from './RemoteSeekEvent';
import type { RemoteSetRatingEvent } from './RemoteSetRatingEvent';
import type { RemoteSkipEvent } from './RemoteSkipEvent';

// TODO: implement exhaustive checking
export type EventPayloadByEvent = {
  [Event.PlaybackState]: PlaybackState;
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

export type EventPayloadByEventWithType = {
  [K in keyof EventPayloadByEvent]: EventPayloadByEvent[K] extends never
    ? { type: K }
    : Simplify<EventPayloadByEvent[K] & { type: K }>;
};
