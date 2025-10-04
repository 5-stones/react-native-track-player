/**
 * Emitted when a playback error occurs.
 */
export interface PlaybackErrorEvent {
  error?: {
    code: string;
    message: string;
  };
}
