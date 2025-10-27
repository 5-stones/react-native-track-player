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

### Hook Behavior Updates

### Player Method Updates

### Player State Updates

### General Deprecations

### Removals

1. `getState()` - Please use the `state` property returned by [`getPlaybackState()`](./api/functions/player.md#getplaybackstate).
2. `getDuration()` -  Please use the `duration` property returned by [`getProgress()`](./api/functions/player.md#getprogress).
3. `getPosition()` -  Please use the `position` property returned by [`getProgress()`](./api/functions/player.md#getprogress).
4. `getBufferedPosition()` -  Please use the `buffered` property returned by [`getProgress()`](./api/functions/player.md#getprogress).
5. `getCurrentTrack()` - Please use [`getActiveTrackIndex()`](./api/functions/queue.md#getactivetrackindex).
6. `Event.PlaybackTrackChanged` - Please use [`Event.PlaybackActiveTrackChanged`](./api/events.md#playbackactivetrackchanged). Also note that in 4.0 `Event.PlaybackTrackChanged` is no longer emitted when a track repeats.
7. `autoHandleInterruptions` player option - This option has been removed. Audio interruption handling is now managed automatically by the system. If you were previously using this option, simply remove it from your `setupPlayer()` call.
8. `Event.RemoteDuck` - This event has been removed as audio interruptions are now handled automatically by the system. If you were previously listening to this event, you can remove the event listener.
9. `color` update option - This option has been removed from `UpdateOptions`. If you were previously using this option to customize notification colors, you will need to use alternative styling approaches.
10. `compactCapabilities` update option - This option has been removed from `UpdateOptions`. Use the `notificationCapabilities` option instead to configure Android notification controls.
11. Custom icon update options - The following custom icon options have been removed from `UpdateOptions`: `playIcon`, `pauseIcon`, `stopIcon`, `previousIcon`, `nextIcon`, `rewindIcon`, `forwardIcon`. Custom notification icons are no longer supported.
