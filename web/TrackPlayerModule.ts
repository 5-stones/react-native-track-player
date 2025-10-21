import { DeviceEventEmitter } from 'react-native';

import { State } from '../src/constants/State';

// Web-specific event constants
const Event = {
  PlaybackState: 'playback-state',
  PlaybackProgressUpdated: 'playback-progress-updated',
  PlaybackQueueEnded: 'playback-queue-ended',
  PlaybackPlayWhenReadyChanged: 'playback-play-when-ready-changed',
  PlaybackActiveTrackChanged: 'playback-active-track-changed',
};
import type { Spec } from '../src/NativeTrackPlayer';
import type { PlaybackState, PlayingState, Track, UpdateOptions } from '../src/features';
import { PlaylistPlayer } from './TrackPlayer';
import { SetupNotCalledError } from './TrackPlayer/SetupNotCalledError';

export class TrackPlayerModule extends PlaylistPlayer implements Spec {
  protected emitter = DeviceEventEmitter;
  protected progressUpdateEventInterval: NodeJS.Timeout | undefined;

  // MARK: init and config
  public updateOptions(options: UpdateOptions) {
    this.setupProgressUpdates(options.progressUpdateEventInterval);
  }

  // observe and emit state changes
  protected get state(): PlaybackState {
    return super.state;
  }
  protected set state(newState: PlaybackState) {
    super.state = newState;
    this.emitter.emit(Event.PlaybackState, newState);
  }

  protected setupProgressUpdates(interval?: number) {
    // clear and reset interval
    this.clearUpdateEventInterval();
    if (interval) {
      this.clearUpdateEventInterval();
      this.progressUpdateEventInterval = setInterval(async () => {
        if (this.state.state === State.Playing) {
          const progress = await this.getProgress();
          this.emitter.emit(Event.PlaybackProgressUpdated, {
            ...progress,
            track: this.currentIndex,
          });
        }
      }, interval * 1000);
    }
  }

  protected clearUpdateEventInterval() {
    if (this.progressUpdateEventInterval) {
      clearInterval(this.progressUpdateEventInterval);
    }
  }

  protected onPlaylistEnded() {
    super.onPlaylistEnded();
    this.emitter.emit(Event.PlaybackQueueEnded, {
      track: this.currentIndex,
      position: this.element!.currentTime,
    });
  }

  public getPlayWhenReady(): boolean {
    return super.playWhenReady;
  }

  public setPlayWhenReady(pwr: boolean) {
    const didChange = pwr !== this._playWhenReady;
    super.playWhenReady = pwr;

    if (didChange) {
      this.emitter.emit(Event.PlaybackPlayWhenReadyChanged, {
        playWhenReady: this._playWhenReady,
      });
    }

    return super.playWhenReady;
  }

  public async load(
    track: Track,
    onComplete?: (track: Track) => void,
  ) {
    if (!this.element) throw new SetupNotCalledError();
    const lastTrack = this.current;
    const lastPosition = this.element.currentTime;
    super.load(track, () => {
      onComplete?.(track);
      this.emitter.emit(Event.PlaybackActiveTrackChanged, {
        lastTrack,
        lastPosition,
        lastIndex: this.lastIndex,
        index: this.currentIndex,
        track,
      });
    });
  }

  public getQueue(): Track[] {
    return this.playlist;
  }

  public async setQueue(queue: Track[]) {
    await this.stop();
    this.playlist = queue;
    if (queue.length) {
      this.skip(0);
    }
  }

  public getActiveTrack(): Track | undefined {
    return this.current;
  }

  public getActiveTrackIndex(): number | undefined {
    // per the existing spec, this should throw if setup hasn't been called
    if (!this.element || !this.player) throw new SetupNotCalledError();
    return this.currentIndex;
  }

  public getPlaybackState(): PlaybackState {
    return this.state;
  }

  public async togglePlayback() {
    return super.togglePlayback();
  }

  // MARK: playingState
  public getPlayingState(state?: PlaybackState): PlayingState {
    const curState = state ? state.state : this.state.state;
    return {
      playing: curState === State.Playing,
      buffering: curState === State.Buffering
    };
  }

  public onPlaybackPlayingState(
    callback: (state: PlayingState) => void
  ) {
    return this.emitter.addListener(
      Event.PlaybackState,
      (state: PlaybackState) => {
        return callback(this.getPlayingState(state));
      }
    );
  }

  // MARK: errors
  public getPlaybackError() {
    if (this.state.state === State.Error) {
      return this.state.error?.error || null;
    }
    return null;
  }

  public onPlaybackError(callback: (event: { error?: unknown }) => void) {
    return this.emitter.addListener(Event.PlaybackError, callback);
  }

  // MARK: progress
  public onPlaybackProgressUpdated(
    callback: (event: {
      position: number;
      duration: number;
      buffered: number;
      track: number;
    }) => void
  ) {
    return this.emitter.addListener(Event.PlaybackProgressUpdated, callback);
  }

  // MARK: activeTrack
  public onPlaybackActiveTrackChanged(callback: (event: object) => void) {
    return this.emitter.addListener(Event.PlaybackActiveTrackChanged, callback);
  }

  public async acquireWakeLock() {}
  public async abandonWakeLock() {}

  // MARK: Android Auto media browser stubs
  // @ts-expect-error - these are stubs
  public onGetItemRequest() { return { remove: () => {} }; }
  public resolveGetItemRequest() {}

  // @ts-expect-error - these are stubs
  public onGetChildrenRequest() { return { remove: () => {} }; }
  public resolveGetChildrenRequest() {}

  // @ts-expect-error - these are stubs
  public onGetSearchResultRequest() { return { remove: () => {} }; }
  public resolveSearchResultRequest() {}

  public setMediaBrowserReady() {}
}
