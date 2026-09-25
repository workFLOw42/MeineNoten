import type { Song, Setlist, BackupManifest, SerializableAppSettings, SongMatchCategory, SongImportAction } from '../model/types';
import JSZip from 'jszip';

export interface SongComparisonItem {
  backupSong: Song;
  localSong: Song | null;
  category: SongMatchCategory;
  scoreFileInBackup: boolean;
  action: SongImportAction;
  mergeForeignNotes: boolean;
}

export interface SetlistImportItem {
  backupSetlist: Setlist;
  localSetlist: Setlist | null;
  importSetlist: boolean;
}

export interface BackupAnalysisResult {
  manifest: BackupManifest;
  songs: SongComparisonItem[];
  setlists: SetlistImportItem[];
  settingsInBackup: SerializableAppSettings | null;
  importSettings: boolean;
  adoptAuthorIdentity: boolean;
  zipFiles: JSZip.JSZipOutputs;
}

export async function analyzeBackupFile(
  file: File | ArrayBuffer,
  localSongs: Song[],
  localSetlists: Setlist[]
): Promise<BackupAnalysisResult> {
  const zip = new JSZip();
  const zipContent = await zip.loadAsync(file);

  const manifestFile = zipContent.file('manifest.json');
  if (!manifestFile) throw new Error('Ungültige Sicherung: manifest.json fehlt');
  const manifest: BackupManifest = JSON.parse(await manifestFile.async('text'));

  const songsFile = zipContent.file('songs.json');
  const backupSongs: Song[] = songsFile ? JSON.parse(await songsFile.async('text')) : [];

  const setlistsFile = zipContent.file('setlists.json');
  const backupSetlists: Setlist[] = setlistsFile ? JSON.parse(await setlistsFile.async('text')) : [];

  const settingsFile = zipContent.file('settings.json');
  const settingsInBackup: SerializableAppSettings | null = settingsFile ? JSON.parse(await settingsFile.async('text')) : null;

  const localSongsByHash = new Map<string, Song>();
  localSongs.forEach(s => { if (s.fileHash) localSongsByHash.set(s.fileHash, s); });

  const localSongsByKey = new Map<string, Song>();
  localSongs.forEach(s => localSongsByKey.set(`${s.artist.toLowerCase()}|${s.title.toLowerCase()}`, s));

  const songItems: SongComparisonItem[] = backupSongs.map(backupSong => {
    const hasScoreFile = !!zipContent.file(backupSong.fileUri);
    const matchedByHash = backupSong.fileHash ? localSongsByHash.get(backupSong.fileHash) : null;
    const key = `${backupSong.artist.toLowerCase()}|${backupSong.title.toLowerCase()}`;
    const matchedByKey = localSongsByKey.get(key);

    let category: SongMatchCategory = 'NEW';
    let action: SongImportAction = 'TAKE_BACKUP';
    let localSong: Song | null = null;

    if (matchedByHash) {
      localSong = matchedByHash;
      const isIdentical = matchedByHash.title === backupSong.title &&
        matchedByHash.artist === backupSong.artist &&
        matchedByHash.genre === backupSong.genre &&
        matchedByHash.lyrics === backupSong.lyrics;
      category = isIdentical ? 'IDENTICAL' : 'SAME_FILE_DIFFERENT_METADATA';
      action = 'KEEP_OWN';
    } else if (matchedByKey) {
      localSong = matchedByKey;
      category = 'POSSIBLE_OTHER_VERSION';
      action = 'KEEP_OWN';
    }

    return {
      backupSong,
      localSong,
      category,
      scoreFileInBackup: hasScoreFile,
      action,
      mergeForeignNotes: true,
    };
  });

  const localSetlistsMap = new Map<string, Setlist>();
  localSetlists.forEach(s => localSetlistsMap.set(`${s.title.toLowerCase()}|${s.date}`, s));

  const setlistItems: SetlistImportItem[] = backupSetlists.map(backupSetlist => {
    const key = `${backupSetlist.title.toLowerCase()}|${backupSetlist.date}`;
    return {
      backupSetlist,
      localSetlist: localSetlistsMap.get(key) || null,
      importSetlist: true,
    };
  });

  const zipFiles = await zipContent.generateAsync({ type: 'uint8array' }); // or store files

  return {
    manifest,
    songs: songItems,
    setlists: setlistItems,
    settingsInBackup,
    importSettings: false,
    adoptAuthorIdentity: false,
    zipFiles: zipContent.files,
  };
}
