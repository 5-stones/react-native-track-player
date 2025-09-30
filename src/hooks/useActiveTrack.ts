import { useState } from 'react';

import { Event } from '../constants';
import { getActiveTrack } from '../trackPlayer';
import type { Track } from '../types/Track';
import { useTrackPlayerEvents } from './useTrackPlayerEvents';

export function useActiveTrack(): Track | undefined {
  const [track, setTrack] = useState(() => getActiveTrack());

  useTrackPlayerEvents([Event.PlaybackActiveTrackChanged], (event) => {
    setTrack(event.track);
  });
  return track;
}
