import type { AppKilledPlaybackBehavior } from '../constants';

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
}
