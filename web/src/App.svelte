<script lang="ts">
  import { onMount } from 'svelte';
  import type { Song, Setlist, AppSettings, SongSortMode, SetlistPeriod, SetlistSortMode, ScoreDarkMode } from './lib/model/types';
  import { loadSongsDB, saveSongsDB, loadSetlistsDB, saveSetlistsDB, loadSettingsDB, saveSettingsDB, saveScoreFile, loadScoreFile, deleteSongFromDB } from './lib/storage/db';
  import { loadPdfDocument } from './lib/pdf/pdfRenderer';
  import PdfViewer from './lib/pdf/PdfViewer.svelte';
  import { renderMusicXml } from './lib/musicxml/osmdRenderer';
  import {
    matchesQuery,
    sortSongs,
    possessiveName,
    mergeNotes,
    displayTitle,
    ownNote,
    otherNotes,
    authorNumbers,
    songGroupHeading,
    filterAndSortSetlists,
    formatSetlistDate,
    nextUpcomingSetlist,
  } from './lib/logic/songLogic';
  import { migrateLegacyNote, toInt, scoreDarkModeOf } from './lib/logic/serialization';
  import { createFullBackupZip, createSetlistBackupZip, generateBackupFilename } from './lib/logic/backupLogic';
  import { analyzeBackupFile, type BackupAnalysisResult } from './lib/logic/backupImportLogic';
  import SelfTest from './routes/SelfTest.svelte';
  import NoteAuthorLabel from './lib/components/NoteAuthorLabel.svelte';
  import AlphabetIndex from './lib/components/AlphabetIndex.svelte';

  let songs = $state<Song[]>([]);
  let setlists = $state<Setlist[]>([]);
  let settings = $state<AppSettings | null>(null);
  let currentSong = $state<Song | null>(null);
  let currentPage = $state<number>(0);
  let pageCount = $state<number>(0);
  let route = $state<'songs' | 'setlists' | 'setlist' | 'detail' | 'settings' | 'selftest' | 'edit' | 'compare'>('songs');

  // Geöffnete Setlist
  let currentSetlist = $state<Setlist | null>(null);
  let setlistIndex = $state<number>(-1);
  let backRoute = $state<'songs' | 'setlist'>('songs');

  // Song list search, genre, setlist filter & sort
  let searchQuery = $state<string>('');
  let selectedGenre = $state<string>('');
  let selectedSetlistId = $state<string>('');
  let sortBy = $state<SongSortMode>('ARTIST');

  // Setlist list search, period & sort
  let setlistQuery = $state<string>('');
  let setlistPeriod = $state<SetlistPeriod>('ALL');
  let setlistSortBy = $state<SetlistSortMode>('DATE');

  // Detail view state
  let showLyricsMode = $state<boolean>(false);
  let flashActive = $state<boolean>(false);
  let songChangeBannerText = $state<string | null>(null);
  let notesDropdownOpen = $state<boolean>(false);
  let shownAuthors = $state<Set<string>>(new Set());

  // Edit song state
  let editTitle = $state<string>('');
  let editArtist = $state<string>('');
  let editVersion = $state<string>('');
  let editGenre = $state<string>('');
  let editBpm = $state<number>(120);
  let editLyrics = $state<string>('');
  let editDarkMode = $state<ScoreDarkMode>('NORMAL');
  let editNoteText = $state<string>('');

  // Song to delete confirmation
  let songToDelete = $state<Song | null>(null);

  // Setlist editing modal state
  let createSetlistModalOpen = $state<boolean>(false);
  let newSetlistTitle = $state<string>('');
  let newSetlistDate = $state<string>('');
  let newSetlistNotes = $state<string>('');

  let editSetlistModalOpen = $state<boolean>(false);
  let editSetlistTitle = $state<string>('');
  let editSetlistDate = $state<string>('');
  let editSetlistNotes = $state<string>('');

  let addSongsModalOpen = $state<boolean>(false);
  let addSongsQuery = $state<string>('');

  let setlistToDelete = $state<Setlist | null>(null);
  let copyrightDialogSetlist = $state<Setlist | null>(null);
  let dontShowCopyrightWarningAgain = $state<boolean>(false);

  // Settings input state
  let inputUserName = $state<string>('');

  // Backup analysis result
  let backupAnalysis = $state<BackupAnalysisResult | null>(null);

  let osmdContainer = $state<HTMLDivElement | null>(null);
  let pdfDoc = $state<any>(null);
  let pdfError = $state<string>('');

  // Theme support
  let systemPrefersDark = $state<boolean>(
    typeof window !== 'undefined' ? window.matchMedia('(prefers-color-scheme: dark)').matches : true
  );

  onMount(async () => {
    songs = await loadSongsDB();
    setlists = await loadSetlistsDB();
    settings = await loadSettingsDB();
    inputUserName = settings?.userName || '';

    if (typeof window !== 'undefined') {
      const mq = window.matchMedia('(prefers-color-scheme: dark)');
      const handler = (e: MediaQueryListEvent) => { systemPrefersDark = e.matches; };
      mq.addEventListener('change', handler);
    }

    await requestWakeLock();

    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState === 'visible') {
        requestWakeLock();
      }
    });

    window.addEventListener('keydown', handleGlobalKey);
  });

  let isDarkTheme = $derived(
    settings?.themeMode === 'LIGHT' ? false : settings?.themeMode === 'DARK' ? true : systemPrefersDark
  );

  // Noten folgen dem Design: im hellen immer normal, im dunklen wie beim Lied eingestellt.
  // Werte wie scoreCssFilter() in Android (ScoreAppearance.kt).
  let scoreFilter = $derived.by(() => {
    const mode = isDarkTheme ? scoreDarkModeOf(currentSong?.darkMode) : 'NORMAL';
    return mode === 'SOFT' ? 'brightness(0.72) sepia(0.12)'
      : mode === 'INVERTED' ? 'invert(0.92) hue-rotate(180deg)'
      : 'none';
  });

  $effect(() => {
    if (typeof document !== 'undefined') {
      const meta = document.querySelector('meta[name="theme-color"]');
      if (meta) {
        meta.setAttribute('content', isDarkTheme ? '#121212' : '#f5f5f5');
      }
      document.body.style.backgroundColor = isDarkTheme ? '#121212' : '#f5f5f5';
      document.body.style.color = isDarkTheme ? '#ffffff' : '#1c1b1f';
    }
  });

  async function requestWakeLock() {
    if (settings?.keepScreenOn && 'wakeLock' in navigator) {
      try {
        await (navigator as any).wakeLock.request('screen');
      } catch (e) {}
    }
  }

  /** Lieder der Setlist in ihrer Reihenfolge; gelöschte Lieder fallen heraus. */
  function setlistSongs(setlist: Setlist | null): Song[] {
    if (!setlist) return [];
    const byId = new Map(songs.map(s => [s.id, s]));
    return setlist.songIds.map(id => byId.get(id)).filter((s): s is Song => !!s);
  }

  function openSetlist(setlist: Setlist) {
    currentSetlist = setlist;
    route = 'setlist';
  }

  /** Öffnet ein Lied aus der Setlist; [startPage] < 0 heißt: letzte Seite (beim Zurückblättern). */
  async function openFromSetlist(index: number, startPage = 0) {
    const list = setlistSongs(currentSetlist);
    if (index < 0 || index >= list.length) return;
    setlistIndex = index;
    backRoute = 'setlist';
    await openSong(list[index], startPage);
  }

  async function openSong(song: Song, startPage = 0) {
    if (route !== 'detail' && backRoute !== 'setlist') setlistIndex = -1;
    currentSong = song;
    currentPage = 0;
    pageCount = 0;
    pdfDoc = null;
    pdfError = '';
    showLyricsMode = song.sourceType === 'TEXT';
    notesDropdownOpen = false;
    route = 'detail';

    const now = Date.now();
    songs = songs.map(s => s.id === song.id ? { ...s, lastOpenedAt: now } : s);
    await saveSongsDB(songs);

    if (song.sourceType === 'PDF') {
      const file = await loadScoreFile(song.fileUri);
      if (!file) {
        pdfError = 'Notendatei nicht gefunden. Bitte die Sicherung erneut einlesen.';
        return;
      }
      try {
        const doc = await loadPdfDocument(file);
        pageCount = doc.numPages;
        currentPage = startPage < 0 ? doc.numPages - 1 : Math.min(startPage, doc.numPages - 1);
        pdfDoc = doc;
      } catch (e: any) {
        pdfError = `PDF konnte nicht geöffnet werden: ${e?.message ?? e}`;
      }
    } else if (song.sourceType === 'MUSIC_XML') {
      const file = await loadScoreFile(song.fileUri);
      if (file && osmdContainer) {
        await renderMusicXml(file, osmdContainer);
      }
    }
    await rememberSetlistPosition();
  }

  /** Merkt sich wie Android pro Setlist, wo zuletzt gespielt wurde. */
  async function rememberSetlistPosition() {
    if (!currentSetlist || setlistIndex < 0 || !currentSong) return;
    const updated: Setlist = { ...currentSetlist, lastSongId: currentSong.id, lastPage: currentPage, lastPlayedAt: Date.now() };
    currentSetlist = updated;
    setlists = setlists.map(s => s.id === updated.id ? updated : s);
    await saveSetlistsDB(setlists);
  }

  function closeDetail() {
    if (pageViewSaveTimer) {
      clearTimeout(pageViewSaveTimer);
      pageViewSaveTimer = null;
      saveSongsDB($state.snapshot(songs) as Song[]);
    }
    pdfDoc = null;
    if (setlistIndex >= 0 && currentSetlist) {
      route = 'setlist';
    } else {
      route = 'songs';
    }
    backRoute = 'songs';
    setlistIndex = -1;
  }

  let pageViewSaveTimer: ReturnType<typeof setTimeout> | null = null;
  function handlePageViewChange(view: import('./lib/model/types').PageView) {
    if (!currentSong || settings?.rememberZoom === false) return;
    const key = String(currentPage);
    const updated: Song = { ...currentSong, pageViews: { ...currentSong.pageViews, [key]: view } };
    currentSong = updated;
    songs = songs.map(s => s.id === updated.id ? updated : s);
    if (pageViewSaveTimer) clearTimeout(pageViewSaveTimer);
    pageViewSaveTimer = setTimeout(() => saveSongsDB(songs), 500);
  }

  function startEditSong(song: Song) {
    currentSong = song;
    editTitle = song.title;
    editArtist = song.artist;
    editVersion = song.version;
    editGenre = song.genre;
    editBpm = song.bpm;
    editLyrics = song.lyrics || '';
    editDarkMode = scoreDarkModeOf(song.darkMode);
    const own = song.notes.find(n => n.authorId === settings?.userId);
    editNoteText = own ? own.text : '';
    route = 'edit';
  }

  async function saveEditedSong() {
    if (!currentSong || !settings) return;
    const now = Date.now();
    let updatedNotes = [...currentSong.notes];
    if (editNoteText.trim()) {
      const idx = updatedNotes.findIndex(n => n.authorId === settings.userId);
      if (idx >= 0) {
        updatedNotes[idx] = { ...updatedNotes[idx], text: editNoteText, authorName: settings.userName || 'Ich', editedAt: now };
      } else {
        updatedNotes.push({ authorId: settings.userId, authorName: settings.userName || 'Ich', text: editNoteText, editedAt: now });
      }
    } else {
      updatedNotes = updatedNotes.filter(n => n.authorId !== settings.userId);
    }

    const updatedSong: Song = {
      ...currentSong,
      title: editTitle,
      artist: editArtist,
      version: editVersion,
      genre: editGenre,
      bpm: toInt(editBpm, 120, 1),
      lyrics: editLyrics,
      darkMode: editDarkMode,
      notes: updatedNotes,
    };

    songs = songs.map(s => s.id === updatedSong.id ? updatedSong : s);
    await saveSongsDB(songs);
    currentSong = updatedSong;
    route = 'detail';
  }

  async function confirmDeleteSong() {
    if (!songToDelete) return;
    const res = await deleteSongFromDB(songToDelete, songs, setlists);
    songs = res.songs;
    setlists = res.setlists;
    songToDelete = null;
    if (currentSong?.id === songToDelete?.id) {
      currentSong = null;
      route = 'songs';
    }
  }

  async function createSetlist() {
    if (!newSetlistTitle.trim()) return;
    const newSetlist: Setlist = {
      id: crypto.randomUUID(),
      title: newSetlistTitle.trim(),
      date: newSetlistDate.trim(),
      songIds: [],
      notes: newSetlistNotes.trim(),
      lastSongId: null,
      lastPage: 0,
      lastPlayedAt: 0,
    };
    setlists = [...setlists, newSetlist];
    await saveSetlistsDB(setlists);
    createSetlistModalOpen = false;
    newSetlistTitle = '';
    newSetlistDate = '';
    newSetlistNotes = '';
    openSetlist(newSetlist);
  }

  function startEditSetlist() {
    if (!currentSetlist) return;
    editSetlistTitle = currentSetlist.title;
    editSetlistDate = currentSetlist.date;
    editSetlistNotes = currentSetlist.notes;
    editSetlistModalOpen = true;
  }

  async function saveEditedSetlist() {
    if (!currentSetlist) return;
    const updated: Setlist = {
      ...currentSetlist,
      title: editSetlistTitle.trim(),
      date: editSetlistDate.trim(),
      notes: editSetlistNotes.trim(),
    };
    currentSetlist = updated;
    setlists = setlists.map(s => s.id === updated.id ? updated : s);
    await saveSetlistsDB(setlists);
    editSetlistModalOpen = false;
  }

  async function duplicateSetlist(setlistToDup: Setlist) {
    const dup: Setlist = {
      ...setlistToDup,
      id: crypto.randomUUID(),
      title: `${setlistToDup.title} (Kopie)`.trim(),
      lastSongId: null,
      lastPage: 0,
      lastPlayedAt: 0,
    };
    setlists = [...setlists, dup];
    await saveSetlistsDB(setlists);
    openSetlist(dup);
  }

  async function confirmDeleteSetlist() {
    if (!setlistToDelete) return;
    const targetId = setlistToDelete.id;
    setlists = setlists.filter(s => s.id !== targetId);
    await saveSetlistsDB(setlists);
    setlistToDelete = null;
    if (currentSetlist?.id === targetId) {
      currentSetlist = null;
      route = 'setlists';
    }
  }

  async function addSongToCurrentSetlist(songId: string) {
    if (!currentSetlist) return;
    if (currentSetlist.songIds.includes(songId)) return;
    const updated: Setlist = {
      ...currentSetlist,
      songIds: [...currentSetlist.songIds, songId],
    };
    currentSetlist = updated;
    setlists = setlists.map(s => s.id === updated.id ? updated : s);
    await saveSetlistsDB(setlists);
  }

  async function removeSongFromCurrentSetlist(index: number) {
    if (!currentSetlist) return;
    const newSongIds = [...currentSetlist.songIds];
    const removedId = newSongIds.splice(index, 1)[0];
    const lastSongId = currentSetlist.lastSongId === removedId ? null : currentSetlist.lastSongId;
    const lastPage = lastSongId === null ? 0 : currentSetlist.lastPage;

    const updated: Setlist = {
      ...currentSetlist,
      songIds: newSongIds,
      lastSongId,
      lastPage,
    };
    currentSetlist = updated;
    setlists = setlists.map(s => s.id === updated.id ? updated : s);
    await saveSetlistsDB(setlists);
  }

  async function moveSongInCurrentSetlist(fromIndex: number, toIndex: number) {
    if (!currentSetlist) return;
    if (toIndex < 0 || toIndex >= currentSetlist.songIds.length) return;
    const newSongIds = [...currentSetlist.songIds];
    const item = newSongIds.splice(fromIndex, 1)[0];
    newSongIds.splice(toIndex, 0, item);

    const updated: Setlist = {
      ...currentSetlist,
      songIds: newSongIds,
    };
    currentSetlist = updated;
    setlists = setlists.map(s => s.id === updated.id ? updated : s);
    await saveSetlistsDB(setlists);
  }

  async function shareSetlist(setlist: Setlist) {
    if (!settings) return;
    if (settings.showCopyrightWarning) {
      copyrightDialogSetlist = setlist;
      dontShowCopyrightWarningAgain = false;
    } else {
      await performShareSetlist(setlist);
    }
  }

  async function confirmCopyrightAndShare() {
    if (!copyrightDialogSetlist || !settings) return;
    const targetSetlist = copyrightDialogSetlist;
    copyrightDialogSetlist = null;

    if (dontShowCopyrightWarningAgain) {
      const updated = { ...settings, showCopyrightWarning: false };
      await updateSettings(updated);
    }

    await performShareSetlist(targetSetlist);
  }

  async function performShareSetlist(setlist: Setlist) {
    if (!settings) return;
    try {
      const blob = await createSetlistBackupZip(setlist, songs, settings, loadScoreFile);
      const filename = generateBackupFilename('SETLIST', setlist.title, settings.userName);
      const file = new File([blob], filename, { type: 'application/zip' });

      if (navigator.canShare && navigator.canShare({ files: [file] })) {
        try {
          await navigator.share({
            title: `Setlist: ${setlist.title}`,
            files: [file],
          });
          return;
        } catch (e: any) {
          if (e.name === 'AbortError') return;
        }
      }

      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = filename;
      a.click();
      URL.revokeObjectURL(url);
    } catch (err: any) {
      console.error('Fehler beim Teilen der Setlist:', err);
      alert(`Fehler beim Teilen der Setlist: ${err?.message ?? err}`);
    }
  }

  async function updateSettings(newSettings: AppSettings) {
    settings = newSettings;
    await saveSettingsDB(newSettings);
    await requestWakeLock();
  }

  async function saveSettings() {
    if (!settings) return;
    const updated = { ...settings, userName: inputUserName };
    await updateSettings(updated);
    alert('Einstellungen gespeichert!');
  }

  async function exportBackup() {
    if (!settings) return;
    const blob = await createFullBackupZip(songs, setlists, settings, loadScoreFile);
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = generateBackupFilename('KOMPLETT', undefined, settings.userName);
    a.click();
    URL.revokeObjectURL(url);
  }

  async function handleBackupFileSelected(e: Event) {
    const input = e.target as HTMLInputElement;
    if (!input.files || input.files.length === 0) return;
    const file = input.files[0];
    try {
      backupAnalysis = await analyzeBackupFile(file, songs, setlists);
      route = 'compare';
    } catch (err: any) {
      alert(`Fehler beim Einlesen der Sicherung: ${err.message}`);
    }
  }

  async function applyBackupImport() {
    if (!backupAnalysis || !settings) return;

    try {
      const analysis = backupAnalysis;

      let activeSettings = settings;
      if (analysis.adoptAuthorIdentity && analysis.manifest.authorId) {
        activeSettings = { ...activeSettings, userId: analysis.manifest.authorId, userName: analysis.manifest.authorName || activeSettings.userName };
      }
      const backupName = (analysis.settingsInBackup as any)?.userName || analysis.manifest.authorName || '';
      if (!activeSettings.userName.trim() && backupName.trim()) {
        activeSettings = { ...activeSettings, userName: backupName.trim() };
      }
      if (activeSettings !== settings) {
        settings = activeSettings;
        inputUserName = activeSettings.userName;
        await saveSettingsDB(activeSettings);
      }

      const updatedSongs = [...songs];
      const updatedSetlists = [...setlists];
      const idMap = new Map<string, string>();

      for (const item of analysis.songs) {
        const backupSong = migrateLegacyNote(item.backupSong, activeSettings.userId, activeSettings.userName);

        if (item.action === 'SKIP') {
          if (item.localSong) idMap.set(backupSong.id, item.localSong.id);
          continue;
        }

        if (item.action === 'KEEP_OWN') {
          if (item.localSong) idMap.set(backupSong.id, item.localSong.id);
          if (item.localSong && item.mergeForeignNotes) {
            const idx = updatedSongs.findIndex(s => s.id === item.localSong!.id);
            if (idx >= 0) {
              updatedSongs[idx] = { ...updatedSongs[idx], notes: mergeNotes(updatedSongs[idx].notes, backupSong.notes) };
            }
          }
          continue;
        }

        const newId = item.action === 'KEEP_BOTH' ? crypto.randomUUID() : (item.localSong?.id ?? backupSong.id);
        let newFileUri = '';
        const zipFileEntry = backupSong.fileUri ? analysis.zipFiles[backupSong.fileUri] : undefined;
        if (zipFileEntry) {
          const arrayBuffer = await zipFileEntry.async('arraybuffer');
          const ext = backupSong.fileUri.substring(backupSong.fileUri.lastIndexOf('.') + 1);
          newFileUri = await saveScoreFile(newId, ext, arrayBuffer);
        } else if (item.action === 'TAKE_BACKUP' && item.localSong?.fileUri) {
          newFileUri = item.localSong.fileUri;
        }

        const finalSong: Song = {
          ...backupSong,
          id: newId,
          fileUri: newFileUri,
          sourceType: newFileUri || backupSong.sourceType !== 'PDF' ? backupSong.sourceType : 'TEXT',
          version: item.action === 'KEEP_BOTH' ? `${backupSong.version} (Sicherung)`.trim() : backupSong.version,
          notes: item.localSong && item.action === 'TAKE_BACKUP'
            ? mergeNotes(item.localSong.notes, backupSong.notes)
            : backupSong.notes,
        };

        const existingIdx = updatedSongs.findIndex(s => s.id === finalSong.id);
        if (existingIdx >= 0) updatedSongs[existingIdx] = finalSong;
        else updatedSongs.push(finalSong);
        idMap.set(backupSong.id, finalSong.id);
      }

      for (const sItem of analysis.setlists) {
        if (!sItem.importSetlist) continue;
        const s = { ...sItem.backupSetlist, songIds: sItem.backupSetlist.songIds.map(id => idMap.get(id) ?? id) };
        const existingIdx = updatedSetlists.findIndex(set => set.id === s.id || (sItem.localSetlist && set.id === sItem.localSetlist.id));
        if (existingIdx >= 0) updatedSetlists[existingIdx] = s;
        else updatedSetlists.push(s);
      }

      songs = updatedSongs;
      setlists = updatedSetlists;
      await saveSongsDB($state.snapshot(songs) as Song[]);
      await saveSetlistsDB($state.snapshot(setlists) as Setlist[]);

      alert('Sicherung erfolgreich eingelesen!');
      route = 'songs';
      backupAnalysis = null;
    } catch (err: any) {
      console.error(err);
      alert(`Fehler beim Übernehmen der Sicherung: ${err?.message ?? err}`);
    }
  }

  async function renderCurrentPdfPage() {
    await rememberSetlistPosition();
  }

  function triggerFlash() {
    if (!settings?.pageTurnFlash) return;
    flashActive = true;
    setTimeout(() => { flashActive = false; }, 500);
  }

  function showSongBanner(title: string) {
    if (!settings?.songChangeBanner) return;
    songChangeBannerText = title;
    setTimeout(() => { songChangeBannerText = null; }, 1500);
  }

  function handleGlobalKey(e: KeyboardEvent) {
    if (route !== 'detail') return;
    let forward = false;
    let handled = false;

    if (['ArrowRight', 'ArrowDown', 'PageDown', ' '].includes(e.key)) {
      forward = true;
      handled = true;
    } else if (['ArrowLeft', 'ArrowUp', 'PageUp'].includes(e.key)) {
      forward = false;
      handled = true;
    }

    if (handled) {
      e.preventDefault();
      if (settings?.reversePedalDirection) {
        forward = !forward;
      }
      if (forward) nextPage();
      else prevPage();
    }
  }

  function nextPage() {
    const isPaged = currentSong?.sourceType === 'PDF' && !showLyricsMode;
    if (isPaged && currentPage < pageCount - 1) {
      currentPage++;
      triggerFlash();
      renderCurrentPdfPage();
    } else if (setlistIndex >= 0 && setlistIndex < setlistSongs(currentSetlist).length - 1) {
      const nextS = setlistSongs(currentSetlist)[setlistIndex + 1];
      showSongBanner(displayTitle(nextS));
      triggerFlash();
      openFromSetlist(setlistIndex + 1, 0);
    }
  }

  function prevPage() {
    const isPaged = currentSong?.sourceType === 'PDF' && !showLyricsMode;
    if (isPaged && currentPage > 0) {
      currentPage--;
      triggerFlash();
      renderCurrentPdfPage();
    } else if (setlistIndex > 0) {
      const prevS = setlistSongs(currentSetlist)[setlistIndex - 1];
      showSongBanner(displayTitle(prevS));
      triggerFlash();
      openFromSetlist(setlistIndex - 1, -1);
    }
  }

  async function handleFileUpload(e: Event) {
    const input = e.target as HTMLInputElement;
    if (!input.files || input.files.length === 0) return;
    const file = input.files[0];
    const ext = file.name.substring(file.name.lastIndexOf('.') + 1).toLowerCase();
    const isXml = ['xml', 'musicxml', 'mxl'].includes(ext);
    const sourceType = isXml ? 'MUSIC_XML' : 'PDF';
    const id = crypto.randomUUID();
    const fileUri = await saveScoreFile(id, ext, file);

    const newSong: Song = {
      id,
      title: file.name.substring(0, file.name.lastIndexOf('.')) || file.name,
      artist: '',
      version: '',
      fileUri,
      sourceType,
      genre: '',
      bpm: 120,
      timeSignature: '4/4',
      totalBars: 0,
      pageViews: {},
      legacyNotes: '',
      notes: [],
      lyrics: '',
      lastOpenedAt: Date.now(),
      fileHash: '',
      darkMode: 'NORMAL',
    };

    songs = [...songs, newSong];
    await saveSongsDB(songs);
  }

  // Derived song list calculations
  let availableGenres = $derived(
    Array.from(new Set(songs.map(s => s.genre.trim()).filter(Boolean))).sort()
  );

  let songsInFilter = $derived.by(() => {
    let list = songs;
    if (selectedSetlistId) {
      const setlist = setlists.find(s => s.id === selectedSetlistId);
      if (setlist) {
        const idSet = new Set(setlist.songIds);
        list = list.filter(s => idSet.has(s.id));
      }
    }
    if (selectedGenre) {
      list = list.filter(s => s.genre.trim() === selectedGenre);
    }
    list = list.filter(s => matchesQuery(s, searchQuery));
    const setlistObj = setlists.find(s => s.id === selectedSetlistId);
    return sortSongs(list, sortBy, setlistObj?.songIds);
  });

  let sectionLetters = $derived.by(() => {
    const mode = sortBy.toUpperCase();
    if (!['ARTIST', 'TITLE', 'GENRE'].includes(mode)) return [];
    const set = new Set<string>();
    for (const s of songsInFilter) {
      const heading = songGroupHeading(s, sortBy);
      if (heading) set.add(heading);
    }
    return Array.from(set);
  });

  function scrollToSection(letter: string) {
    const el = document.getElementById(`heading-${letter}`);
    if (el) el.scrollIntoView({ behavior: 'smooth' });
  }

  // Setlist calculations
  let songsByIdMap = $derived(new Map(songs.map(s => [s.id, s])));
  let filteredSetlists = $derived(filterAndSortSetlists(setlists, setlistQuery, setlistPeriod, setlistSortBy, songsByIdMap));
  let upcomingSetlist = $derived(nextUpcomingSetlist(setlists));

  let titleName = $derived(settings?.userName ? possessiveName(settings.userName) + ' Noten' : 'Meine Noten');
  let currentAuthorNumbers = $derived(authorNumbers(songs));

  // Navigation helpers for detail view
  let currentSetlistSongs = $derived(setlistSongs(currentSetlist));
  let hasPreviousSong = $derived(setlistIndex > 0);
  let hasNextSong = $derived(setlistIndex >= 0 && setlistIndex < currentSetlistSongs.length - 1);

  let hasPreviousPage = $derived(currentSong?.sourceType === 'PDF' && !showLyricsMode && currentPage > 0);
  let hasNextPage = $derived(currentSong?.sourceType === 'PDF' && !showLyricsMode && currentPage < pageCount - 1);

  let canGoPrevious = $derived(hasPreviousPage || hasPreviousSong);
  let canGoNext = $derived(hasNextPage || hasNextSong);

  let previousIsSongChange = $derived(
    !!settings?.announceSongChange &&
    (currentSong?.sourceType !== 'PDF' || showLyricsMode || currentPage === 0) &&
    hasPreviousSong
  );

  let nextIsSongChange = $derived(
    !!settings?.announceSongChange &&
    (currentSong?.sourceType !== 'PDF' || showLyricsMode || currentPage >= pageCount - 1) &&
    hasNextSong
  );

  let currentOwnNote = $derived(currentSong && settings ? ownNote(currentSong.notes, settings.userId) : undefined);
  let currentOtherNotes = $derived(currentSong && settings ? otherNotes(currentSong.notes, settings.userId) : []);
  let hasNotes = $derived(!!(currentOwnNote?.text.trim() || currentOtherNotes.length > 0));
