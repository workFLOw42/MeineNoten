import type { Song, SongNote } from '../model/types';

export function matchesQuery(song: Song, query: string): boolean {
  const needle = query.trim().toLowerCase();
  if (!needle) return true;
  return (
    song.title.toLowerCase().includes(needle) ||
    song.artist.toLowerCase().includes(needle) ||
    song.version.toLowerCase().includes(needle) ||
    song.genre.toLowerCase().includes(needle)
  );
}

export function sortSongs(songs: Song[], sortBy: 'title' | 'artist' | 'recent'): Song[] {
  const collator = new Intl.Collator('de', { numeric: true, sensitivity: 'accent' });
  const copy = [...songs];
  if (sortBy === 'title') {
    copy.sort((a, b) => collator.compare(a.title, b.title));
  } else if (sortBy === 'artist') {
    copy.sort((a, b) => {
      const keyA = a.artist.trim() || a.title;
      const keyB = b.artist.trim() || b.title;
      return collator.compare(keyA, keyB);
    });
  } else if (sortBy === 'recent') {
    copy.sort((a, b) => (b.lastOpenedAt || 0) - (a.lastOpenedAt || 0));
  }
  return copy;
}

export function mergeNotes(localNotes: SongNote[], incomingNotes: SongNote[]): SongNote[] {
  const map = new Map<string, SongNote>();
  for (const note of localNotes) map.set(note.authorId, note);
  for (const note of incomingNotes) {
    const current = map.get(note.authorId);
    if (!current || note.editedAt > current.editedAt) {
      map.set(note.authorId, note);
    }
  }
  return Array.from(map.values());
}

export function possessiveName(name: string): string {
  const trimmed = name.trim();
  if (!trimmed) return 'Meine';
  const sEndings = ['s', 'ß', 'x', 'z', '.'];
  if (sEndings.some(end => trimmed.toLowerCase().endsWith(end))) {
    return `${trimmed}’`;
  }
  return `${trimmed}s`;
}
