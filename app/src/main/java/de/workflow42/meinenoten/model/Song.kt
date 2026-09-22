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
    /**
     * Free text, e.g. "Gospel", "Taizé", "Weihnachten". Deliberately not an enum: a
     * fixed list would need a code change for every new style, and the useful
     * categories here are as much occasion as musical genre.
     */
    val genre: String = "",
    val bpm: Int = 120,
    val timeSignature: String = "4/4",
    val totalBars: Int = 0,
    /**
     * No longer written. The resume position now lives on [Setlist], because the same
     * song can appear in several programmes and opening it from the library must not
     * move a half-played service along.
     *
     * Kept so that already stored songs still deserialise; can go once no installation
     * carries it any more.
     */
    val lastPage: Int = 0,
    /**
     * Short memo shown as an overlay next to a score, e.g. "Capo 2", "DADGAD".
     *
     * Kept separate from [lyrics]: this is glanced at while playing, so it has to stay
     * short enough for the small overlay box.
     */
    val notes: String = "",
    /**
     * Full song text – verses, chords, a lead sheet typed by hand.
     *
     * Independent of [fileUri] on purpose: a song may have a score *and* its text, and
     * attaching a PDF to a song that started out as text must not destroy what was
     * typed. Songs without a file render this as their main content.
     */
    val lyrics: String = "",
) {
    val displayTitle: String
        get() = if (version.isNotBlank()) "$title - $version" else title

    /** True once a score or lead sheet file has been imported for this song. */
    val hasFile: Boolean
        get() = fileUri.isNotBlank()

    /**
     * Primary key for alphabetical ordering: the artist, or the title when no artist
     * is set. Songs without an artist therefore sort into their alphabetical position
     * instead of clustering at the end of the list.
     */
    val sortKey: String
        get() = artist.ifBlank { title }
}
