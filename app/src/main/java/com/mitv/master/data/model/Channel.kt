package com.mitv.master.data.model

/**
 * Represents a single playable channel/stream entry.
 * Supports M3U/M3U8, Xtream Codes, and YouTube-sourced entries.
 */
data class Channel(
    val id: String = "",
    val name: String = "",
    val streamUrl: String = "",
    val logoUrl: String = "",       // supports .png/.jpg/.svg
    val groupTitle: String = "General",
    val tvgId: String = "",
    val tvgLanguage: String = "",
    val sourceType: SourceType = SourceType.M3U,
    val isFavorite: Boolean = false,
    val audioTracks: List<String> = emptyList(),
    val lastWatchedTimestamp: Long = 0L
)

enum class SourceType {
    M3U,
    M3U8,
    XTREAM,
    YOUTUBE
}
