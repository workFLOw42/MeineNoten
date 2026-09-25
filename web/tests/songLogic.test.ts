import { describe, it, expect } from 'vitest';
import {
  matchesQuery,
  sortSongs,
  mergeNotes,
  possessiveName,
  personColor,
  ownNote,
  otherNotes,
  authorNumbers,
  parseSetlistDate,
  formatSetlistDate,
  matchesSetlist,
  filterAndSortSetlists,
  nextUpcomingSetlist,
} from '../src/lib/logic/songLogic';
import type { Song, SongNote, Setlist } from '../src/lib/model/types';

describe('Song Logic', () => {
  const dummySong: Song = {
    id: '1',
    title: 'Amazing Grace',
    artist: 'John Newton',
    version: 'G-Dur',
    fileUri: '',
    sourceType: 'TEXT',
    genre: 'Gospel',
    bpm: 100,
    timeSignature: '3/4',
    totalBars: 32,
    pageViews: {},
    legacyNotes: '',
    notes: [],
    lyrics: '',
    lastOpenedAt: 1000,
    fileHash: '',
    darkMode: 'NORMAL',
  };

  it('matches query case-insensitively across fields', () => {
    expect(matchesQuery(dummySong, 'grace')).toBe(true);
    expect(matchesQuery(dummySong, 'Newton')).toBe(true);
    expect(matchesQuery(dummySong, 'g-dur')).toBe(true);
    expect(matchesQuery(dummySong, 'gospel')).toBe(true);
    expect(matchesQuery(dummySong, 'unknown')).toBe(false);
  });

  it('computes possessive name correctly', () => {
    expect(possessiveName('Anna')).toBe('Annas');
    expect(possessiveName('Hans')).toBe('Hans’');
    expect(possessiveName('Anna S.')).toBe('Anna S.’');
  });

  it('merges notes by timestamp (newer wins)', () => {
    const local: SongNote[] = [{ authorId: 'a1', authorName: 'Anna', text: 'Old', editedAt: 100 }];
    const incoming: SongNote[] = [{ authorId: 'a1', authorName: 'Anna', text: 'New', editedAt: 200 }];
    const merged = mergeNotes(local, incoming);
    expect(merged.length).toBe(1);
    expect(merged[0].text).toBe('New');
  });

  it('returns stable person colors based on user ID', () => {
    const c1 = personColor('user-123');
    const c2 = personColor('user-123');
    expect(c1).toEqual(c2);
    expect(c1.light).toBeDefined();
    expect(c1.dark).toBeDefined();
  });

  it('separates own note and other notes', () => {
    const notes: SongNote[] = [
      { authorId: 'me', authorName: 'Ich', text: 'Kapodaster 2', editedAt: 100 },
      { authorId: 'other', authorName: 'Anna', text: 'Soli beachten', editedAt: 200 },
    ];
    expect(ownNote(notes, 'me')?.text).toBe('Kapodaster 2');
    expect(otherNotes(notes, 'me').length).toBe(1);
    expect(otherNotes(notes, 'me')[0].authorName).toBe('Anna');
  });

  it('assigns numbers to authors with duplicate names', () => {
    const songA: Song = { ...dummySong, notes: [{ authorId: 'a1', authorName: 'Anna', text: 'N1', editedAt: 100 }] };
    const songB: Song = { ...dummySong, notes: [{ authorId: 'a2', authorName: 'Anna', text: 'N2', editedAt: 200 }] };
    const nums = authorNumbers([songA, songB]);
    expect(nums.get('a1')).toBe(1);
    expect(nums.get('a2')).toBe(2);
  });

  it('parses and formats setlist dates', () => {
    const parsed = parseSetlistDate('24.12.2025');
    expect(parsed).toEqual({ year: 2025, month: 12, day: 24 });
    expect(formatSetlistDate('2025-12-24')).toBe('24.12.2025');
  });

  it('filters and sorts setlists', () => {
    const s1: Setlist = { id: 's1', title: 'Weihnachten', date: '2025-12-24', songIds: ['1'], notes: '', lastSongId: null, lastPage: 0, lastPlayedAt: 100 };
    const s2: Setlist = { id: 's2', title: 'Ostern', date: '2025-04-20', songIds: [], notes: '', lastSongId: null, lastPage: 0, lastPlayedAt: 200 };
    const map = new Map<string, Song>([['1', dummySong]]);

    expect(matchesSetlist(s1, 'Grace', map)).toBe(true);

    const sorted = filterAndSortSetlists([s1, s2], '', 'ALL', 'DATE', map);
    expect(sorted[0].id).toBe('s1'); // newest date first

    const upcoming = nextUpcomingSetlist([s1, s2], new Date(2025, 0, 1));
    expect(upcoming?.id).toBe('s2'); // earliest upcoming date
  });
});
