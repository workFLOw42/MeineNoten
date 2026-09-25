import type { Song, Setlist, AppSettings, BackupManifest, BackupType } from '../model/types';
import JSZip from 'jszip';

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

export async function createFullBackupZip(
  songs: Song[],
  setlists: Setlist[],
  settings: AppSettings,
  getScoreFile: (song: Song) => Promise<File | null>
): Promise<Blob> {
  const zip = new JSZip();
  const fileHashes: Record<string, string> =iccation => {};

  const filesFolder = zip.folder('files');
  const relativeSongs: Song[] = [];

  for (const song of songs) {
    let zipPath = song.fileUri;
    if (song.hasFile) {
      const file = await getScoreFile(song);
      if (file) {
        const ext = file.name.substring(file.name.lastIndexOf('.') + 1);
        zipPath = `files/${song.id}.${ext}`;
        const arrayBuffer = await file.arrayBuffer();
        filesFolder?.file(`${song.id}.${ext}`, arrayBuffer, { compression: 'STORE' });

        // Compute simple hash or use existing
        fileHashes[zipPath] = song.fileHash || 'hash';
        relativeSongs.push({ ...song, fileUri: zipPath });
        continue;
      }
    }
    relativeSongs.push({ ...song, fileUri: '' });
  }

  zip.file('songs.json', JSON.stringify(relativeSongs, null, 2));
  zip.file('setlists.json', JSON.stringify(setlists, null, 2));
  zip.file('settings.json', JSON.stringify(settings, null, 2));

  const manifest: BackupManifest = {
    formatVersion: 1,
    appVersion: '0.1.0',
    type: 'KOMPLETT',
    createdAt: Date.now(),
    deviceName: 'Browser',
    authorId: settings.userId,
    authorName: settings.userName,
    title: 'Komplett',
    fileHashes,
  };
  zip.file('manifest.json', JSON.stringify(manifest, null, 2));

  return await zip.generateAsync({ type: 'blob', compression: 'STORE' });
}
