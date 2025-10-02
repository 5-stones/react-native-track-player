# iOS Development Guide

## Quick Commands

- `yarn ios:rebuild` - Rebuild and run with pod install (required after adding/removing files)
- `yarn ios:format` - Format Swift code

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
