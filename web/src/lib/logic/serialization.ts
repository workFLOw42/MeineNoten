import type { Song, SongNote, Setlist, PageView, BackupManifest, SongSource } from '../model/types';

/**
 * Umwandlung zwischen dem ZIP-Format (siehe format/BACKUP_FORMAT.md) und dem Web-Datenmodell.
 *
 * Die Android-App schreibt mit kotlinx.serialization ohne `encodeDefaults`: Felder mit
 * Standardwert (leerer Interpret, bpm 120, …) fehlen im JSON komplett. Außerdem heißen
 * die Notizen im Format anders als im Web-Modell:
 *   - JSON `notes`     (String) = alte Einzelnotiz  → Web `legacyNotes`
 *   - JSON `songNotes` (Liste)  = Notizen je Person → Web `notes`
 * Unbekannte Felder werden durchgereicht, damit sie beim erneuten Export erhalten bleiben.
 */

const str = (v: unknown, fallback = ''): string => (typeof v === 'string' ? v : fallback);
const num = (v: unknown, fallback = 0): number => (typeof v === 'number' && Number.isFinite(v) ? v : fallback);
const SOURCES: SongSource[] = ['PDF', 'MUSIC_XML', 'TEXT'];

function normalizeNote(raw: any): SongNote | null {
  if (!raw || typeof raw !== 'object' || typeof raw.authorId !== 'string') return null;
  return {
    authorId: raw.authorId,
    authorName: str(raw.authorName),
    text: str(raw.text),
    editedAt: num(raw.editedAt),
  };
}

function normalizePageViews(raw: any): Record<string, PageView> {
  const result: Record<string, PageView> = {};
  if (!raw || typeof raw !== 'object') return result;
  for (const [key, v] of Object.entries<any>(raw)) {
    result[key] = {
      scale: num(v?.scale, 1),
      offsetXRatio: num(v?.offsetXRatio),
      offsetYRatio: num(v?.offsetYRatio),
    };
  }
  return result;
}

/** Liest ein Lied aus songs.json (Android oder Web) und ergänzt fehlende Felder. */
export function songFromJson(raw: any): Song {
  const { notes: rawNotes, songNotes: rawSongNotes, ...rest } = raw ?? {};
  // Ältere Web-Exporte hatten die Liste unter `notes` – auch das akzeptieren.
  const noteList = Array.isArray(rawSongNotes) ? rawSongNotes : Array.isArray(rawNotes) ? rawNotes : [];
  return {
    ...rest,
    id: str(raw?.id) || crypto.randomUUID(),
    title: str(raw?.title),
    artist: str(raw?.artist),
    version: str(raw?.version),
    fileUri: str(raw?.fileUri),
    sourceType: SOURCES.includes(raw?.sourceType) ? raw.sourceType : 'PDF',
    genre: str(raw?.genre),
    bpm: num(raw?.bpm, 120),
    timeSignature: str(raw?.timeSignature, '4/4'),
    totalBars: num(raw?.totalBars),
    pageViews: normalizePageViews(raw?.pageViews),
    legacyNotes: typeof rawNotes === 'string' ? rawNotes : str(raw?.legacyNotes),
    notes: noteList.map(normalizeNote).filter((n: SongNote | null): n is SongNote => n !== null),
    lyrics: str(raw?.lyrics),
    lastOpenedAt: num(raw?.lastOpenedAt),
    fileHash: str(raw?.fileHash),
  };
}

/** Schreibt ein Lied im Format der Android-App (Feldnamen `notes`/`songNotes`). */
export function songToJson(song: Song): Record<string, unknown> {
  const { notes, legacyNotes, ...rest } = song;
  return { ...rest, notes: legacyNotes ?? '', songNotes: notes ?? [] };
}

export function setlistFromJson(raw: any): Setlist {
  return {
    ...(raw ?? {}),
    id: str(raw?.id) || crypto.randomUUID(),
    title: str(raw?.title),
    date: str(raw?.date),
    songIds: Array.isArray(raw?.songIds) ? raw.songIds.filter((id: unknown) => typeof id === 'string') : [],
    notes: str(raw?.notes),
    lastSongId: typeof raw?.lastSongId === 'string' ? raw.lastSongId : null,
    lastPage: num(raw?.lastPage),
    lastPlayedAt: num(raw?.lastPlayedAt),
  };
}

export function manifestFromJson(raw: any): BackupManifest {
  return {
    ...(raw ?? {}),
    formatVersion: num(raw?.formatVersion, 1),
    appVersion: str(raw?.appVersion),
    type: raw?.type === 'SETLIST' ? 'SETLIST' : 'KOMPLETT',
    createdAt: num(raw?.createdAt),
    deviceName: str(raw?.deviceName),
    authorId: str(raw?.authorId),
    authorName: str(raw?.authorName),
    title: str(raw?.title),
    fileHashes: raw?.fileHashes && typeof raw.fileHashes === 'object' ? raw.fileHashes : {},
  };
}

/**
 * Übernimmt eine alte Einzelnotiz als eigene Notiz (wie Song.migrateLegacyNote in Android).
 * Eine vorhandene eigene Notiz wird nie überschrieben.
 */
export function migrateLegacyNote(song: Song, userId: string, userName: string): Song {
  if (!song.legacyNotes.trim() || !userId) return song;
  const hasOwn = song.notes.some(n => n.authorId === userId);
  const notes = hasOwn ? song.notes : [...song.notes, { authorId: userId, authorName: userName, text: song.legacyNotes, editedAt: 0 }];
  return { ...song, legacyNotes: '', notes };
}

/** SHA-256 als Hex-String in Kleinbuchstaben (wie Android). */
export async function sha256Hex(data: ArrayBuffer): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', data);
  return Array.from(new Uint8Array(digest), b => b.toString(16).padStart(2, '0')).join('');
}