</script>

<main
  class="app-root"
  data-theme={isDarkTheme ? 'dark' : 'light'}
  style="position: fixed; inset: 0; display: flex; flex-direction: column; background: var(--bg-primary); color: var(--text-primary); box-sizing: border-box;"
>
  {#if route !== 'detail' && route !== 'edit' && route !== 'selftest' && route !== 'compare' && route !== 'setlist'}
    <!-- Navigation Bar -->
    <nav style="display: flex; background: var(--bg-surface); border-bottom: 1px solid var(--border-color); padding: 0 16px; overflow-x: auto; flex-shrink: 0;">
      <button onclick={() => route = 'songs'} style="background: none; border: none; padding: 14px 20px; color: {route === 'songs' ? '#2196f3' : 'var(--text-secondary)'}; font-weight: 500; cursor: pointer; border-bottom: 2px solid {route === 'songs' ? '#2196f3' : 'transparent'};">Lieder ({songs.length})</button>
      <button onclick={() => route = 'setlists'} style="background: none; border: none; padding: 14px 20px; color: {route === 'setlists' ? '#2196f3' : 'var(--text-secondary)'}; font-weight: 500; cursor: pointer; border-bottom: 2px solid {route === 'setlists' ? '#2196f3' : 'transparent'};">Setlists ({setlists.length})</button>
      <button onclick={() => route = 'settings'} style="background: none; border: none; padding: 14px 20px; color: {route === 'settings' ? '#2196f3' : 'var(--text-secondary)'}; font-weight: 500; cursor: pointer; border-bottom: 2px solid {route === 'settings' ? '#2196f3' : 'transparent'};">Einstellungen & Sicherung</button>
    </nav>
  {/if}

  {#if route === 'selftest'}
    <div style="padding: 12px; background: var(--bg-surface); display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid var(--border-color);">
      <button onclick={() => route = 'settings'} style="background: var(--bg-surface-secondary); color: inherit; border: 1px solid var(--border-color); padding: 8px 16px; border-radius: 4px; cursor: pointer;">← Zurück</button>
      <h3>Selbsttest</h3>
      <div></div>
    </div>
    <div style="flex: 1; min-height: 0; overflow-y: auto; -webkit-overflow-scrolling: touch;">
      <SelfTest />
    </div>
  {:else if route === 'compare'}
    <div style="padding: 16px 24px; background: var(--bg-surface); display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid var(--border-color);">
      <button onclick={() => route = 'settings'} style="background: var(--bg-surface-secondary); color: inherit; border: 1px solid var(--border-color); padding: 8px 16px; border-radius: 4px; cursor: pointer;">Abbrechen</button>
      <h3>Sicherung vergleichen & einlesen</h3>
      <button onclick={applyBackupImport} style="background: #4caf50; color: white; border: none; padding: 8px 16px; border-radius: 4px; cursor: pointer; font-weight: 500;">Jetzt importieren</button>
    </div>
    <div class="scroll" style="padding: 24px; max-width: 800px; margin: 0 auto; width: 100%; display: flex; flex-direction: column; gap: 16px;">
      {#if backupAnalysis}
        <div style="background: var(--bg-surface); padding: 16px; border-radius: 8px; border: 1px solid var(--border-color);">
          <strong>{backupAnalysis.manifest.title}</strong> · {backupAnalysis.manifest.authorName || 'Unbekannt'} · {backupAnalysis.songs.length} Lieder, {backupAnalysis.setlists.length} Setlists
        </div>

        {#if backupAnalysis.manifest.authorId && backupAnalysis.manifest.authorId !== settings?.userId}
          <label style="background: var(--bg-surface-secondary); padding: 12px; border-radius: 8px; display: flex; gap: 12px; align-items: center; cursor: pointer;">
            <input type="checkbox" bind:checked={backupAnalysis.adoptAuthorIdentity} />
            <span>Identität von <strong>{backupAnalysis.manifest.authorName}</strong> übernehmen (Notizen bleiben den eigenen zugeordnet)</span>
          </label>
        {/if}

        <h4>Lieder ({backupAnalysis.songs.length})</h4>
        <div style="display: flex; flex-direction: column; gap: 8px;">
          {#each backupAnalysis.songs as item}
            <div style="background: var(--bg-surface); padding: 12px; border-radius: 8px; display: flex; justify-content: space-between; align-items: center; border: 1px solid var(--border-color);">
              <div>
                <div style="font-weight: 500;">{item.backupSong.title}</div>
                <div style="font-size: 12px; color: var(--text-secondary);">Kategorie: {item.category}</div>
              </div>
              <select bind:value={item.action} style="background: var(--bg-surface-secondary); color: inherit; border: 1px solid var(--border-color); padding: 6px; border-radius: 4px;">
                <option value="KEEP_OWN">Meins behalten</option>
                <option value="TAKE_BACKUP">Sicherung übernehmen</option>
                <option value="KEEP_BOTH">Beide behalten</option>
                <option value="SKIP">Überspringen</option>
              </select>
            </div>
          {/each}
        </div>
      {/if}
    </div>
  {:else if route === 'settings' && settings}
    <div class="scroll" style="padding: 24px; max-width: 650px; margin: 0 auto; width: 100%; display: flex; flex-direction: column; gap: 24px;">
      <h2>Einstellungen & Datensicherung</h2>

      <!-- Statusleiste in Notenansicht -->
      <section style="background: var(--bg-surface); padding: 16px; border-radius: 8px; border: 1px solid var(--border-color); display: flex; flex-direction: column; gap: 12px;">
        <h3 style="margin: 0 0 4px 0; font-size: 16px;">Statusleiste in Notenansicht</h3>
        <label style="display: flex; justify-content: space-between; align-items: center; cursor: pointer;">
          <span>Liedtitel (2. Zeile) anzeigen</span>
          <input type="checkbox" bind:checked={settings.showSongTitle} onchange={() => updateSettings(settings!)} />
        </label>
        <label style="display: flex; justify-content: space-between; align-items: center; cursor: pointer;">
          <span>Position „Lied x/n“ anzeigen</span>
          <input type="checkbox" bind:checked={settings.showSongPosition} onchange={() => updateSettings(settings!)} />
        </label>
        <label style="display: flex; justify-content: space-between; align-items: center; cursor: pointer;">
          <span>Seite „Seite x/n“ anzeigen</span>
          <input type="checkbox" bind:checked={settings.showPageNumber} onchange={() => updateSettings(settings!)} />
        </label>
        <label style="display: flex; justify-content: space-between; align-items: center; cursor: pointer;">
          <span>Blättern-Knöpfe ◀ ▶ anzeigen</span>
          <input type="checkbox" bind:checked={settings.showPageButtons} onchange={() => updateSettings(settings!)} />
        </label>
        <label style="display: flex; justify-content: space-between; align-items: center; cursor: pointer;">
          <span>Liedwechsel auf letzter Seite ankündigen (⏭ / ⏮)</span>
          <input type="checkbox" bind:checked={settings.announceSongChange} onchange={() => updateSettings(settings!)} />
        </label>
      </section>

      <!-- Blättern -->
      <section style="background: var(--bg-surface); padding: 16px; border-radius: 8px; border: 1px solid var(--border-color); display: flex; flex-direction: column; gap: 12px;">
        <h3 style="margin: 0 0 4px 0; font-size: 16px;">Blättern</h3>
        <label style="display: flex; justify-content: space-between; align-items: center; cursor: pointer;">
          <span>Tippzonen unten aktivieren</span>
          <input type="checkbox" bind:checked={settings.tapZonesEnabled} onchange={() => updateSettings(settings!)} />
        </label>
        {#if settings.tapZonesEnabled}
          <label style="display: flex; justify-content: space-between; align-items: center; cursor: pointer;">
            <span>Tippzonen-Größe</span>
            <select bind:value={settings.tapZoneSize} onchange={() => updateSettings(settings!)} style="padding: 4px 8px; background: var(--bg-surface-secondary); color: inherit; border: 1px solid var(--border-color); border-radius: 4px;">
              <option value="LOWER_THIRD">Unteres Drittel</option>
              <option value="LOWER_HALF">Untere Hälfte</option>
              <option value="FULL_HEIGHT">Volle Höhe</option>
            </select>
          </label>
          <label style="display: flex; justify-content: space-between; align-items: center; cursor: pointer;">
            <span>Tippzonen tauschen (rechts = zurück)</span>
            <input type="checkbox" bind:checked={settings.swapTapZones} onchange={() => updateSettings(settings!)} />
          </label>
        {/if}
        <label style="display: flex; justify-content: space-between; align-items: center; cursor: pointer;">
          <span>Grüner Randblitz beim Blättern</span>
          <input type="checkbox" bind:checked={settings.pageTurnFlash} onchange={() => updateSettings(settings!)} />
        </label>
        <label style="display: flex; justify-content: space-between; align-items: center; cursor: pointer;">
          <span>Titel bei Liedwechsel einblenden</span>
          <input type="checkbox" bind:checked={settings.songChangeBanner} onchange={() => updateSettings(settings!)} />
        </label>
      </section>

      <!-- Pedal & Tasten -->
      <section style="background: var(--bg-surface); padding: 16px; border-radius: 8px; border: 1px solid var(--border-color); display: flex; flex-direction: column; gap: 12px;">
        <h3 style="margin: 0 0 4px 0; font-size: 16px;">Pedal & Tasten</h3>
        <label style="display: flex; justify-content: space-between; align-items: center; cursor: pointer;">
          <span>Pedal-Richtung umkehren</span>
          <input type="checkbox" bind:checked={settings.reversePedalDirection} onchange={() => updateSettings(settings!)} />
        </label>
      </section>

      <!-- Anzeige -->
      <section style="background: var(--bg-surface); padding: 16px; border-radius: 8px; border: 1px solid var(--border-color); display: flex; flex-direction: column; gap: 12px;">
        <h3 style="margin: 0 0 4px 0; font-size: 16px;">Anzeige</h3>
        <label style="display: flex; justify-content: space-between; align-items: center; cursor: pointer;">
          <span>Display eingeschaltet lassen (Wake Lock)</span>
          <input type="checkbox" bind:checked={settings.keepScreenOn} onchange={() => updateSettings(settings!)} />
        </label>
        <label style="display: flex; justify-content: space-between; align-items: center; cursor: pointer;">
          <span>Zoom pro Seite merken</span>
          <input type="checkbox" bind:checked={settings.rememberZoom} onchange={() => updateSettings(settings!)} />
        </label>
        <label style="display: flex; justify-content: space-between; align-items: center; cursor: pointer;">
          <span>Design</span>
          <select bind:value={settings.themeMode} onchange={() => updateSettings(settings!)} style="padding: 4px 8px; background: var(--bg-surface-secondary); color: inherit; border: 1px solid var(--border-color); border-radius: 4px;">
            <option value="SYSTEM">System</option>
            <option value="LIGHT">Hell</option>
            <option value="DARK">Dunkel</option>
          </select>
        </label>
      </section>

      <!-- Person -->
      <section style="background: var(--bg-surface); padding: 16px; border-radius: 8px; border: 1px solid var(--border-color); display: flex; flex-direction: column; gap: 12px;">
        <h3 style="margin: 0 0 4px 0; font-size: 16px;">Person</h3>
        <label style="display: flex; flex-direction: column; gap: 6px;">
          <span>Dein Name (für Noten-Verfasser):</span>
          <input type="text" bind:value={inputUserName} style="padding: 10px; background: var(--bg-surface-secondary); border: 1px solid var(--border-color); color: inherit; border-radius: 6px;" />
        </label>
        <button onclick={saveSettings} style="background: #2196f3; color: white; border: none; padding: 10px 20px; border-radius: 6px; cursor: pointer; font-size: 15px; align-self: flex-start;">Namen speichern</button>
      </section>

      <!-- Notizen anderer -->
      <section style="background: var(--bg-surface); padding: 16px; border-radius: 8px; border: 1px solid var(--border-color); display: flex; flex-direction: column; gap: 12px;">
        <h3 style="margin: 0 0 4px 0; font-size: 16px;">Notizen anderer</h3>
        <label style="display: flex; justify-content: space-between; align-items: center; cursor: pointer;">
          <span>Farbiger Punkt vor dem Namen</span>
          <input type="checkbox" bind:checked={settings.noteAuthorDot} onchange={() => updateSettings(settings!)} />
        </label>
        <label style="display: flex; justify-content: space-between; align-items: center; cursor: pointer;">
          <span>Name in Personenfarbe</span>
          <input type="checkbox" bind:checked={settings.noteAuthorColoredName} onchange={() => updateSettings(settings!)} />
        </label>
        <label style="display: flex; justify-content: space-between; align-items: center; cursor: pointer;">
          <span>Nummer bei gleichen Namen („Anna · 2“)</span>
          <input type="checkbox" bind:checked={settings.noteAuthorNumber} onchange={() => updateSettings(settings!)} />
        </label>
      </section>

      <!-- Datensicherung -->
      <section style="background: var(--bg-surface); padding: 16px; border-radius: 8px; border: 1px solid var(--border-color); display: flex; flex-direction: column; gap: 12px;">
        <h3 style="margin: 0 0 4px 0; font-size: 16px;">Datensicherung</h3>
        <p style="color: var(--text-secondary); font-size: 14px; margin: 0;">Sichere deine gesamte Sammlung inklusive Notendateien und Einstellungen als ZIP-Datei.</p>
        <div style="display: flex; gap: 12px; flex-wrap: wrap;">
          <button onclick={exportBackup} style="background: #4caf50; color: white; border: none; padding: 12px 20px; border-radius: 6px; cursor: pointer; font-size: 15px;">Alles sichern (ZIP)</button>
          <label style="background: #ff9800; color: white; padding: 12px 20px; border-radius: 6px; cursor: pointer; font-size: 15px; display: inline-flex; align-items: center;">
            Sicherung einlesen
            <input type="file" accept=".zip" onchange={handleBackupFileSelected} style="display: none;" />
          </label>
        </div>
        <label style="display: flex; justify-content: space-between; align-items: center; cursor: pointer; margin-top: 8px;">
          <span>30-Tage-Erinnerung</span>
          <input type="checkbox" bind:checked={settings.showBackupReminder} onchange={() => updateSettings(settings!)} />
        </label>
        <label style="display: flex; justify-content: space-between; align-items: center; cursor: pointer;">
          <span>Urheberrechtshinweis beim Teilen</span>
          <input type="checkbox" bind:checked={settings.showCopyrightWarning} onchange={() => updateSettings(settings!)} />
        </label>
      </section>

      <!-- Version und Autor -->
      <footer style="margin-top: 16px; padding-bottom: 24px; display: flex; flex-direction: column; align-items: center; gap: 2px; color: var(--text-secondary);">
        <button onclick={() => route = 'selftest'} style="background: none; border: none; padding: 0; font: inherit; font-size: 12px; color: inherit; cursor: pointer;" title="Build {__BUILD_STAMP__} UTC – tippen für Selbsttest">Webversion {__WEB_VERSION__}</button>
        <span style="font-size: 12px; font-weight: 500; letter-spacing: 0.3px;">by workFLOw42 · ©2026</span>
      </footer>
    </div>
  {:else if route === 'setlists'}
    <div class="scroll" style="padding: 24px; max-width: 800px; margin: 0 auto; width: 100%;">
      <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; flex-wrap: wrap; gap: 12px;">
        <h2 style="margin: 0;">Setlists</h2>
        <div style="display: flex; gap: 8px; align-items: center; flex-wrap: wrap;">
          <button onclick={() => createSetlistModalOpen = true} style="background: #2196f3; color: white; border: none; padding: 8px 16px; border-radius: 6px; cursor: pointer; font-size: 14px; font-weight: 500;">+ Neue Setlist</button>
          <div style="display: flex; gap: 4px; background: var(--bg-surface-secondary); padding: 2px; border-radius: 20px; border: 1px solid var(--border-color);">
            <button onclick={() => setlistPeriod = 'ALL'} style="padding: 4px 10px; border-radius: 16px; border: none; background: {setlistPeriod === 'ALL' ? '#2196f3' : 'transparent'}; color: {setlistPeriod === 'ALL' ? '#fff' : 'inherit'}; cursor: pointer; font-size: 12px;">Alle</button>
            <button onclick={() => setlistPeriod = 'UPCOMING'} style="padding: 4px 10px; border-radius: 16px; border: none; background: {setlistPeriod === 'UPCOMING' ? '#2196f3' : 'transparent'}; color: {setlistPeriod === 'UPCOMING' ? '#fff' : 'inherit'}; cursor: pointer; font-size: 12px;">Kommende</button>
            <button onclick={() => setlistPeriod = 'PAST'} style="padding: 4px 10px; border-radius: 16px; border: none; background: {setlistPeriod === 'PAST' ? '#2196f3' : 'transparent'}; color: {setlistPeriod === 'PAST' ? '#fff' : 'inherit'}; cursor: pointer; font-size: 12px;">Vergangene</button>
          </div>
        </div>
      </div>

      <div style="display: flex; gap: 12px; margin-bottom: 20px; align-items: center;">
        <input type="text" placeholder="Setlists oder enthaltene Lieder suchen..." bind:value={setlistQuery} style="flex: 1; padding: 10px 14px; background: var(--bg-surface-secondary); border: 1px solid var(--border-color); color: inherit; border-radius: 6px; font-size: 14px;" />
        <select bind:value={setlistSortBy} style="padding: 10px 12px; background: var(--bg-surface-secondary); border: 1px solid var(--border-color); color: inherit; border-radius: 6px; font-size: 14px;">
          <option value="DATE">Nach Datum</option>
          <option value="TITLE">Nach Titel</option>
          <option value="RECENT">Zuletzt gespielt</option>
        </select>
      </div>

      {#if filteredSetlists.length === 0}
        <p style="color: var(--text-secondary);">Keine Setlists gefunden.</p>
      {:else}
        <div style="display: flex; flex-direction: column; gap: 12px;">
          {#each filteredSetlists as setlist}
            {@const isUpcoming = upcomingSetlist?.id === setlist.id}
            <div style="background: var(--bg-surface); padding: 16px; border-radius: 8px; border: 1px solid {isUpcoming ? '#2196f3' : 'var(--border-color)'}; display: flex; justify-content: space-between; align-items: center; position: relative;">
              <div role="button" tabindex="0" onclick={() => openSetlist(setlist)} onkeydown={(e) => e.key === 'Enter' && openSetlist(setlist)} style="cursor: pointer; flex: 1; min-width: 0; padding-right: 12px;">
                {#if isUpcoming}
                  <span style="display: inline-block; background: #2196f3; color: white; font-size: 11px; padding: 2px 8px; border-radius: 10px; font-weight: 500; margin-bottom: 4px;">Nächster Auftritt</span>
                {/if}
                <div style="font-size: 16px; font-weight: 500;">{setlist.title}</div>
                <div style="font-size: 13px; color: var(--text-secondary); margin-top: 4px;">
                  {setlistSongs(setlist).length} Lieder · {formatSetlistDate(setlist.date) || 'Kein Datum'}
                </div>
                {#if setlist.notes}
                  <div style="font-size: 12px; color: var(--text-secondary); margin-top: 6px; font-style: italic;">{setlist.notes}</div>
                {/if}
              </div>
              <div style="display: flex; gap: 6px; align-items: center;">
                <button
                  onclick={() => shareSetlist(setlist)}
                  style="background: #4caf50; color: white; border: none; padding: 6px 10px; border-radius: 4px; cursor: pointer; font-size: 13px; font-weight: 500;"
                  title="Setlist teilen"
                >
                  Teilen 📤
                </button>
                <button
                  onclick={() => duplicateSetlist(setlist)}
                  style="background: var(--bg-surface-secondary); border: 1px solid var(--border-color); color: inherit; padding: 6px 10px; border-radius: 4px; cursor: pointer; font-size: 13px;"
                  title="Setlist duplizieren"
                >
                  📋 Duplizieren
                </button>
                <button
                  onclick={() => setlistToDelete = setlist}
                  style="background: var(--bg-surface-secondary); border: 1px solid var(--border-color); color: inherit; padding: 6px 10px; border-radius: 4px; cursor: pointer; font-size: 13px;"
                  title="Setlist löschen"
                >
                  🗑
                </button>
              </div>
            </div>
          {/each}
        </div>
      {/if}
    </div>
  {:else if route === 'setlist' && currentSetlist}
    {@const list = setlistSongs(currentSetlist)}
    {@const resumeIdx = currentSetlist.lastSongId ? list.findIndex(s => s.id === currentSetlist!.lastSongId) : -1}
    <div style="padding: 12px 16px; background: var(--bg-surface); display: flex; gap: 12px; align-items: center; border-bottom: 1px solid var(--border-color); flex-wrap: wrap; justify-content: space-between;">
      <div style="display: flex; gap: 12px; align-items: center; min-width: 0;">
        <button onclick={() => { currentSetlist = null; route = 'setlists'; }} style="background: var(--bg-surface-secondary); color: inherit; border: 1px solid var(--border-color); padding: 8px 16px; border-radius: 4px; cursor: pointer;">← Zurück</button>
        <div style="min-width: 0;">
          <div style="font-size: 17px; font-weight: 500; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">{currentSetlist.title}</div>
          <div style="font-size: 13px; color: var(--text-secondary);">{list.length} Lieder · {formatSetlistDate(currentSetlist.date) || 'Kein Datum'}</div>
        </div>
      </div>
      <div style="display: flex; gap: 8px; align-items: center; flex-wrap: wrap;">
        <button onclick={() => currentSetlist && shareSetlist(currentSetlist)} style="background: #4caf50; color: white; border: none; padding: 8px 14px; border-radius: 4px; cursor: pointer; font-size: 13px; font-weight: 500;">Teilen 📤</button>
        <button onclick={() => addSongsModalOpen = true} style="background: #2196f3; color: white; border: none; padding: 8px 14px; border-radius: 4px; cursor: pointer; font-size: 13px; font-weight: 500;">+ Lieder</button>
        <button onclick={startEditSetlist} style="background: var(--bg-surface-secondary); color: inherit; border: 1px solid var(--border-color); padding: 8px 14px; border-radius: 4px; cursor: pointer; font-size: 13px;">Bearbeiten</button>
        <button onclick={() => currentSetlist && duplicateSetlist(currentSetlist)} style="background: var(--bg-surface-secondary); color: inherit; border: 1px solid var(--border-color); padding: 8px 14px; border-radius: 4px; cursor: pointer; font-size: 13px;">Duplizieren</button>
        <button onclick={() => setlistToDelete = currentSetlist} style="background: var(--bg-surface-secondary); color: inherit; border: 1px solid var(--border-color); padding: 8px 14px; border-radius: 4px; cursor: pointer; font-size: 13px;">Löschen</button>
      </div>
    </div>
    <div style="flex: 1; min-height: 0; overflow-y: auto; -webkit-overflow-scrolling: touch; padding: 16px; max-width: 800px; margin: 0 auto; width: 100%; box-sizing: border-box; display: flex; flex-direction: column; gap: 8px;">
      {#if list.length > 0}
        <div style="display: flex; gap: 8px; margin-bottom: 8px; flex-wrap: wrap;">
          <button onclick={() => openFromSetlist(0, 0)} style="background: #2196f3; color: white; border: none; padding: 12px 18px; border-radius: 6px; cursor: pointer; font-size: 15px;">▶ Von vorne</button>
          {#if resumeIdx >= 0}
            <button onclick={() => openFromSetlist(resumeIdx, currentSetlist!.lastPage)} style="background: #4caf50; color: white; border: none; padding: 12px 18px; border-radius: 6px; cursor: pointer; font-size: 15px;">Weiter bei „{displayTitle(list[resumeIdx])}“</button>
          {/if}
        </div>
      {:else}
        <p style="color: var(--text-secondary);">Diese Setlist enthält noch keine Lieder. Klicke oben auf <strong>+ Lieder</strong>, um Lieder hinzuzufügen.</p>
      {/if}
      {#each list as song, i}
        <div style="background: var(--bg-surface); padding: 10px 14px; border-radius: 8px; border: 1px solid {i === resumeIdx ? '#4caf50' : 'var(--border-color)'}; display: flex; gap: 10px; align-items: center; width: 100%;">
          <button onclick={() => openFromSetlist(i, 0)} style="text-align: left; color: inherit; font: inherit; background: none; border: none; padding: 0; cursor: pointer; display: flex; gap: 12px; align-items: center; flex: 1; min-width: 0;">
            <span style="color: var(--text-secondary); min-width: 1.5em; text-align: right;">{i + 1}.</span>
            <span style="min-width: 0;">
              <span style="display: block; font-size: 16px; font-weight: 500; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">{displayTitle(song)}</span>
              {#if song.artist}
                <span style="display: block; font-size: 13px; color: var(--text-secondary); margin-top: 2px;">{song.artist}</span>
              {/if}
            </span>
          </button>
          <div style="display: flex; gap: 4px; align-items: center;">
            <button
              onclick={() => moveSongInCurrentSetlist(i, i - 1)}
              disabled={i === 0}
              style="background: var(--bg-surface-secondary); color: inherit; border: 1px solid var(--border-color); padding: 4px 8px; border-radius: 4px; cursor: pointer; opacity: {i === 0 ? 0.3 : 1};"
              title="Nach oben verschieben"
            >
              ▲
            </button>
            <button
              onclick={() => moveSongInCurrentSetlist(i, i + 1)}
              disabled={i === list.length - 1}
              style="background: var(--bg-surface-secondary); color: inherit; border: 1px solid var(--border-color); padding: 4px 8px; border-radius: 4px; cursor: pointer; opacity: {i === list.length - 1 ? 0.3 : 1};"
              title="Nach unten verschieben"
            >
              ▼
            </button>
            <button
              onclick={() => removeSongFromCurrentSetlist(i)}
              style="background: var(--bg-surface-secondary); color: #f44336; border: 1px solid var(--border-color); padding: 4px 8px; border-radius: 4px; cursor: pointer;"
              title="Aus Setlist entfernen"
            >
              ✕
            </button>
          </div>
        </div>
      {/each}
    </div>
  {:else if route === 'edit'}
    <div style="padding: 12px 24px; background: var(--bg-surface); display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid var(--border-color);">
      <button onclick={() => route = 'detail'} style="background: var(--bg-surface-secondary); color: inherit; border: 1px solid var(--border-color); padding: 8px 16px; border-radius: 4px; cursor: pointer;">Abbrechen</button>
      <h3>Lied bearbeiten</h3>
      <button onclick={saveEditedSong} style="background: #2196f3; color: white; border: none; padding: 8px 16px; border-radius: 4px; cursor: pointer;">Speichern</button>
    </div>
    <div class="scroll" style="padding: 24px; max-width: 600px; margin: 0 auto; width: 100%; display: flex; flex-direction: column; gap: 16px;">
      <label style="display: flex; flex-direction: column; gap: 4px;">
        Titel:
        <input type="text" bind:value={editTitle} style="padding: 8px; background: var(--bg-surface-secondary); border: 1px solid var(--border-color); color: inherit; border-radius: 4px;" />
      </label>
      <label style="display: flex; flex-direction: column; gap: 4px;">
        Künstler:
        <input type="text" bind:value={editArtist} style="padding: 8px; background: var(--bg-surface-secondary); border: 1px solid var(--border-color); color: inherit; border-radius: 4px;" />
      </label>
      <label style="display: flex; flex-direction: column; gap: 4px;">
        Fassung / Untertitel:
        <input type="text" bind:value={editVersion} style="padding: 8px; background: var(--bg-surface-secondary); border: 1px solid var(--border-color); color: inherit; border-radius: 4px;" />
      </label>
      <label style="display: flex; flex-direction: column; gap: 4px;">
        Genre / Anlass:
        <input type="text" bind:value={editGenre} style="padding: 8px; background: var(--bg-surface-secondary); border: 1px solid var(--border-color); color: inherit; border-radius: 4px;" />
      </label>
      <label style="display: flex; flex-direction: column; gap: 4px;">
        BPM (Tempo):
        <input type="number" inputmode="numeric" min="1" step="1" bind:value={editBpm} style="padding: 8px; background: var(--bg-surface-secondary); border: 1px solid var(--border-color); color: inherit; border-radius: 4px;" />
      </label>
      <label style="display: flex; flex-direction: column; gap: 4px;">
        Liedtext:
        <textarea bind:value={editLyrics} rows="5" style="padding: 8px; background: var(--bg-surface-secondary); border: 1px solid var(--border-color); color: inherit; border-radius: 4px; resize: vertical;"></textarea>
      </label>
      <label style="display: flex; flex-direction: column; gap: 4px;">
        Persönliche Notiz:
        <textarea bind:value={editNoteText} rows="3" style="padding: 8px; background: var(--bg-surface-secondary); border: 1px solid var(--border-color); color: inherit; border-radius: 4px; resize: vertical;"></textarea>
      </label>
      {#if currentSong?.fileUri}
        <div style="display: flex; flex-direction: column; gap: 6px;">
          <span>Noten im Dunkeldesign:</span>
          <div style="display: flex; border: 1px solid var(--border-color); border-radius: 6px; overflow: hidden;">
            {#each [['NORMAL', 'Normal'], ['SOFT', 'Dezenter'], ['INVERTED', 'Invertiert']] as [value, label]}
              <button
                onclick={() => editDarkMode = value as ScoreDarkMode}
                style="flex: 1; padding: 8px; border: none; cursor: pointer; background: {editDarkMode === value ? '#2196f3' : 'var(--bg-surface-secondary)'}; color: {editDarkMode === value ? 'white' : 'inherit'};"
              >{label}</button>
            {/each}
          </div>
          <span style="font-size: 12px; color: var(--text-secondary);">Wirkt nur, solange die App dunkel ist. Im hellen Design werden die Noten immer normal gezeigt.</span>
        </div>
      {/if}
    </div>
  {:else if route === 'songs'}
    <header style="padding: 16px 24px; background: var(--bg-surface); display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid var(--border-color);">
      <h1 style="margin: 0; font-size: 20px;">{titleName}</h1>
      <label style="background: #2196f3; color: white; padding: 8px 16px; border-radius: 6px; cursor: pointer; font-size: 14px;">
        Noten importieren (PDF / MusicXML)
        <input type="file" accept=".pdf,.xml,.musicxml,.mxl" onchange={handleFileUpload} style="display: none;" />
      </label>
    </header>

    <div style="padding: 12px 24px; background: var(--bg-surface-secondary); display: flex; flex-wrap: wrap; gap: 10px; border-bottom: 1px solid var(--border-color); align-items: center;">
      <input type="text" placeholder="Suchen nach Titel, Künstler, Anlass..." bind:value={searchQuery} style="flex: 1; min-width: 200px; padding: 8px 12px; background: var(--bg-surface); border: 1px solid var(--border-color); color: inherit; border-radius: 6px; font-size: 14px;" />

      {#if availableGenres.length > 0}
        <select bind:value={selectedGenre} style="padding: 8px 12px; background: var(--bg-surface); border: 1px solid var(--border-color); color: inherit; border-radius: 6px; font-size: 14px;">
          <option value="">Alle Genres</option>
          {#each availableGenres as g}
            <option value={g}>{g}</option>
          {/each}
        </select>
      {/if}

      {#if setlists.length > 0}
        <select bind:value={selectedSetlistId} style="padding: 8px 12px; background: var(--bg-surface); border: 1px solid var(--border-color); color: inherit; border-radius: 6px; font-size: 14px;">
          <option value="">Alle Lieder</option>
          {#each setlists as s}
            <option value={s.id}>Setlist: {s.title}</option>
          {/each}
        </select>
      {/if}

      <select bind:value={sortBy} style="padding: 8px 12px; background: var(--bg-surface); border: 1px solid var(--border-color); color: inherit; border-radius: 6px; font-size: 14px;">
        <option value="ARTIST">Nach Künstler</option>
        <option value="TITLE">Nach Titel</option>
        <option value="RECENT">Zuletzt geöffnet</option>
        <option value="GENRE">Nach Genre</option>
        {#if selectedSetlistId}
          <option value="SETLIST_ORDER">Setlist-Reihenfolge</option>
        {/if}
      </select>
    </div>

    <div class="scroll" style="padding: 24px; max-width: 800px; margin: 0 auto; width: 100%; position: relative;">
      {#if songsInFilter.length === 0}
        <div style="text-align: center; color: var(--text-secondary); margin-top: 60px;">
          <p style="font-size: 18px;">Keine Noten gefunden.</p>
          {#if songs.length === 0}
            <p>Klicke oben auf *Noten importieren*, um eine PDF- oder MusicXML-Datei hinzuzufügen.</p>
          {/if}
        </div>
      {:else}
        <AlphabetIndex letters={sectionLetters} onSelect={scrollToSection} />

        <div style="display: flex; flex-direction: column; gap: 8px;">
          {#each songsInFilter as song, index}
            {@const heading = songGroupHeading(song, sortBy)}
            {@const prevHeading = index > 0 ? songGroupHeading(songsInFilter[index - 1], sortBy) : null}
            {#if heading && heading !== prevHeading}
              <div id="heading-{heading}" style="font-size: 13px; font-weight: 600; color: #2196f3; margin-top: 12px; margin-bottom: 2px; padding-left: 4px;">
                {heading}
              </div>
            {/if}

            <div role="button" tabindex="0" onclick={() => openSong(song)} onkeydown={(e) => e.key === 'Enter' && openSong(song)} style="background: var(--bg-surface); padding: 14px 16px; border-radius: 8px; cursor: pointer; display: flex; justify-content: space-between; align-items: center; border: 1px solid var(--border-color);">
              <div style="min-width: 0;">
                <div style="font-size: 16px; font-weight: 500; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">{displayTitle(song)}</div>
                <div style="font-size: 13px; color: var(--text-secondary); margin-top: 4px;">
                  {song.artist || 'Unbekannter Künstler'}
                  {#if song.genre} · {song.genre}{/if}
                </div>
              </div>
              <div style="display: flex; gap: 8px; align-items: center;">
                <div style="font-size: 12px; background: var(--bg-surface-secondary); padding: 4px 8px; border-radius: 4px; color: var(--text-secondary);">
                  {song.sourceType}
                </div>
                <button
                  onclick={(e) => { e.stopPropagation(); songToDelete = song; }}
                  style="background: none; border: none; color: var(--text-secondary); padding: 4px 8px; cursor: pointer; font-size: 16px;"
                  title="Lied löschen"
                >
                  🗑
                </button>
              </div>
            </div>
          {/each}
        </div>
      {/if}
    </div>
  {:else if route === 'detail'}
    <!-- Detail / Notenansicht Statusleiste -->
    {@const setlistPosStr = (setlistIndex >= 0 && currentSetlist && settings?.showSongPosition) ? `Lied ${setlistIndex + 1}/${setlistSongs(currentSetlist).length}` : ''}
    {@const pagePosStr = (currentSong?.sourceType === 'PDF' && pageCount > 0 && settings?.showPageNumber && !showLyricsMode) ? `Seite ${currentPage + 1}/${pageCount}` : ''}
    {@const line1Parts = [setlistPosStr, pagePosStr].filter(Boolean).join(' · ')}

    <div style="height: 52px; background: var(--bg-surface); display: flex; justify-content: space-between; align-items: center; padding: 0 12px; border-bottom: 1px solid var(--border-color); flex-shrink: 0; position: relative; z-index: 5;">
      <button onclick={closeDetail} style="background: var(--bg-surface-secondary); color: inherit; border: 1px solid var(--border-color); padding: 6px 12px; border-radius: 4px; cursor: pointer; font-size: 14px;">← Zurück</button>

      <!-- Center status title -->
      <div style="display: flex; flex-direction: column; align-items: center; min-width: 0; overflow: hidden; text-align: center; padding: 0 8px;">
        {#if line1Parts}
          <div style="font-size: 13px; font-weight: 500;">{line1Parts}</div>
        {/if}
        {#if settings?.showSongTitle || !line1Parts}
          <div style="font-size: {line1Parts ? '12px' : '15px'}; font-weight: 500; color: {line1Parts ? 'var(--text-secondary)' : 'inherit'}; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">
            {currentSong ? displayTitle(currentSong) : ''}
          </div>
        {/if}
      </div>

      <!-- Right controls: Lyrics Toggle, Notes, Page buttons, Edit -->
      <div style="display: flex; gap: 6px; align-items: center;">
        {#if currentSong && currentSong.fileUri && currentSong.lyrics}
          <button
            onclick={() => showLyricsMode = !showLyricsMode}
            style="background: var(--bg-surface-secondary); color: inherit; border: 1px solid var(--border-color); padding: 6px 10px; border-radius: 4px; cursor: pointer; font-size: 13px;"
          >
            {showLyricsMode ? 'Noten' : 'Text'}
          </button>
        {/if}

        {#if hasNotes}
          <div style="position: relative;">
            <button
              onclick={() => notesDropdownOpen = !notesDropdownOpen}
              style="background: var(--bg-surface-secondary); color: inherit; border: 1px solid var(--border-color); padding: 6px 10px; border-radius: 4px; cursor: pointer; font-size: 14px;"
              title="Notizen"
            >
              📝
            </button>

            {#if notesDropdownOpen}
              <div style="position: absolute; right: 0; top: 40px; background: var(--bg-surface); border: 1px solid var(--border-color); border-radius: 8px; padding: 12px; min-width: 240px; max-width: 320px; box-shadow: 0 4px 16px rgba(0,0,0,0.5); z-index: 20;">
                {#if currentOwnNote?.text}
                  <div style="margin-bottom: 8px;">
                    <strong style="font-size: 12px; color: var(--text-secondary);">Eigene Notiz:</strong>
                    <p style="margin: 4px 0; font-size: 14px; white-space: pre-wrap;">{currentOwnNote.text}</p>
                  </div>
                {/if}
                {#if currentOtherNotes.length > 0}
                  {#if currentOwnNote?.text}
                    <hr style="border: none; border-top: 1px solid var(--border-color); margin: 8px 0;" />
                  {/if}
                  <strong style="font-size: 12px; color: var(--text-secondary);">Notizen anderer:</strong>
                  <div style="display: flex; flex-direction: column; gap: 8px; margin-top: 6px;">
                    {#each currentOtherNotes as note}
                      {@const isShown = shownAuthors.has(note.authorId)}
                      <div style="display: flex; flex-direction: column; gap: 4px;">
                        <div style="display: flex; justify-content: space-between; align-items: center;">
                          <NoteAuthorLabel
                            {note}
                            {settings}
                            number={currentAuthorNumbers.get(note.authorId)}
                            isDark={isDarkTheme}
                          />
                          <input
                            type="checkbox"
                            checked={isShown}
                            onchange={(e) => {
                              const target = e.target as HTMLInputElement;
                              const nextSet = new Set(shownAuthors);
                              if (target.checked) nextSet.add(note.authorId);
                              else nextSet.delete(note.authorId);
                              shownAuthors = nextSet;
                            }}
                          />
                        </div>
                        {#if isShown}
                          <p style="margin: 2px 0 0 0; font-size: 13px; color: var(--text-secondary); white-space: pre-wrap; padding-left: 8px; border-left: 2px solid var(--border-color);">
                            {note.text}
                          </p>
                        {/if}
                      </div>
                    {/each}
                  </div>
                {/if}
              </div>
            {/if}
          </div>
        {/if}

        {#if settings?.showPageButtons}
          <button
            onclick={prevPage}
            disabled={!canGoPrevious}
            style="background: var(--bg-surface-secondary); color: inherit; border: 1px solid var(--border-color); padding: 6px 12px; border-radius: 4px; cursor: pointer; font-size: 14px; opacity: {canGoPrevious ? 1 : 0.4};"
          >
            {previousIsSongChange ? '⏮' : '◀'}
          </button>
          <button
            onclick={nextPage}
            disabled={!canGoNext}
            style="background: var(--bg-surface-secondary); color: inherit; border: 1px solid var(--border-color); padding: 6px 12px; border-radius: 4px; cursor: pointer; font-size: 14px; opacity: {canGoNext ? 1 : 0.4};"
          >
            {nextIsSongChange ? '⏭' : '▶'}
          </button>
        {/if}

        <button onclick={() => currentSong && startEditSong(currentSong)} style="background: var(--bg-surface-secondary); color: inherit; border: 1px solid var(--border-color); padding: 6px 12px; border-radius: 4px; cursor: pointer; font-size: 13px;">Bearbeiten</button>
      </div>
    </div>

    <!-- Noten/Text Hauptbereich -->
    <div style="flex: 1; position: relative; overflow: hidden; display: flex; justify-content: center; align-items: center; background: #000;">
      <!-- Randblitz Overlay -->
      {#if flashActive}
        <div style="position: absolute; inset: 0; border: 6px solid #81c784; opacity: 0.8; pointer-events: none; z-index: 10; transition: opacity 0.5s ease-out;"></div>
      {/if}

      <!-- Song Change Banner Overlay -->
      {#if songChangeBannerText}
        <div style="position: absolute; z-index: 15; background: rgba(30,30,30,0.92); color: white; padding: 14px 24px; border-radius: 12px; font-size: 18px; font-weight: 500; box-shadow: 0 4px 20px rgba(0,0,0,0.6); pointer-events: none;">
          {songChangeBannerText}
        </div>
      {/if}

      {#if showLyricsMode || currentSong?.sourceType === 'TEXT'}
        <div class="scroll" style="width: 100%; height: 100%; background: var(--bg-primary); color: var(--text-primary); padding: 24px; max-width: 700px; margin: 0 auto; box-sizing: border-box;">
          <h2>{currentSong ? displayTitle(currentSong) : ''}</h2>
          {#if currentSong?.artist}
            <h4 style="color: var(--text-secondary); margin-top: -8px;">{currentSong.artist}</h4>
          {/if}
          <hr style="border: none; border-top: 1px solid var(--border-color); margin: 16px 0;" />
          {#if currentSong?.lyrics}
            <div style="font-size: 16px; line-height: 1.6; white-space: pre-wrap;">{currentSong.lyrics}</div>
          {:else if currentOwnNote?.text}
            <div style="font-size: 16px; line-height: 1.6; white-space: pre-wrap;">{currentOwnNote.text}</div>
          {:else}
            <p style="color: var(--text-secondary); font-style: italic;">Kein Liedtext vorhanden.</p>
          {/if}
        </div>
      {:else if currentSong?.sourceType === 'PDF'}
        {#if pdfError}
          <p style="color: #f88; padding: 24px; text-align: center;">{pdfError}</p>
        {:else if pdfDoc}
          <PdfViewer
            {pdfDoc}
            pageIndex={currentPage}
            pageView={settings?.rememberZoom === false ? undefined : currentSong.pageViews[String(currentPage)]}
            tapZonesEnabled={settings?.tapZonesEnabled ?? true}
            tapZoneSize={settings?.tapZoneSize ?? 'LOWER_THIRD'}
            swapTapZones={settings?.swapTapZones ?? false}
            filter={scoreFilter}
            onPageViewChange={handlePageViewChange}
            onNext={nextPage}
            onPrev={prevPage}
          />
        {:else}
          <p style="color: #888;">Lade …</p>
        {/if}
      {:else if currentSong?.sourceType === 'MUSIC_XML'}
        <div bind:this={osmdContainer} style="width: 100%; height: 100%; overflow: auto; background: white; color: black; padding: 16px; filter: {scoreFilter};"></div>
      {/if}
    </div>
  {/if}

  <!-- Modal for Song Delete Confirmation -->
  {#if songToDelete}
    <div style="position: fixed; inset: 0; background: rgba(0,0,0,0.6); display: flex; justify-content: center; align-items: center; z-index: 100; padding: 16px;">
      <div style="background: var(--bg-surface); border: 1px solid var(--border-color); border-radius: 12px; padding: 20px; max-width: 400px; width: 100%;">
        <h3 style="margin-top: 0;">Lied löschen?</h3>
        <p>Möchtest du „<strong>{displayTitle(songToDelete)}</strong>“ wirklich löschen? Die Notendatei wird entfernt.</p>
        <div style="display: flex; justify-content: flex-end; gap: 12px; margin-top: 20px;">
          <button onclick={() => songToDelete = null} style="background: var(--bg-surface-secondary); color: inherit; border: 1px solid var(--border-color); padding: 8px 16px; border-radius: 6px; cursor: pointer;">Abbrechen</button>
          <button onclick={confirmDeleteSong} style="background: #f44336; color: white; border: none; padding: 8px 16px; border-radius: 6px; cursor: pointer;">Löschen</button>
        </div>
      </div>
    </div>
  {/if}

  <!-- Modal for Create Setlist -->
  {#if createSetlistModalOpen}
    <div style="position: fixed; inset: 0; background: rgba(0,0,0,0.6); display: flex; justify-content: center; align-items: center; z-index: 100; padding: 16px;">
      <div style="background: var(--bg-surface); border: 1px solid var(--border-color); border-radius: 12px; padding: 20px; max-width: 450px; width: 100%; display: flex; flex-direction: column; gap: 14px;">
        <h3 style="margin: 0;">Neue Setlist anlegen</h3>
        <label style="display: flex; flex-direction: column; gap: 4px;">
          Titel:
          <input type="text" placeholder="z. B. Gottesdienst Ostern" bind:value={newSetlistTitle} style="padding: 8px; background: var(--bg-surface-secondary); border: 1px solid var(--border-color); color: inherit; border-radius: 4px;" />
        </label>
        <label style="display: flex; flex-direction: column; gap: 4px;">
          Datum (optional, z. B. 24.12.2025 oder 2025-12-24):
          <input type="text" placeholder="TT.MM.YYYY" bind:value={newSetlistDate} style="padding: 8px; background: var(--bg-surface-secondary); border: 1px solid var(--border-color); color: inherit; border-radius: 4px;" />
        </label>
        <label style="display: flex; flex-direction: column; gap: 4px;">
          Notizen / Anlass:
          <textarea bind:value={newSetlistNotes} rows="3" style="padding: 8px; background: var(--bg-surface-secondary); border: 1px solid var(--border-color); color: inherit; border-radius: 4px; resize: vertical;"></textarea>
        </label>
        <div style="display: flex; justify-content: flex-end; gap: 12px; margin-top: 8px;">
          <button onclick={() => createSetlistModalOpen = false} style="background: var(--bg-surface-secondary); color: inherit; border: 1px solid var(--border-color); padding: 8px 16px; border-radius: 6px; cursor: pointer;">Abbrechen</button>
          <button onclick={createSetlist} disabled={!newSetlistTitle.trim()} style="background: #2196f3; color: white; border: none; padding: 8px 16px; border-radius: 6px; cursor: pointer; opacity: {newSetlistTitle.trim() ? 1 : 0.5};">Erstellen</button>
        </div>
      </div>
    </div>
  {/if}

  <!-- Modal for Edit Setlist -->
  {#if editSetlistModalOpen}
    <div style="position: fixed; inset: 0; background: rgba(0,0,0,0.6); display: flex; justify-content: center; align-items: center; z-index: 100; padding: 16px;">
      <div style="background: var(--bg-surface); border: 1px solid var(--border-color); border-radius: 12px; padding: 20px; max-width: 450px; width: 100%; display: flex; flex-direction: column; gap: 14px;">
        <h3 style="margin: 0;">Setlist bearbeiten</h3>
        <label style="display: flex; flex-direction: column; gap: 4px;">
          Titel:
          <input type="text" bind:value={editSetlistTitle} style="padding: 8px; background: var(--bg-surface-secondary); border: 1px solid var(--border-color); color: inherit; border-radius: 4px;" />
        </label>
        <label style="display: flex; flex-direction: column; gap: 4px;">
          Datum:
          <input type="text" bind:value={editSetlistDate} style="padding: 8px; background: var(--bg-surface-secondary); border: 1px solid var(--border-color); color: inherit; border-radius: 4px;" />
        </label>
        <label style="display: flex; flex-direction: column; gap: 4px;">
          Notizen / Anlass:
          <textarea bind:value={editSetlistNotes} rows="3" style="padding: 8px; background: var(--bg-surface-secondary); border: 1px solid var(--border-color); color: inherit; border-radius: 4px; resize: vertical;"></textarea>
        </label>
        <div style="display: flex; justify-content: flex-end; gap: 12px; margin-top: 8px;">
          <button onclick={() => editSetlistModalOpen = false} style="background: var(--bg-surface-secondary); color: inherit; border: 1px solid var(--border-color); padding: 8px 16px; border-radius: 6px; cursor: pointer;">Abbrechen</button>
          <button onclick={saveEditedSetlist} disabled={!editSetlistTitle.trim()} style="background: #2196f3; color: white; border: none; padding: 8px 16px; border-radius: 6px; cursor: pointer; opacity: {editSetlistTitle.trim() ? 1 : 0.5};">Speichern</button>
        </div>
      </div>
    </div>
  {/if}

  <!-- Modal for Adding Songs to Setlist -->
  {#if addSongsModalOpen && currentSetlist}
    {@const currentSongIds = new Set(currentSetlist.songIds)}
    {@const availableSongsToPick = songs.filter(s => matchesQuery(s, addSongsQuery))}
    <div style="position: fixed; inset: 0; background: rgba(0,0,0,0.6); display: flex; justify-content: center; align-items: center; z-index: 100; padding: 16px;">
      <div style="background: var(--bg-surface); border: 1px solid var(--border-color); border-radius: 12px; padding: 20px; max-width: 600px; width: 100%; max-height: 80vh; display: flex; flex-direction: column; gap: 12px;">
        <div style="display: flex; justify-content: space-between; align-items: center;">
          <h3 style="margin: 0;">Lieder zu „{currentSetlist.title}“ hinzufügen</h3>
          <button onclick={() => addSongsModalOpen = false} style="background: none; border: none; font-size: 20px; color: var(--text-secondary); cursor: pointer;">✕</button>
        </div>
        <input type="text" placeholder="Lieder suchen..." bind:value={addSongsQuery} style="padding: 8px 12px; background: var(--bg-surface-secondary); border: 1px solid var(--border-color); color: inherit; border-radius: 6px; font-size: 14px;" />
        <div style="flex: 1; overflow-y: auto; display: flex; flex-direction: column; gap: 8px; min-height: 200px; max-height: 400px; padding-right: 4px;">
          {#if availableSongsToPick.length === 0}
            <p style="color: var(--text-secondary); text-align: center; margin-top: 20px;">Keine Lieder gefunden.</p>
          {:else}
            {#each availableSongsToPick as song}
              {@const isAdded = currentSongIds.has(song.id)}
              <div style="display: flex; justify-content: space-between; align-items: center; padding: 10px 12px; background: var(--bg-surface-secondary); border-radius: 6px; border: 1px solid var(--border-color);">
                <div>
                  <div style="font-weight: 500;">{displayTitle(song)}</div>
                  <div style="font-size: 12px; color: var(--text-secondary);">{song.artist || 'Unbekannter Künstler'} {song.genre ? `· ${song.genre}` : ''}</div>
                </div>
                {#if isAdded}
                  <span style="font-size: 13px; color: #4caf50; font-weight: 500;">✓ Hinzugefügt</span>
                {:else}
                  <button
                    onclick={() => addSongToCurrentSetlist(song.id)}
                    style="background: #2196f3; color: white; border: none; padding: 6px 12px; border-radius: 4px; cursor: pointer; font-size: 13px;"
                  >
                    + Hinzufügen
                  </button>
                {/if}
              </div>
            {/each}
          {/if}
        </div>
        <div style="display: flex; justify-content: flex-end; margin-top: 8px;">
          <button onclick={() => addSongsModalOpen = false} style="background: #2196f3; color: white; border: none; padding: 8px 18px; border-radius: 6px; cursor: pointer;">Fertig</button>
        </div>
      </div>
    </div>
  {/if}

  <!-- Modal for Delete Setlist Confirmation -->
  {#if setlistToDelete}
    <div style="position: fixed; inset: 0; background: rgba(0,0,0,0.6); display: flex; justify-content: center; align-items: center; z-index: 100; padding: 16px;">
      <div style="background: var(--bg-surface); border: 1px solid var(--border-color); border-radius: 12px; padding: 20px; max-width: 400px; width: 100%;">
        <h3 style="margin-top: 0;">Setlist löschen?</h3>
        <p>Möchtest du die Setlist „<strong>{setlistToDelete.title}</strong>“ wirklich löschen? Die enthaltenen Lieder bleiben erhalten.</p>
        <div style="display: flex; justify-content: flex-end; gap: 12px; margin-top: 20px;">
          <button onclick={() => setlistToDelete = null} style="background: var(--bg-surface-secondary); color: inherit; border: 1px solid var(--border-color); padding: 8px 16px; border-radius: 6px; cursor: pointer;">Abbrechen</button>
          <button onclick={confirmDeleteSetlist} style="background: #f44336; color: white; border: none; padding: 8px 16px; border-radius: 6px; cursor: pointer;">Löschen</button>
        </div>
      </div>
    </div>
  {/if}

  <!-- Modal for Copyright Warning before Sharing Setlist -->
  {#if copyrightDialogSetlist}
    <div style="position: fixed; inset: 0; background: rgba(0,0,0,0.6); display: flex; justify-content: center; align-items: center; z-index: 100; padding: 16px;">
      <div style="background: var(--bg-surface); border: 1px solid var(--border-color); border-radius: 12px; padding: 20px; max-width: 450px; width: 100%; display: flex; flex-direction: column; gap: 14px;">
        <h3 style="margin: 0;">Urheberrecht beachten</h3>
        <p style="margin: 0; font-size: 14px; line-height: 1.5; color: var(--text-primary);">
          Bitte beachte, dass vervielfältigte Noten urheberrechtlich geschützt sein können. Teile Notendateien nur mit Personen, die zur Nutzung berechtigt sind.
        </p>
        <label style="display: flex; gap: 8px; align-items: center; cursor: pointer; font-size: 13px; color: var(--text-secondary); margin-top: 4px;">
          <input type="checkbox" bind:checked={dontShowCopyrightWarningAgain} />
          <span>Nicht mehr anzeigen</span>
        </label>
        <div style="display: flex; justify-content: flex-end; gap: 12px; margin-top: 8px;">
          <button onclick={() => copyrightDialogSetlist = null} style="background: var(--bg-surface-secondary); color: inherit; border: 1px solid var(--border-color); padding: 8px 16px; border-radius: 6px; cursor: pointer;">Abbrechen</button>
          <button onclick={confirmCopyrightAndShare} style="background: #2196f3; color: white; border: none; padding: 8px 16px; border-radius: 6px; cursor: pointer;">Verstanden & Teilen</button>
        </div>
      </div>
    </div>
  {/if}
</main>

<style>
  :global([data-theme="light"]) {
    --bg-primary: #f5f5f5;
    --bg-surface: #ffffff;
    --bg-surface-secondary: #eef0f2;
    --border-color: #dddddd;
    --text-primary: #1c1b1f;
    --text-secondary: #666666;
  }
  :global([data-theme="dark"]) {
    --bg-primary: #121212;
    --bg-surface: #1e1e1e;
    --bg-surface-secondary: #262626;
    --border-color: #333333;
    --text-primary: #ffffff;
    --text-secondary: #aaaaaa;
  }
</style>
