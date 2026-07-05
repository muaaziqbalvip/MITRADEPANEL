package com.mitv.master.data.repository

import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ktx.getValue
import com.mitv.master.data.model.Channel
import com.mitv.master.data.model.Playlist
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles all Firebase Realtime Database reads/writes:
 *  - /channels/{channelId}
 *  - /playlists/{userId}/{playlistId}
 *  - /app_config/update  (force-update / "new update available" flag)
 *  - /user_tracking/{uid}/sessions/{sessionId}
 */
@Singleton
class FirebaseRepository @Inject constructor() {

    private val db = FirebaseDatabase.getInstance()

    // ---------- Channels (admin-curated, shared across all users) ----------

    fun observeChannels(): Flow<List<Channel>> = callbackFlow {
        val ref = db.getReference("channels")
        val listener = ref.addValueEventListener(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                val list = snapshot.children.mapNotNull { it.getValue<Channel>() }
                trySend(list)
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                close(error.toException())
            }
        })
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun pushChannel(channel: Channel) {
        db.getReference("channels").child(channel.id).setValue(channel).await()
    }

    // ---------- User playlists ----------

    suspend fun savePlaylist(uid: String, playlist: Playlist) {
        db.getReference("playlists").child(uid).child(playlist.id).setValue(playlist).await()
    }

    fun observeUserPlaylists(uid: String): Flow<List<Playlist>> = callbackFlow {
        val ref = db.getReference("playlists").child(uid)
        val listener = ref.addValueEventListener(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                val list = snapshot.children.mapNotNull { it.getValue<Playlist>() }
                trySend(list)
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                close(error.toException())
            }
        })
        awaitClose { ref.removeEventListener(listener) }
    }

    // ---------- Remote update flag (Netflix-style "Update Available") ----------

    fun observeUpdateFlag(): Flow<AppUpdateInfo> = callbackFlow {
        val ref = db.getReference("app_config").child("update")
        val listener = ref.addValueEventListener(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                val info = snapshot.getValue<AppUpdateInfo>() ?: AppUpdateInfo()
                trySend(info)
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                close(error.toException())
            }
        })
        awaitClose { ref.removeEventListener(listener) }
    }
}

data class AppUpdateInfo(
    val latestVersionCode: Int = 0,
    val latestVersionName: String = "",
    val forceUpdate: Boolean = false,
    val updateMessage: String = "",
    val downloadUrl: String = ""
)
