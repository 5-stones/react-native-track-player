import { Event } from '../constants';
import { useTrackPlayerEvents } from './useTrackPlayerEvents';

/**
 * Logs all TrackPlayer events to the console for debugging purposes
 */
export function useDebugPlayer() {
  useTrackPlayerEvents(Object.values(Event), ({ type, ...event }) => {
    console.debug(`Event: ${type}`, event);
  });
}
