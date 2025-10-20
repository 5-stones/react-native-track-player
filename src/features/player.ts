import { AppRegistry, Platform } from 'react-native';

import TrackPlayer from '../NativeTrackPlayer';
import type { PlayerOptions } from './options';

const isAndroid = Platform.OS === 'android';

// MARK: - Types

export type ServiceHandler = () => Promise<void>;

// MARK: - Lifecycle

/**
 * Initializes the player with the specified options.
 * @param options - The options to initialize the player with.
 * @see https://rntp.dev/docs/api/functions/lifecycle
 */
export async function setupPlayer(options: PlayerOptions = {}): Promise<void> {
  return TrackPlayer.setupPlayer(options);
}

/**
 * Register the playback service. The service will run as long as the player runs.
 */
export function registerPlaybackService(factory: () => ServiceHandler) {
  if (isAndroid) {
    // Registers the headless task
    AppRegistry.registerHeadlessTask('TrackPlayer', factory);
  } else if (Platform.OS === 'web') {
    factory()();
  } else {
    // Initializes and runs the service in the next tick
    setImmediate(factory());
  }
}

// MARK: - Android-specific

/**
 * Acquires the wake lock of MusicService (Android only).
 */
export function acquireWakeLock() {
  if (!isAndroid) return;
  TrackPlayer.acquireWakeLock();
}

/**
 * Abandons the wake lock of MusicService (Android only).
 */
export function abandonWakeLock() {
  if (!isAndroid) return;
  TrackPlayer.abandonWakeLock();
}
