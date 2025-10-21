#import "TrackPlayer.h"
#import <ReactCommon/RCTTurboModule.h>

#if __has_include("react_native_track_player-Swift.h")
#import "react_native_track_player-Swift.h"
#else
#import "react_native_track_player/react_native_track_player-Swift.h"
#endif

@interface TrackPlayer () <NativeTrackPlayerImplDelegate>
@end

@implementation TrackPlayer {
  NativeTrackPlayerImpl *nativeTrackPlayer;
}

RCT_EXPORT_MODULE()

- (instancetype) init {
  self = [super init];
  if (self) {
    nativeTrackPlayer = [NativeTrackPlayerImpl new];
    // Critical: Register ourselves as the Objective-C bridge's event emitter
    nativeTrackPlayer.delegate = self;
  }
  return self;
}


+ (BOOL)requiresMainQueueSetup {
  return NO;
}

- (std::shared_ptr<facebook::react::TurboModule>)getTurboModule:
    (const facebook::react::ObjCTurboModule::InitParams &)params
{
    return std::make_shared<facebook::react::NativeTrackPlayerSpecJSI>(params);
}


- (NSNumber *)add:(NSArray *)tracks insertBeforeIndex:(NSNumber *)insertBeforeIndex {
  [nativeTrackPlayer addWithTracks:tracks before:insertBeforeIndex];
  return insertBeforeIndex;
}

- (NSDictionary * _Nullable)getActiveTrack {
  return [nativeTrackPlayer getActiveTrack];
}

- (NSNumber * _Nullable)getActiveTrackIndex {
  return [nativeTrackPlayer getActiveTrackIndex];
}

- (NSNumber *)getPlayWhenReady {
  return @([nativeTrackPlayer getPlayWhenReady]);
}

- (NSDictionary *)getPlaybackState {
  return [nativeTrackPlayer getPlaybackState];
}

- (NSDictionary *)getPlayingState {
  return [nativeTrackPlayer getPlayingState];
}

- (NSDictionary * _Nullable)getPlaybackError {
  return [nativeTrackPlayer getPlaybackError];
}

- (NSDictionary *)getProgress {
  return [nativeTrackPlayer getProgress];
}

- (NSArray<NSDictionary *> *)getQueue {
  return [nativeTrackPlayer getQueue];
}

- (NSNumber *)getRate {
  return @([nativeTrackPlayer getRate]);
}

- (NSString *)getRepeatMode {
  return [nativeTrackPlayer getRepeatMode];
}

- (NSDictionary * _Nullable)getTrack:(double)index {
  return [nativeTrackPlayer getTrackWithIndex:index];
}

- (NSNumber *)getVolume {
  return @([nativeTrackPlayer getVolume]);
}

- (void)load:(NSDictionary *)track {
  [nativeTrackPlayer loadWithTrack:track];
}

- (void)move:(double)fromIndex toIndex:(double)toIndex {
  [nativeTrackPlayer moveFromIndex:(int)fromIndex toIndex:(int)toIndex];
}

- (void)pause {
  [nativeTrackPlayer pause];
}

- (void)play {
  [nativeTrackPlayer play];
}

- (void)remove:(NSArray *)indexes {
  [nativeTrackPlayer removeWithTracks:indexes];
}

- (void)removeUpcomingTracks {
  [nativeTrackPlayer removeUpcomingTracks];
}

- (void)reset {
  [nativeTrackPlayer reset];
}

- (void)retry {
  [nativeTrackPlayer retry];
}

- (void)seekBy:(double)offset {
  [nativeTrackPlayer seekByOffset:offset];
}

- (void)seekTo:(double)position {
  [nativeTrackPlayer seekToTime:position];
}

- (void)setPlayWhenReady:(BOOL)playWhenReady {
  [nativeTrackPlayer setPlayWhenReadyWithPlayWhenReady:playWhenReady];
}

- (void)setQueue:(NSArray *)tracks {
  [nativeTrackPlayer setQueueWithTracks:tracks];
}

- (void)setRate:(double)rate {
  [nativeTrackPlayer setRateWithRate:rate];
}

- (void)setRepeatMode:(NSString *)mode {
  [nativeTrackPlayer setRepeatModeWithRepeatMode:mode];
}

- (void)setVolume:(double)level {
  [nativeTrackPlayer setVolumeWithLevel:level];
}

- (void)setupPlayer:(NSDictionary *)options resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject {
  [nativeTrackPlayer setupPlayer:options resolver:resolve rejecter:reject];
}

- (void)skip:(double)index initialPosition:(NSNumber *)initialPosition {
  [nativeTrackPlayer skipTo:(int)index initialTime:initialPosition.doubleValue];
}

