package com.doublesymmetry.kotlinaudio.models

/**
 * Indicates the reason why an [AudioItem] transitioned to another.
 */
enum class AudioItemTransitionReason {
    /**
     * Playback has automatically transitioned to the next [AudioItem].
     *
     * This reason also indicates a transition caused by another player.
     */
    AUTO,

    /**
     * A seek to another [AudioItem] has occurred. Usually triggered when calling
     * [AudioPlayer.next][com.doublesymmetry.kotlinaudio.AudioPlayer.next]
     * or [AudioPlayer.previous][com.doublesymmetry.kotlinaudio.AudioPlayer.previous].
     */
    SEEK_TO_ANOTHER_AUDIO_ITEM,

    /**
     * The [AudioItem] has been repeated.
     */
    REPEAT,

    /**
     * The current [AudioItem] has changed because of a change in the queue. This can either be if
     * the [AudioItem] previously being played has been removed, or when the queue becomes non-empty
     * after being empty.
     */
    QUEUE_CHANGED
}

/**
 * Represents a transition from one [AudioItem] to another.
 * Examples include changes to [AudioItem] queue, an [AudioItem] on repeat, skipping an [AudioItem],
 * or simply when the [AudioItem] has finished.
 */
data class AudioItemTransition(
    val reason: AudioItemTransitionReason,
    val oldPosition: Long
)
