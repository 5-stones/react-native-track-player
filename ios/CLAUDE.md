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
Observers call AudioPlayer methods directly via weak player references:

```swift
// Usage - observers call AudioPlayer methods directly
player?.handleSecondElapsed(time.seconds)
player?.playerStatusDidChange(status)
player?.handleDurationUpdate(duration)
player?.handleItemDidPlayToEndTime()
```
