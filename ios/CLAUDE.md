# iOS Development Guide

## Quick Commands

- `yarn ios:rebuild` - Rebuild and run with pod install (required after adding/removing files)
- `yarn ios:format` - Format Swift code

## Architecture

```mermaid
graph TB
JS[React Native Layer<br/>JavaScript]
TPM[TrackPlayerModule<br/>RN Bridge<br/>Implements TrackPlayerCallbacks]
TP[TrackPlayer<br/>Core Player]
CB[TrackPlayerCallbacks<br/>Protocol]
AVP[AVPlayer<br/>Apple AVFoundation]
MPRC[MPRemoteCommandCenter<br/>System Media Controls]

subgraph Observers[Observer Layer - Closure-Based]
  PSO[PlayerStateObserver]
  PTO[PlayerTimeObserver]
  PINO[PlayerItemNotificationObserver]
  PIPO[PlayerItemPropertyObserver]
  PUM[PlaybackProgressUpdateManager]
end

subgraph Controllers[Controller Layer]
  RCC[RemoteCommandController]
  NPIC[NowPlayingInfoController]
end

JS -->|Commands| TPM
TPM -->|Player Commands| TP
TPM -.->|implements| CB

TP -->|Owns & Initializes<br/>with Closures| Observers
TP -->|Invokes Callbacks| CB
TP -->|Controls| AVP
TP -->|Owns| NPIC
TP -->|Creates with Callbacks| RCC

Observers -->|Observe via KVO<br/>& Notifications| AVP
Observers -->|Invoke Closures| TP

RCC -->|Weak ref to| CB
RCC -->|Handles Commands from| MPRC
RCC -->|Invokes Callbacks| CB

CB -->|Events| TPM
TPM -->|Events| JS

classDef bridge fill:#e1f5ff,stroke:#333,stroke-width:2px
classDef core fill:#ffe1e1,stroke:#333,stroke-width:2px
classDef observer fill:#e1ffe1,stroke:#333,stroke-width:2px
classDef controller fill:#fff3e1,stroke:#333,stroke-width:2px
classDef platform fill:#f0f0f0,stroke:#333,stroke-width:2px
classDef protocol fill:#ffe6f0,stroke:#333,stroke-width:2px,stroke-dasharray: 5 5

class TPM bridge
class TP core
class PSO,PTO,PINO,PIPO,PUM observer
class RCC,NPIC controller
class AVP,JS,MPRC platform
class CB protocol
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
- **NowPlayingInfo/** - Media control center integration
- **RemoteCommand/** - Remote control handling (invokes callbacks)
- **Util/** - Utility classes
