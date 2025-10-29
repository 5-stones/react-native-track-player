import TrackPlayer from 'react-native-track-player';

export async function installListeners() {
  TrackPlayer.onRemotePause(() => {
    console.log('Event.RemotePause');
    TrackPlayer.pause();
  });

  TrackPlayer.onRemotePlay(() => {
    console.log('Event.RemotePlay');
    TrackPlayer.play();
  });

  TrackPlayer.onRemoteNext(() => {
    console.log('Event.RemoteNext');
    TrackPlayer.skipToNext();
  });

  TrackPlayer.onRemotePrevious(() => {
    console.log('Event.RemotePrevious');
    TrackPlayer.skipToPrevious();
  });

  TrackPlayer.onRemoteJumpForward((event) => {
    console.log('Event.RemoteJumpForward', event);
    TrackPlayer.seekBy(event.interval);
  });

  TrackPlayer.onRemoteJumpBackward((event) => {
    console.log('Event.RemoteJumpBackward', event);
    TrackPlayer.seekBy(-event.interval);
  });

  TrackPlayer.onRemoteSeek((event) => {
    console.log('Event.RemoteSeek', event);
    TrackPlayer.seekTo(event.position);
  });

  TrackPlayer.onQueueEnded((event) => {
    console.log('onQueueEnded', event);
  });

  TrackPlayer.onActiveTrackChanged((event) => {
    console.log('onActiveTrackChanged', event);
  });

  TrackPlayer.onProgressUpdated((event) => {
    console.log('onProgressUpdated', event);
  });

  TrackPlayer.onPlayWhenReadyChanged((event) => {
    console.log('onPlayWhenReadyChanged', event);
  });

  TrackPlayer.onPlaybackState((event) => {
    console.log('onPlaybackState', event);
  });

  TrackPlayer.onMetadataChapterReceived((event) => {
    console.log('onMetadataChapterReceived', event);
  });

  TrackPlayer.onMetadataTimedReceived((event) => {
    console.log('onMetadataTimedReceived', event);
  });

  TrackPlayer.onMetadataCommonReceived((event) => {
    console.log('onMetadataCommonReceived', event);
  });
}
