@file: OptIn(UnstableApi::class)

package com.doublesymmetry.trackplayer.option

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi

data class PlayerOptions(
  val cacheSizeKb: Long = 0,
  val audioContentType: AudioContentType = AudioContentType.MUSIC,
  val wakeMode: PlayerWakeMode = PlayerWakeMode.NONE,

  /**
   * Toggle whether the player should pause automatically when audio is rerouted from a headset to device speakers.
   */
  val handleAudioBecomingNoisy: Boolean = true,

  /**
   * Whether audio focus should be managed automatically. See https://medium.com/google-exoplayer/easy-audio-focus-with-exoplayer-a2dcbbe4640e
   */
  val handleAudioFocus: Boolean = true,
  var alwaysPauseOnInterruption: Boolean = true,
  var repeatMode: PlayerRepeatMode = PlayerRepeatMode.ALL,
  val bufferOptions: BufferOptions = BufferOptions(null, null, null, null),
  val parseEmbeddedArtwork: Boolean = false,
  val skipSilence: Boolean = false,

  /**
   * Toggle whether or not a player action triggered from an outside source should be intercepted.
   *
   * The sources can be: media buttons on headphones, Android Wear, Android Auto, Google Assistant, media notification, etc.
   *
   * Setting this to true enables the use of [onPlayerActionTriggeredExternally][com.doublesymmetry.trackplayer.player.PlayerEvents.onPlayerActionTriggeredExternally] events.
   */
  val interceptPlayerActionsTriggeredExternally: Boolean = false
)

data class BufferOptions (
    val minBuffer: Int?,
    val maxBuffer: Int?,
    val playBuffer: Int?,
    val backBuffer: Int?,
)
