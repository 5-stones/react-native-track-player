import { RepeatMode } from '../constants';
import TrackPlayer from '../NativeTrackPlayer';

// MARK: - Getters

/**
 * Gets the queue repeat mode.
 * @see https://rntp.dev/docs/api/constants/repeat-mode
 */
export function getRepeatMode(): RepeatMode {
  return TrackPlayer.getRepeatMode() as RepeatMode;
}

// MARK: - Setters

/**
 * Sets the queue repeat mode.
 * @param mode - The repeat mode to set.
 * @see https://rntp.dev/docs/api/constants/repeat-mode
 */
export function setRepeatMode(mode: RepeatMode): void {
  TrackPlayer.setRepeatMode(mode);
}

// Re-export RepeatMode enum for convenience
export { RepeatMode };
