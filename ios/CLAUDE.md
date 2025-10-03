# iOS Development Guide

## Quick Commands

- `yarn ios:rebuild` - Rebuild and run with pod install (required after adding/removing files)
- `yarn ios:format` - Format Swift code

## Architecture

```mermaid
graph TB
JS[React Native Layer<br/>JavaScript]
TPM[TrackPlayerModule<br/>RN Bridge]
TP[TrackPlayer<br/>Core Player]
EVT[Event System<br/>event.stateChange, fail, etc]
AVP[AVPlayer<br/>Apple AVFoundation]

subgraph Observers[Observer Layer - Closure-Based]
  PSO[PlayerStateObserver]
  PTO[PlayerTimeObserver]
  PINO[PlayerItemNotificationObserver]
  PIPO[PlayerItemPropertyObserver]
end

subgraph Controllers[Controller Layer]
  RCC[RemoteCommandController]
  NPIC[NowPlayingInfoController]
end

JS -->|Commands| TPM
TPM -->|Player Commands| TP
TPM -->|Listen to Events| EVT

TP -->|Owns & Initializes<br/>with Closures| Observers
TP -->|Emits Events| EVT
TP -->|Controls| AVP
TP -->|Owns| Controllers

Observers -->|Observe via KVO<br/>& Notifications| AVP
Observers -->|Invoke Closures| TP

RCC -->|Weak ref| TP
RCC -->|System Events| TP

EVT -->|Bridges Events| TPM
TPM -->|Events| JS

classDef bridge fill:#e1f5ff,stroke:#333,stroke-width:2px
classDef core fill:#ffe1e1,stroke:#333,stroke-width:2px
classDef observer fill:#e1ffe1,stroke:#333,stroke-width:2px
classDef controller fill:#fff3e1,stroke:#333,stroke-width:2px
classDef platform fill:#f0f0f0,stroke:#333,stroke-width:2px

class TPM bridge
class TP core
class PSO,PTO,PINO,PIPO observer
class RCC,NPIC controller
class AVP,JS,EVT platform
```

## Project Structure

- **TrackPlayer.swift** - Core audio player (mirrors Android's TrackPlayer.kt)
- **TrackPlayerModule.swift** - React Native bridge (mirrors Android's TrackPlayerModule.kt)
- **Event/** - Event types and enums
- **Option/** - Configuration options and enums
- **Model/** - Data models and error definitions
- **Player/** - Player event system
- **Observer/** - AVPlayer observation classes (input layer)
- **NowPlayingInfo/** - Media control center integration
- **RemoteCommand/** - Remote control handling
- **Util/** - Utility classes
