import type { Song, Setlist, AppSettings } from '../model/types';
import { songFromJson } from '../logic/serialization';

const DB_NAME = 'meinenoten_db';
const DB_VERSION = 2;

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
      // Ausweichspeicher für Notendateien, wenn OPFS nicht schreibbar ist (Safari/iOS)
      if (!db.objectStoreNames.contains('files')) {
        db.createObjectStore('files');
      }
    };
  });
}

// Notendateien: bevorzugt OPFS ("opfs://ordner/datei"), sonst IndexedDB ("idb://datei").
// Safari auf iOS bietet OPFS, aber createWritable() fehlt dort je nach Version.

async function idbFileOp<T>(mode: IDBTransactionMode, op: (store: IDBObjectStore) => IDBRequest<T>): Promise<T> {
  const db = await openDB();
  return new Promise((resolve, reject) => {
    const tx = db.transaction('files', mode);
    const request = op(tx.objectStore('files'));
    tx.oncomplete = () => resolve(request.result);
    tx.onerror = () => reject(tx.error);
    tx.onabort = () => reject(tx.error ?? new Error('Speichern abgebrochen (Speicherplatz voll?)'));
  });
}

function parseFileUri(fileUri: string): { kind: 'opfs' | 'idb'; folder: string; name: string } | null {
  if (fileUri.startsWith('idb://')) return { kind: 'idb', folder: '', name: fileUri.slice(6) };
  const parts = fileUri.replace('opfs://', '').split('/');
  if (parts.length < 2) return null;
  return { kind: 'opfs', folder: parts[0], name: parts[1] };
}

export async function saveScoreFile(songId: string, extension: string, data: Blob | ArrayBuffer): Promise<string> {
  const ext = extension.toLowerCase();
  const fileName = `${songId}.${ext}`;
  const blob = data instanceof Blob ? data : new Blob([data], { type: ext === 'pdf' ? 'application/pdf' : 'application/octet-stream' });
  try {
    const root = await navigator.storage.getDirectory();
    const folderName = ext === 'pdf' ? 'songs' : 'musicxml';
    const folderHandle = await root.getDirectoryHandle(folderName, { create: true });
    const fileHandle = await folderHandle.getFileHandle(fileName, { create: true });
    if (typeof (fileHandle as any).createWritable !== 'function') throw new Error('createWritable nicht verfügbar');
    const writable = await (fileHandle as any).createWritable();
    await writable.write(blob);
    await writable.close();
    return `opfs://${folderName}/${fileName}`;
  } catch (e) {
    console.warn('OPFS nicht schreibbar, speichere Notendatei in IndexedDB', e);
    await idbFileOp('readwrite', store => store.put(blob, fileName));
    return `idb://${fileName}`;
  }
}

export async function loadScoreFile(fileUri: string): Promise<File | null> {
  const ref = fileUri ? parseFileUri(fileUri) : null;
  if (!ref) return null;
  try {
    if (ref.kind === 'idb') {
      const blob = await idbFileOp<Blob | undefined>('readonly', store => store.get(ref.name));
      return blob ? new File([blob], ref.name, { type: blob.type }) : null;
    }
    const root = await navigator.storage.getDirectory();
    const folderHandle = await root.getDirectoryHandle(ref.folder);
    const fileHandle = await folderHandle.getFileHandle(ref.name);
    return await fileHandle.getFile();
  } catch (e) {
    console.error('Notendatei nicht gefunden', fileUri, e);
    return null;
  }
}

export async function deleteScoreFile(fileUri: string): Promise<void> {
  const ref = fileUri ? parseFileUri(fileUri) : null;
  if (!ref) return;
  try {
    if (ref.kind === 'idb') {
      await idbFileOp('readwrite', store => store.delete(ref.name));
      return;
    }
    const root = await navigator.storage.getDirectory();
    const folderHandle = await root.getDirectoryHandle(ref.folder);
    await folderHandle.removeEntry(ref.name);
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

export async function deleteSongFromDB(
  songToDelete: Song,
  currentSongs: Song[],
  currentSetlists: Setlist[]
): Promise<{ songs: Song[]; setlists: Setlist[] }> {
  if (songToDelete.fileUri) {
    await deleteScoreFile(songToDelete.fileUri);
  }

  const updatedSongs = currentSongs.filter(s => s.id !== songToDelete.id);
  const updatedSetlists = currentSetlists.map(setlist => {
    if (!setlist.songIds.includes(songToDelete.id)) return setlist;
    const newSongIds = setlist.songIds.filter(id => id !== songToDelete.id);
    const lastSongId = setlist.lastSongId === songToDelete.id ? null : setlist.lastSongId;
    const lastPage = lastSongId === null ? 0 : setlist.lastPage;
    return { ...setlist, songIds: newSongIds, lastSongId, lastPage };
  });

  await saveSongsDB(updatedSongs);
  await saveSetlistsDB(updatedSetlists);

  return { songs: updatedSongs, setlists: updatedSetlists };
}

