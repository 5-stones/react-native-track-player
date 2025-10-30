---
sidebar_position: 9
---

# Migrating from v4 to v5

### General Additions

### General Changes

**Enums converted to union types**: All enums have been converted to string union types for better tree-shaking and bundle size optimization. This is a breaking change that requires updating enum property access to string literals.

**Before (v4):**
```typescript
import { RepeatMode, State, Capability } from 'react-native-track-player';

// Enum property access
TrackPlayer.setRepeatMode(RepeatMode.Track);
const isPlaying = state === State.Playing;
const capabilities = [Capability.Play, Capability.Pause];
```

**After (v5):**
```typescript
import type { RepeatMode, State, Capability } from 'react-native-track-player';

// String literal values
TrackPlayer.setRepeatMode('track');
const isPlaying = state === 'playing';
const capabilities = ['play', 'pause'];
```

**Migration**: Replace all enum property access with their corresponding string literal values:
- `RepeatMode.Off` → `'off'`
- `RepeatMode.Track` → `'track'`
- `RepeatMode.Queue` → `'queue'`
- `State.Playing` → `'playing'`
- `State.Paused` → `'paused'`
- `State.Error` → `'error'`
- `Capability.Play` → `'play'`
- `Capability.Pause` → `'pause'`
- `Capability.SkipToNext` → `'skip-to-next'`
- And so on for all enum values...

Note: Types can now be imported as `type` imports since they're no longer runtime values.

- `androidAudioContentType` behavior: With the removal of `autoHandleInterruptions`, the `androidAudioContentType` option now directly controls Android's audio focus behavior. When set to `'speech'`, audio will be paused during short interruptions (like message notifications). When set to `'music'` (default), the playback volume is reduced while notifications play.

- **iOS category policy restriction**: The `'independent'` option has been removed as Apple's documentation states "Apps shouldn't try to set this value directly" - it's intended for system use only. Use `'default'` or `'longFormAudio'` instead.

- **Platform options moved to namespaces**: Platform-specific configuration options have been moved under their respective namespaces for better organization and consistency.

  **Android options moved to `android.*` namespace:**
  - `maxBuffer` → `android.maxBuffer`
  - `playBuffer` → `android.playBuffer`
  - `rebufferBuffer` → `android.rebufferBuffer`
  - `backBuffer` → `android.backBuffer`
  - `maxCacheSize` → `android.maxCacheSize`
  - `androidAudioContentType` → `android.audioContentType`

  **iOS options moved to `ios.*` namespace:**
  - `iosCategory` → `ios.category`
  - `iosCategoryMode` → `ios.categoryMode`
  - `iosCategoryOptions` → `ios.categoryOptions`
  - `likeOptions` → `ios.likeOptions`
  - `dislikeOptions` → `ios.dislikeOptions`
  - `bookmarkOptions` → `ios.bookmarkOptions`

  **Before (v4):**
  ```javascript
  // setupPlayer
  await TrackPlayer.setupPlayer({
    maxBuffer: 50,
    playBuffer: 2.5,
    backBuffer: 0,
    androidAudioContentType: 'music',
    iosCategory: 'playback',
    iosCategoryMode: 'default',
    iosCategoryOptions: ['allowBluetooth'],
    android: {
      // other Android options
    }
  });

  // updateOptions
  TrackPlayer.updateOptions({
    capabilities: ['play', 'pause'],
    likeOptions: { isActive: false, title: 'Like' },
    dislikeOptions: { isActive: false, title: 'Dislike' },
    bookmarkOptions: { isActive: false, title: 'Bookmark' }
  });
  ```

  **After (v5):**
  ```javascript
  // setupPlayer
  await TrackPlayer.setupPlayer({
    android: {
      maxBuffer: 50,
      playBuffer: 2.5,
      backBuffer: 0,
      audioContentType: 'music',
      // other Android options
    },
    ios: {
      category: 'playback',
      categoryMode: 'default',
      categoryOptions: ['allowBluetooth'],
      // other iOS options
    }
  });

  // updateOptions
  TrackPlayer.updateOptions({
    capabilities: ['play', 'pause'],
    ios: {
      likeOptions: { isActive: false, title: 'Like' },
      dislikeOptions: { isActive: false, title: 'Dislike' },
      bookmarkOptions: { isActive: false, title: 'Bookmark' }
    }
  });
  ```

### Options API Updates

**Default capabilities changed**: The default `capabilities` array has changed from empty (`[]`) to include basic playback controls. This provides a better out-of-the-box experience.

**Before (v4):**
```typescript
// Default capabilities were empty - no controls visible
const options = TrackPlayer.getOptions();
// options.capabilities === []
```

**After (v5):**
```typescript
// Default capabilities include basic playback controls
const options = TrackPlayer.getOptions();
// options.capabilities === ['play', 'pause', 'skip-to-next', 'skip-to-previous', 'seek-to']
```

**Migration**: If you specifically want no capabilities (unusual), explicitly set an empty array:
```typescript
TrackPlayer.updateOptions({ capabilities: [] });
```

