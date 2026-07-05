package com.mitv.master.data.repository

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import com.google.android.gms.location.LocationServices
import com.google.firebase.database.FirebaseDatabase
import com.mitv.master.data.model.UserSession
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes per-user session data (location, device, timestamps) to:
 * /user_tracking/{uid}/sessions/{sessionId}
 *
 * This is intentionally a custom node (not Firebase Analytics) so it can be
 * browsed directly from an admin panel / Firebase console tree view.
 */
@Singleton
class UserTrackingRepository @Inject constructor() {

    private val db = FirebaseDatabase.getInstance()
    private var currentSessionId: String = ""

    @SuppressLint("MissingPermission") // caller must check permission before invoking
    suspend fun startSession(context: Context, uid: String, email: String) {
        currentSessionId = UUID.randomUUID().toString()

        var lat = 0.0
        var lng = 0.0
        try {
            val fused = LocationServices.getFusedLocationProviderClient(context)
            val location = fused.lastLocation.await()
            if (location != null) {
                lat = location.latitude
                lng = location.longitude
            }
        } catch (_: Exception) {
            // location unavailable — proceed with 0.0/0.0, non-blocking
        }

        val session = UserSession(
            sessionId = currentSessionId,
            uid = uid,
            email = email,
            deviceModel = Build.MODEL,
            deviceManufacturer = Build.MANUFACTURER,
            osVersion = "Android ${Build.VERSION.RELEASE}",
            appVersion = "1.0.0",
            latitude = lat,
            longitude = lng,
            loginTimestamp = System.currentTimeMillis(),
            lastActiveTimestamp = System.currentTimeMillis()
        )

        db.getReference("user_tracking")
            .child(uid)
            .child("sessions")
            .child(currentSessionId)
            .setValue(session)
            .await()
    }

    suspend fun updateActivity(uid: String, currentChannelId: String, networkType: String) {
        if (currentSessionId.isEmpty()) return
        val updates = mapOf(
            "lastActiveTimestamp" to System.currentTimeMillis(),
            "currentChannelId" to currentChannelId,
            "networkType" to networkType
        )
        db.getReference("user_tracking")
            .child(uid)
            .child("sessions")
            .child(currentSessionId)
            .updateChildren(updates)
            .await()
    }
}
