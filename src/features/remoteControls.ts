import TrackPlayer from '../NativeTrackPlayer';

/**
 * Remote jump backward event.
 */
export interface RemoteJumpBackwardEvent {
  /** Jump interval in seconds */
  interval: number;
}

/**
 * Remote jump forward event.
 */
export interface RemoteJumpForwardEvent {
  /** Jump interval in seconds */
  interval: number;
}

/**
 * Remote play ID event (Android only).
 */
export interface RemotePlayIdEvent {
  /** The ID of the track to play */
  id: string;
  /** Optional index in the queue */
  index?: number;
}

/**
 * Remote play search event (Android only).
 */
export interface RemotePlaySearchEvent {
  /** The search query */
  query: string;
}

/**
 * Remote seek event.
 */
export interface RemoteSeekEvent {
  /** The position to seek to in seconds */
  position: number;
}

/**
 * Remote set rating event.
 */
export interface RemoteSetRatingEvent {
  /** The rating value */
  rating: unknown;
}

/**
 * Remote skip event (Android only).
 */
export interface RemoteSkipEvent {
  /** The index to skip to */
  index: number;
}

/**
 * Android controller connected event.
 */
export interface AndroidControllerConnectedEvent {
  /** Name of the connected controller */
  name: string;
}

/**
 * Android controller disconnected event.
 */
export interface AndroidControllerDisconnectedEvent {
  /** Name of the disconnected controller */
  name: string;
}

// MARK: - Event Callbacks

/**
 * Subscribes to remote bookmark events (iOS only).
 * @param callback - Called when the user presses the bookmark button
 * @returns Cleanup function to unsubscribe
 */
export function onRemoteBookmark(callback: () => void): () => void {
  return TrackPlayer.onRemoteBookmark(callback).remove;
}

/**
 * Subscribes to remote dislike events (iOS only).
 * @param callback - Called when the user presses the dislike button
 * @returns Cleanup function to unsubscribe
 */
export function onRemoteDislike(callback: () => void): () => void {
  return TrackPlayer.onRemoteDislike(callback).remove;
}

/**
 * Subscribes to remote jump backward events.
 * @param callback - Called when the user presses the jump backward button
 * @returns Cleanup function to unsubscribe
 */
export function onRemoteJumpBackward(
  callback: (event: RemoteJumpBackwardEvent) => void
): () => void {
  return TrackPlayer.onRemoteJumpBackward(callback as () => void).remove;
}

/**
 * Subscribes to remote jump forward events.
 * @param callback - Called when the user presses the jump forward button
 * @returns Cleanup function to unsubscribe
 */
export function onRemoteJumpForward(
  callback: (event: RemoteJumpForwardEvent) => void
): () => void {
  return TrackPlayer.onRemoteJumpForward(callback as () => void).remove;
}

/**
 * Subscribes to remote like events (iOS only).
 * @param callback - Called when the user presses the like button
 * @returns Cleanup function to unsubscribe
 */
export function onRemoteLike(callback: () => void): () => void {
  return TrackPlayer.onRemoteLike(callback).remove;
}

/**
 * Subscribes to remote next events.
 * @param callback - Called when the user presses the next track button
 * @returns Cleanup function to unsubscribe
 */
export function onRemoteNext(callback: () => void): () => void {
  return TrackPlayer.onRemoteNext(callback).remove;
}

/**
 * Subscribes to remote pause events.
 * @param callback - Called when the user presses the pause button
 * @returns Cleanup function to unsubscribe
 */
export function onRemotePause(callback: () => void): () => void {
  return TrackPlayer.onRemotePause(callback).remove;
}

/**
 * Subscribes to remote play events.
 * @param callback - Called when the user presses the play button
 * @returns Cleanup function to unsubscribe
 */
export function onRemotePlay(callback: () => void): () => void {
  return TrackPlayer.onRemotePlay(callback).remove;
}

/**
 * Subscribes to remote play ID events (Android only).
 * @param callback - Called when the user selects a track from an external device
 * @returns Cleanup function to unsubscribe
 */
export function onRemotePlayId(
  callback: (event: RemotePlayIdEvent) => void
): () => void {
  return TrackPlayer.onRemotePlayId(callback as () => void).remove;
}

/**
 * Subscribes to remote play search events (Android only).
 * @param callback - Called when the user searches for a track (usually voice search)
 * @returns Cleanup function to unsubscribe
 */
export function onRemotePlaySearch(
  callback: (event: RemotePlaySearchEvent) => void
): () => void {
  return TrackPlayer.onRemotePlaySearch(callback as () => void).remove;
}

/**
 * Subscribes to remote previous events.
 * @param callback - Called when the user presses the previous track button
 * @returns Cleanup function to unsubscribe
 */
export function onRemotePrevious(callback: () => void): () => void {
  return TrackPlayer.onRemotePrevious(callback).remove;
}

/**
 * Subscribes to remote seek events.
 * @param callback - Called when the user changes the position of the timeline
 * @returns Cleanup function to unsubscribe
 */
export function onRemoteSeek(
  callback: (event: RemoteSeekEvent) => void
): () => void {
  return TrackPlayer.onRemoteSeek(callback as () => void).remove;
}

/**
 * Subscribes to remote set rating events.
 * @param callback - Called when the user changes the rating for the track remotely
 * @returns Cleanup function to unsubscribe
 */
export function onRemoteSetRating(
  callback: (event: RemoteSetRatingEvent) => void
): () => void {
  return TrackPlayer.onRemoteSetRating(callback as () => void).remove;
}

/**
 * Subscribes to remote skip events (Android only).
 * @param callback - Called when the user presses the skip button
 * @returns Cleanup function to unsubscribe
 */
export function onRemoteSkip(
  callback: (event: RemoteSkipEvent) => void
): () => void {
  return TrackPlayer.onRemoteSkip(callback as () => void).remove;
}

/**
 * Subscribes to remote stop events.
 * @param callback - Called when the user presses the stop button
 * @returns Cleanup function to unsubscribe
 */
export function onRemoteStop(callback: () => void): () => void {
  return TrackPlayer.onRemoteStop(callback).remove;
}

/**
 * Subscribes to Android controller connected events.
 * @param callback - Called when an Android controller (media notification or Android Auto) connects
 * @returns Cleanup function to unsubscribe
 */
export function onAndroidControllerConnected(
  callback: (event: AndroidControllerConnectedEvent) => void
): () => void {
  return TrackPlayer.onAndroidControllerConnected(callback as () => void)
    .remove;
}

/**
 * Subscribes to Android controller disconnected events.
 * @param callback - Called when an Android controller (media notification or Android Auto) disconnects
 * @returns Cleanup function to unsubscribe
 */
export function onAndroidControllerDisconnected(
  callback: (event: AndroidControllerDisconnectedEvent) => void
): () => void {
  return TrackPlayer.onAndroidControllerDisconnected(callback as () => void)
    .remove;
}
