import { describe, it, expect } from 'vitest';
import JSZip from 'jszip';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { songFromJson, songToJson, migrateLegacyNote, sha256Hex } from '../src/lib/logic/serialization';
import { analyzeBackupFile } from '../src/lib/logic/backupImportLogic';
import { createFullBackupZip, createSetlistBackupZip } from '../src/lib/logic/backupLogic';
import type { AppSettings, Song } from '../src/lib/model/types';

const sample = (name: string) => readFileSync(resolve(__dirname, '../../format/samples', name));

describe('songFromJson', () => {
  it('fills defaults for fields Android omits', () => {
    const song = songFromJson({ id: 's1', title: 'Nur Titel', fileUri: '' });
    expect(song.artist).toBe('');
    expect(song.version).toBe('');
    expect(song.genre).toBe('');
    expect(song.bpm).toBe(120);
    expect(song.timeSignature).toBe('4/4');
    expect(song.sourceType).toBe('PDF');
    expect(song.notes).toEqual([]);
    expect(song.pageViews).toEqual({});
  });

  it('maps Android field names: notes = legacy text, songNotes = per-person list', () => {
    const song = songFromJson({
      id: 's1', title: 't', fileUri: '',
      notes: 'Alte Notiz',
      songNotes: [{ authorId: 'a', authorName: 'Anna', text: 'Capo 1', editedAt: 5 }],
    });
    expect(song.legacyNotes).toBe('Alte Notiz');
    expect(song.notes).toEqual([{ authorId: 'a', authorName: 'Anna', text: 'Capo 1', editedAt: 5 }]);
  });

  it('keeps unknown fields for round trips', () => {
    const song = songFromJson({ id: 's1', title: 't', fileUri: '', futureField: 42 }) as Song & { futureField?: number };
    expect(song.futureField).toBe(42);
    expect(songToJson(song).futureField).toBe(42);
  });

  it('writes Android field names back', () => {
    const json = songToJson(songFromJson({ id: 's1', title: 't', fileUri: '', songNotes: [{ authorId: 'a', text: 'x' }] }));
    expect(Array.isArray(json.songNotes)).toBe(true);
    expect(json.notes).toBe('');
  });
});

describe('migrateLegacyNote', () => {
  it('turns legacy text into own note, never overwrites an existing own note', () => {
    const legacy = songFromJson({ id: 's', title: 't', fileUri: '', notes: 'Alt' });
    expect(migrateLegacyNote(legacy, 'me', 'Ich').notes).toEqual([{ authorId: 'me', authorName: 'Ich', text: 'Alt', editedAt: 0 }]);

    const withOwn = songFromJson({ id: 's', title: 't', fileUri: '', notes: 'Alt', songNotes: [{ authorId: 'me', text: 'Neu' }] });
    expect(migrateLegacyNote(withOwn, 'me', 'Ich').notes.map(n => n.text)).toEqual(['Neu']);
  });
});

describe('analyzeBackupFile with sample ZIPs', () => {
  for (const name of ['komplett_minimal.zip', 'setlist.zip', 'notizen_personen.zip', 'alt_formatVersion1_einzelnotiz.zip']) {
    it(`reads ${name} without errors`, async () => {
      const result = await analyzeBackupFile(sample(name).buffer as ArrayBuffer, [], []);
      expect(result.songs.length).toBeGreaterThan(0);
      for (const item of result.songs) {
        expect(typeof item.backupSong.artist).toBe('string');
        expect(item.category).toBe('NEW');
      }
    });
  }

  it('reads per-person notes from notizen_personen.zip', async () => {
    const result = await analyzeBackupFile(sample('notizen_personen.zip').buffer as ArrayBuffer, [], []);
    expect(result.songs[0].backupSong.notes).toHaveLength(3);
  });

  it('detects the score file in komplett_minimal.zip', async () => {
    const result = await analyzeBackupFile(sample('komplett_minimal.zip').buffer as ArrayBuffer, [], []);
    expect(result.songs[0].scoreFileInBackup).toBe(true);
  });

  it('matches an existing local song by artist and title', async () => {
    const local = songFromJson({ id: 'local', title: 'Beispiel-Lied', artist: 'Test Artist', fileUri: '' });
    const result = await analyzeBackupFile(sample('komplett_minimal.zip').buffer as ArrayBuffer, [local], []);
    expect(result.songs[0].category).toBe('POSSIBLE_OTHER_VERSION');
    expect(result.songs[0].action).toBe('KEEP_OWN');
  });
});

