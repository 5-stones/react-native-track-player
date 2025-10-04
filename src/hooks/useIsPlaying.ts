import * as TrackPlayer from '../trackPlayer';
import { useUpdatedNativeValue } from './useUpdatedNativeValue';

/**
 * Tells whether the TrackPlayer is in a mode that most people would describe
 * as "playing." Great for UI to decide whether to show a Play or Pause button.
 * @returns playing - whether UI should likely show as Playing
 * @returns buffering - whether UI should show as Buffering
 */
export function useIsPlaying() {
  return useUpdatedNativeValue(
    TrackPlayer.getPlayingState,
    TrackPlayer.onPlaybackPlayingState
  );
}

/**
 * This exists if you need realtime status on whether the TrackPlayer is
 * playing, whereas the hooks all have a delay because they depend on responding
 * to events before their state is updated.
 *
 * It also exists whenever you need to know the play state outside of a React
 * component, since hooks only work in components.
 *
 * @returns playing - whether UI should likely show as Playing
 * @returns buffering - whether UI should show as Buffering
 */
export function isPlaying() {
  return TrackPlayer.getPlayingState();
}
