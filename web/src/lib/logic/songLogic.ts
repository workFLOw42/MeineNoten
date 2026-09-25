import type { Song, SongNote, Setlist } from '../model/types';

export interface PersonColor {
  light: string;
  dark: string;
}

export const PersonPalette: PersonColor[] = [
  { light: '#B3261E', dark: '#FFB4AB' }, // red
  { light: '#1B5E20', dark: '#81C784' }, // green
  { light: '#0D47A1', dark: '#90CAF9' }, // blue
  { light: '#8A4A00', dark: '#FFB870' }, // orange
  { light: '#6A1B9A', dark: '#CE93D8' }, // purple
  { light: '#006064', dark: '#80DEEA' }, // teal
  { light: '#AD1457', dark: '#F48FB1' }, // pink
  { light: '#5D4037', dark: '#D7B8A8' }, // brown
];

function stringHashCode(str: string): number {
  let hash = 0;
  for (let i = 0; i < str.length; i++) {
    hash = (hash << 5) - hash + str.charCodeAt(i);
    hash |= 0;
  }
  return hash;
}

export function personColorIndex(userId: string): number {
  const hash = stringHashCode(userId);
  return ((hash % PersonPalette.length) + PersonPalette.length) % PersonPalette.length;
}

export function personColor(userId: string): PersonColor {
  return PersonPalette[personColorIndex(userId)];
}

export function ownNote(notes: SongNote[], userId: string): SongNote | undefined {
  return notes.find(n => n.authorId === userId);
}

export function otherNotes(notes: SongNote[], userId: string): SongNote[] {
  return notes
    .filter(n => n.authorId !== userId && n.text.trim().length > 0)
    .sort((a, b) => a.editedAt - b.editedAt);
}

export function authorNumbers(songs: Song[]): Map<string, number> {
  const firstSeen = new Map<string, number>();
  const latest = new Map<string, SongNote>();

  for (const song of songs) {
    for (const note of song.notes) {
      const prevSeen = firstSeen.get(note.authorId) ?? Infinity;
      firstSeen.set(note.authorId, Math.min(prevSeen, note.editedAt));
      const current = latest.get(note.authorId);
      if (!current || note.editedAt >= current.editedAt) {
        latest.set(note.authorId, note);
      }
    }
  }

  const result = new Map<string, number>();
  const grouped = new Map<string, Array<{ id: string; note: SongNote }>>();

  for (const [id, note] of latest.entries()) {
    const name = note.authorName.trim().toLowerCase();
    if (!grouped.has(name)) grouped.set(name, []);
    grouped.get(name)!.push({ id, note });
  }

  for (const group of grouped.values()) {
    if (group.length > 1) {
      group.sort((a, b) => (firstSeen.get(a.id) ?? 0) - (firstSeen.get(b.id) ?? 0));
      group.forEach((item, index) => {
        result.set(item.id, index + 1);
      });
    }
  }

  return result;
}

export function displayTitle(song: Song): string {
  return song.version.trim() ? `${song.title} - ${song.version}` : song.title;
}

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

export type SongSortMode = 'ARTIST' | 'TITLE' | 'RECENT' | 'GENRE' | 'SETLIST_ORDER' | 'title' | 'artist' | 'recent';

export function initialLetter(str: string): string {
  const trimmed = str.trimStart();
  if (!trimmed) return '?';
  const first = trimmed[0];
  if (/\d/.test(first)) return '#';
  if (!/[a-zA-ZäöüÄÖÜa-zA-Z]/.test(first)) return '?';
  const normalized = first.normalize('NFD').replace(/[\u0300-\u036f]/g, '');
  return normalized.toUpperCase();
}

export function songSortKey(song: Song): string {
  return song.artist.trim() || song.title;
}

