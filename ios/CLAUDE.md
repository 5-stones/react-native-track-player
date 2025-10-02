# iOS Development Guide

## After Adding/Removing Files

When you add or remove Swift/Objective-C files in the iOS codebase, you need to reinstall CocoaPods:

```bash
cd example/ios
pod install
```

Then build the project:

```bash
cd example
yarn ios
```

## Project Structure

- **Event/** - Event types and enums
- **Option/** - Configuration options and enums
- **Model/** - Data models and error definitions
- **Player/** - Core audio player implementation
- **Observer/** - AVPlayer observation classes
- **NowPlayingInfo/** - Media control center integration
- **RemoteCommand/** - Remote control handling
- **Util/** - Utility classes

## Key Architecture Patterns

### Observer Pattern
Observers are initialized with player reference and call methods directly:

```swift
// Initialization (matches Android's PlayerListener(this) pattern)
playerObserver = PlayerStateObserver(player: self)
playerTimeObserver = PlayerTimeObserver(player: self, periodicObserverTimeInterval: interval)

// Usage - observers call AudioPlayer methods directly
player?.handleSecondElapsed(time.seconds)
player?.playerStatusDidChange(status)
player?.handleDurationUpdate(duration)
player?.handleItemDidPlayToEndTime()
```

This matches Android where `PlayerListener(trackPlayer)` is passed in the constructor and calls methods directly on TrackPlayer.
