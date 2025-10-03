import { AppRegistry, Platform } from 'react-native';

import type { EventEmitter } from 'react-native/Libraries/Types/CodegenTypes';
import { Event, RepeatMode } from './constants';
import TrackPlayer from './NativeTrackPlayer';
import resolveAssetSource from './resolveAssetSource';
import type {
  AddTrack,
  EventPayloadByEvent,
  NowPlayingMetadata,
  PlaybackState,
  PlayerOptions,
  Progress,
  ServiceHandler,
  Track,
  TrackMetadataBase,
  UpdateOptions,
} from './types';

const isAndroid = Platform.OS === 'android';

// MARK: - Helpers

function resolveImportedAssetOrPath(pathOrAsset: string | number | undefined) {
  return pathOrAsset === undefined
    ? undefined
    : typeof pathOrAsset === 'string'
      ? pathOrAsset
      : resolveImportedAsset(pathOrAsset);
}

function resolveImportedAsset(id?: number) {
  return id
    ? ((resolveAssetSource(id) as { uri: string } | null) ?? undefined)
    : undefined;
}

function resolveTrackAssets(track: AddTrack) {
  return {
    ...track,
    url: resolveImportedAssetOrPath(track.url),
    artwork: resolveImportedAssetOrPath(track.artwork),
  };
}

// MARK: - General API

/**
 * Initializes the player with the specified options.
 *
 * @param options The options to initialize the player with.
 * @see https://rntp.dev/docs/api/functions/lifecycle
 */
export async function setupPlayer(options: PlayerOptions = {}): Promise<void> {
  return TrackPlayer.setupPlayer(options);
}

/**
 * Register the playback service. The service will run as long as the player runs.
 */
export function registerPlaybackService(factory: () => ServiceHandler) {
  if (isAndroid) {
    // Registers the headless task
    AppRegistry.registerHeadlessTask('TrackPlayer', factory);
  } else if (Platform.OS === 'web') {
    factory()();
  } else {
    // Initializes and runs the service in the next tick
    setImmediate(factory());
  }
}

const callbackByEvent = {
  'android-controller-connected': TrackPlayer.onAndroidControllerConnected,
  'android-controller-disconnected':
    TrackPlayer.onAndroidControllerDisconnected,
  'metadata-chapter-received': TrackPlayer.onMetadataChapterReceived,
  'metadata-common-received': TrackPlayer.onMetadataCommonReceived,
  'metadata-timed-received': TrackPlayer.onMetadataTimedReceived,
  'playback-state': TrackPlayer.onPlaybackState,
  'playback-active-track-changed': TrackPlayer.onPlaybackActiveTrackChanged,
  'playback-progress-updated': TrackPlayer.onPlaybackProgressUpdated,
  'playback-play-when-ready-changed':
    TrackPlayer.onPlaybackPlayWhenReadyChanged,
  'playback-queue-ended': TrackPlayer.onPlaybackQueueEnded,
  'playback-error': TrackPlayer.onPlaybackError,
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
} satisfies Record<Event, EventEmitter<object>>;

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

// MARK: - Queue API

/**
 * Adds one or more tracks to the queue.
 *
 * @param tracks The tracks to add to the queue.
 * @param insertBeforeIndex (Optional) The index to insert the tracks before.
 * By default the tracks will be added to the end of the queue.
 */
export function add(tracks: AddTrack[], insertBeforeIndex?: number): void;
/**
 * Adds a track to the queue.
 *
 * @param track The track to add to the queue.
 * @param insertBeforeIndex (Optional) The index to insert the track before.
 * By default the track will be added to the end of the queue.
 */
export function add(track: AddTrack, insertBeforeIndex?: number): void;
export function add(
  tracks: AddTrack | AddTrack[],
  insertBeforeIndex = -1
): void {
  const addTracks = Array.isArray(tracks) ? tracks : [tracks];
  if (addTracks.length > 0) {
    TrackPlayer.add(addTracks.map(resolveTrackAssets), insertBeforeIndex);
  }
}

/**
 * Replaces the current track or loads the track as the first in the queue.
 *
 * @param track The track to load.
 */
export function load(track: Track): void {
  TrackPlayer.load(resolveTrackAssets(track));
}

