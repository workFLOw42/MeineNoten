package de.workflow42.meinenoten.model

import kotlinx.serialization.Serializable

@Serializable
data class Setlist(
    val id: String,
    val title: String,
    val date: String = "",
    val songIds: List<String>,
    val notes: String = "",
    /**
     * Song this programme was last left at, or null before it was ever played and again
     * once it was played to the end.
     *
     * Tracked per setlist rather than reusing [Song.lastPage]: the same song can sit in
     * several programmes, and browsing it from the song library must not move the
     * position of a service that is half-played.
     */
    val lastSongId: String? = null,
    /** Page within [lastSongId]. Meaningless while that is null. */
    val lastPage: Int = 0,
) {
    /** True once there is a position worth offering to resume from. */
    val hasProgress: Boolean
        get() = lastSongId != null
}
