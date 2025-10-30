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
import type { PlaybackState, Track, UpdateOptions } from '../src/types';
import { PlaylistPlayer, RepeatMode } from './TrackPlayer';
import { SetupNotCalledError } from './TrackPlayer/SetupNotCalledError';

export class TrackPlayerModule extends PlaylistPlayer implements Spec {
  protected emitter = DeviceEventEmitter;
  protected progressUpdateEventInterval: NodeJS.Timeout | undefined;

  public getConstants() {
    return {
      // Capabilities
      CAPABILITY_PLAY: 'play',
      CAPABILITY_PLAY_FROM_ID: 'play-from-id',
      CAPABILITY_PLAY_FROM_SEARCH: 'play-from-search',
      CAPABILITY_PAUSE: 'pause',
      CAPABILITY_STOP: 'stop',
      CAPABILITY_SEEK_TO: 'seek-to',
      CAPABILITY_SKIP: 'skip',
      CAPABILITY_SKIP_TO_NEXT: 'skip-to-next',
      CAPABILITY_SKIP_TO_PREVIOUS: 'skip-to-previous',
      CAPABILITY_SET_RATING: 'set-rating',
      CAPABILITY_JUMP_FORWARD: 'jump-forward',
      CAPABILITY_JUMP_BACKWARD: 'jump-backward',

      // Rating Types
      RATING_HEART: 'heart',
      RATING_THUMBS_UP_DOWN: 'thumbs-up-down',
      RATING_3_STARS: '3-stars',
      RATING_4_STARS: '4-stars',
      RATING_5_STARS: '5-stars',
      RATING_PERCENTAGE: 'percentage',

      // Pitch Algorithms
      PITCH_ALGORITHM_LINEAR: 'linear',
      PITCH_ALGORITHM_MUSIC: 'music',
      PITCH_ALGORITHM_VOICE: 'voice',

      // States
      STATE_BUFFERING: 'STATE_BUFFERING',
      STATE_LOADING: 'STATE_LOADING',
      STATE_NONE: 'STATE_NONE',
      STATE_PAUSED: 'STATE_PAUSED',
      STATE_PLAYING: 'STATE_PLAYING',
      STATE_READY: 'STATE_READY',
      STATE_STOPPED: 'STATE_STOPPED',

      // Repeat Modes
      REPEAT_OFF: RepeatMode.Off,
      REPEAT_TRACK: RepeatMode.Track,
      REPEAT_QUEUE: RepeatMode.Playlist,
    };
  }

  // observe and emit state changes
  public get state(): PlaybackState {
    return super.state;
  }
  public set state(newState: PlaybackState) {
    super.state = newState;
    this.emitter.emit(Event.PlaybackState, newState);
  }

  public async updateOptions(options: UpdateOptions) {
    this.setupProgressUpdates(options.progressUpdateEventInterval);
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

  protected async onPlaylistEnded() {
    await super.onPlaylistEnded();
    this.emitter.emit(Event.PlaybackQueueEnded, {
      track: this.currentIndex,
      position: this.element!.currentTime,
    });
  }

  public get playWhenReady(): boolean {
    return super.playWhenReady;
  }

  public set playWhenReady(pwr: boolean) {
    const didChange = pwr !== this._playWhenReady;
    super.playWhenReady = pwr;

    if (didChange) {
      this.emitter.emit(Event.PlaybackPlayWhenReadyChanged, {
        playWhenReady: this._playWhenReady,
      });
    }
  }

  public async getPlayWhenReady(): Promise<boolean> {
    return this.playWhenReady;
  }

  public async setPlayWhenReady(pwr: boolean): Promise<boolean> {
    this.playWhenReady = pwr;
    return this.playWhenReady;
  }

  public async load(track: Track) {
    if (!this.element) throw new SetupNotCalledError();
    const lastTrack = this.current;
    const lastPosition = this.element.currentTime;
    await super.load(track);

    this.emitter.emit(Event.PlaybackActiveTrackChanged, {
      lastTrack,
      lastPosition,
      lastIndex: this.lastIndex,
      index: this.currentIndex,
      track,
    });
  }

  public async getQueue(): Promise<Track[]> {
    return this.playlist;
  }

  public async setQueue(queue: Track[]) {
    await this.stop();
    this.playlist = queue;
  }

  public async getActiveTrack(): Promise<Track | undefined> {
    return this.current;
  }

  public async getActiveTrackIndex(): Promise<number | undefined> {
    // per the existing spec, this should throw if setup hasn't been called
    if (!this.element || !this.player) throw new SetupNotCalledError();
    return this.currentIndex;
  }

  public async getPlaybackState(): Promise<PlaybackState> {
    return this.state;
  }

  /**
   * overrides to match interface definition
   *
   * NOTE: these can be removed once we migrate to a sync API
   */
  public async pause() {
    return super.pause();
  }
  public async seekBy(seconds: number) {
    return super.seekBy(seconds);
  }
  public async seekTo(seconds: number) {
    return super.seekTo(seconds);
  }
  public async setVolume(volume: number) {
    return super.setVolume(volume);
  }
  // @ts-expect-error - promise return
  public async getVolume() {
    return super.getVolume();
  }
  // @ts-expect-error - promise return
  public async setRate(rate: number) {
    return super.setRate(rate);
  }

  public async acquireWakeLock() {}
  public async abandonWakeLock() {}
}
