import type {
  AndroidAudioContentType,
  AppKilledPlaybackBehavior,
  Capability,
  IOSCategory,
  IOSCategoryMode,
  IOSCategoryOptions,
  RatingType,
} from '../constants';
import TrackPlayer from '../NativeTrackPlayer';

// MARK: - Types

export interface FeedbackOptions {
  /** Marks wether the option should be marked as active or "done" */
  isActive: boolean;

  /** The title to give the action (relevant for iOS) */
  title: string;
}

export interface AndroidOptions {
  /**
   * Whether the audio playback notification is also removed when the playback
   * stops. **If `stoppingAppPausesPlayback` is set to false, this will be
   * ignored.**
   */
  appKilledPlaybackBehavior?: AppKilledPlaybackBehavior;

  /**
   * https://developer.android.com/media/media3/exoplayer/track-selection#audioOffload
   */
  audioOffload?: boolean;

  /**
   * enables exoplayer's skipSilence parser
   * @default false
   */
  skipSilence?: boolean;

  /**
   * enables exoplayer's shuffle mode
   * @default false
   */
  shuffle?: boolean;

  /**
   * Maximum duration of media that the player will attempt to buffer in seconds.
   * Max buffer may not be lower than min buffer.
   *
   * @throws Will throw if max buffer is lower than min buffer.
   * @default 50
   */
  maxBuffer?: number;

  /**
   * Duration in seconds that should be kept in the buffer behind the current
   * playhead time.
   *
   * @default 0
   */
  backBuffer?: number;

  /**
   * Duration of media in seconds that must be buffered for playback to start or
   * resume following a user action such as a seek.
   *
   * @default 2.5
   */
  playBuffer?: number;

  /**
   * Duration of media in seconds that must be buffered for playback to resume
   * after a rebuffer (when the buffer runs empty during playback).
   *
   * When not specified, defaults to playBuffer * 1.6 (maintaining ExoPlayer's
   * default ratio). Should be >= playBuffer for optimal behavior.
   *
   * @default playBuffer * 1.6
   */
  rebufferBuffer?: number;

  /**
   * Maximum cache size in kilobytes.
   *
   * @default 0
   */
  maxCacheSize?: number;
}

export interface PlayerOptions {
  /**
   * Minimum duration of media that the player will attempt to buffer in seconds.
   *
   * Supported on Android & iOS.
   *
   * @throws Will throw on Android if min buffer is higher than max buffer.
   * @default 50
   */
  minBuffer?: number;

  /** Android-specific configuration options for setup */
  android?: AndroidOptions;
  /**
   * [AVAudioSession.Category](https://developer.apple.com/documentation/avfoundation/avaudiosession/1616615-category)
   * for iOS. Sets on `play()`.
   */
  iosCategory?: IOSCategory;
  /**
   * (iOS only) The audio session mode, together with the audio session category,
   * indicates to the system how you intend to use audio in your app. You can use
   * a mode to configure the audio system for specific use cases such as video
   * recording, voice or video chat, or audio analysis.
   * Sets on `play()`.
   *
   * See https://developer.apple.com/documentation/avfoundation/avaudiosession/1616508-mode
   */
  iosCategoryMode?: IOSCategoryMode;
  /**
   * [AVAudioSession.CategoryOptions](https://developer.apple.com/documentation/avfoundation/avaudiosession/1616503-categoryoptions) for iOS.
   * Sets on `play()`.
   */
  iosCategoryOptions?: IOSCategoryOptions[];
  /**
   * (Android only) The audio content type indicates to the android system how
   * you intend to use audio in your app.
   *
   * With `androidAudioContentType: AndroidAudioContentType.Speech`, the audio will be
   * paused during short interruptions, such as when a message arrives.
   * Otherwise the playback volume is reduced while the notification is playing.
   *
   * @default AndroidAudioContentType.Music
   */
  androidAudioContentType?: AndroidAudioContentType;
  /**
   * Indicates whether the player should automatically update now playing metadata data in control center / notification.
   * Defaults to `true`.
   */
  autoUpdateMetadata?: boolean;
}

export interface UpdateOptions {
  /** Android-specific configuration options */
  android?: AndroidOptions;

  /**
   * The rating type to use for ratings.
   * Determines how star ratings and thumbs up/down are handled.
   */
  ratingType?: RatingType;

  /**
   * Jump forward interval in seconds when using jump forward controls.
   * @default 15
   */
  forwardJumpInterval?: number;

  /**
   * Jump backward interval in seconds when using jump backward controls.
   * @default 15
   */
  backwardJumpInterval?: number;

  /**
   * How often progress events are emitted in seconds.
   * @default 1
   */
  progressUpdateEventInterval?: number;

  likeOptions?: FeedbackOptions;
  dislikeOptions?: FeedbackOptions;
  bookmarkOptions?: FeedbackOptions;

  capabilities?: Capability[];

  /**
   * Android-specific capabilities that control which buttons appear in
   * notifications only. This does NOT affect other controllers like
   * Bluetooth, Android Auto, or lock screen.
   *
   * When null/undefined, defaults to the global capabilities.
   * Use an empty array to show no notification buttons.
   *
   * @platform android
   */
  notificationCapabilities?: Capability[];
}

// MARK: - Functions

/**
 * Updates the configuration for the components.
 * @param options - The options to update.
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