describe('export → import round trip', () => {
  it('re-imports its own backup with file, hash and notes', async () => {
    const pdf = new TextEncoder().encode('%PDF-1.4 test');
    const song = songFromJson({
      id: 'song-x', title: 'Lied', artist: 'A', fileUri: 'opfs://songs/song-x.pdf',
      songNotes: [{ authorId: 'me', authorName: 'Ich', text: 'Capo 2', editedAt: 1 }],
    });
    const settings = { userId: 'me', userName: 'Ich' } as AppSettings;
    const blob = await createFullBackupZip([song], [], settings, async () => new File([pdf], 'song-x.pdf'));

    const result = await analyzeBackupFile(await blob.arrayBuffer(), [], []);
    const imported = result.songs[0];
    expect(imported.scoreFileInBackup).toBe(true);
    expect(imported.backupSong.fileUri).toBe('files/song-x.pdf');
    expect(imported.backupSong.fileHash).toBe(await sha256Hex(pdf.buffer as ArrayBuffer));
    expect(imported.backupSong.notes[0].text).toBe('Capo 2');
    expect(result.manifest.fileHashes['files/song-x.pdf']).toBe(imported.backupSong.fileHash);
  });

  it('exports and re-imports a setlist backup ZIP', async () => {
    const pdf = new TextEncoder().encode('%PDF-1.4 setlist test');
    const song = songFromJson({
      id: 'song-s1', title: 'Ostern Lied', artist: 'B', fileUri: 'opfs://songs/song-s1.pdf',
    });
    const setlist = {
      id: 'setlist-1', title: 'Ostern 2025', date: '2025-04-20', songIds: ['song-s1'], notes: 'Festmesse', lastSongId: null, lastPage: 0, lastPlayedAt: 0,
    };
    const settings = { userId: 'user-setlist', userName: 'Maria' } as AppSettings;

    const blob = await createSetlistBackupZip(setlist, [song], settings, async () => new File([pdf], 'song-s1.pdf'));

    const result = await analyzeBackupFile(await blob.arrayBuffer(), [], []);
    expect(result.manifest.type).toBe('SETLIST');
    expect(result.manifest.title).toBe('Ostern 2025');
    expect(result.songs).toHaveLength(1);
    expect(result.songs[0].backupSong.title).toBe('Ostern Lied');
    expect(result.setlists).toHaveLength(1);
    expect(result.setlists[0].backupSetlist.title).toBe('Ostern 2025');
  });
});

describe('Kompatibilität Web → Android', () => {
  const isInt = (v: unknown) => typeof v === 'number' && Number.isInteger(v);

  it('setzt darkMode auf NORMAL, wenn es fehlt oder unbekannt ist', () => {
    expect(songFromJson({ id: 's', title: 'T', fileUri: '' }).darkMode).toBe('NORMAL');
    expect(songFromJson({ id: 's', title: 'T', fileUri: '', darkMode: 'SEPIA' }).darkMode).toBe('NORMAL');
    const song = songFromJson({ id: 's', title: 'T', fileUri: '', darkMode: 'INVERTED' });
    expect(song.darkMode).toBe('INVERTED');
    expect(songToJson(song).darkMode).toBe('INVERTED');
    expect(songToJson({ ...song, darkMode: undefined as any }).darkMode).toBe('NORMAL');
  });

  it('schreibt nur ganze Zahlen in Felder, die Android als Int/Long liest', () => {
    const base = songFromJson({ id: 's', title: 'T', fileUri: '' });
    for (const bpm of [null, undefined, '', 'abc', NaN, 120.5, -3, '96']) {
      const json = songToJson({ ...base, bpm: bpm as any, totalBars: 3.7 as any, lastOpenedAt: 1.5 as any });
      expect(isInt(json.bpm), `bpm=${String(bpm)}`).toBe(true);
      expect(json.bpm as number).toBeGreaterThanOrEqual(1);
      expect(isInt(json.totalBars)).toBe(true);
      expect(isInt(json.lastOpenedAt)).toBe(true);
    }
    expect(songToJson({ ...base, bpm: '' as any }).bpm).toBe(120);
    expect(songToJson({ ...base, bpm: '96' as any }).bpm).toBe(96);
    expect(songToJson({ ...base, bpm: 120.5 }).bpm).toBe(121);
  });

  it('lässt nur Seitenzahlen als Schlüssel der pageViews zu (Android: Map<Int, PageView>)', () => {
    const base = songFromJson({ id: 's', title: 'T', fileUri: '' });
    const json = songToJson({
      ...base,
      pageViews: { '0': { scale: 2, offsetXRatio: 0.1, offsetYRatio: NaN }, 'x': { scale: 1, offsetXRatio: 0, offsetYRatio: 0 } },
    });
    expect(Object.keys(json.pageViews as object)).toEqual(['0']);
    expect((json.pageViews as any)['0'].offsetYRatio).toBe(0);
  });

  it('schreibt den Namen in settings.json, aber nicht die Person-ID', async () => {
    const settings = { userId: 'me-123', userName: 'Flo', rememberZoom: true } as AppSettings;
    const blob = await createFullBackupZip([], [], settings, async () => null);
    const zip = await JSZip.loadAsync(await blob.arrayBuffer());
    const written = JSON.parse(await zip.file('settings.json')!.async('string'));
    expect(written.userName).toBe('Flo');
    expect(written.userId).toBeUndefined();
  });

  it('schreibt ganze Zahlen in setlists.json', async () => {
    const setlist = { id: 'l', title: 'L', date: '', songIds: [], notes: '', lastSongId: null, lastPage: 1.5, lastPlayedAt: NaN } as any;
    const blob = await createFullBackupZip([], [setlist], { userId: 'me', userName: '' } as AppSettings, async () => null);
    const zip = await JSZip.loadAsync(await blob.arrayBuffer());
    const [written] = JSON.parse(await zip.file('setlists.json')!.async('string'));
    expect(isInt(written.lastPage)).toBe(true);
    expect(written.lastPlayedAt).toBe(0);
  });
});
