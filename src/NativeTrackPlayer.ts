import { type TurboModule, TurboModuleRegistry } from 'react-native';
import {
  type EventEmitter,
  type UnsafeObject,
} from 'react-native/Libraries/Types/CodegenTypes';

export interface Spec extends TurboModule {
  // init and config
  setupPlayer(options: UnsafeObject): Promise<void>;
  updateOptions(options: UnsafeObject): void;

  // events
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

  // player api
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
  getPlaybackError(): UnsafeObject | null;
  retry(): void;

  // playlist management
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
  setRepeatMode(mode: string): void;
  getRepeatMode(): string;
  getTrack(index: number): UnsafeObject | undefined;
  getActiveTrackIndex(): number | undefined;
  getActiveTrack(): UnsafeObject | undefined;

  // event listeners
  addListener(eventName: string): void;
  removeListeners(count: number): void;

  // android methods
  acquireWakeLock(): void;
  abandonWakeLock(): void;
  validateOnStartCommandIntent(): boolean;
}

const module = TurboModuleRegistry.getEnforcing<Spec>('TrackPlayer');
export default module;
