import { useEffect, useState } from 'react';

import { Event } from '../constants';
import { addEventListener, getPlayWhenReady } from '../trackPlayer';

export const usePlayWhenReady = () => {
  const [playWhenReady, setPlayWhenReady] = useState(() => getPlayWhenReady());
  useEffect(
    () =>
      addEventListener(Event.PlaybackPlayWhenReadyChanged, (event) => {
        setPlayWhenReady(event.playWhenReady);
      }).remove,
    []
  );

  return playWhenReady;
};
