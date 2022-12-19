import { Event } from '../../constants'
import type { PlaybackStateEvent } from './PlaybackStateEvent'
import type { PlaybackErrorEvent } from './PlaybackErrorEvent'
import type { PlaybackQueueEndedEvent } from './PlaybackQueueEndedEvent'
import type { PlaybackTrackChangedEvent } from './PlaybackTrackChangedEvent'
import type { PlaybackMetadataReceivedEvent } from './PlaybackMetadataReceivedEvent'
import type { PlaybackProgressUpdatedEvent } from './PlaybackProgressUpdatedEvent'
import type { RemotePlayIdEvent } from './RemotePlayIdEvent'
import type { RemotePlaySearchEvent } from './RemotePlaySearchEvent'
import type { RemoteSkipEvent } from './RemoteSkipEvent'
import type { RemoteJumpForwardEvent } from './RemoteJumpForwardEvent'
import type { RemoteJumpBackwardEvent } from './RemoteJumpBackwardEvent'
import type { RemoteSeekEvent } from './RemoteSeekEvent'
import type { RemoteSetRatingEvent } from './RemoteSetRatingEvent'
import type { RemoteDuckEvent } from './RemoteDuckEvent'

export interface EventsPayloadByEvent {
  [Event.PlaybackState]: PlaybackStateEvent & { type: Event.PlaybackState }
  [Event.PlaybackError]: PlaybackErrorEvent & { type: Event.PlaybackError }
  [Event.PlaybackQueueEnded]: PlaybackQueueEndedEvent & { type: Event.PlaybackQueueEnded }
  [Event.PlaybackTrackChanged]: PlaybackTrackChangedEvent & { type: Event.PlaybackTrackChanged }
  [Event.PlaybackMetadataReceived]: PlaybackMetadataReceivedEvent & { type: Event.PlaybackMetadataReceived }
  [Event.PlaybackProgressUpdated]: PlaybackProgressUpdatedEvent & { type: Event.PlaybackProgressUpdated }
  [Event.RemotePlay]: { type: Event.RemotePlay }
  [Event.RemotePlayId]: RemotePlayIdEvent & { type: Event.RemotePlayId }
  [Event.RemotePlaySearch]: RemotePlaySearchEvent & { type: Event.RemotePlaySearch }
  [Event.RemotePause]: { type: Event.RemotePause }
  [Event.RemoteStop]: { type: Event.RemoteStop }
  [Event.RemoteSkip]: RemoteSkipEvent & { type: Event.RemoteSkip }
  [Event.RemoteNext]: { type: Event.RemoteNext }
  [Event.RemotePrevious]: { type: Event.RemotePrevious }
  [Event.RemoteJumpForward]: RemoteJumpForwardEvent & { type: Event.RemoteJumpForward }
  [Event.RemoteJumpBackward]: RemoteJumpBackwardEvent & { type: Event.RemoteJumpBackward }
  [Event.RemoteSeek]: RemoteSeekEvent & { type: Event.RemoteSeek }
  [Event.RemoteSetRating]: RemoteSetRatingEvent & { type: Event.RemoteSetRating }
  [Event.RemoteDuck]: RemoteDuckEvent & { type: Event.RemoteDuck }
  [Event.RemoteLike]: { type: Event.RemoteLike }
  [Event.RemoteDislike]: { type: Event.RemoteDislike }
  [Event.RemoteBookmark]: { type: Event.RemoteBookmark }
}
