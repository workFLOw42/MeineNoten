import type { Song, Setlist, AppSettings, BackupManifest, BackupType } from '../model/types';
import JSZip from 'jszip';
import { songToJson, sha256Hex } from './serialization';

export function generateBackupFilename(
  type: BackupType,
  setlistTitle?: string,
  userName?: string,
  deviceName: string = 'Browser'
): string {
  const now = new Date();
  const dateStr = now.toISOString().slice(0, 10).replace(/-/g, '');
  const typePart = type === 'SETLIST' && setlistTitle ? `Setlist-${sanitizeFilenamePart(setlistTitle)}` : 'Komplett';
  const devicePart = sanitizeFilenamePart(deviceName);
  const namePart = sanitizeFilenamePart(userName || 'Meine-Noten');
  return `${dateStr}_${typePart}_${devicePart}_${namePart}.zip`;
}

export function sanitizeFilenamePart(part: string): string {
  return part.trim()
    .replace(/[\\/:*?"<>|\s]+/g, '-')
    .replace(/-+/g, '-')
    .replace(/^-|-$/g, '');
}

/** Einstellungen ohne Identität (userId/userName gehören nicht in settings.json). */
function serializableSettings(settings: AppSettings): Record<string, unknown> {
  const { userId, userName, lastBackupAt, ...rest } = settings as AppSettings & { key?: string };
  delete (rest as { key?: string }).key;
  return rest;
}

export async function createFullBackupZip(
  songs: Song[],
  setlists: Setlist[],
  settings: AppSettings,
  getScoreFile: (fileUri: string) => Promise<File | null>
): Promise<Blob> {
  const zip = new JSZip();
  const fileHashes: Record<string, string> = {};
  const exportedSongs: Record<string, unknown>[] = [];

  for (const song of songs) {
    let exported: Song = { ...song, fileUri: '' };
    if (song.fileUri) {
      const file = await getScoreFile(song.fileUri);
      if (file) {
        const dot = file.name.lastIndexOf('.');
        const ext = dot >= 0 ? file.name.substring(dot + 1).toLowerCase() : 'pdf';
        const zipPath = `files/${song.id}.${ext}`;
        const data = await file.arrayBuffer();
        zip.file(zipPath, data, { compression: 'STORE' });
        const hash = await sha256Hex(data);
        fileHashes[zipPath] = hash;
        exported = { ...song, fileUri: zipPath, fileHash: hash };
      }
    }
    exportedSongs.push(songToJson(exported));
  }

  const json = (v: unknown) => JSON.stringify(v, null, 2);
  zip.file('songs.json', json(exportedSongs), { compression: 'DEFLATE' });
  zip.file('setlists.json', json(setlists), { compression: 'DEFLATE' });
  zip.file('settings.json', json(serializableSettings(settings)), { compression: 'DEFLATE' });

  const manifest: BackupManifest = {
    formatVersion: 1,
    appVersion: 'web-0.1.0',
    type: 'KOMPLETT',
    createdAt: Date.now(),
    deviceName: 'Browser',
    authorId: settings.userId,
    authorName: settings.userName,
    title: 'Komplett',
    fileHashes,
  };
  zip.file('manifest.json', json(manifest), { compression: 'DEFLATE' });

  return await zip.generateAsync({ type: 'blob' });
}
