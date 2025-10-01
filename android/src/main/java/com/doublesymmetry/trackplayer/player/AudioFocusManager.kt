package com.doublesymmetry.trackplayer.player

import android.content.Context
import android.media.AudioManager
import androidx.core.content.ContextCompat
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import androidx.media3.common.util.UnstableApi
import com.doublesymmetry.trackplayer.TrackPlayer
import com.doublesymmetry.trackplayer.event.AudioFocusChange
import timber.log.Timber

/**
 * Manages Android audio focus for the track player.
 * Handles audio focus requests, abandonment, and ducking behavior.
 */
@UnstableApi
class AudioFocusManager(private val trackPlayer: TrackPlayer) {
    private var hasAudioFocus = false
    private var focus: AudioFocusRequestCompat? = null
    private val context: Context
        get() = trackPlayer.context
    private val options
        get() = trackPlayer.options
    private val events
        get() = trackPlayer.events

    fun requestAudioFocus() {
        if (hasAudioFocus) return

        val manager = ContextCompat.getSystemService(context, AudioManager::class.java)

        focus = AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN)
            .setOnAudioFocusChangeListener(
                { focusChange ->
                    Timber.d("Audio focus changed")
                    val isPermanent = focusChange == AudioManager.AUDIOFOCUS_LOSS
                    val isPaused = when (focusChange) {
                        AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> true
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> options.alwaysPauseOnInterruption
                        else -> false
                    }
                    if (!options.handleAudioFocus) {
                        if (isPermanent) abandonAudioFocusIfHeld()

                        val isDucking = focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK
                                && !options.alwaysPauseOnInterruption
                        if (isDucking) {
                            trackPlayer.setVolumeMultiplier(0.5f)
                            trackPlayer.wasDucking = true
                        } else if (trackPlayer.wasDucking) {
                            trackPlayer.setVolumeMultiplier(1f)
                            trackPlayer.wasDucking = false
                        }
                    }
                    events.onAudioFocusChanged.emit(AudioFocusChange(isPaused, isPermanent))
                }
            )
            .setAudioAttributes(
                AudioAttributesCompat.Builder()
                    .setUsage(AudioAttributesCompat.USAGE_MEDIA)
                    .setContentType(AudioAttributesCompat.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setWillPauseWhenDucked(options.alwaysPauseOnInterruption)
            .build()

        val result: Int = if (manager != null && focus != null) {
            AudioManagerCompat.requestAudioFocus(manager, focus!!)
        } else {
            AudioManager.AUDIOFOCUS_REQUEST_FAILED
        }

        hasAudioFocus = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
    }

    fun abandonAudioFocusIfHeld() {
        if (!hasAudioFocus) return

        val manager = ContextCompat.getSystemService(context, AudioManager::class.java)

        val result: Int = if (manager != null && focus != null) {
            AudioManagerCompat.abandonAudioFocusRequest(manager, focus!!)
        } else {
            AudioManager.AUDIOFOCUS_REQUEST_FAILED
        }

        hasAudioFocus = (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
    }
}
