package com.doublesymmetry.trackplayer.event

import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableMap

enum class AudioPlayerState {
    /** The current [com.doublesymmetry.trackplayer.model.AudioItem] is being loaded for playback. */
    LOADING,

    /** The current [com.doublesymmetry.trackplayer.model.AudioItem] is loaded, and the player is ready to start playing. */
    READY,

    /** The current [com.doublesymmetry.trackplayer.model.AudioItem] is currently buffering. */
    BUFFERING,

    /** The player is paused. */
    PAUSED,

    /** The player is stopped. */
    STOPPED,

    /** The player is playing. */
    PLAYING,

    /** No [com.doublesymmetry.trackplayer.model.AudioItem] is loaded and the player is doing nothing. */
    IDLE,

    /** Playback stopped due to the end of the queue being reached. */
    ENDED,

    /** The player stopped playing due to an error. */
    ERROR
}

val AudioPlayerState.bridge: String
    get() {
        return when(this) {
            AudioPlayerState.LOADING -> "loading"
            AudioPlayerState.READY -> "ready"
            AudioPlayerState.BUFFERING -> "buffering"
            AudioPlayerState.PAUSED -> "paused"
            AudioPlayerState.PLAYING -> "playing"
            AudioPlayerState.IDLE -> "none"
            AudioPlayerState.ENDED -> "ended"
            AudioPlayerState.ERROR -> "error"
            AudioPlayerState.STOPPED -> "stopped"
        }
    }

/**
 * Represents the current state of the audio player.
 * Includes the playback state and any associated error information.
 */
data class PlaybackState(
    val state: AudioPlayerState,
    val error: PlaybackError? = null
) {
    fun toBridge(): WritableMap {
        return Arguments.createMap().apply {
            putString("state", state.bridge)
            error?.let { putMap("error", it.toBridge()) }
        }
    }
}
