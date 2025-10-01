package com.doublesymmetry.trackplayer.player

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.doublesymmetry.trackplayer.event.MediaSessionCallback

/**
 * ForwardingPlayer that intercepts external player actions and emits them as events.
 *
 * This class blocks all external media item modifications to delegate control to RNTP. These
 * overrides prevent media controllers (like Android Auto, notifications) from directly modifying
 * the queue, ensuring all queue changes go through the RNTP API for proper state management and
 * event handling.
 */
@UnstableApi
class ForwardingPlayer(player: ExoPlayer, private val events: PlayerEvents) :
  ForwardingPlayer(player) {

  // Block all external media item modifications
  override fun setMediaItems(mediaItems: MutableList<MediaItem>, resetPosition: Boolean) {
    return
  }

  override fun addMediaItems(mediaItems: MutableList<MediaItem>) {
    return
  }

  override fun addMediaItems(index: Int, mediaItems: MutableList<MediaItem>) {
    return
  }

  override fun setMediaItems(
    mediaItems: MutableList<MediaItem>,
    startIndex: Int,
    startPositionMs: Long,
  ) {
    return
  }

  override fun setMediaItems(mediaItems: MutableList<MediaItem>) {
    return
  }

  // Intercept playback controls and emit events
  override fun play() {
    events.onPlayerActionTriggeredExternally.emit(MediaSessionCallback.PLAY)
  }

  override fun pause() {
    events.onPlayerActionTriggeredExternally.emit(MediaSessionCallback.PAUSE)
  }

  override fun seekToNext() {
    events.onPlayerActionTriggeredExternally.emit(MediaSessionCallback.NEXT)
  }

  override fun seekToNextMediaItem() {
    events.onPlayerActionTriggeredExternally.emit(MediaSessionCallback.NEXT)
  }

  override fun seekToPrevious() {
    events.onPlayerActionTriggeredExternally.emit(MediaSessionCallback.PREVIOUS)
  }

  override fun seekToPreviousMediaItem() {
    events.onPlayerActionTriggeredExternally.emit(MediaSessionCallback.PREVIOUS)
  }

  override fun seekForward() {
    events.onPlayerActionTriggeredExternally.emit(MediaSessionCallback.FORWARD)
  }

  override fun seekBack() {
    events.onPlayerActionTriggeredExternally.emit(MediaSessionCallback.REWIND)
  }

  override fun stop() {
    events.onPlayerActionTriggeredExternally.emit(MediaSessionCallback.STOP)
  }

  override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
    events.onPlayerActionTriggeredExternally.emit(MediaSessionCallback.SEEK(positionMs))
  }

  override fun seekTo(positionMs: Long) {
    events.onPlayerActionTriggeredExternally.emit(MediaSessionCallback.SEEK(positionMs))
  }
}
