import { useEffect, useState } from 'react';

import TrackPlayer from '../NativeTrackPlayer';

// MARK: - Types

export interface Progress {
  /**
   * The playback position of the current track in seconds.
   * See https://rntp.dev/docs/api/functions/player#getposition
   **/
  position: number;
  /** The duration of the current track in seconds.
   * See https://rntp.dev/docs/api/functions/player#getduration
   **/
  duration: number;
  /**
   * The buffered position of the current track in seconds.
   **/
  buffered: number;
}

/**
 * Event data for playback progress updates.
 */
export interface PlaybackProgressUpdatedEvent extends Progress {
  /** The current track index */
  track: number;
}

// MARK: - Getters

/**
 * Gets information on the progress of the currently active track, including its
 * current playback position in seconds, buffered position in seconds and
 * duration in seconds.
 */
export function getProgress(): Progress {
  return TrackPlayer.getProgress() as Progress;
}

// MARK: - Event Callbacks

/**
 * Subscribes to playback progress updates.
 * @param callback - Called periodically with playback progress updates
 * @returns Cleanup function to unsubscribe
 */
export function onProgressUpdated(
  callback: (event: PlaybackProgressUpdatedEvent) => void
): () => void {
  return TrackPlayer.onPlaybackProgressUpdated(callback as () => void).remove;
}

// MARK: - Hooks

export interface UseProgressOptions {
  /** Update interval in milliseconds */
  updateInterval?: number;
}

/**
 * Hook that returns the current playback progress and updates periodically.
 * @param options - Configuration options
 * @returns The current playback progress
 */
export function useProgress(options?: UseProgressOptions): Progress {
  const [state, setState] = useState(getProgress);

  useEffect(() => {
    const unsubscribe = onProgressUpdated(setState);

    // Also poll for progress updates since events may not fire frequently enough
    const interval =
      options?.updateInterval ??
      (typeof options?.updateInterval === 'number' ? 0 : 1000);

    if (interval > 0) {
      const id = setInterval(() => {
        setState(getProgress());
      }, interval);

      return () => {
        unsubscribe();
        clearInterval(id);
      };
    }

    return unsubscribe;
  }, [options?.updateInterval]);

  return state;
}