**`notificationCapabilities` moved to Android namespace**: The `notificationCapabilities` option has been moved from the top-level `updateOptions()` to the Android-specific namespace where it belongs.

**Before (v4):**
```typescript
TrackPlayer.updateOptions({
  capabilities: ['play', 'pause'],
  notificationCapabilities: ['play', 'pause', 'skip-to-next'], // top-level
});
```

**After (v5):**
```typescript
TrackPlayer.updateOptions({
  capabilities: ['play', 'pause'],
  android: {
    notificationCapabilities: ['play', 'pause', 'skip-to-next'], // moved to android.*
  }
});
```

### Hook Behavior Updates

### Player Method Updates

### Player State Updates

### General Deprecations

### Event Listener Pattern Changes

**Automatic remote control handlers**: Remote controls now work automatically with sane default behavior. You no longer need to manually install basic remote control listeners - they are set up automatically when the module loads.

**New override system**: Use `handleRemote*` functions to override default behavior and `onRemote*` functions for listening/debugging without affecting the default behavior.

**Before (v4):**
```typescript
// Required manual setup for basic remote controls
export function installListeners() {
  TrackPlayer.onRemotePlay(() => {
    TrackPlayer.play();
  });
  
  TrackPlayer.onRemotePause(() => {
    TrackPlayer.pause();
  });
  
  TrackPlayer.onRemoteNext(() => {
    TrackPlayer.skipToNext();
  });
  
  // All remote controls required manual setup
}
```

**After (v5):**
```typescript
// Remote controls work automatically - no setup required!
// Only override if you need custom behavior:

export function installListeners() {
  // Override default behavior when needed
  TrackPlayer.handleRemotePause(() => {
    console.log('Custom pause logic');
    TrackPlayer.pause();
  });
  
  // Listen for debugging without affecting behavior
  TrackPlayer.onRemotePlay(() => {
    console.log('Play button pressed');
    // Default TrackPlayer.play() still happens automatically
  });
  
  // Most remote controls work automatically:
  // - Play/Pause: TrackPlayer.play()/pause()
  // - Next/Previous: TrackPlayer.skipToNext()/skipToPrevious()
  // - Seek: TrackPlayer.seekTo()
  // - Jump Forward/Backward: TrackPlayer.seekBy()
  // - Stop: TrackPlayer.stop()
}
```

**Migration**: Remove manual remote control setup for basic functionality. Only use `handleRemote*` functions if you need custom behavior beyond the defaults.

**`registerPlaybackService` removed**: The playback service pattern has been replaced with direct event listener installation. Event listeners should now be set up directly in your app initialization rather than in a separate service.

**Before (v4):**
```typescript
// index.js
import TrackPlayer from 'react-native-track-player';
import { PlaybackService } from './src/services';

AppRegistry.registerComponent(appName, () => App);
TrackPlayer.registerPlaybackService(() => PlaybackService);

// PlaybackService.ts
export async function PlaybackService() {
  TrackPlayer.addEventListener(Event.RemotePause, () => {
    TrackPlayer.pause();
  });
  
  TrackPlayer.addEventListener(Event.RemotePlay, () => {
    TrackPlayer.play();
  });
  
  // other event listeners...
}
```

**After (v5):**
```typescript
// index.js
import { AppRegistry } from 'react-native';
import App from './src/App';

AppRegistry.registerComponent(appName, () => App);

// App.tsx or listeners.ts
import { installListeners } from './listeners';

installListeners(); // Call early in app initialization

// listeners.ts
export function installListeners() {
  // Remote controls work automatically now!
  // Only add listeners for non-remote events or custom behavior:
  
  TrackPlayer.onQueueEnded(() => {
    console.log('Queue ended');
  });
  
  TrackPlayer.onActiveTrackChanged(() => {
    console.log('Track changed');
  });
  
  // Override remote controls only if you need custom behavior:
  TrackPlayer.handleRemotePause(() => {
    console.log('Custom pause logic');
    TrackPlayer.pause();
  });
}
```

**`addEventListener` deprecated**: The generic `addEventListener` method is deprecated in favor of specific event handler methods. Use `onRemote*`, `onPlaybackState`, `onActiveTrackChanged`, etc. instead.

**Before (v4):**
```typescript
TrackPlayer.addEventListener(Event.RemotePause, () => {
  TrackPlayer.pause();
});

TrackPlayer.addEventListener(Event.PlaybackState, (event) => {
  console.log('Playback state:', event.state);
});
```

**After (v5):**
```typescript
// Use specific event handlers instead
TrackPlayer.onRemotePause(() => {
  TrackPlayer.pause();
});

TrackPlayer.onPlaybackState((event) => {
  console.log('Playback state:', event.state);
});
```

**Migration steps:**
1. Remove `TrackPlayer.registerPlaybackService()` call from your index file
2. Replace `addEventListener` calls with specific `on*` event handler methods
3. Remove basic remote control listeners (play, pause, next, previous, seek, jump, stop) - they work automatically now
4. For custom remote control behavior, replace `onRemote*` calls with `handleRemote*` calls
5. Keep `onRemote*` calls only for debugging/logging purposes
6. Move remaining non-remote event listeners to a function called early in your app initialization

