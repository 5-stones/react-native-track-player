import { RatingType } from '../constants';
import TrackPlayer from '../NativeTrackPlayer';
import resolveAssetSource from '../resolveAssetSource';

// MARK: - Types

export interface TrackMetadataBase {
  /** The track title */
  title?: string;
  /** The track album */
  album?: string;
  /** The track artist */
  artist?: string;
  /** The track duration in seconds */
  duration?: number;
  /** The track artwork */
  artwork?: string;
  /** track description */
  description?: string;
  /** track mediaId */
  mediaId?: string;
  /** The track genre */
  genre?: string;
  /** The track release date in [RFC 3339](https://www.ietf.org/rfc/rfc3339.txt) */
  date?: string;
  /** The track rating */
  rating?: RatingType;
  /**
   * (iOS only) Whether the track is presented in the control center as being
   * live
   **/
  isLiveStream?: boolean;
}

export interface NowPlayingMetadata extends TrackMetadataBase {
  elapsedTime?: number;
}

/**
 * Common metadata received event.
 */
export interface AudioCommonMetadataReceivedEvent {
  metadata: unknown;
}

/**
 * Timed metadata received event.
 */
export interface AudioMetadataReceivedEvent {
  metadata: unknown;
}

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

// MARK: - Metadata Updates

/**
 * Updates the metadata of a track in the queue. If the current track is updated,
 * the notification and the Now Playing Center will be updated accordingly.
 *
 * @param trackIndex - The index of the track whose metadata will be updated.
 * @param metadata - The metadata to update.
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

// MARK: - Event Callbacks

/**
 * Subscribes to chapter metadata events (iOS only).
 * @param callback - Called when chapter metadata is received
 * @returns Cleanup function to unsubscribe
 */
export function onMetadataChapterReceived(
  callback: (event: AudioMetadataReceivedEvent) => void
): () => void {
  return TrackPlayer.onMetadataChapterReceived(callback as () => void).remove;
}

/**
 * Subscribes to common metadata events.
 * @param callback - Called when common (static) metadata is received
 * @returns Cleanup function to unsubscribe
 */
export function onMetadataCommonReceived(
  callback: (event: AudioCommonMetadataReceivedEvent) => void
): () => void {
  return TrackPlayer.onMetadataCommonReceived(callback as () => void).remove;
}

/**
 * Subscribes to timed metadata events.
 * @param callback - Called when timed metadata is received
 * @returns Cleanup function to unsubscribe
 */
export function onMetadataTimedReceived(
  callback: (event: AudioMetadataReceivedEvent) => void
): () => void {
  return TrackPlayer.onMetadataTimedReceived(callback as () => void).remove;
}

/**
 * Subscribes to playback metadata events.
 * @param callback - Called when playback metadata is received
 * @returns Cleanup function to unsubscribe
 */
export function onPlaybackMetadata(callback: () => void): () => void {
  return TrackPlayer.onPlaybackMetadata(callback).remove;
}
