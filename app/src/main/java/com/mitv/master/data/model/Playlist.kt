package com.mitv.master.data.model

data class Playlist(
    val id: String = "",
    val name: String = "",
    val sourceUrl: String = "",       // M3U/M3U8 URL
    val type: SourceType = SourceType.M3U,
    val addedTimestamp: Long = System.currentTimeMillis(),
    val channelCount: Int = 0
)

/**
 * Xtream Codes login configuration.
 * Player API base call pattern:
 * http://{server}/player_api.php?username={u}&password={p}
 */
data class XtreamConfig(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val port: String = "80"
) {
    fun buildPlayerApiUrl(): String =
        "$serverUrl:$port/player_api.php?username=$username&password=$password"

    fun buildLiveStreamUrl(streamId: String, ext: String = "m3u8"): String =
        "$serverUrl:$port/live/$username/$password/$streamId.$ext"
}
