import { useEffect, useState } from 'react';
import TrackPlayer, { AppKilledPlaybackBehavior, Capability } from 'react-native-track-player';
import { tracks } from '../services';

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
      TrackPlayer.updateOptions({
        android: {
          appKilledPlaybackBehavior:
            AppKilledPlaybackBehavior.StopPlaybackAndRemoveNotification,
          notificationCapabilities: [
            Capability.Play,
            Capability.Pause,
            Capability.SeekTo,
            Capability.SkipToNext,
            Capability.SkipToPrevious,
          ],
        },
        capabilities: [
          Capability.Play,
          Capability.Pause,
          Capability.SkipToNext,
          Capability.SkipToPrevious,
          Capability.SeekTo,
          Capability.JumpBackward,
          Capability.JumpForward,
        ],
        progressUpdateEventInterval: 2
      });
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
