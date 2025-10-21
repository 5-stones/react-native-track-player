import { useEffect, useState } from 'react';
import TrackPlayer from 'react-native-track-player';
import { playerOptions, tracks } from '../services';

export function useSetupPlayer() {
  const [playerReady, setPlayerReady] = useState(false);
  useEffect(() => {
    let unmounted = false;
    (async () => {
      try {
        await TrackPlayer.setupPlayer();
      } catch (error) {
        console.error('Error setting up player:', error);
        throw error;
      }
      TrackPlayer.updateOptions(playerOptions);
      TrackPlayer.setRepeatMode(playerOptions.repeatMode);
      if (unmounted) return;
      setPlayerReady(true);
      if (TrackPlayer.getQueue().length <= 0) {
        TrackPlayer.setQueue(tracks);
      }
    })();
    return () => {
      unmounted = true;
    };
  }, []);
  return playerReady;
}
