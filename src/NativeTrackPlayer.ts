import { type TurboModule, TurboModuleRegistry } from 'react-native';
import {
  type EventEmitter,
  type UnsafeObject,
} from 'react-native/Libraries/Types/CodegenTypes';

export interface Spec extends TurboModule {
  // MARK: init and config
  setupPlayer(options: UnsafeObject): Promise<void>;
  updateOptions(options: UnsafeObject): void;
  getOptions(): UnsafeObject;

  // MARK: events
  readonly onAndroidControllerConnected: EventEmitter<UnsafeObject>;
  readonly onAndroidControllerDisconnected: EventEmitter<UnsafeObject>;
  readonly onMetadataChapterReceived: EventEmitter<UnsafeObject>;
  readonly onMetadataCommonReceived: EventEmitter<UnsafeObject>;
  readonly onMetadataTimedReceived: EventEmitter<UnsafeObject>;
  readonly onPlaybackActiveTrackChanged: EventEmitter<UnsafeObject>;
  readonly onPlaybackError: EventEmitter<UnsafeObject>;
  readonly onPlaybackMetadata: EventEmitter<UnsafeObject>;
  readonly onPlaybackPlayWhenReadyChanged: EventEmitter<UnsafeObject>;
  readonly onPlaybackPlayingState: EventEmitter<UnsafeObject>;
  readonly onPlaybackProgressUpdated: EventEmitter<UnsafeObject>;
  readonly onPlaybackQueueEnded: EventEmitter<UnsafeObject>;
  readonly onPlaybackRepeatModeChanged: EventEmitter<UnsafeObject>;
  readonly onPlaybackState: EventEmitter<UnsafeObject>;
  readonly onRemoteBookmark: EventEmitter<UnsafeObject>;
  readonly onRemoteDislike: EventEmitter<UnsafeObject>;
  readonly onRemoteJumpBackward: EventEmitter<UnsafeObject>;
  readonly onRemoteJumpForward: EventEmitter<UnsafeObject>;
  readonly onRemoteLike: EventEmitter<UnsafeObject>;
  readonly onRemoteNext: EventEmitter<UnsafeObject>;
  readonly onRemotePause: EventEmitter<UnsafeObject>;
  readonly onRemotePlay: EventEmitter<UnsafeObject>;
  readonly onRemotePlayId: EventEmitter<UnsafeObject>;
  readonly onRemotePlaySearch: EventEmitter<UnsafeObject>;
  readonly onRemotePrevious: EventEmitter<UnsafeObject>;
  readonly onRemoteSeek: EventEmitter<UnsafeObject>;
  readonly onRemoteSetRating: EventEmitter<UnsafeObject>;
  readonly onRemoteSkip: EventEmitter<UnsafeObject>;
  readonly onRemoteStop: EventEmitter<UnsafeObject>;
  readonly onOptionsChanged: EventEmitter<UnsafeObject>;

  // MARK: player api
  load(track: UnsafeObject): void;
  reset(): void;
  play(): void;
  pause(): void;
  stop(): void;
  setPlayWhenReady(playWhenReady: boolean): void;
  getPlayWhenReady(): boolean;
  seekTo(position: number): void;
  seekBy(offset: number): void;
  setVolume(level: number): void;
  getVolume(): number;
  setRate(rate: number): void;
  getRate(): number;
  getProgress(): UnsafeObject;
  getPlaybackState(): UnsafeObject;
  getPlayingState(): UnsafeObject;
  getRepeatMode(): string;
  setRepeatMode(mode: string): void;
  getPlaybackError(): UnsafeObject | null;
  retry(): void;

  // MARK: playlist management
  add(tracks: UnsafeObject[], insertBeforeIndex?: number): void;
  move(fromIndex: number, toIndex: number): void;
  remove(indexes: number[]): void;
  removeUpcomingTracks(): void;
  skip(index: number, initialPosition?: number): void;
  skipToNext(initialPosition?: number): void;
  skipToPrevious(initialPosition?: number): void;
  updateMetadataForTrack(trackIndex: number, metadata: UnsafeObject): void;
  updateNowPlayingMetadata(metadata: UnsafeObject): void;
  setQueue(tracks: UnsafeObject[]): void;
  getQueue(): UnsafeObject[];
  getTrack(index: number): UnsafeObject | undefined;
  getActiveTrackIndex(): number | undefined;
  getActiveTrack(): UnsafeObject | undefined;

  // MARK: Android methods
  acquireWakeLock(): void;
  abandonWakeLock(): void;

  // MARK: Media Browser Methods:
  readonly onGetItemRequest: EventEmitter<{ requestId: string; id: string }>;
  resolveGetItemRequest(id: string, item: UnsafeObject): void;

  readonly onGetChildrenRequest: EventEmitter<{
    requestId: string;
    id: string;
    page: number;
    pageSize: number;
  }>;
  resolveGetChildrenRequest(
    requestId: string,
    items: UnsafeObject[],
    totalChildrenCount: number,
  ): void;

  readonly onGetSearchResultRequest: EventEmitter<{
    requestId: string;
    query: string;
    extras?: UnsafeObject;
    page: number;
    pageSize: number;
  }>;
  resolveSearchResultRequest(
    requestId: string,
    items: UnsafeObject[],
    totalMatchesCount: number,
  ): void;

  // Signal that JS side is ready to receive media browser events
  setMediaBrowserReady(): void;
}

const module = TurboModuleRegistry.getEnforcing<Spec>('TrackPlayer');
export default module;