export function sortSongs(
  songs: Song[],
  sortBy: SongSortMode,
  setlistSongIds?: string[]
): Song[] {
  const collator = new Intl.Collator('de', { numeric: true, sensitivity: 'accent' });
  const copy = [...songs];

  const mode = sortBy.toUpperCase() as 'ARTIST' | 'TITLE' | 'RECENT' | 'GENRE' | 'SETLIST_ORDER';

  if (mode === 'TITLE') {
    copy.sort((a, b) => collator.compare(a.title, b.title));
  } else if (mode === 'ARTIST') {
    copy.sort((a, b) => {
      const cmp = collator.compare(songSortKey(a), songSortKey(b));
      if (cmp !== 0) return cmp;
      return collator.compare(a.title, b.title);
    });
  } else if (mode === 'RECENT') {
    copy.sort((a, b) => {
      const diff = (b.lastOpenedAt || 0) - (a.lastOpenedAt || 0);
      if (diff !== 0) return diff;
      const cmp = collator.compare(songSortKey(a), songSortKey(b));
      if (cmp !== 0) return cmp;
      return collator.compare(a.title, b.title);
    });
  } else if (mode === 'GENRE') {
    copy.sort((a, b) => {
      const aEmpty = !a.genre.trim();
      const bEmpty = !b.genre.trim();
      if (aEmpty !== bEmpty) return aEmpty ? 1 : -1;
      const genreCmp = collator.compare(a.genre, b.genre);
      if (genreCmp !== 0) return genreCmp;
      const keyCmp = collator.compare(songSortKey(a), songSortKey(b));
      if (keyCmp !== 0) return keyCmp;
      return collator.compare(a.title, b.title);
    });
  } else if (mode === 'SETLIST_ORDER' && setlistSongIds) {
    const posMap = new Map(setlistSongIds.map((id, idx) => [id, idx]));
    return copy
      .filter(s => posMap.has(s.id))
      .sort((a, b) => (posMap.get(a.id) ?? 0) - (posMap.get(b.id) ?? 0));
  }

  return copy;
}

