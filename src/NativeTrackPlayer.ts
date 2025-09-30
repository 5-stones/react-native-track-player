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
  readonly onAndroidPlaybackResume: EventEmitter<UnsafeObject>;
  readonly onMetadataChapterReceived: EventEmitter<UnsafeObject>;
  readonly onMetadataCommonReceived: EventEmitter<UnsafeObject>;
  readonly onMetadataTimedReceived: EventEmitter<UnsafeObject>;
  readonly onPlaybackActiveTrackChanged: EventEmitter<UnsafeObject>;
  readonly onPlaybackError: EventEmitter<UnsafeObject>;
  readonly onPlaybackMetadata: EventEmitter<UnsafeObject>;
  readonly onPlaybackPlayWhenReadyChanged: EventEmitter<UnsafeObject>;
  readonly onPlaybackProgressUpdated: EventEmitter<UnsafeObject>;
  readonly onPlaybackQueueEnded: EventEmitter<UnsafeObject>;
  readonly onPlaybackState: EventEmitter<UnsafeObject>;
  readonly onRemoteBookmark: EventEmitter<UnsafeObject>;
  readonly onRemoteDislike: EventEmitter<UnsafeObject>;
  readonly onRemoteDuck: EventEmitter<UnsafeObject>;
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
  retry(): void;

  // playlist management
  add(tracks: UnsafeObject[], insertBeforeIndex?: number): number;
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
  setRepeatMode(mode: number): void;
  getRepeatMode(): number;
  getTrack(index: number): UnsafeObject | undefined;
  getActiveTrackIndex(): number | undefined;
  getActiveTrack(): UnsafeObject | undefined;

  // event listeners
  addListener(eventName: string): void;
  removeListeners(count: number): void;

  // constants
  getConstants: () => {
    // Capabilities
    CAPABILITY_PLAY: number;
    CAPABILITY_PLAY_FROM_ID: number;
    CAPABILITY_PLAY_FROM_SEARCH: number;
    CAPABILITY_PAUSE: number;
    CAPABILITY_STOP: number;
    CAPABILITY_SEEK_TO: number;
    CAPABILITY_SKIP: number;
    CAPABILITY_SKIP_TO_NEXT: number;
    CAPABILITY_SKIP_TO_PREVIOUS: number;
    CAPABILITY_SET_RATING: number;
    CAPABILITY_JUMP_FORWARD: number;
    CAPABILITY_JUMP_BACKWARD: number;

    // States
    STATE_NONE: string;
    STATE_READY: string;
    STATE_PLAYING: string;
    STATE_PAUSED: string;
    STATE_STOPPED: string;
    STATE_BUFFERING: string;
    STATE_LOADING: string;

    // Rating Types
    RATING_HEART: number;
    RATING_THUMBS_UP_DOWN: number;
    RATING_3_STARS: number;
    RATING_4_STARS: number;
    RATING_5_STARS: number;
    RATING_PERCENTAGE: number;

    // Repeat Modes
    REPEAT_OFF: number;
    REPEAT_TRACK: number;
    REPEAT_QUEUE: number;

    // Pitch Algorithms - iOS
    PITCH_ALGORITHM_LINEAR: number;
    PITCH_ALGORITHM_MUSIC: number;
    PITCH_ALGORITHM_VOICE: number;
  };

  // android methods
  acquireWakeLock(): void;
  abandonWakeLock(): void;
  validateOnStartCommandIntent(): boolean;
}

const module = TurboModuleRegistry.getEnforcing<Spec>('TrackPlayer');
export const Constants = module?.getConstants();
export default module;
