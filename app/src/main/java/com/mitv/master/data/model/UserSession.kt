package com.mitv.master.data.model

/**
 * Written to Firebase Realtime Database under: /user_tracking/{uid}/sessions/{sessionId}
 * Lets the admin panel view per-user location, device, and time-based activity.
 */
data class UserSession(
    val sessionId: String = "",
    val uid: String = "",
    val email: String = "",
    val deviceModel: String = "",
    val deviceManufacturer: String = "",
    val osVersion: String = "",
    val appVersion: String = "",
    val country: String = "",
    val city: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val loginTimestamp: Long = 0L,
    val lastActiveTimestamp: Long = 0L,
    val currentChannelId: String = "",
    val networkType: String = "" // wifi / mobile
)
