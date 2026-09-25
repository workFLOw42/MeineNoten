<script lang="ts">
  import { onMount } from 'svelte';
  import type { Song, Setlist, AppSettings } from './lib/model/types';
  import { loadSongsDB, saveSongsDB, loadSetlistsDB, saveSetlistsDB, loadSettingsDB, saveSettingsDB, saveScoreFile, loadScoreFile } from './lib/storage/db';
  import { loadPdfDocument } from './lib/pdf/pdfRenderer';
  import PdfViewer from './lib/pdf/PdfViewer.svelte';
  import { renderMusicXml } from './lib/musicxml/osmdRenderer';
  import { matchesQuery, sortSongs, possessiveName, mergeNotes } from './lib/logic/songLogic';
  import { migrateLegacyNote, toInt } from './lib/logic/serialization';
  import { createFullBackupZip, generateBackupFilename } from './lib/logic/backupLogic';
  import { analyzeBackupFile, type BackupAnalysisResult } from './lib/logic/backupImportLogic';
  import SelfTest from './routes/SelfTest.svelte';

  let songs = $state<Song[]>([]);
  let setlists = $state<Setlist[]>([]);
  let settings = $state<AppSettings | null>(null);
  let currentSong = $state<Song | null>(null);
  let currentPage = $state<number>(0);
  let pageCount = $state<number>(0);
  let route = $state<'songs' | 'setlists' | 'setlist' | 'detail' | 'settings' | 'selftest' | 'edit' | 'compare'>('songs');

  // Geöffnete Setlist: Lieder werden in deren Reihenfolge durchgeblättert
  let currentSetlist = $state<Setlist | null>(null);
  let setlistIndex = $state<number>(-1);
  let backRoute = $state<'songs' | 'setlist'>('songs');

  // List view search & sort
  let searchQuery = $state<string>('');
  let sortBy = $state<'title' | 'artist' | 'recent'>('title');

  // Edit song state
  let editTitle = $state<string>('');
  let editArtist = $state<string>('');
  let editVersion = $state<string>('');
  let editGenre = $state<string>('');
  let editBpm = $state<number>(120);
  let editNoteText = $state<string>('');

  // Settings state
  let inputUserName = $state<string>('');

  // Backup analysis result
  let backupAnalysis = $state<BackupAnalysisResult | null>(null);

  let osmdContainer = $state<HTMLDivElement | null>(null);
  let pdfDoc = $state<any>(null);
  let pdfError = $state<string>('');

  onMount(async () => {
    songs = await loadSongsDB();
    setlists = await loadSetlistsDB();
    settings = await loadSettingsDB();
    inputUserName = settings?.userName || '';

    if (settings?.keepScreenOn && 'wakeLock' in navigator) {
      try {
        await (navigator as any).wakeLock.request('screen');
      } catch (e) {}
    }

    window.addEventListener('keydown', handleGlobalKey);
  });

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
    // Noch ausstehenden Zoom sofort speichern, nicht erst nach Ablauf des Timers
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
      notes: updatedNotes,
    };

    songs = songs.map(s => s.id === updatedSong.id ? updatedSong : s);
    await saveSongsDB(songs);
    currentSong = updatedSong;
    route = 'detail';
  }

  async function saveSettings() {
    if (!settings) return;
    const updated = { ...settings, userName: inputUserName };
    settings = updated;
    await saveSettingsDB(updated);
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

      // Identität zuerst übernehmen, damit alte Einzelnotizen der richtigen Person zugeordnet werden
      let activeSettings = settings;
      if (analysis.adoptAuthorIdentity && analysis.manifest.authorId) {
        activeSettings = { ...activeSettings, userId: analysis.manifest.authorId, userName: analysis.manifest.authorName || activeSettings.userName };
      }
      // Eigenen Namen aus der Sicherung übernehmen, solange hier noch keiner eingetragen ist
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
      // Setlists zeigen auf Lied-IDs; bei "Meins behalten"/"Beide behalten" ändert sich die ID
      const idMap = new Map<string, string>();

      for (const item of analysis.songs) {
        const backupSong = migrateLegacyNote(item.backupSong, activeSettings.userId, activeSettings.userName);

        if (item.action === 'SKIP') {
          if (item.localSong) idMap.set(backupSong.id, item.localSong.id);
          continue;
        }

        if (item.action === 'KEEP_OWN') {
          if (item.localSong) idMap.set(backupSong.id, item.localSong.id);
          // Eigenes Lied bleibt, aber Notizen anderer Personen werden auf Wunsch ergänzt
          if (item.localSong && item.mergeForeignNotes) {
            const idx = updatedSongs.findIndex(s => s.id === item.localSong!.id);
            if (idx >= 0) {
              updatedSongs[idx] = { ...updatedSongs[idx], notes: mergeNotes(updatedSongs[idx].notes, backupSong.notes) };
            }
          }
          continue;
        }

        // TAKE_BACKUP oder KEEP_BOTH
        const newId = item.action === 'KEEP_BOTH' ? crypto.randomUUID() : (item.localSong?.id ?? backupSong.id);
        let newFileUri = '';
        const zipFileEntry = backupSong.fileUri ? analysis.zipFiles[backupSong.fileUri] : undefined;
        if (zipFileEntry) {
          const arrayBuffer = await zipFileEntry.async('arraybuffer');
          const ext = backupSong.fileUri.substring(backupSong.fileUri.lastIndexOf('.') + 1);
          newFileUri = await saveScoreFile(newId, ext, arrayBuffer);
        } else if (item.action === 'TAKE_BACKUP' && item.localSong?.fileUri) {
          // Sicherung ohne Datei (z. B. Setlist-Teilsicherung): vorhandene Datei behalten
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
    // Zeichnen erledigt PdfViewer; hier nur die Position in der Setlist merken
    await rememberSetlistPosition();
  }

  function handleGlobalKey(e: KeyboardEvent) {
    if (route !== 'detail') return;
    // Pedale senden meist Pfeiltasten, Bild auf/ab oder Leertaste
    if (['ArrowRight', 'ArrowDown', 'PageDown', ' '].includes(e.key)) { e.preventDefault(); nextPage(); }
    else if (['ArrowLeft', 'ArrowUp', 'PageUp'].includes(e.key)) { e.preventDefault(); prevPage(); }
  }

  function nextPage() {
    if (currentSong?.sourceType === 'PDF' && currentPage < pageCount - 1) {
      currentPage++;
      renderCurrentPdfPage();
    } else if (setlistIndex >= 0 && setlistIndex < setlistSongs(currentSetlist).length - 1) {
      openFromSetlist(setlistIndex + 1, 0);
    }
  }

  function prevPage() {
    if (currentSong?.sourceType === 'PDF' && currentPage > 0) {
      currentPage--;
      renderCurrentPdfPage();
    } else if (setlistIndex > 0) {
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
    };

    songs = [...songs, newSong];
    await saveSongsDB(songs);
  }

  let filteredSongs = $derived(sortSongs(songs.filter(s => matchesQuery(s, searchQuery)), sortBy));
  let titleName = $derived(settings?.userName ? possessiveName(settings.userName) + ' Noten' : 'Meine Noten');
</script>

<main class="app-root" style="position: fixed; inset: 0; display: flex; flex-direction: column; background: #121212; color: #fff; box-sizing: border-box;">
  {#if route !== 'detail' && route !== 'edit' && route !== 'selftest' && route !== 'compare' && route !== 'setlist'}
    <!-- Navigation Bar -->
    <nav style="display: flex; background: #181818; border-bottom: 1px solid #333; padding: 0 16px; overflow-x: auto; flex-shrink: 0;">
      <button onclick={() => route = 'songs'} style="background: none; border: none; padding: 14px 20px; color: {route === 'songs' ? '#2196f3' : '#aaa'}; font-weight: 500; cursor: pointer; border-bottom: 2px solid {route === 'songs' ? '#2196f3' : 'transparent'};">Lieder ({songs.length})</button>
      <button onclick={() => route = 'setlists'} style="background: none; border: none; padding: 14px 20px; color: {route === 'setlists' ? '#2196f3' : '#aaa'}; font-weight: 500; cursor: pointer; border-bottom: 2px solid {route === 'setlists' ? '#2196f3' : 'transparent'};">Setlists ({setlists.length})</button>
      <button onclick={() => route = 'settings'} style="background: none; border: none; padding: 14px 20px; color: {route === 'settings' ? '#2196f3' : '#aaa'}; font-weight: 500; cursor: pointer; border-bottom: 2px solid {route === 'settings' ? '#2196f3' : 'transparent'};">Einstellungen & Sicherung</button>
      <!-- Selbsttest-Reiter ausgeblendet; erreichbar über Tipp auf die Webversion in den Einstellungen.
      <button onclick={() => route = 'selftest'} style="background: none; border: none; padding: 14px 20px; color: #aaa; font-weight: 500; cursor: pointer;">Selbsttest</button>
      -->
    </nav>
  {/if}

  {#if route === 'selftest'}
    <div style="padding: 12px; background: #1e1e1e; display: flex; justify-content: space-between; align-items: center;">
      <button onclick={() => route = 'songs'} style="background: #333; color: white; border: none; padding: 8px 16px; border-radius: 4px; cursor: pointer;">← Zurück</button>
      <h3>Selbsttest</h3>
      <div></div>
    </div>
    <div style="flex: 1; min-height: 0; overflow-y: auto; -webkit-overflow-scrolling: touch;">
      <SelfTest />
    </div>
  {:else if route === 'compare'}
    <div style="padding: 16px 24px; background: #1e1e1e; display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid #333;">
      <button onclick={() => route = 'settings'} style="background: #333; color: white; border: none; padding: 8px 16px; border-radius: 4px; cursor: pointer;">Abbrechen</button>
      <h3>Sicherung vergleichen & einlesen</h3>
      <button onclick={applyBackupImport} style="background: #4caf50; color: white; border: none; padding: 8px 16px; border-radius: 4px; cursor: pointer; font-weight: 500;">Jetzt importieren</button>
    </div>
    <div class="scroll" style="padding: 24px; max-width: 800px; margin: 0 auto; width: 100%; display: flex; flex-direction: column; gap: 16px;">
      {#if backupAnalysis}
        <div style="background: #1e1e1e; padding: 16px; border-radius: 8px; border: 1px solid #333;">
          <strong>{backupAnalysis.manifest.title}</strong> · {backupAnalysis.manifest.authorName || 'Unbekannt'} · {backupAnalysis.songs.length} Lieder, {backupAnalysis.setlists.length} Setlists
        </div>

        {#if backupAnalysis.manifest.authorId && backupAnalysis.manifest.authorId !== settings?.userId}
          <label style="background: #2a2a2a; padding: 12px; border-radius: 8px; display: flex; gap: 12px; align-items: center; cursor: pointer;">
            <input type="checkbox" bind:checked={backupAnalysis.adoptAuthorIdentity} />
            <span>Identität von <strong>{backupAnalysis.manifest.authorName}</strong> übernehmen (Notizen bleiben den eigenen zugeordnet)</span>
          </label>
        {/if}

        <h4>Lieder ({backupAnalysis.songs.length})</h4>
        <div style="display: flex; flex-direction: column; gap: 8px;">
          {#each backupAnalysis.songs as item}
            <div style="background: #1e1e1e; padding: 12px; border-radius: 8px; display: flex; justify-content: space-between; align-items: center; border: 1px solid #333;">
              <div>
                <div style="font-weight: 500;">{item.backupSong.title}</div>
                <div style="font-size: 12px; color: #aaa;">Kategorie: {item.category}</div>
              </div>
              <select bind:value={item.action} style="background: #333; color: white; border: 1px solid #555; padding: 6px; border-radius: 4px;">
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
  {:else if route === 'settings'}
    <div class="scroll" style="padding: 24px; max-width: 600px; margin: 0 auto; width: 100%; display: flex; flex-direction: column; gap: 20px;">
      <h2>Einstellungen & Datensicherung</h2>
      <label style="display: flex; flex-direction: column; gap: 6px;">
        Dein Name (für Noten-Verfasser):
        <input type="text" bind:value={inputUserName} style="padding: 10px; background: #222; border: 1px solid #444; color: white; border-radius: 6px;" />
      </label>
      <button onclick={saveSettings} style="background: #2196f3; color: white; border: none; padding: 10px 20px; border-radius: 6px; cursor: pointer; font-size: 15px;">Einstellungen speichern</button>

      <hr style="border-color: #333; margin: 20px 0;" />

      <h3>Datensicherung</h3>
      <p style="color: #aaa; font-size: 14px;">Sichere deine gesamte Sammlung inklusive Notendateien und Einstellungen als ZIP-Datei.</p>
      <div style="display: flex; gap: 12px;">
        <button onclick={exportBackup} style="background: #4caf50; color: white; border: none; padding: 12px 20px; border-radius: 6px; cursor: pointer; font-size: 16px;">Alles sichern (ZIP)</button>
        <label style="background: #ff9800; color: white; padding: 12px 20px; border-radius: 6px; cursor: pointer; font-size: 16px; display: inline-flex; align-items: center;">
          Sicherung einlesen
          <input type="file" accept=".zip" onchange={handleBackupFileSelected} style="display: none;" />
        </label>
      </div>

      <!-- Version und Autor, wie in der Android-App am Ende der Einstellungen -->
      <footer style="margin-top: 32px; padding-bottom: 24px; display: flex; flex-direction: column; align-items: center; gap: 2px; color: #8a8a8a;">
        <!-- Tipp auf die Version öffnet den Selbsttest (Diagnose bei Problemen) -->
        <button onclick={() => route = 'selftest'} style="background: none; border: none; padding: 0; font: inherit; font-size: 12px; color: inherit; cursor: pointer;" title="Build {__BUILD_STAMP__} UTC – tippen für Selbsttest">Webversion {__WEB_VERSION__}</button>
        <span style="font-size: 12px; font-weight: 500; letter-spacing: 0.3px;">by workFLOw42 · ©2026</span>
      </footer>
    </div>
  {:else if route === 'setlists'}
    <div class="scroll" style="padding: 24px; max-width: 800px; margin: 0 auto; width: 100%;">
      <h2>Setlists</h2>
      {#if setlists.length === 0}
        <p style="color: #888;">Keine Setlists vorhanden.</p>
      {:else}
        <div style="display: flex; flex-direction: column; gap: 12px;">
          {#each setlists as setlist}
            <button onclick={() => openSetlist(setlist)} style="text-align: left; color: inherit; font: inherit; background: #1e1e1e; padding: 16px; border-radius: 8px; border: 1px solid #333; cursor: pointer; width: 100%;">
              <div style="font-size: 16px; font-weight: 500;">{setlist.title}</div>
              <div style="font-size: 13px; color: #aaa; margin-top: 4px;">{setlistSongs(setlist).length} Lieder · {setlist.date || 'Kein Datum'}</div>
            </button>
          {/each}
        </div>
      {/if}
    </div>
  {:else if route === 'setlist' && currentSetlist}
    {@const list = setlistSongs(currentSetlist)}
    {@const resumeIdx = currentSetlist.lastSongId ? list.findIndex(s => s.id === currentSetlist!.lastSongId) : -1}
    <div style="padding: 12px 16px; background: #1e1e1e; display: flex; gap: 12px; align-items: center; border-bottom: 1px solid #333;">
      <button onclick={() => { currentSetlist = null; route = 'setlists'; }} style="background: #333; color: white; border: none; padding: 8px 16px; border-radius: 4px; cursor: pointer;">← Zurück</button>
      <div style="min-width: 0;">
        <div style="font-size: 17px; font-weight: 500; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">{currentSetlist.title}</div>
        <div style="font-size: 13px; color: #aaa;">{list.length} Lieder · {currentSetlist.date || 'Kein Datum'}</div>
      </div>
    </div>
    <div style="flex: 1; min-height: 0; overflow-y: auto; -webkit-overflow-scrolling: touch; padding: 16px; max-width: 800px; margin: 0 auto; width: 100%; box-sizing: border-box; display: flex; flex-direction: column; gap: 8px;">
      {#if list.length > 0}
        <div style="display: flex; gap: 8px; margin-bottom: 8px; flex-wrap: wrap;">
          <button onclick={() => openFromSetlist(0, 0)} style="background: #2196f3; color: white; border: none; padding: 12px 18px; border-radius: 6px; cursor: pointer; font-size: 15px;">▶ Von vorne</button>
          {#if resumeIdx >= 0}
            <button onclick={() => openFromSetlist(resumeIdx, currentSetlist!.lastPage)} style="background: #4caf50; color: white; border: none; padding: 12px 18px; border-radius: 6px; cursor: pointer; font-size: 15px;">Weiter bei „{list[resumeIdx].title}“</button>
          {/if}
        </div>
      {:else}
        <p style="color: #888;">Diese Setlist enthält keine Lieder, die hier vorhanden sind.</p>
      {/if}
      {#each list as song, i}
        <button onclick={() => openFromSetlist(i, 0)} style="text-align: left; color: inherit; font: inherit; background: #1e1e1e; padding: 14px 16px; border-radius: 8px; border: 1px solid {i === resumeIdx ? '#4caf50' : '#333'}; cursor: pointer; display: flex; gap: 14px; align-items: center; width: 100%;">
          <span style="color: #888; min-width: 1.5em; text-align: right;">{i + 1}.</span>
          <span style="min-width: 0;">
            <span style="display: block; font-size: 16px; font-weight: 500;">{song.title}</span>
            {#if song.artist || song.version}
              <span style="display: block; font-size: 13px; color: #aaa; margin-top: 2px;">{song.artist} {song.version ? `(${song.version})` : ''}</span>
            {/if}
          </span>
        </button>
      {/each}
    </div>
  {:else if route === 'edit'}
    <div style="padding: 12px 24px; background: #1e1e1e; display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid #333;">
      <button onclick={() => route = 'detail'} style="background: #333; color: white; border: none; padding: 8px 16px; border-radius: 4px; cursor: pointer;">Abbrechen</button>
      <h3>Lied bearbeiten</h3>
      <button onclick={saveEditedSong} style="background: #2196f3; color: white; border: none; padding: 8px 16px; border-radius: 4px; cursor: pointer;">Speichern</button>
    </div>
    <div class="scroll" style="padding: 24px; max-width: 600px; margin: 0 auto; width: 100%; display: flex; flex-direction: column; gap: 16px;">
      <label style="display: flex; flex-direction: column; gap: 4px;">
        Titel:
        <input type="text" bind:value={editTitle} style="padding: 8px; background: #222; border: 1px solid #444; color: white; border-radius: 4px;" />
      </label>
      <label style="display: flex; flex-direction: column; gap: 4px;">
        Künstler:
        <input type="text" bind:value={editArtist} style="padding: 8px; background: #222; border: 1px solid #444; color: white; border-radius: 4px;" />
      </label>
      <label style="display: flex; flex-direction: column; gap: 4px;">
        Fassung / Untertitel:
        <input type="text" bind:value={editVersion} style="padding: 8px; background: #222; border: 1px solid #444; color: white; border-radius: 4px;" />
      </label>
      <label style="display: flex; flex-direction: column; gap: 4px;">
        Genre / Anlass:
        <input type="text" bind:value={editGenre} style="padding: 8px; background: #222; border: 1px solid #444; color: white; border-radius: 4px;" />
      </label>
      <label style="display: flex; flex-direction: column; gap: 4px;">
        BPM (Tempo):
        <input type="number" inputmode="numeric" min="1" step="1" bind:value={editBpm} style="padding: 8px; background: #222; border: 1px solid #444; color: white; border-radius: 4px;" />
      </label>
      <label style="display: flex; flex-direction: column; gap: 4px;">
        Persönliche Notiz:
        <textarea bind:value={editNoteText} rows="3" style="padding: 8px; background: #222; border: 1px solid #444; color: white; border-radius: 4px; resize: vertical;"></textarea>
      </label>
    </div>
  {:else if route === 'songs'}
    <header style="padding: 16px 24px; background: #1e1e1e; display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid #333;">
      <h1 style="margin: 0; font-size: 20px;">{titleName}</h1>
      <label style="background: #2196f3; color: white; padding: 8px 16px; border-radius: 6px; cursor: pointer; font-size: 14px;">
        Noten importieren (PDF / MusicXML)
        <input type="file" accept=".pdf,.xml,.musicxml,.mxl" onchange={handleFileUpload} style="display: none;" />
      </label>
    </header>

    <div style="padding: 16px 24px; background: #181818; display: flex; gap: 12px; border-bottom: 1px solid #333; align-items: center;">
      <input type="text" placeholder="Suchen nach Titel, Künstler, Anlass..." bind:value={searchQuery} style="flex: 1; padding: 8px 12px; background: #262626; border: 1px solid #444; color: white; border-radius: 6px; font-size: 14px;" />
      <select bind:value={sortBy} style="padding: 8px 12px; background: #262626; border: 1px solid #444; color: white; border-radius: 6px; font-size: 14px;">
        <option value="title">Nach Titel</option>
        <option value="artist">Nach Künstler</option>
        <option value="recent">Zuletzt geöffnet</option>
      </select>
    </div>

    <div class="scroll" style="padding: 24px; max-width: 800px; margin: 0 auto; width: 100%;">
      {#if filteredSongs.length === 0}
        <div style="text-align: center; color: #888; margin-top: 60px;">
          <p style="font-size: 18px;">Keine Noten gefunden.</p>
          {#if songs.length === 0}
            <p>Klicke oben auf *Noten importieren*, um eine PDF- oder MusicXML-Datei hinzuzufügen.</p>
          {/if}
        </div>
      {:else}
        <div style="display: flex; flex-direction: column; gap: 8px;">
          {#each filteredSongs as song}
            <div role="button" tabindex="0" onclick={() => openSong(song)} onkeydown={(e) => e.key === 'Enter' && openSong(song)} style="background: #1e1e1e; padding: 16px; border-radius: 8px; cursor: pointer; display: flex; justify-content: space-between; align-items: center; border: 1px solid #333;">
              <div>
                <div style="font-size: 16px; font-weight: 500;">{song.title}</div>
                <div style="font-size: 13px; color: #aaa; margin-top: 4px;">{song.artist || 'Unbekannter Künstler'} {song.version ? `(${song.version})` : ''}</div>
              </div>
              <div style="font-size: 12px; background: #333; padding: 4px 8px; border-radius: 4px; color: #aaa;">
                {song.sourceType}
              </div>
            </div>
          {/each}
        </div>
      {/if}
    </div>
  {:else if route === 'detail'}
    <!-- Detail / Notenansicht -->
    <div style="height: 48px; background: #1e1e1e; display: flex; justify-content: space-between; align-items: center; padding: 0 16px; border-bottom: 1px solid #333;">
      <button onclick={closeDetail} style="background: #333; color: white; border: none; padding: 6px 12px; border-radius: 4px; cursor: pointer;">← Zurück</button>
      <div style="font-size: 15px; font-weight: 500; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; padding: 0 8px;">
        {currentSong?.title}
        {#if setlistIndex >= 0 && currentSetlist}
          <span style="color: #aaa; font-weight: 400;"> · {setlistIndex + 1}/{setlistSongs(currentSetlist).length}</span>
        {/if}
      </div>
      <div style="display: flex; gap: 8px; align-items: center;">
        <button onclick={() => currentSong && startEditSong(currentSong)} style="background: #333; color: white; border: none; padding: 6px 12px; border-radius: 4px; cursor: pointer; font-size: 13px;">Bearbeiten</button>
        <div style="font-size: 13px; color: #aaa;">
          {#if currentSong?.sourceType === 'PDF'}
            Seite {currentPage + 1} / {pageCount}
          {:else}
            MusicXML
          {/if}
        </div>
      </div>
    </div>

    <div style="flex: 1; position: relative; overflow: hidden; display: flex; justify-content: center; align-items: center; background: #000;">
      {#if currentSong?.sourceType === 'PDF'}
        {#if pdfError}
          <p style="color: #f88; padding: 24px; text-align: center;">{pdfError}</p>
        {:else if pdfDoc}
          <PdfViewer
            {pdfDoc}
            pageIndex={currentPage}
            pageView={settings?.rememberZoom === false ? undefined : currentSong.pageViews[String(currentPage)]}
            onPageViewChange={handlePageViewChange}
            onNext={nextPage}
            onPrev={prevPage}
          />
        {:else}
          <p style="color: #888;">Lade …</p>
        {/if}
      {:else}
        <div bind:this={osmdContainer} style="width: 100%; height: 100%; overflow: auto; background: white; color: black; padding: 16px;"></div>
      {/if}
    </div>
  {/if}
</main>
