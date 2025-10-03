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
EVT[Event System<br/>Output Layer]
OBS[Observer Classes<br/>Input Layer]
RCC[RemoteCommandController]
AVP[AVPlayer<br/>Apple Platform]

JS -->|Commands| TPM
TPM -->|Player Commands| TP
TPM -.->|Customizes| RCC
TPM -.->|Listens to Events| EVT

TP -->|Emits| EVT
TP -->|Controls| AVP
RCC -.->|Weak ref| TP

OBS -->|KVO Observes| AVP
OBS -->|Calls Methods| TP

EVT -.->|event.stateChange<br/>event.fail<br/>event.updateDuration| TPM

TPM -->|Events| JS

classDef bridge fill:#e1f5ff,stroke:#333,stroke-width:2px
classDef core fill:#ffe1e1,stroke:#333,stroke-width:2px
classDef io fill:#e1ffe1,stroke:#333,stroke-width:2px
classDef platform fill:#f0f0f0,stroke:#333,stroke-width:2px

class TPM bridge
class TP,RCC core
class EVT,OBS io
class AVP,JS platform
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
