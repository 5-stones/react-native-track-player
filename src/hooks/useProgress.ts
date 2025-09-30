import { useEffect, useState } from 'react';

import { Event } from '../constants';
import { getProgress } from '../trackPlayer';
import { useTrackPlayerEvents } from './useTrackPlayerEvents';

/**
 * Poll for track progress for the given interval (in miliseconds)
 * @param updateInterval - ms interval
 */
export function useProgress(updateInterval = 1000) {
  const [state, setState] = useState(() => getProgress());
  useTrackPlayerEvents([Event.PlaybackActiveTrackChanged], () => {
    setState(getProgress());
  });

  useEffect(() => {
    const update = () => {
      try {
        const { position, duration, buffered } = getProgress();

        setState((currentState) =>
          position === currentState.position &&
          duration === currentState.duration &&
          buffered === currentState.buffered
            ? currentState
            : { position, duration, buffered }
        );
      } catch {
        // these method only throw while you haven't yet setup, ignore failure.
      }
    };

    const interval = setInterval(update, updateInterval);

    return () => {
      clearInterval(interval);
    };
  }, [updateInterval]);

  return state;
}
