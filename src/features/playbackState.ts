import { State } from '../constants';
import { useUpdatedNativeValue } from '../hooks/useUpdatedNativeValue';
import TrackPlayer from '../NativeTrackPlayer';
import type { PlaybackErrorEvent } from './errors';

// MARK: - Types

export type PlaybackState =
  | {
      state: Exclude<State, State.Error>;
    }
  | {
      state: State.Error;
      error: PlaybackErrorEvent;
    };

// MARK: - Getters

/**
 * Gets the playback state of the player.
 * @see https://rntp.dev/docs/api/constants/state
 */
export function getPlaybackState(): PlaybackState {
  return TrackPlayer.getPlaybackState() as PlaybackState;
}

// MARK: - Event Callbacks

/**
 * Subscribes to playback state changes.
 * @param callback - Called when the playback state changes
 * @returns Cleanup function to unsubscribe
 */
export function onPlaybackState(
  callback: (state: PlaybackState) => void
): () => void {
  return TrackPlayer.onPlaybackState(callback as () => void).remove;
}

// MARK: - Hooks

/**
 * Hook that returns the current playback state and updates when it changes.
 * @returns The current playback state
 */
export function usePlaybackState(): PlaybackState {
  return useUpdatedNativeValue(getPlaybackState, onPlaybackState);
}
