# Android Development Guide

## Quick Commands

- `yarn android` - Build and run on connected device/emulator
- `yarn android:format` - Format Kotlin code

## Architecture

```mermaid
graph TB
JS[React Native Layer<br/>JavaScript]
TPM[TrackPlayerModule<br/>RN Bridge]
TPS[TrackPlayerService<br/>MediaLibraryService]
TP[TrackPlayer<br/>Core Player + MediaSession.Callback]
TPCB[TrackPlayerCallbacks<br/>Event Interface]
PL[PlayerListener<br/>ExoPlayer Listener]
MSM[MediaSessionManager<br/>Command & Notification Manager]
MF[MediaFactory<br/>MediaSource Factory]
PPM[PlaybackProgressUpdateManager<br/>Progress Updates]
EXO[ExoPlayer<br/>Media3/ExoPlayer]
MS[MediaSession<br/>Media3 Session]

subgraph "Event System"
  EVENTS[Event Classes<br/>PlaybackErrorEvent<br/>PlaybackProgressUpdatedEvent<br/>PlaybackActiveTrackChangedEvent<br/>RemoteSeekEvent, etc.]
end

subgraph "Data Models"
  MODELS[Model Classes<br/>Track, PlaybackState<br/>PlayerSetupOptions<br/>PlayerUpdateOptions, etc.]
end

subgraph "Utilities"
  UTILS[Utility Classes<br/>BundleUtils<br/>MetadataAdapter<br/>PlayerCache]
end

JS -->|Commands| TPM
TPM -->|Service Connection| TPS
TPS -->|Contains & Manages| TP
TPM -->|Implements| TPCB

TP -->|Uses| MSM
TP -->|Uses| MF
TP -->|Uses| PPM
TP -->|Listens via| PL
TP -->|Controls| EXO
TP -->|Manages| MS

PL -->|Player.Listener| EXO
PL -->|Forwards Events| TP

MSM -->|Configures| MS
MSM -->|Command Handling| TP

MF -->|Creates MediaSources| EXO

PPM -->|Periodic Updates| TP

TP -->|Emits| EVENTS
EVENTS -->|Via Callbacks| TPCB
TPCB -->|RN Events| TPM
TPM -->|Events| JS

TP -->|Uses| MODELS
TP -->|Uses| UTILS

classDef bridge fill:#e1f5ff,stroke:#333,stroke-width:2px
classDef core fill:#ffe1e1,stroke:#333,stroke-width:2px
classDef service fill:#fff2e1,stroke:#333,stroke-width:2px
classDef manager fill:#e1f0ff,stroke:#333,stroke-width:2px
classDef events fill:#f0e1ff,stroke:#333,stroke-width:2px
classDef models fill:#e1ffe1,stroke:#333,stroke-width:2px
classDef platform fill:#f0f0f0,stroke:#333,stroke-width:2px

class TPM bridge
class TPS service
class TP,MS core
class MSM,MF,PPM,PL manager
class EVENTS,TPCB events
class MODELS,UTILS models
class EXO,JS platform
```

## Project Structure

### Core Components
- **TrackPlayer.kt** - Core audio player with MediaSession.Callback implementation
- **TrackPlayerModule.kt** - React Native bridge implementing TrackPlayerCallbacks
- **TrackPlayerService.kt** - MediaLibraryService wrapper managing player lifecycle
- **TrackPlayerCallbacks.kt** - Event callback interface for RN communication
- **TrackPlayerPackage.kt** - React Native package registration

### Supporting Components
- **player/** - Core player logic and utilities
  - **PlayerListener.kt** - ExoPlayer event listener forwarding to TrackPlayer
  - **MediaFactory.kt** - MediaSource factory for different content types
  - **PlaybackProgressUpdateManager.kt** - Handles periodic progress updates
  - **PlayingState.kt** - Playing state enumeration

### Event System
- **event/** - Comprehensive event types for all player interactions
  - Playback events (PlaybackErrorEvent, PlaybackProgressUpdatedEvent, etc.)
  - Remote control events (RemoteSeekEvent, RemoteJumpForwardEvent, etc.)

### Data Models
- **model/** - Data structures and configuration
  - **Track.kt** - Track metadata and source information
  - **PlaybackState.kt**, **PlaybackMetadata.kt** - Player state representations
  - **PlayerSetupOptions.kt**, **PlayerUpdateOptions.kt** - Configuration objects
  - **AppKilledPlaybackBehavior.kt** - Background behavior settings

### Configuration & Options
- **option/** - Player configuration enumerations
  - **PlayerCapability.kt** - Available player capabilities
  - **PlayerRepeatMode.kt** - Repeat mode options
  - **AudioContentType.kt** - Audio content type classification
  - **CacheConfig.kt** - Caching configuration

### Utilities
- **util/** - Helper classes and utilities
  - **MediaSessionManager.kt** - MediaSession command and notification management
  - **MetadataAdapter.kt** - Metadata conversion utilities
  - **BundleUtils.kt** - Android Bundle manipulation helpers
  - **PlayerCache.kt** - Player state caching

### Extensions
- **extension/** - Kotlin extension functions
  - **EnumExtensions.kt** - Enum conversion utilities
  - **NumberExt.kt** - Numeric conversion helpers
