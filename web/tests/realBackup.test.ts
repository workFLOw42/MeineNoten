import { describe, it, expect } from 'vitest';
import { existsSync, readFileSync } from 'node:fs';
import { analyzeBackupFile } from '../src/lib/logic/backupImportLogic';
import { sha256Hex } from '../src/lib/logic/serialization';

/**
 * Liest eine echte Sicherung der Android-App ein. Die Datei liegt bewusst nicht im Repo
 * (enthält Noten-PDFs); Pfad per Umgebungsvariable MEINENOTEN_REAL_BACKUP setzen.
 */
const path = process.env.MEINENOTEN_REAL_BACKUP ?? '';

describe.skipIf(!path || !existsSync(path))('echte Android-Sicherung', () => {
  it('wird vollständig und fehlerfrei analysiert', async () => {
    const buf = readFileSync(path);
    const result = await analyzeBackupFile(buf.buffer.slice(buf.byteOffset, buf.byteOffset + buf.byteLength) as ArrayBuffer, [], []);

    console.log('Manifest:', result.manifest.deviceName, result.manifest.authorName, 'formatVersion', result.manifest.formatVersion);
    for (const item of result.songs) {
      const s = item.backupSong;
      console.log(`  ${s.title} | artist="${s.artist}" | ${s.sourceType} | Datei im ZIP: ${item.scoreFileInBackup} | Seiten-Zoom: ${Object.keys(s.pageViews).length}`);
      expect(item.scoreFileInBackup).toBe(true);
      expect(item.category).toBe('NEW');

      // Prüfsumme der Datei im ZIP muss zu songs.json und manifest.json passen
      const data = await result.zipFiles[s.fileUri].async('arraybuffer');
      const hash = await sha256Hex(data);
      expect(hash).toBe(s.fileHash);
      expect(result.manifest.fileHashes[s.fileUri]).toBe(hash);
    }
    for (const sl of result.setlists) {
      console.log(`  Setlist "${sl.backupSetlist.title}" mit ${sl.backupSetlist.songIds.length} Liedern`);
      for (const id of sl.backupSetlist.songIds) {
        expect(result.songs.some(i => i.backupSong.id === id)).toBe(true);
      }
    }
    expect(result.songs.length).toBeGreaterThan(0);
  }, 60_000);
});
