import { useState } from 'react';

import { Event } from '../constants';
import type { Track } from '../interfaces/Track';
import { getActiveTrack } from '../trackPlayer';
import { useTrackPlayerEvents } from './useTrackPlayerEvents';

export function useActiveTrack(): Track | undefined {
  const [track, setTrack] = useState(() => getActiveTrack());

  useTrackPlayerEvents([Event.PlaybackActiveTrackChanged], (event) => {
    setTrack(event.track);
  });
  return track;
}
