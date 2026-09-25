import type { Song, Setlist, AppSettings, BackupManifest, SerializableAppSettings, SongMatchCategory, SongImportAction } from '../model/types';
import JSZip from 'jszip';
import { songFromJson, setlistFromJson, manifestFromJson } from './serialization';

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
  zipFiles: Record<string, JSZip.JSZipObject>;
}

const songKey = (s: Song) => `${s.artist.trim().toLowerCase()}|${s.title.trim().toLowerCase()}`;

/** Werte, die nie aus settings.json kommen: Identität und Stand der letzten Sicherung. */
const NON_IMPORTED_SETTINGS = new Set<keyof AppSettings>(['userId', 'userName', 'lastBackupAt']);

const ENUM_SETTINGS: Partial<Record<keyof AppSettings, readonly string[]>> = {
  tapZoneSize: ['LOWER_THIRD', 'LOWER_HALF', 'FULL_HEIGHT'],
  themeMode: ['SYSTEM', 'LIGHT', 'DARK'],
};

/**
 * Übernimmt die Einstellungen aus einer Sicherung in [current].
 *
 * Nur bekannte Schlüssel mit passendem Typ; unbekannte Werte (etwa ein neuer Modus aus
 * einer neueren Version) lassen die aktuelle Einstellung stehen – wie toAppSettings in der
 * Android-App. Identität (userId/userName) bleibt unberührt, die regelt die Frage
 * „Bist du diese Person?“.
 */
export function applyBackupSettings(current: AppSettings, backup: SerializableAppSettings | null): AppSettings {
  if (!backup || typeof backup !== 'object') return current;
  const result: AppSettings = { ...current };
  for (const key of Object.keys(current) as (keyof AppSettings)[]) {
    if (NON_IMPORTED_SETTINGS.has(key)) continue;
    const value = (backup as Record<string, unknown>)[key];
    if (value === undefined || typeof value !== typeof current[key]) continue;
    const allowed = ENUM_SETTINGS[key];
    if (allowed && !allowed.includes(value as string)) continue;
    (result as any)[key] = value;
  }
  return result;
}

async function readJson(zip: JSZip, name: string): Promise<any> {
  const entry = zip.file(name);
  if (!entry) return null;
  try {
    return JSON.parse(await entry.async('text'));
  } catch {
    throw new Error(`Ungültige Sicherung: ${name} ist kein gültiges JSON`);
  }
}

export async function analyzeBackupFile(
  file: File | ArrayBuffer,
  localSongs: Song[],
  localSetlists: Setlist[]
): Promise<BackupAnalysisResult> {
  let zipContent: JSZip;
  try {
    zipContent = await new JSZip().loadAsync(file);
  } catch {
    throw new Error('Die Datei ist keine gültige ZIP-Sicherung');
  }

  const rawManifest = await readJson(zipContent, 'manifest.json');
  if (!rawManifest) throw new Error('Ungültige Sicherung: manifest.json fehlt');
  const manifest = manifestFromJson(rawManifest);
  if (manifest.formatVersion > 1) {
    throw new Error(`Diese Sicherung hat Format-Version ${manifest.formatVersion} und ist zu neu für diese App-Version`);
  }

  const rawSongs = await readJson(zipContent, 'songs.json');
  const backupSongs: Song[] = Array.isArray(rawSongs) ? rawSongs.map(songFromJson) : [];

  const rawSetlists = await readJson(zipContent, 'setlists.json');
  const backupSetlists: Setlist[] = Array.isArray(rawSetlists) ? rawSetlists.map(setlistFromJson) : [];

  const settingsInBackup: SerializableAppSettings | null = await readJson(zipContent, 'settings.json');

  const localSongsByHash = new Map<string, Song>();
  localSongs.forEach(s => { if (s.fileHash) localSongsByHash.set(s.fileHash, s); });

  const localSongsByKey = new Map<string, Song>();
  localSongs.forEach(s => localSongsByKey.set(songKey(s), s));

  const localSongsById = new Map<string, Song>();
  localSongs.forEach(s => localSongsById.set(s.id, s));

  const songItems: SongComparisonItem[] = backupSongs.map(backupSong => {
    const hasScoreFile = !!backupSong.fileUri && !!zipContent.file(backupSong.fileUri);
    const matchedByHash = backupSong.fileHash ? localSongsByHash.get(backupSong.fileHash) : undefined;
    const matchedByKey = localSongsByKey.get(songKey(backupSong)) ?? localSongsById.get(backupSong.id);

    let category: SongMatchCategory = 'NEW';
    let action: SongImportAction = 'TAKE_BACKUP';
    let localSong: Song | null = null;

    if (matchedByHash) {
      localSong = matchedByHash;
      const isIdentical = matchedByHash.title === backupSong.title &&
        matchedByHash.artist === backupSong.artist &&
        matchedByHash.version === backupSong.version &&
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

  const setlistKey = (s: Setlist) => `${s.title.trim().toLowerCase()}|${s.date}`;
  const localSetlistsMap = new Map<string, Setlist>();
  localSetlists.forEach(s => localSetlistsMap.set(setlistKey(s), s));

  const setlistItems: SetlistImportItem[] = backupSetlists.map(backupSetlist => ({
    backupSetlist,
    localSetlist: localSetlistsMap.get(setlistKey(backupSetlist)) || null,
    importSetlist: true,
  }));

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
