package com.doublesymmetry.trackplayer.event

/**
 * Indicates the reason why an [com.doublesymmetry.trackplayer.model.AudioItem] transitioned to another.
 */
enum class AudioItemTransitionReason {
    /**
     * Playback has automatically transitioned to the next [com.doublesymmetry.trackplayer.model.AudioItem].
     *
     * This reason also indicates a transition caused by another player.
     */
    AUTO,

    /**
     * A seek to another [com.doublesymmetry.trackplayer.model.AudioItem] has occurred. Usually triggered when calling
     * [AudioPlayer.next][com.doublesymmetry.trackplayer.TrackPlayer.next]
     * or [AudioPlayer.previous][com.doublesymmetry.trackplayer.TrackPlayer.previous].
     */
    SEEK_TO_ANOTHER_AUDIO_ITEM,

    /**
     * The [com.doublesymmetry.trackplayer.model.AudioItem] has been repeated.
     */
    REPEAT,

    /**
     * The current [com.doublesymmetry.trackplayer.model.AudioItem] has changed because of a change in the queue. This can either be if
     * the [com.doublesymmetry.trackplayer.model.AudioItem] previously being played has been removed, or when the queue becomes non-empty
     * after being empty.
     */
    QUEUE_CHANGED
}

/**
 * Represents a transition from one [com.doublesymmetry.trackplayer.model.AudioItem] to another.
 * Examples include changes to [com.doublesymmetry.trackplayer.model.AudioItem] queue, an [com.doublesymmetry.trackplayer.model.AudioItem] on repeat, skipping an [com.doublesymmetry.trackplayer.model.AudioItem],
 * or simply when the [com.doublesymmetry.trackplayer.model.AudioItem] has finished.
 */
data class AudioItemTransition(
    val reason: AudioItemTransitionReason,
    val oldPosition: Long
)
