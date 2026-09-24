package de.workflow42.meinenoten.model

import kotlinx.serialization.Serializable

/**
 * One person's memo on a song, e.g. "Capo 2" or "2. Strophe auslassen".
 *
 * Each person has at most one note per song. Authorship is tied to [authorId], never to
 * the name: two people called "Anna" stay apart, and renaming oneself does not orphan
 * earlier notes.
 */
@Serializable
data class SongNote(
    /** Person id ([de.workflow42.meinenoten.data.AppSettings.userId]), fixed. */
    val authorId: String,
    /** Name at the time of the last edit; display only. */
    val authorName: String = "",
    val text: String,
    /**
     * Last edit, as epoch millis. Decides between two versions of the same person's
     * note when a backup is read in: the newer one wins, an older one is ignored.
     */
    val editedAt: Long = 0L,
)

/** The note written by [userId], if any. */
fun List<SongNote>.ownNote(userId: String): SongNote? =
    firstOrNull { it.authorId == userId }

/** Everyone else's notes, in stable order (oldest edit first). */
fun List<SongNote>.otherNotes(userId: String): List<SongNote> =
    filter { it.authorId != userId && it.text.isNotBlank() }.sortedBy { it.editedAt }

/**
 * Replaces the own note with [text], or removes it when [text] is blank.
 *
 * Leaves other people's notes untouched – they can be deleted, but never edited here,
 * as that would put someone else's name under one's own words.
 */
fun List<SongNote>.withOwnNote(
    userId: String,
    userName: String,
    text: String,
    now: Long,
): List<SongNote> {
    val others = filter { it.authorId != userId }
    val existing = ownNote(userId)
    if (text.isBlank()) return others
    // Unchanged text keeps its timestamp, so merely opening and saving the dialog does
    // not make this device's version "newer" than one on another device.
    if (existing != null && existing.text == text && existing.authorName == userName) {
        return others + existing
    }
    return others + SongNote(authorId = userId, authorName = userName, text = text, editedAt = now)
}

/**
 * Merges notes read from elsewhere (a backup, a shared setlist) into [this].
 *
 * Per author the newer version by [SongNote.editedAt] wins; notes of authors not yet
 * present are added. This is the one place where something is overwritten without
 * asking, because author and order are unambiguous.
 */
fun List<SongNote>.mergedWith(incoming: List<SongNote>): List<SongNote> {
    val byAuthor = LinkedHashMap<String, SongNote>()
    for (note in this) byAuthor[note.authorId] = note
    for (note in incoming) {
        val current = byAuthor[note.authorId]
        if (current == null || note.editedAt > current.editedAt) {
            byAuthor[note.authorId] = note
        }
    }
    return byAuthor.values.toList()
}

/**
 * "Anna · 1", "Anna · 2": a number for every author whose name another author shares.
 *
 * Order is by the earliest note each person has anywhere in [songs], so the same Anna keeps
 * her number from song to song and it only changes if her notes are all deleted. Authors
 * with a unique name are absent from the result. Names compare trimmed and
 * case-insensitively, as "anna" and "Anna " look the same on screen.
 */
fun authorNumbers(songs: List<Song>): Map<String, Int> {
    val firstSeen = HashMap<String, Long>()
    // Name of each author's most recent note: that is the one shown on screen.
    val latest = HashMap<String, SongNote>()
    for (note in songs.asSequence().flatMap { it.notes }) {
        firstSeen[note.authorId] = minOf(firstSeen[note.authorId] ?: Long.MAX_VALUE, note.editedAt)
        val current = latest[note.authorId]
        if (current == null || note.editedAt >= current.editedAt) latest[note.authorId] = note
    }
    val result = HashMap<String, Int>()
    latest.entries
        .groupBy { it.value.authorName.trim().lowercase() }
        .values
        .filter { it.size > 1 }
        .forEach { group ->
            group.map { it.key }
                .sortedWith(compareBy({ firstSeen.getValue(it) }, { it }))
                .forEachIndexed { index, id -> result[id] = index + 1 }
        }
    return result
}
