# iOS Development Guide

## Quick Commands

- `yarn ios:rebuild` - Rebuild and run with pod install (required after adding/removing files)
- `yarn ios:format` - Format Swift code

## Architecture

```mermaid
graph TB
JS[React Native Layer<br/>JavaScript]
TPM[TrackPlayerModule<br/>RN Bridge<br/>Implements TrackPlayerCallbacks]
NTPI[NativeTrackPlayerImpl<br/>Obj-C Bridge Layer]
TP[TrackPlayer<br/>Core Player]
CB[TrackPlayerCallbacks<br/>Protocol]
AVP[AVPlayer<br/>Apple AVFoundation]
MPRC[MPRemoteCommandCenter<br/>System Media Controls]
AS[AVAudioSession<br/>Audio Session Management]

subgraph Observers[Observer Layer - Closure-Based]
  PSO[PlayerStateObserver]
  PTO[PlayerTimeObserver]
  PINO[PlayerItemNotificationObserver]
  PIPO[PlayerItemPropertyObserver]
  PUM[PlaybackProgressUpdateManager]
  PS[PlayingState<br/>State Manager]
end

subgraph Controllers[Controller Layer]
  RCC[RemoteCommandController]
  NPIC[NowPlayingInfoController]
end

subgraph Models[Model Layer]
  T[Track]
  UO[PlayerUpdateOptions]
  ST[State/Events]
end

JS -->|Commands| TPM
TPM -->|Native Calls| NTPI
NTPI -->|Player Commands| TP
TPM -.->|implements| CB
NTPI -.->|implements| CB

TP -->|Owns & Initializes<br/>with Closures| Observers
TP -->|Invokes Callbacks| CB
TP -->|Controls| AVP
TP -->|Owns| NPIC
TP -->|Creates with Callbacks| RCC
TP -->|Uses| Models
NTPI -->|Manages| AS

Observers -->|Observe via KVO<br/>& Notifications| AVP
Observers -->|Invoke Closures| TP

RCC -->|Weak ref to| CB
RCC -->|Handles Commands from| MPRC
RCC -->|Invokes Callbacks| CB

CB -->|Events| TPM
CB -->|Events| NTPI
TPM -->|Events| JS

classDef bridge fill:#e1f5ff,stroke:#333,stroke-width:2px
classDef core fill:#ffe1e1,stroke:#333,stroke-width:2px
classDef observer fill:#e1ffe1,stroke:#333,stroke-width:2px
classDef controller fill:#fff3e1,stroke:#333,stroke-width:2px
classDef platform fill:#f0f0f0,stroke:#333,stroke-width:2px
classDef protocol fill:#ffe6f0,stroke:#333,stroke-width:2px,stroke-dasharray: 5 5
classDef model fill:#f0f8ff,stroke:#333,stroke-width:2px

class TPM,NTPI bridge
class TP core
class PSO,PTO,PINO,PIPO,PUM,PS observer
class RCC,NPIC controller
class AVP,JS,MPRC,AS platform
class CB protocol
class T,UO,ST model
```

## Key Architecture Changes (Callback Refactor)

The iOS player now uses a **callback protocol pattern** instead of event emitters:

1. **TrackPlayerCallbacks Protocol** - Defines all event callbacks (playback state, progress, metadata, remote controls)
2. **TrackPlayerModule implements TrackPlayerCallbacks** - Bridges events to React Native
3. **RemoteCommandController** - Receives callbacks in init, invokes them for remote commands (no player dependency)
4. **Clean separation** - TrackPlayer invokes callbacks directly, no event system indirection

## Project Structure

- **TrackPlayer.swift** - Core audio player (mirrors Android's TrackPlayer.kt)
- **TrackPlayerModule.swift** - React Native bridge, implements TrackPlayerCallbacks (mirrors Android's TrackPlayerModule.kt)
- **TrackPlayerCallbacks.swift** - Protocol defining all event callbacks
- **Event/** - Event types and enums (for bridging to RN)
- **Option/** - Configuration options and enums
- **Model/** - Data models and error definitions
- **Observer/** - AVPlayer observation classes (input layer, use closures)
  - **PlayerStateObserver.swift** - Observes AVPlayer status and time control
  - **PlayerTimeObserver.swift** - Periodic time observations and audio start detection
  - **PlayerItemNotificationObserver.swift** - Track end/error notifications
  - **PlayerItemPropertyObserver.swift** - Duration, metadata, and buffering state
- **Player/** - Player state management
  - **PlaybackProgressUpdateManager.swift** - Manages progress update timers
  - **PlayingState.swift** - Tracks playing/buffering state changes
- **NowPlayingInfo/** - Media control center integration
  - **NowPlayingInfoController.swift** - Thread-safe Now Playing info management
  - **NowPlayingInfoCenter.swift** - Protocol abstraction for MPNowPlayingInfoCenter
  - **MediaItemProperty.swift** - Property definitions for media items
- **RemoteCommand/** - Remote control handling (invokes callbacks)
  - **RemoteCommandController.swift** - Manages MPRemoteCommandCenter integration
- **Util/** - Utility classes
  - **MetadataAdapter.swift** - Metadata conversion utilities
