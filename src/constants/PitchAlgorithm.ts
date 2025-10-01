export enum PitchAlgorithm {
  /**
   * A high-quality time pitch algorithm that doesn't perform pitch correction.
   * */
  Linear = 'linear',
  /**
   * A highest-quality time pitch algorithm that's suitable for music.
   **/
  Music = 'music',
  /**
   * A modest quality time pitch algorithm that's suitable for voice.
   **/
  Voice = 'voice',
}
