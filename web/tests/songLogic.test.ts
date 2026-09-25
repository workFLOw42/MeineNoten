import { describe, it, expect } from 'vitest';
import { matchesQuery, sortSongs, mergeNotes, possessiveName } from '../src/lib/logic/songLogic';
import type { Song, SongNote } from '../src/lib/model/types';

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
});
