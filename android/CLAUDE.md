# Android Development Guide

## Quick Commands

- `yarn android` - Build and run on connected device/emulator
- `yarn android:format` - Format Kotlin code

## Architecture

```mermaid
graph TB
JS[React Native Layer<br/>JavaScript]
TPM[TrackPlayerModule<br/>RN Bridge]
TPS[TrackPlayerService<br/>Android Service]
TP[TrackPlayer<br/>Core Player]
EVT[PlayerEvents<br/>Output Layer]
PL[PlayerListener<br/>Input Layer]
MS[MediaSessionCallback]
EXO[ExoPlayer<br/>Media3/ExoPlayer]

JS -->|Commands| TPM
TPM -->|Service Calls| TPS
TPS -->|Player Commands| TP
TPM -.->|Customizes| MS
TPM -.->|Listens to Events| EVT

TP -->|Emits| EVT
TP -->|Controls| EXO
MS -.->|Weak ref| TP

PL -->|Implements Player.Listener| EXO
PL -->|Calls Methods| TP

EVT -.->|onPlaybackStateChange<br/>onPlaybackError<br/>onPlayerStateChange| TPM

TPM -->|Events| JS

classDef bridge fill:#e1f5ff,stroke:#333,stroke-width:2px
classDef core fill:#ffe1e1,stroke:#333,stroke-width:2px
classDef io fill:#e1ffe1,stroke:#333,stroke-width:2px
classDef platform fill:#f0f0f0,stroke:#333,stroke-width:2px

class TPM,TPS bridge
class TP,MS core
class EVT,PL io
class EXO,JS platform
```

## Project Structure

- **TrackPlayer.kt** - Core audio player (mirrors iOS TrackPlayer.swift)
- **TrackPlayerModule.kt** - React Native bridge (mirrors iOS TrackPlayerModule.swift)
- **TrackPlayerService.kt** - Android foreground service wrapper
- **TrackPlayerPackage.kt** - React Native package registration
- **event/** - Event types and enums
- **model/** - Data models (Track, etc.)
- **option/** - Configuration options and enums
- **player/** - PlayerListener (input layer) and PlayerEvents (output layer)
- **util/** - Utility classes