- (void)skipToNext:(NSNumber *)initialPosition {
  [nativeTrackPlayer skipToNextWithInitialTime:initialPosition.doubleValue];
}

- (void)skipToPrevious:(NSNumber *)initialPosition {
  [nativeTrackPlayer skipToPreviousWithInitialTime:initialPosition.doubleValue];
}

- (void)stop {
  [nativeTrackPlayer stop];
}

- (void)updateMetadataForTrack:(double)trackIndex metadata:(NSDictionary *)metadata {
  [nativeTrackPlayer updateMetadataFor:(int)trackIndex metadata:metadata];
}

- (void)updateNowPlayingMetadata:(NSDictionary *)metadata {
  [nativeTrackPlayer updateNowPlayingMetadataWithMetadata:metadata];
}

- (void)updateOptions:(NSDictionary *)options {
  [nativeTrackPlayer updateOptionsWithOptions:options];
}

// event listeners
- (void)addListener:(NSString *)eventName {
  // Event listeners are managed by RCTEventEmitter automatically
}

- (void)removeListeners:(double)count {
  // Event listeners are managed by RCTEventEmitter automatically
}

- (void)emitPlaybackState:(NSDictionary *)body {
  [self emitOnPlaybackState:body];
}

- (void)emitPlaybackActiveTrackChanged:(NSDictionary *)body {
  [self emitOnPlaybackActiveTrackChanged:body];
}

- (void)emitPlaybackProgressUpdated:(NSDictionary *)body {
  [self emitOnPlaybackProgressUpdated:body];
}

- (void)emitPlaybackPlayWhenReadyChanged:(NSDictionary *)body {
  [self emitOnPlaybackPlayWhenReadyChanged:body];
}

- (void)emitPlaybackPlayingState:(NSDictionary *)body {
  [self emitOnPlaybackPlayingState:body];
}

- (void)emitPlaybackQueueEnded:(NSDictionary *)body {
  [self emitOnPlaybackQueueEnded:body];
}

- (void)emitPlaybackError:(NSDictionary *)body {
  [self emitOnPlaybackError:body];
}

- (void)emitRemotePlay:(NSDictionary *)body {
  [self emitOnRemotePlay:body];
}

- (void)emitRemotePause:(NSDictionary *)body {
  [self emitOnRemotePause:body];
}

- (void)emitRemoteNext:(NSDictionary *)body {
  [self emitOnRemoteNext:body];
}

- (void)emitRemotePrevious:(NSDictionary *)body {
  [self emitOnRemotePrevious:body];
}

- (void)emitRemoteSeek:(NSDictionary *)body {
  [self emitOnRemoteSeek:body];
}

- (void)emitRemoteJumpForward:(NSDictionary *)body {
  [self emitOnRemoteJumpForward:body];
}

- (void)emitRemoteJumpBackward:(NSDictionary *)body {
  [self emitOnRemoteJumpBackward:body];
}

- (void)emitRemoteStop:(NSDictionary *)body {
  [self emitOnRemoteStop:body];
}

- (void)emitRemoteSetRating:(NSDictionary *)body {
  [self emitOnRemoteSetRating:body];
}

- (void)emitRemotePlayId:(NSDictionary *)body {
  [self emitOnRemotePlayId:body];
}

- (void)emitRemotePlaySearch:(NSDictionary *)body {
  [self emitOnRemotePlaySearch:body];
}

- (void)emitRemoteSkip:(NSDictionary *)body {
  [self emitOnRemoteSkip:body];
}

- (void)emitRemoteLike:(NSDictionary *)body {
  [self emitOnRemoteLike:body];
}

- (void)emitRemoteDislike:(NSDictionary *)body {
  [self emitOnRemoteDislike:body];
}

- (void)emitRemoteBookmark:(NSDictionary *)body {
  [self emitOnRemoteBookmark:body];
}

- (void)emitMetadataTimedReceived:(NSDictionary *)body {
  [self emitOnMetadataTimedReceived:body];
}

- (void)emitMetadataCommonReceived:(NSDictionary *)body {
  [self emitOnMetadataCommonReceived:body];
}

- (void)emitMetadataChapterReceived:(NSDictionary *)body {
  [self emitOnMetadataChapterReceived:body];
}

- (void)emitPlaybackMetadata:(NSDictionary *)body {
  [self emitOnPlaybackMetadata:body];
}



/*****************************************
 * Android Only Methods (Stubs)
 *****************************************/
- (void)abandonWakeLock {
  // iOS doesn't need wake lock management
}
- (void)acquireWakeLock {
  // iOS doesn't need wake lock management
}

@end
