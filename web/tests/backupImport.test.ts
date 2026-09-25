import { describe, it, expect } from 'vitest';
import { reassignNoteAuthor, rewireSetlist } from '../src/lib/logic/songLogic';
import { applyBackupSettings } from '../src/lib/logic/backupImportLogic';
import { songFromJson, setlistFromJson } from '../src/lib/logic/serialization';

const song = (id: string, notes: any[] = []) => songFromJson({ id, title: id, fileUri: '', songNotes: notes });

describe('reassignNoteAuthor', () => {
  it('moves own notes to the adopted id, newer note wins', () => {
    const songs = [
      song('a', [{ authorId: 'old', text: 'hier', editedAt: 5 }]),
      song('b', [
        { authorId: 'old', text: 'alt', editedAt: 1 },
        { authorId: 'new', text: 'neu', editedAt: 9 },
      ]),
      song('c', [{ authorId: 'other', text: 'fremd', editedAt: 1 }]),
    ];
    const result = reassignNoteAuthor(songs, 'old', 'new');
    expect(result[0].notes.map(n => n.authorId)).toEqual(['new']);
    expect(result[1].notes).toHaveLength(1);
    expect(result[1].notes[0].text).toBe('neu');
    expect(result[2]).toBe(songs[2]);
  });

  it('does nothing for blank or equal ids', () => {
    const songs = [song('a', [{ authorId: 'x', text: 't', editedAt: 1 }])];
    expect(reassignNoteAuthor(songs, '', 'x')).toBe(songs);
    expect(reassignNoteAuthor(songs, 'x', 'x')).toBe(songs);
  });
});

describe('rewireSetlist', () => {
  it('maps ids and drops songs that do not exist', () => {
    const setlist = setlistFromJson({
      id: 's', title: 'GD', songIds: ['b1', 'skipped'], lastSongId: 'skipped', lastPage: 3,
    });
    const result = rewireSetlist(setlist, new Map([['b1', 'l1']]), new Set(['l1']));
    expect(result.songIds).toEqual(['l1']);
    expect(result.lastSongId).toBeNull();
    expect(result.lastPage).toBe(0);
  });

  it('keeps and maps a valid resume position', () => {
    const setlist = setlistFromJson({ id: 's', title: 'GD', songIds: ['b1'], lastSongId: 'b1', lastPage: 2 });
    const result = rewireSetlist(setlist, new Map([['b1', 'l1']]), new Set(['l1']));
    expect(result.lastSongId).toBe('l1');
    expect(result.lastPage).toBe(2);
  });
});

describe('applyBackupSettings', () => {
  const current: any = {
    showSongTitle: true, themeMode: 'SYSTEM', tapZoneSize: 'LOWER_THIRD',
    userId: 'me', userName: 'Ich', lastBackupAt: 7,
  };

  it('takes known values, skips identity, wrong types and unknown enum values', () => {
    const result = applyBackupSettings(current, {
      showSongTitle: false,
      themeMode: 'NEON',
      tapZoneSize: 'FULL_HEIGHT',
      userId: 'fremd',
      userName: 'Fremd',
      lastBackupAt: 99,
      rememberZoom: 'ja',
    } as any);
    expect(result.showSongTitle).toBe(false);
    expect(result.themeMode).toBe('SYSTEM');
    expect(result.tapZoneSize).toBe('FULL_HEIGHT');
    expect(result.userId).toBe('me');
    expect(result.userName).toBe('Ich');
    expect(result.lastBackupAt).toBe(7);
  });

  it('returns current settings without backup settings', () => {
    expect(applyBackupSettings(current, null)).toBe(current);
  });
});