/**
 * Move a track within the queue.
 *
 * @param fromIndex The index of the track to be moved.
 * @param toIndex The index to move the track to. If the index is larger than
 * the size of the queue, then the track is moved to the end of the queue.
 */
export function move(fromIndex: number, toIndex: number): void {
  TrackPlayer.move(fromIndex, toIndex);
}

/**
 * Removes multiple tracks from the queue by their indexes.
 *
 * If the current track is removed, the next track will activated. If the
 * current track was the last track in the queue, the first track will be
 * activated.
 *
 * @param indexes The indexes of the tracks to be removed.
 */
export function remove(indexes: number[]): void;
/**
 * Removes a track from the queue by its index.
 *
 * If the current track is removed, the next track will activated. If the
 * current track was the last track in the queue, the first track will be
 * activated.
 *
 * @param index The index of the track to be removed.
 */
export function remove(index: number): void;
export function remove(indexOrIndexes: number | number[]): void {
  TrackPlayer.remove(
    Array.isArray(indexOrIndexes) ? indexOrIndexes : [indexOrIndexes]
  );
}

/**
 * Clears any upcoming tracks from the queue.
 */
export function removeUpcomingTracks(): void {
  TrackPlayer.removeUpcomingTracks();
}

/**
 * Skips to a track in the queue.
 *
 * @param index The index of the track to skip to.
 * @param initialPosition (Optional) The initial position to seek to in seconds.
 */
export function skip(index: number, initialPosition = -1): void {
  TrackPlayer.skip(index, initialPosition);
}

/**
 * Skips to the next track in the queue.
 *
 * @param initialPosition (Optional) The initial position to seek to in seconds.
 */
export function skipToNext(initialPosition = -1): void {
  TrackPlayer.skipToNext(initialPosition);
}

/**
 * Skips to the previous track in the queue.
 *
 * @param initialPosition (Optional) The initial position to seek to in seconds.
 */
export function skipToPrevious(initialPosition = -1): void {
  TrackPlayer.skipToPrevious(initialPosition);
}

// MARK: - Control Center / Notifications API

/**
 * Updates the configuration for the components.
 *
 * @param options The options to update.
 * @see https://rntp.dev/docs/api/functions/player#updateoptionsoptions
 */
export function updateOptions(options: UpdateOptions = {}): void {
  TrackPlayer.updateOptions({
    ...options,
    android: {
      ...options.android,
    },
  });
}

/**
 * Updates the metadata of a track in the queue. If the current track is updated,
 * the notification and the Now Playing Center will be updated accordingly.
 *
 * @param trackIndex The index of the track whose metadata will be updated.
 * @param metadata The metadata to update.
 */
export function updateMetadataForTrack(
  trackIndex: number,
  metadata: TrackMetadataBase
): void {
  TrackPlayer.updateMetadataForTrack(trackIndex, {
    ...metadata,
    artwork: resolveImportedAssetOrPath(metadata.artwork),
  });
}

/**
 * Updates the metadata content of the notification (Android) and the Now Playing Center (iOS)
 * without affecting the data stored for the current track.
 */
export function updateNowPlayingMetadata(metadata: NowPlayingMetadata): void {
  TrackPlayer.updateNowPlayingMetadata({
    ...metadata,
    artwork: resolveImportedAssetOrPath(metadata.artwork),
  });
}

// MARK: - Player API

/**
 * Resets the player stopping the current track and clearing the queue.
 */
export function reset(): void {
  TrackPlayer.reset();
}

/**
 * Plays or resumes the current track.
 */
export function play(): void {
  TrackPlayer.play();
}

/**
 * Pauses the current track.
 */
export function pause(): void {
  TrackPlayer.pause();
}

/**
 * Stops the current track.
 */
export function stop(): void {
  TrackPlayer.stop();
}

/**
 * Sets whether the player will play automatically when it is ready to do so.
 * This is the equivalent of calling `TrackPlayer.play()` when `playWhenReady = true`
 * or `TrackPlayer.pause()` when `playWhenReady = false`.
 */
export function setPlayWhenReady(playWhenReady: boolean): void {
  TrackPlayer.setPlayWhenReady(playWhenReady);
}

/**
 * Gets whether the player will play automatically when it is ready to do so.
 */
