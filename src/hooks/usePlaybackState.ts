import { useEffect, useState } from 'react';

import { Event } from '../constants';
import type { PlaybackState } from '../interfaces';
import { addEventListener, getPlaybackState } from '../trackPlayer';

/**
 * Get current playback state and subsequent updates.
 *
 * Note: While it is fetching the initial state from the native module, the
 * returned state property will be `undefined`.
 * */
export const usePlaybackState = (): PlaybackState => {
  const [playbackState, setPlaybackState] = useState(() => getPlaybackState());
  useEffect(
    () => addEventListener(Event.PlaybackState, setPlaybackState).remove,
    []
  );

  return playbackState;
};
