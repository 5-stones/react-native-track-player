package com.doublesymmetry.trackplayer.event

/**
 * Use these events to track when and why the positionMs of an [com.doublesymmetry.trackplayer.model.AudioItem] changes.
 * Examples include changes to [com.doublesymmetry.trackplayer.model.AudioItem] queue, seeking, skipping, etc.
 */
sealed class PositionChangedReason(val oldPosition: Long, val newPosition: Long) {
    /**
     * Position has changed because the player has automatically transitioned to the next [com.doublesymmetry.trackplayer.model.AudioItem].
     *
     * @see [AudioItemTransitionReason]
     */
    class AUTO(oldPosition: Long, newPosition: Long) : PositionChangedReason(oldPosition, newPosition)

    /**
     * Position has changed because of a queue update.
     */
    class QUEUE_CHANGED(oldPosition: Long, newPosition: Long) : PositionChangedReason(oldPosition, newPosition)

    /**
     * Position has changed because a seek has occurred within the current [com.doublesymmetry.trackplayer.model.AudioItem], or another one.
     */
    class SEEK(oldPosition: Long, newPosition: Long) : PositionChangedReason(oldPosition, newPosition)

    /**
     * Position has changed because an attempted seek has failed. This can occur if we tried to see to an invalid positionMs.
     */
    class SEEK_FAILED(oldPosition: Long, newPosition: Long) : PositionChangedReason(oldPosition, newPosition)

    /**
     * Position has changed because a period (example: an ad) has been skipped.
     */
    class SKIPPED_PERIOD(oldPosition: Long, newPosition: Long) : PositionChangedReason(oldPosition, newPosition)

    /**
     * Position has changed for an unknown reason.
     */
    class UNKNOWN(oldPosition: Long, newPosition: Long) : PositionChangedReason(oldPosition, newPosition)
}
