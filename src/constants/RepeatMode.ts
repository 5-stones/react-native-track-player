export enum RepeatMode {
  /** Playback stops when the last track in the queue has finished playing. */
  Off = 'off',
  /** Repeats the current track infinitely during ongoing playback. */
  Track = 'track',
  /** Repeats the entire queue infinitely. */
  Queue = 'queue',
}