See the example app for a complete implementation of the new pattern.

### Hook Migration

**`useTrackPlayerEvents` removed**: The generic `useTrackPlayerEvents` hook has been removed in favor of specific, type-safe hooks for each event type.

**Before (v4):**
```typescript
import { useTrackPlayerEvents, Event } from 'react-native-track-player';

function MyComponent() {
  const [trackTitle, setTrackTitle] = useState('');
  const [isPlaying, setIsPlaying] = useState(false);
  
  useTrackPlayerEvents([Event.PlaybackState, Event.PlaybackActiveTrackChanged], (event) => {
    if (event.type === Event.PlaybackState) {
      setIsPlaying(event.state === 'playing');
    } else if (event.type === Event.PlaybackActiveTrackChanged) {
      setTrackTitle(event.track?.title || '');
    }
  });
  
  return <Text>{isPlaying ? 'Playing' : 'Paused'}: {trackTitle}</Text>;
}
```

**After (v5):**
```typescript
import { usePlaybackState, useActiveTrack } from 'react-native-track-player';
import type { State } from 'react-native-track-player';

function MyComponent() {
  const playbackState = usePlaybackState();
  const activeTrack = useActiveTrack();
  
  const isPlaying = playbackState.state === 'playing';
  const trackTitle = activeTrack?.title || '';
  
  return <Text>{isPlaying ? 'Playing' : 'Paused'}: {trackTitle}</Text>;
}
```

**Available specific hooks:**
- `usePlaybackState()` - Current playback state (playing, paused, etc.)
- `useActiveTrack()` - Currently active track
- `useProgress()` - Playback progress (position, duration, buffered)
- `usePlayWhenReady()` - Play when ready state
- `useVolume()` - Current volume level
- `useRate()` - Current playback rate

**For other events, use direct event handlers:**
```typescript
import { useEffect } from 'react';
import TrackPlayer from 'react-native-track-player';

function MyComponent() {
  useEffect(() => {
    // Event handlers return a cleanup function directly
    const unsubscribe = TrackPlayer.onQueueEnded(() => {
      console.log('Queue ended');
    });
    
    return unsubscribe; // Call the returned function to unsubscribe
  }, []);
  
  // Or handle multiple events
  useEffect(() => {
    const unsubscribe1 = TrackPlayer.onPlaybackError((error) => {
      console.error('Playback error:', error);
    });
    
    const unsubscribe2 = TrackPlayer.onRemotePlay(() => {
      console.log('Remote play pressed');
    });
    
    return () => {
      unsubscribe1();
      unsubscribe2();
    };
  }, []);
}
```

**Migration steps:**
1. Replace `useTrackPlayerEvents` with specific hooks for common events
2. Use direct event handlers (`TrackPlayer.on*`) for events without dedicated hooks  
3. Remove `Event` enum imports - no longer needed with specific hooks

### Removals

1. `registerPlaybackService()` - Event listeners should now be installed directly in your app. See [Event Listener Pattern Changes](#event-listener-pattern-changes) above.
2. `addEventListener()` - **Deprecated**. Use specific event handler methods (`onRemote*`, `onPlaybackState`, etc.) instead. See [Event Listener Pattern Changes](#event-listener-pattern-changes) above.
3. `useTrackPlayerEvents()` - Use specific hooks and event handlers instead. See [Hook Migration](#hook-migration) below.
4. `getState()` - Please use the `state` property returned by [`getPlaybackState()`](./api/functions/player.md#getplaybackstate).
5. `getDuration()` -  Please use the `duration` property returned by [`getProgress()`](./api/functions/player.md#getprogress).
6. `getPosition()` -  Please use the `position` property returned by [`getProgress()`](./api/functions/player.md#getprogress).
7. `getBufferedPosition()` -  Please use the `buffered` property returned by [`getProgress()`](./api/functions/player.md#getprogress).
8. `getCurrentTrack()` - Please use [`getActiveTrackIndex()`](./api/functions/queue.md#getactivetrackindex).
9. `Event.PlaybackTrackChanged` - Please use [`Event.PlaybackActiveTrackChanged`](./api/events.md#playbackactivetrackchanged). Also note that in 4.0 `Event.PlaybackTrackChanged` is no longer emitted when a track repeats.
10. `autoHandleInterruptions` player option - This option has been removed. Audio interruption handling is now managed automatically by the system. If you were previously using this option, simply remove it from your `setupPlayer()` call.
11. `Event.RemoteDuck` - This event has been removed as audio interruptions are now handled automatically by the system. If you were previously listening to this event, you can remove the event listener.
12. `color` update option - This option has been removed from `UpdateOptions`. If you were previously using this option to customize notification colors, you will need to use alternative styling approaches.
13. `compactCapabilities` update option - This option has been removed from `UpdateOptions`. Use the `notificationCapabilities` option instead to configure Android notification controls.
14. Custom icon update options - The following custom icon options have been removed from `UpdateOptions`: `playIcon`, `pauseIcon`, `stopIcon`, `previousIcon`, `nextIcon`, `rewindIcon`, `forwardIcon`. Custom notification icons are no longer supported.
