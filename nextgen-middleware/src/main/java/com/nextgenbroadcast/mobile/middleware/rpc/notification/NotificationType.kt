package com.nextgenbroadcast.mobile.middleware.rpc.notification

enum class NotificationType(val value: String) {
    SERVICE_CHANGE("serviceChange"),
    SERVICE_GUIDE_CHANGE("serviceGuideChange"),
    RATING_CHANGE("ratingChange"),
    RATING_BLOCK("ratingBlock"),
    CAPTION_STATE("captionState"),
    LANGUAGE_PREF("languagePref"),
    CCD_DISPLAY_PREF("CCDisplayPref"),
    AUDIO_ACCESS_PREF("audioAccessPref"),
    ALERT_CHANGE("alertingChange"),
    CONTENT_CHANGE("contentChange"),
    CONTENT_RECOVERY_STATE_CHANGE("contentRecoveryStateChange"),
    DISPLAY_OVVERIDE_CHANGE("displayOverrideChange"),
    RECOVERED_COMPONENT_INFO_CHANGE("recoveredComponentInfoChange"),
    RMP_MEDIA_TIME_CHANGE("rmpMediaTimeChange"),
    RMP_PLAYBACK_STATE_CHANGE("rmpPlaybackStateChange"),
    RMP_PLAYBACK_RATE_CHANGE("rmpPlaybackRateChange"),
    DRM("DRM"),
    X_LINK_RESOLUTION("xlinkResolution");
}