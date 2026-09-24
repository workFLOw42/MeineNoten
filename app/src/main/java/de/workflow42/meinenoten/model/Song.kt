package de.workflow42.meinenoten.model

import kotlinx.serialization.SerialName
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
    /** Zoom and pan settings per page index. */
    val pageViews: Map<Int, PageView> = emptyMap(),
    /**
     * The single memo of earlier versions, before notes had an author.
     *
     * Still read, so older files and backups stay importable, and turned into the own
     * entry of [notes] by [migrateLegacyNote]. Emptied afterwards and never written again.
     */
    @SerialName("notes")
    val legacyNotes: String = "",
    /**
     * Memos next to the score ("Capo 2", "DADGAD"), at most one per person.
     *
     * Kept separate from [lyrics]: these are glanced at while playing, so they have to
     * stay short enough for the small overlay box.
     */
    @SerialName("songNotes")
    val notes: List<SongNote> = emptyList(),
    /**
     * Full song text – verses, chords, a lead sheet typed by hand.
     *
     * Independent of [fileUri] on purpose: a song may have a score *and* its text, and
     * attaching a PDF to a song that started out as text must not destroy what was
     * typed. Songs without a file render this as their main content.
     */
    val lyrics: String = "",
    /**
     * When this song was last opened, as epoch millis; 0 means never.
     *
     * Drives the "recently opened" ordering. Every open counts, whether the song was
     * picked from the library or reached through a setlist – someone looking for the song
     * they just played does not think about how they got to it.
     *
     * Unlike the resume position this genuinely belongs to the song: "when did I last
     * look at this" is a property of the song itself, not of one programme it sits in.
     */
    val lastOpenedAt: Long = 0,
    /**
     * SHA-256 of the score file as lowercase hex; empty = not computed yet.
     *
     * Recognises the same score in a backup even when it was filed under a different
     * title or id. Proves equality only – a re-saved scan has different bytes.
     */
    val fileHash: String = "",
) {
    /**
     * Turns a memo from before authorship into the own note of [userId].
     *
     * Idempotent: once [legacyNotes] is empty nothing changes. An existing own note is
     * never overwritten.
     */
    fun migrateLegacyNote(userId: String, userName: String): Song {
        if (legacyNotes.isBlank() || userId.isBlank()) return this
        val merged = if (notes.ownNote(userId) != null) {
            notes
        } else {
            notes + SongNote(authorId = userId, authorName = userName, text = legacyNotes)
        }
        return copy(legacyNotes = "", notes = merged)
    }

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

    /**
     * True when [query] appears in the title, artist, version or genre.
     *
     * Lives on the model so the search field and the list can never disagree about what
     * counts as a match.
     *
     * Plain substring matching, case-insensitive but *not* accent-insensitive: "Fur"
     * deliberately does not find "Für". Typing the umlaut is no effort on a German
     * keyboard, and folding accents away would also make unrelated titles collide.
     *
     * A blank query matches everything, so callers can pass the field through unchecked.
     */
    fun matches(query: String): Boolean {
        val needle = query.trim()
        if (needle.isEmpty()) return true
        return title.contains(needle, ignoreCase = true) ||
            artist.contains(needle, ignoreCase = true) ||
            version.contains(needle, ignoreCase = true) ||
            genre.contains(needle, ignoreCase = true)
    }
}