export function getPlayWhenReady(): boolean {
  return TrackPlayer.getPlayWhenReady();
}

/**
 * Seeks to a specified time position in the current track.
 *
 * @param position The position to seek to in seconds.
 */
export function seekTo(position: number): void {
  TrackPlayer.seekTo(position);
}

/**
 * Seeks by a relative time offset in the current track.
 *
 * @param offset The time offset to seek by in seconds.
 */
export function seekBy(offset: number): void {
  TrackPlayer.seekBy(offset);
}

/**
 * Sets the volume of the player.
 *
 * @param volume The volume as a number between 0 and 1.
 */
export function setVolume(level: number): void {
  TrackPlayer.setVolume(level);
}

/**
 * Sets the playback rate.
 *
 * @param rate The playback rate to change to, where 0.5 would be half speed,
 * 1 would be regular speed, 2 would be double speed etc.
 */
export function setRate(rate: number): void {
  TrackPlayer.setRate(rate);
}

/**
 * Sets the queue.
 *
 * @param tracks The tracks to set as the queue.
 * @see https://rntp.dev/docs/api/constants/repeat-mode
 */
export function setQueue(tracks: Track[]): void {
  TrackPlayer.setQueue(tracks.map(resolveTrackAssets));
}

/**
 * Sets the queue repeat mode.
 *
 * @param repeatMode The repeat mode to set.
 * @see https://rntp.dev/docs/api/constants/repeat-mode
 */
export function setRepeatMode(mode: RepeatMode): void {
  TrackPlayer.setRepeatMode(mode);
}

// MARK: - Getters

/**
 * Gets the volume of the player as a number between 0 and 1.
 */
export function getVolume(): number {
  return TrackPlayer.getVolume();
}

/**
 * Gets the playback rate where 0.5 would be half speed, 1 would be
 * regular speed and 2 would be double speed etc.
 */
export function getRate(): number {
  return TrackPlayer.getRate();
}

/**
 * Gets a track object from the queue.
 *
 * @param index The index of the track.
 * @returns The track object or undefined if there isn't a track object at that
 * index.
 */
export function getTrack(index: number): Track | undefined {
  return TrackPlayer.getTrack(index) as unknown as Track;
}

/**
 * Gets the whole queue.
 */
export function getQueue(): Track[] {
  return TrackPlayer.getQueue() as unknown as Track[];
}

/**
 * Gets the index of the active track in the queue or undefined if there is no
 * current track.
 */
export function getActiveTrackIndex(): number | undefined {
  return TrackPlayer.getActiveTrackIndex() ?? undefined;
}

/**
 * Gets the active track or undefined if there is no current track.
 */
export function getActiveTrack(): Track | undefined {
  return (TrackPlayer.getActiveTrack() as Track) ?? undefined;
}

/**
 * Gets information on the progress of the currently active track, including its
 * current playback position in seconds, buffered position in seconds and
 * duration in seconds.
 */
export function getProgress(): Progress {
  return TrackPlayer.getProgress() as Progress;
}

/**
 * Gets the playback state of the player.
 *
 * @see https://rntp.dev/docs/api/constants/state
 */
export function getPlaybackState(): PlaybackState {
  return TrackPlayer.getPlaybackState() as PlaybackState;
}

/**
 * Gets the queue repeat mode.
 *
 * @see https://rntp.dev/docs/api/constants/repeat-mode
 */
export function getRepeatMode(): RepeatMode {
  return TrackPlayer.getRepeatMode();
}

/**
 * Retries the current item when the playback state is `State.Error`.
 */
export function retry(): void {
  TrackPlayer.retry();
}

/**
 * acquires the wake lock of MusicService (android only.)
 */
export function acquireWakeLock() {
  if (!isAndroid) return;
  TrackPlayer.acquireWakeLock();
}

/**
 * acquires the wake lock of MusicService (android only.)
 */
export function abandonWakeLock() {
  if (!isAndroid) return;
  TrackPlayer.abandonWakeLock();
}

/**
 * get onStartCommandIntent is null or not (Android only.). this is used to identify
 * if musicservice is restarted or not.
 */
export function validateOnStartCommandIntent(): boolean {
  if (!isAndroid) return true;
  return TrackPlayer.validateOnStartCommandIntent();
}
