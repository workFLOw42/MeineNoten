import type { Song, Setlist, AppSettings } from '../model/types';
import { songFromJson } from '../logic/serialization';

const DB_NAME = 'meinenoten_db';
const DB_VERSION = 1;

/**
 * Svelte-5-$state liefert Proxys, die IndexedDB nicht klonen kann (DataCloneError).
 * Vor dem Speichern deshalb in ein reines Objekt umwandeln.
 */
function plain<T>(value: T): T {
  return JSON.parse(JSON.stringify(value));
}

export async function openDB(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_VERSION);
    request.onerror = () => reject(request.error);
    request.onsuccess = () => resolve(request.result);
    request.onupgradeneeded = (event) => {
      const db = (event.target as IDBOpenDBRequest).result;
      if (!db.objectStoreNames.contains('songs')) {
        db.createObjectStore('songs', { keyPath: 'id' });
      }
      if (!db.objectStoreNames.contains('setlists')) {
        db.createObjectStore('setlists', { keyPath: 'id' });
      }
      if (!db.objectStoreNames.contains('settings')) {
        db.createObjectStore('settings', { keyPath: 'key' });
      }
    };
  });
}

// OPFS helpers for storing score files
export async function saveScoreFile(songId: string, extension: string, data: Blob | ArrayBuffer): Promise<string> {
  try {
    const root = await navigator.storage.getDirectory();
    const folderName = extension.toLowerCase() === 'pdf' ? 'songs' : 'musicxml';
    const folderHandle = await root.getDirectoryHandle(folderName, { create: true });
    const fileName = `${songId}.${extension.toLowerCase()}`;
    const fileHandle = await folderHandle.getFileHandle(fileName, { create: true });
    const writable = await fileHandle.createWritable();
    await writable.write(data);
    await writable.close();
    return `opfs://${folderName}/${fileName}`;
  } catch (e) {
    console.error('Failed to save score in OPFS, falling back to Blob URL', e);
    // Fallback if OPFS is unavailable
    const blob = data instanceof Blob ? data : new Blob([data]);
    return URL.createObjectURL(blob);
  }
}

export async function loadScoreFile(fileUri: string): Promise<File | null> {
  if (!fileUri || fileUri.startsWith('blob:')) return null;
  try {
    // e.g. "opfs://songs/uuid.pdf" or legacy "files/uuid.pdf"
    const parts = fileUri.replace('opfs://', '').replace('files/', '').split('/');
    if (parts.length < 2) return null;
    const [folderName, fileName] = parts;
    const root = await navigator.storage.getDirectory();
    const folderHandle = await root.getDirectoryHandle(folderName);
    const fileHandle = await folderHandle.getFileHandle(fileName);
    return await fileHandle.getFile();
  } catch (e) {
    console.error('Failed to load score from OPFS', fileUri, e);
    return null;
  }
}

export async function deleteScoreFile(fileUri: string): Promise<void> {
  try {
    const parts = fileUri.replace('opfs://', '').replace('files/', '').split('/');
    if (parts.length < 2) return;
    const [folderName, fileName] = parts;
    const root = await navigator.storage.getDirectory();
    const folderHandle = await root.getDirectoryHandle(folderName);
    await folderHandle.removeEntry(fileName);
  } catch (e) {
    // ignore if not found
  }
}

// DB Data operations
export async function loadSongsDB(): Promise<Song[]> {
  const db = await openDB();
  return new Promise((resolve, reject) => {
    const tx = db.transaction('songs', 'readonly');
    const store = tx.objectStore('songs');
    const request = store.getAll();
    // Durch songFromJson laufen lassen: repariert Läufe, die vor dem Fix unvollständig gespeichert wurden
    request.onsuccess = () => resolve((request.result || []).map((s: any) => songFromJson(s)));
    request.onerror = () => reject(request.error);
  });
}

export async function saveSongsDB(songs: Song[]): Promise<void> {
  const db = await openDB();
  return new Promise((resolve, reject) => {
    const tx = db.transaction('songs', 'readwrite');
    const store = tx.objectStore('songs');
    store.clear();
    songs.forEach(song => store.put(plain(song)));
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
  });
}

export async function loadSetlistsDB(): Promise<Setlist[]> {
  const db = await openDB();
  return new Promise((resolve, reject) => {
    const tx = db.transaction('setlists', 'readonly');
    const store = tx.objectStore('setlists');
    const request = store.getAll();
    request.onsuccess = () => resolve(request.result || []);
    request.onerror = () => reject(request.error);
  });
}

export async function saveSetlistsDB(setlists: Setlist[]): Promise<void> {
  const db = await openDB();
  return new Promise((resolve, reject) => {
    const tx = db.transaction('setlists', 'readwrite');
    const store = tx.objectStore('setlists');
    store.clear();
    setlists.forEach(setlist => store.put(plain(setlist)));
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
  });
}

const DEFAULT_SETTINGS: AppSettings = {
  showSongTitle: true,
  showSongPosition: true,
  showPageNumber: true,
  showPageButtons: true,
  announceSongChange: true,
  tapZonesEnabled: true,
  tapZoneSize: 'LOWER_THIRD',
  swapTapZones: false,
  pageTurnFlash: true,
  songChangeBanner: true,
  volumeKeysTurnPages: true,
  reversePedalDirection: false,
  keepScreenOn: true,
  rememberZoom: true,
  themeMode: 'SYSTEM',
  userName: '',
  userId: '',
  lastBackupAt: 0,
  noteAuthorDot: false,
  noteAuthorColoredName: true,
  noteAuthorNumber: false,
  showBackupReminder: true,
  showCopyrightWarning: true,
};

export async function loadSettingsDB(): Promise<AppSettings> {
  const db = await openDB();
  return new Promise((resolve, reject) => {
    const tx = db.transaction('settings', 'readonly');
    const store = tx.objectStore('settings');
    const request = store.get('app');
    request.onsuccess = () => {
      const stored = request.result;
      if (!stored) {
        // Ensure userId
        const userId = crypto.randomUUID();
        const initial = { ...DEFAULT_SETTINGS, userId, key: 'app' };
        saveSettingsDB(initial);
        resolve(initial);
      } else {
        resolve({ ...DEFAULT_SETTINGS, ...stored });
      }
    };
    request.onerror = () => reject(request.error);
  });
}

export async function saveSettingsDB(settings: AppSettings): Promise<void> {
  const db = await openDB();
  return new Promise((resolve, reject) => {
    const tx = db.transaction('settings', 'readwrite');
    const store = tx.objectStore('settings');
    store.put(plain({ ...settings, key: 'app' }));
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
  });
}
