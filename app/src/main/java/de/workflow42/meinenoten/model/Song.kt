package de.workflow42.meinenoten.model

import kotlinx.serialization.Serializable

@Serializable
enum class SongSource {
    PDF, MUSIC_XML, TEXT
}

@Serializable
data class Song(
    val id: String,
    val title: String,
    val artist: String = "",
    val version: String = "",
    val fileUri: String,
    val sourceType: SongSource = SongSource.PDF,
    val bpm: Int = 120,
    val timeSignature: String = "4/4",
    val totalBars: Int = 0,
    /** Page the user last viewed, so a score reopens where it was left off. */
    val lastPage: Int = 0,
    val notes: String = ""
) {
    val displayTitle: String
        get() = if (version.isNotBlank()) "$title - $version" else title

    /**
     * Primary key for alphabetical ordering: the artist, or the title when no artist
     * is set. Songs without an artist therefore sort into their alphabetical position
     * instead of clustering at the end of the list.
     */
    val sortKey: String
        get() = artist.ifBlank { title }
}
