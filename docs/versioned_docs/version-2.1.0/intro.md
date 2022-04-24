---
sidebar_position: 1
---

# Intro

A fully fledged audio module created for music apps. Provides audio playback, external media controls, background mode and more!

## Features

* **Lightweight** - Optimized to use the least amount of resources according to your needs
* **Feels native** - As everything is built together, it follows the same design principles as real music apps do
* **Multi-platform** - Supports Android, iOS and Windows
* **Media Controls support** - Provides events for controlling the app from a bluetooth device, the lockscreen, a notification, a smartwatch or even a car
* **Local or network, files or streams** - It doesn't matter where the media belongs, we've got you covered
* **Adaptive bitrate streaming support** - Support for DASH, HLS or SmoothStreaming
* **Caching support** - Cache media files to play them again without an internet connection
* **Background support** - Keep playing audio even after the app is in background
* **Fully Customizable** - Even the notification icons are customizable!
* **Supports React Hooks 🎣** - Includes React Hooks for common use-cases so you don't have to write them
* **Casting support** - Use in combination with [react-native-track-casting (WIP)](https://github.com/react-native-kit/react-native-track-casting) to seamlessly switch to any Google Cast compatible device that supports custom media receivers

## Why another music module?
After trying to team up modules like `react-native-sound`, `react-native-music-controls` and `react-native-google-cast`, I've noticed, that their structure and the way should be tied together can cause a lot of problems (mainly on Android). Those can heavily affect the app stability and user experience.

All audio modules (like `react-native-sound`) don't play in a separated service on Android, which should **only** be used for simple audio tracks in the foreground (such as sound effects, voice messages, etc.)

`react-native-music-controls` is meant for apps using those audio modules, but it has a few problems: the audio isn't tied directly to the controls. It can be pretty useful for casting (such as Chromecast).

`react-native-google-cast` works pretty well and also supports custom receivers, but it has fewer player controls, it's harder to integrate and still uses the Cast SDK v2.

## Example

If you want to get started with this module, check the [Installation](./basics/installation.mdx) & [Getting Started](./basics/getting-started.md) page.
If you want detailed information about the API, check the [API Reference](./api/functions/lifecycle.md).
You can also look at our example project [here](https://github.com/doublesymmetry/react-native-track-player/tree/master/example).

```javascript
import TrackPlayer, { RepeatMode } from 'react-native-track-player';

// Creates the player
const setup = async () => {
  await TrackPlayer.setupPlayer({});

  await TrackPlayer.add({
    url: require('track.mp3'),
    title: 'Track Title',
    artist: 'Track Artist',
    artwork: require('track.png')
  });

  TrackPlayer.setRepeatMode(RepeatMode.Queue);
};
```
