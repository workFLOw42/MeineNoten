export type SongSource = 'PDF' | 'MUSIC_XML' | 'TEXT';

/** Darstellung der Noten im Dunkeldesign (wie ScoreDarkMode in Android). Im hellen Design immer normal. */
export type ScoreDarkMode = 'NORMAL' | 'SOFT' | 'INVERTED';

export interface PageView {
  scale: number;
  offsetXRatio: number;
  offsetYRatio: number;
}

export interface SongNote {
  authorId: string;
  authorName: string;
  text: string;
  editedAt: number;
}

export interface Song {
  id: string;
  title: string;
  artist: string;
  version: string;
  fileUri: string;
  sourceType: SongSource;
  genre: string;
  bpm: number;
  timeSignature: string;
  totalBars: number;
  pageViews: Record<string, PageView>;
  legacyNotes: string;
  notes: SongNote[];
  lyrics: string;
  lastOpenedAt: number;
  fileHash: string;
  darkMode: ScoreDarkMode;
}

export interface Setlist {
  id: string;
  title: string;
  date: string;
  songIds: string[];
  notes: string;
  lastSongId: string | null;
  lastPage: number;
  lastPlayedAt: number;
}

export type TapZoneSize = 'LOWER_THIRD' | 'LOWER_HALF' | 'FULL_HEIGHT';
export type ThemeMode = 'SYSTEM' | 'LIGHT' | 'DARK';

export interface AppSettings {
  showSongTitle: boolean;
  showSongPosition: boolean;
  showPageNumber: boolean;
  showPageButtons: boolean;
  announceSongChange: boolean;
  tapZonesEnabled: boolean;
  tapZoneSize: TapZoneSize;
  swapTapZones: boolean;
  pageTurnFlash: boolean;
  songChangeBanner: boolean;
  volumeKeysTurnPages: boolean;
  reversePedalDirection: boolean;
  keepScreenOn: boolean;
  rememberZoom: boolean;
  themeMode: ThemeMode;
  userName: string;
  userId: string;
  lastBackupAt: number;
  noteAuthorDot: boolean;
  noteAuthorColoredName: boolean;
  noteAuthorNumber: boolean;
  showBackupReminder: boolean;
  showCopyrightWarning: boolean;
}

/** Inhalt von settings.json: Einstellungen ohne Identität (userId/userName). */
export type SerializableAppSettings = Partial<Omit<AppSettings, 'userId' | 'userName' | 'lastBackupAt'>>;

export type BackupType = 'KOMPLETT' | 'SETLIST';

export type SongMatchCategory = 'IDENTICAL' | 'SAME_FILE_DIFFERENT_METADATA' | 'POSSIBLE_OTHER_VERSION' | 'NEW';
export type SongImportAction = 'KEEP_OWN' | 'TAKE_BACKUP' | 'KEEP_BOTH' | 'SKIP';

export interface BackupManifest {
  formatVersion: number;
  appVersion: string;
  type: BackupType;
  createdAt: number;
  deviceName: string;
  authorId: string;
  authorName: string;
  title: string;
  fileHashes: Record<string, string>;
}
