package com.doublesymmetry.trackplayer.player

import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import com.doublesymmetry.trackplayer.event.AudioFocusChange
import com.doublesymmetry.trackplayer.event.AudioItemTransition
import com.doublesymmetry.trackplayer.event.EventControllerConnection
import com.doublesymmetry.trackplayer.event.MediaSessionCallback
import com.doublesymmetry.trackplayer.event.PlayWhenReadyChange
import com.doublesymmetry.trackplayer.event.PlaybackEndedReason
import com.doublesymmetry.trackplayer.event.PlaybackError
import com.doublesymmetry.trackplayer.event.PlaybackState
import com.doublesymmetry.trackplayer.event.PositionChangedReason
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class PlayerEvents {
    private val coroutineScope = MainScope()

    inner class Event<T>(replay: Int) : SharedFlow<T> {
        private val _flow = MutableSharedFlow<T>(replay)
        private val _sharedFlow = _flow.asSharedFlow()

        internal fun emit(value: T) {
            coroutineScope.launch { _flow.emit(value) }
        }

        override val replayCache: List<T> get() = _sharedFlow.replayCache
        override suspend fun collect(collector: FlowCollector<T>) = _sharedFlow.collect(collector)
    }

    private inline fun <reified T> event(replay: Int) = Event<T>(replay)

    val stateChange = event<PlaybackState>(replay = 1)
    val playbackEnd = event<PlaybackEndedReason?>(replay = 1)
    val playbackError = event<PlaybackError>(replay = 1)
    val playWhenReadyChange = event<PlayWhenReadyChange>(replay = 1)
    val audioItemTransition = event<AudioItemTransition>(replay = 1)
    val positionChanged = event<PositionChangedReason?>(replay = 1)
    val onAudioFocusChanged = event<AudioFocusChange>(replay = 1)
    val onCommonMetadata = event<MediaMetadata>(replay = 1)
    val onTimedMetadata = event<Metadata>(replay = 1)
    val onPlayerActionTriggeredExternally = event<MediaSessionCallback>(replay = 0)
    val onRatingChanged = event<Any>(replay = 0)
    val onControllerConnected = event<EventControllerConnection>(replay = 0)
    val onControllerDisconnected = event<String>(replay = 0)
    val onPlaybackResume = event<String>(replay = 0)
}
