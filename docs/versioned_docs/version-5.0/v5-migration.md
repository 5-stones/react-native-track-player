---
sidebar_position: 9
---

# Migrating from v4 to v5

### General Additions

### General Changes

- `androidAudioContentType` behavior: With the removal of `autoHandleInterruptions`, the `androidAudioContentType` option now directly controls Android's audio focus behavior. When set to `AndroidAudioContentType.Speech`, audio will be paused during short interruptions (like message notifications). When set to `AndroidAudioContentType.Music` (default), the playback volume is reduced while notifications play.

- **iOS category policy restriction**: The `IOSCategoryPolicy.Independent` option has been removed as Apple's documentation states "Apps shouldn't try to set this value directly" - it's intended for system use only. Use `IOSCategoryPolicy.Default` or `IOSCategoryPolicy.LongFormAudio` instead.

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
    androidAudioContentType: AndroidAudioContentType.Music,
    iosCategory: IOSCategory.Playback,
    iosCategoryMode: IOSCategoryMode.Default,
    iosCategoryOptions: [IOSCategoryOptions.AllowBluetooth],
    android: {
      // other Android options
    }
  });

  // updateOptions
  TrackPlayer.updateOptions({
    capabilities: [Capability.Play, Capability.Pause],
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
      audioContentType: AndroidAudioContentType.Music,
      // other Android options
    },
    ios: {
      category: IOSCategory.Playback,
      categoryMode: IOSCategoryMode.Default,
      categoryOptions: [IOSCategoryOptions.AllowBluetooth],
      // other iOS options
    }
  });

  // updateOptions
  TrackPlayer.updateOptions({
    capabilities: [Capability.Play, Capability.Pause],
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
// options.capabilities === [Capability.Play, Capability.Pause, Capability.SkipToNext, Capability.SkipToPrevious, Capability.SeekTo]
```

**Migration**: If you specifically want no capabilities (unusual), explicitly set an empty array:
```typescript
TrackPlayer.updateOptions({ capabilities: [] });
```

**`notificationCapabilities` moved to Android namespace**: The `notificationCapabilities` option has been moved from the top-level `updateOptions()` to the Android-specific namespace where it belongs.

**Before (v4):**
```typescript
TrackPlayer.updateOptions({
  capabilities: [Capability.Play, Capability.Pause],
  notificationCapabilities: [Capability.Play, Capability.Pause, Capability.SkipToNext], // top-level
});
```

**After (v5):**
```typescript
TrackPlayer.updateOptions({
  capabilities: [Capability.Play, Capability.Pause],
  android: {
    notificationCapabilities: [Capability.Play, Capability.Pause, Capability.SkipToNext], // moved to android.*
  }
});
```

### Hook Behavior Updates

### Player Method Updates

### Player State Updates

### General Deprecations

### Event Listener Pattern Changes

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
  TrackPlayer.addEventListener(Event.RemotePause, () => {
    TrackPlayer.pause();
  });
  
  TrackPlayer.addEventListener(Event.RemotePlay, () => {
    TrackPlayer.play();
  });
  
  // other event listeners...
}
```

**Migration steps:**
1. Remove `TrackPlayer.registerPlaybackService()` call from your index file
2. Create a function to install event listeners directly
3. Call this function early in your app initialization (typically in App.tsx)
4. Move your event listener logic from the playback service to the new listener installation function

See the example app for a complete implementation of the new pattern.

### Removals

1. `registerPlaybackService()` - Event listeners should now be installed directly in your app. See [Event Listener Pattern Changes](#event-listener-pattern-changes) above.
2. `getState()` - Please use the `state` property returned by [`getPlaybackState()`](./api/functions/player.md#getplaybackstate).
3. `getDuration()` -  Please use the `duration` property returned by [`getProgress()`](./api/functions/player.md#getprogress).
4. `getPosition()` -  Please use the `position` property returned by [`getProgress()`](./api/functions/player.md#getprogress).
5. `getBufferedPosition()` -  Please use the `buffered` property returned by [`getProgress()`](./api/functions/player.md#getprogress).
6. `getCurrentTrack()` - Please use [`getActiveTrackIndex()`](./api/functions/queue.md#getactivetrackindex).
7. `Event.PlaybackTrackChanged` - Please use [`Event.PlaybackActiveTrackChanged`](./api/events.md#playbackactivetrackchanged). Also note that in 4.0 `Event.PlaybackTrackChanged` is no longer emitted when a track repeats.
8. `autoHandleInterruptions` player option - This option has been removed. Audio interruption handling is now managed automatically by the system. If you were previously using this option, simply remove it from your `setupPlayer()` call.
9. `Event.RemoteDuck` - This event has been removed as audio interruptions are now handled automatically by the system. If you were previously listening to this event, you can remove the event listener.
10. `color` update option - This option has been removed from `UpdateOptions`. If you were previously using this option to customize notification colors, you will need to use alternative styling approaches.
11. `compactCapabilities` update option - This option has been removed from `UpdateOptions`. Use the `notificationCapabilities` option instead to configure Android notification controls.
12. Custom icon update options - The following custom icon options have been removed from `UpdateOptions`: `playIcon`, `pauseIcon`, `stopIcon`, `previousIcon`, `nextIcon`, `rewindIcon`, `forwardIcon`. Custom notification icons are no longer supported.
