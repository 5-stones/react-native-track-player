package com.doublesymmetry.kotlinaudio.models

data class EventControllerConnectionData(
    val packageName: String,
    val isMediaNotificationController: Boolean,
    val isAutomotiveController: Boolean,
    val isAutoCompanionController: Boolean
)