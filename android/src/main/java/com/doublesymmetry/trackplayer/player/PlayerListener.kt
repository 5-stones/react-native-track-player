package com.doublesymmetry.trackplayer.player

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.doublesymmetry.trackplayer.TrackPlayer
import com.doublesymmetry.trackplayer.event.AudioItemTransition
import com.doublesymmetry.trackplayer.event.AudioItemTransitionReason
import com.doublesymmetry.trackplayer.event.AudioPlayerState
import com.doublesymmetry.trackplayer.event.PlayWhenReadyChange
import com.doublesymmetry.trackplayer.event.PlaybackError
import com.doublesymmetry.trackplayer.event.PositionChangedReason
import java.util.Locale

@UnstableApi
class PlayerListener(private val trackPlayer: TrackPlayer) : Player.Listener {
    /**
     * Called when there is metadata associated with the current playback time.
     */
    override fun onMetadata(metadata: Metadata) {
        trackPlayer.events.onTimedMetadata.emit(metadata)
    }

    override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
        trackPlayer.events.onCommonMetadata.emit(mediaMetadata)
    }

    /**
     * A position discontinuity occurs when the playing period changes, the playback position
     * jumps within the period currently being played, or when the playing period has been
     * skipped or removed.
     */
    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int
    ) {
        trackPlayer.oldPosition = oldPosition.positionMs

        when (reason) {
            Player.DISCONTINUITY_REASON_AUTO_TRANSITION -> trackPlayer.events.positionChanged.emit(
                PositionChangedReason.AUTO(oldPosition.positionMs, newPosition.positionMs)
            )
            Player.DISCONTINUITY_REASON_SEEK -> trackPlayer.events.positionChanged.emit(
                PositionChangedReason.SEEK(oldPosition.positionMs, newPosition.positionMs)
            )
            Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT -> trackPlayer.events.positionChanged.emit(
                PositionChangedReason.SEEK_FAILED(
                    oldPosition.positionMs,
                    newPosition.positionMs
                )
            )
            Player.DISCONTINUITY_REASON_REMOVE -> trackPlayer.events.positionChanged.emit(
                PositionChangedReason.QUEUE_CHANGED(
                    oldPosition.positionMs,
                    newPosition.positionMs
                )
            )
            Player.DISCONTINUITY_REASON_SKIP -> trackPlayer.events.positionChanged.emit(
                PositionChangedReason.SKIPPED_PERIOD(
                    oldPosition.positionMs,
                    newPosition.positionMs
                )
            )
            Player.DISCONTINUITY_REASON_INTERNAL -> trackPlayer.events.positionChanged.emit(
                PositionChangedReason.UNKNOWN(oldPosition.positionMs, newPosition.positionMs)
            )

            Player.DISCONTINUITY_REASON_SILENCE_SKIP -> trackPlayer.events.positionChanged.emit(
                PositionChangedReason.UNKNOWN(oldPosition.positionMs, newPosition.positionMs)
            )
        }
    }

    /**
     * Called when playback transitions to a media item or starts repeating a media item
     * according to the current repeat mode. Note that this callback is also called when the
     * playlist becomes non-empty or empty as a consequence of a playlist change.
     */
    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        when (reason) {
            Player.MEDIA_ITEM_TRANSITION_REASON_AUTO -> trackPlayer.events.audioItemTransition.emit(
                AudioItemTransition(AudioItemTransitionReason.AUTO, trackPlayer.oldPosition)
            )
            Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED -> trackPlayer.events.audioItemTransition.emit(
                AudioItemTransition(AudioItemTransitionReason.QUEUE_CHANGED, trackPlayer.oldPosition)
            )
            Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT -> trackPlayer.events.audioItemTransition.emit(
                AudioItemTransition(AudioItemTransitionReason.REPEAT, trackPlayer.oldPosition)
            )
            Player.MEDIA_ITEM_TRANSITION_REASON_SEEK -> trackPlayer.events.audioItemTransition.emit(
                AudioItemTransition(
                    AudioItemTransitionReason.SEEK_TO_ANOTHER_AUDIO_ITEM,
                    trackPlayer.oldPosition
                )
            )
        }
    }

    /**
     * Called when the value returned from Player.getPlayWhenReady() changes.
     */
    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        val pausedBecauseReachedEnd = reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM
        trackPlayer.events.playWhenReadyChange.emit(
            PlayWhenReadyChange(
                playWhenReady,
                pausedBecauseReachedEnd
            )
        )
    }

    /**
     * The generic onEvents callback provides access to the Player object and specifies the set
     * of events that occurred together. It's always called after the callbacks that correspond
     * to the individual events.
     */
    override fun onEvents(player: Player, events: Player.Events) {
        // Note that it is necessary to set `playerState` in order, since each mutation fires an
        // event.
        for (i in 0 until events.size()) {
            when (events[i]) {
                Player.EVENT_PLAYBACK_STATE_CHANGED -> {
                    val state = when (player.playbackState) {
                        Player.STATE_BUFFERING -> AudioPlayerState.BUFFERING
                        Player.STATE_READY -> AudioPlayerState.READY
                        Player.STATE_IDLE ->
                            // Avoid transitioning to idle from error or stopped
                            if (
                                trackPlayer.playerState == AudioPlayerState.ERROR ||
                                trackPlayer.playerState == AudioPlayerState.STOPPED
                            )
                                null
                            else
                                AudioPlayerState.IDLE
                        Player.STATE_ENDED ->
                            if (player.mediaItemCount > 0) AudioPlayerState.ENDED
                            else AudioPlayerState.IDLE
                        else -> null // noop
                    }
                    if (state != null && state != trackPlayer.playerState) {
                        // Clear error when recovering from ERROR state to a successful state
                        if (trackPlayer.playerState == AudioPlayerState.ERROR && state != AudioPlayerState.ERROR) {
                            trackPlayer.clearPlaybackError()
                        }
                        trackPlayer.setPlayerState(state)
                    }
                }
                Player.EVENT_MEDIA_ITEM_TRANSITION -> {
                    trackPlayer.clearPlaybackError()
                    if (trackPlayer.currentItem != null) {
                        trackPlayer.setPlayerState(AudioPlayerState.LOADING)
                        if (trackPlayer.isPlaying) {
                            trackPlayer.setPlayerState(AudioPlayerState.READY)
                            trackPlayer.setPlayerState(AudioPlayerState.PLAYING)
                        }
                    }
                }
                Player.EVENT_PLAY_WHEN_READY_CHANGED -> {
                    if (!player.playWhenReady && trackPlayer.playerState != AudioPlayerState.STOPPED) {
                        trackPlayer.setPlayerState(AudioPlayerState.PAUSED)
                    }
                }
                Player.EVENT_IS_PLAYING_CHANGED -> {
                    if (player.isPlaying) {
                        trackPlayer.setPlayerState(AudioPlayerState.PLAYING)
                    }
                }
            }
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        val playbackError = PlaybackError(
            error.errorCodeName
                .replace("ERROR_CODE_", "")
                .lowercase(Locale.getDefault())
                .replace("_", "-"),
            error.message
        )
        trackPlayer.events.playbackError.emit(playbackError)
        trackPlayer.playbackError = playbackError
        trackPlayer.setPlayerState(AudioPlayerState.ERROR)
    }
}