export function songGroupHeading(song: Song, sortBy: SongSortMode): string | null {
  const mode = sortBy.toUpperCase();
  if (mode === 'GENRE') {
    return song.genre.trim() || 'Ohne Genre';
  }
  if (mode === 'ARTIST') {
    return initialLetter(songSortKey(song));
  }
  if (mode === 'TITLE') {
    return initialLetter(song.title);
  }
  return null;
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

// Setlist Logic

export function parseSetlistDate(raw: string): { year: number; month: number; day: number } | null {
  const value = raw.trim();
  if (!value) return null;

  // ISO: yyyy-MM-dd
  const isoMatch = value.match(/^(\d{4})-(\d{1,2})-(\d{1,2})$/);
  if (isoMatch) {
    return { year: parseInt(isoMatch[1], 10), month: parseInt(isoMatch[2], 10), day: parseInt(isoMatch[3], 10) };
  }

  // German: dd.MM.yyyy
  const deMatch = value.match(/^(\d{1,2})\.(\d{1,2})\.(\d{4})$/);
  if (deMatch) {
    return { year: parseInt(deMatch[3], 10), month: parseInt(deMatch[2], 10), day: parseInt(deMatch[1], 10) };
  }

  // Short German: dd.MM. or dd.MM
  const shortMatch = value.match(/^(\d{1,2})\.(\d{1,2})\.?$/);
  if (shortMatch) {
    const currentYear = new Date().getFullYear();
    return { year: currentYear, month: parseInt(shortMatch[2], 10), day: parseInt(shortMatch[1], 10) };
  }

  return null;
}

export function formatSetlistDate(raw: string): string {
  const parsed = parseSetlistDate(raw);
  if (!parsed) return raw.trim();
  const d = String(parsed.day).padStart(2, '0');
  const m = String(parsed.month).padStart(2, '0');
  return `${d}.${m}.${parsed.year}`;
}

export type SetlistPeriod = 'ALL' | 'UPCOMING' | 'PAST';
export type SetlistSortMode = 'DATE' | 'TITLE' | 'RECENT';

export function matchesSetlist(setlist: Setlist, query: string, songsById: Map<string, Song>): boolean {
  const needle = query.trim().toLowerCase();
  if (!needle) return true;

  if (setlist.title.toLowerCase().includes(needle)) return true;
  if (setlist.notes.toLowerCase().includes(needle)) return true;
  if (setlist.date.toLowerCase().includes(needle)) return true;
  if (formatSetlistDate(setlist.date).toLowerCase().includes(needle)) return true;

  return setlist.songIds.some(id => {
    const song = songsById.get(id);
    if (!song) return false;
    return song.title.toLowerCase().includes(needle) || song.artist.toLowerCase().includes(needle);
  });
}

export function isInSetlistPeriod(setlist: Setlist, period: SetlistPeriod, todayDate = new Date()): boolean {
  if (period === 'ALL') return true;
  const parsed = parseSetlistDate(setlist.date);
  if (!parsed) return false;

  const target = new Date(parsed.year, parsed.month - 1, parsed.day);
  const today = new Date(todayDate.getFullYear(), todayDate.getMonth(), todayDate.getDate());

  if (period === 'UPCOMING') {
    return target >= today;
  } else if (period === 'PAST') {
    return target < today;
  }
  return true;
}

export function filterAndSortSetlists(
  setlists: Setlist[],
  query: string,
  period: SetlistPeriod,
  sortBy: SetlistSortMode,
  songsById: Map<string, Song>
): Setlist[] {
  const collator = new Intl.Collator('de', { numeric: true, sensitivity: 'accent' });

  const filtered = setlists.filter(s => matchesSetlist(s, query, songsById) && isInSetlistPeriod(s, period));

  if (sortBy === 'TITLE') {
    filtered.sort((a, b) => collator.compare(a.title, b.title));
  } else if (sortBy === 'DATE') {
    const dated: Array<{ setlist: Setlist; key: number }> = [];
    const undated: Setlist[] = [];

    for (const s of filtered) {
      const p = parseSetlistDate(s.date);
      if (p) {
        const timestamp = new Date(p.year, p.month - 1, p.day).getTime();
        dated.push({ setlist: s, key: timestamp });
      } else {
        undated.push(s);
      }
    }

    dated.sort((a, b) => {
      if (b.key !== a.key) return b.key - a.key;
      return collator.compare(a.setlist.title, b.setlist.title);
    });

    undated.sort((a, b) => collator.compare(a.title, b.title));

    return [...dated.map(d => d.setlist), ...undated];
  } else if (sortBy === 'RECENT') {
    const dateSorted = filterAndSortSetlists(filtered, '', 'ALL', 'DATE', songsById);
    const dateOrderMap = new Map(dateSorted.map((s, idx) => [s.id, idx]));

    filtered.sort((a, b) => {
      const diff = (b.lastPlayedAt || 0) - (a.lastPlayedAt || 0);
      if (diff !== 0) return diff;
      return (dateOrderMap.get(a.id) ?? 99999) - (dateOrderMap.get(b.id) ?? 99999);
    });
  }

  return filtered;
}

export function nextUpcomingSetlist(setlists: Setlist[], todayDate = new Date()): Setlist | null {
  const collator = new Intl.Collator('de', { numeric: true, sensitivity: 'accent' });
  const today = new Date(todayDate.getFullYear(), todayDate.getMonth(), todayDate.getDate());

  let best: { setlist: Setlist; target: Date } | null = null;

  for (const s of setlists) {
    const p = parseSetlistDate(s.date);
    if (!p) continue;
    const target = new Date(p.year, p.month - 1, p.day);
    if (target >= today) {
      if (!best || target < best.target || (target.getTime() === best.target.getTime() && collator.compare(s.title, best.setlist.title) < 0)) {
        best = { setlist: s, target };
      }
    }
  }

  return best ? best.setlist : null;
}
