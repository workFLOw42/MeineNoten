<script lang="ts">
  import { onMount, onDestroy } from 'svelte';
  import { openDB } from '../lib/storage/db';
  import { loadPdfDocument, renderPdfPageToCanvas } from '../lib/pdf/pdfRenderer';

  type Status = 'ok' | 'fail' | 'info' | 'running';

  let testResults = $state<Array<{ name: string; status: Status; details: string }>>([
    { name: 'Browser & UA', status: 'info', details: navigator.userAgent },
    { name: 'IndexedDB', status: 'running', details: '' },
    { name: 'OPFS (Origin Private File System)', status: 'running', details: '' },
    { name: 'Wake Lock API', status: 'running', details: '' },
    { name: 'Web Share API', status: 'running', details: '' },
    { name: 'Standalone (Home Screen App)', status: 'running', details: '' },
    { name: 'PDF Renderer (pdf.js)', status: 'running', details: '' },
    { name: 'Pedal / Keyboard Log', status: 'ok', details: 'Drücke Pedaltasten oder Tasten zum Testen' },
  ]);

  let lastKey = $state('Keine Taste gedrückt');
  let canvasEl: HTMLCanvasElement;

  onMount(async () => {
    // 1. IndexedDB
    try {
      const db = await openDB();
      updateResult(1, 'ok', `IndexedDB offen (${db.name})`);
    } catch (e: any) {
      updateResult(1, 'fail', e.message);
    }

    // 2. OPFS
    try {
      if (navigator.storage && navigator.storage.getDirectory) {
        const root = await navigator.storage.getDirectory();
        const testHandle = await root.getFileHandle('selftest.txt', { create: true });
        const writable = await testHandle.createWritable();
        await writable.write('test');
        await writable.close();
        await root.removeEntry('selftest.txt');
        updateResult(2, 'ok', 'OPFS schreiben/lesen erfolgreich');
      } else {
        updateResult(2, 'fail', 'OPFS nicht verfügbar in diesem Browser');
      }
    } catch (e: any) {
      updateResult(2, 'fail', e.message);
    }

    // 3. Wake Lock
    if ('wakeLock' in navigator) {
      try {
        const wakeLock = await (navigator as any).wakeLock.request('screen');
        wakeLock.release();
        updateResult(3, 'ok', 'Wake Lock API unterstützt und aktivierbar');
      } catch (e: any) {
        updateResult(3, 'fail', `Wake Lock Fehler: ${e.message}`);
      }
    } else {
      updateResult(3, 'fail', 'Wake Lock API wird von diesem Browser nicht unterstützt');
    }

    // 4. Web Share
    if (navigator.share) {
      updateResult(4, 'ok', 'Web Share API verfügbar');
    } else {
      updateResult(4, 'fail', 'Web Share API nicht verfügbar');
    }

    // 5. Standalone / Home screen
    const isStandalone = window.matchMedia('(display-mode: standalone)').matches || (navigator as any).standalone;
    updateResult(
      5,
      isStandalone ? 'ok' : 'info',
      isStandalone
        ? 'Läuft als Home-Bildschirm-App'
        : 'Läuft im Browser-Tab – funktioniert, für Vollbild auf dem iPad „Zum Home-Bildschirm“ hinzufügen'
    );

    // 6. PDF Renderer
    try {
      // Echter Test: Mini-PDF laden und Seite 1 auf ein Canvas rendern
      const pdfText =
        '%PDF-1.4\n' +
        '1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n' +
        '2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n' +
        '3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 200 100]>>endobj\n' +
        'trailer<</Root 1 0 R>>\n%%EOF';
      const pdfBytes = new TextEncoder().encode(pdfText);
      const started = performance.now();
      const doc = await loadPdfDocument(pdfBytes.buffer);
      const canvas = document.createElement('canvas');
      await renderPdfPageToCanvas(doc, 1, canvas, 1.0);
      const ms = Math.round(performance.now() - started);
      updateResult(6, 'ok', `pdf.js ${doc.numPages} Seite gerendert (${canvas.width}×${canvas.height} px, ${ms} ms, Worker lokal)`);
      await doc.destroy();
    } catch (e: any) {
      updateResult(6, 'fail', e.message);
    }

    window.addEventListener('keydown', handleKeyDown);
  });

  onDestroy(() => {
    window.removeEventListener('keydown', handleKeyDown);
  });

  function handleKeyDown(e: KeyboardEvent) {
    lastKey = `Key: "${e.key}" | Code: "${e.code}" | Which: ${e.keyCode}`;
    testResults[7].details = lastKey;
  }

  function updateResult(index: number, status: Status, details: string) {
    testResults[index] = { ...testResults[index], status, details };
  }

  function copyReport() {
    const icon = (s: Status) => (s === 'ok' ? '✅' : s === 'info' ? 'ℹ️' : s === 'running' ? '⏳' : '❌');
    const report = testResults.map(r => `[${icon(r.status)}] ${r.name}: ${r.details}`).join('\n');
    navigator.clipboard.writeText(report);
    alert('Selbsttest-Bericht in die Zwischenablage kopiert!');
  }
</script>

<div style="padding: 24px; max-width: 600px; margin: 0 auto;">
  <h2>Selbsttest (iPad & Web-App Diagnose)</h2>
  <p>Dieser Test prüft alle technischen Voraussetzungen der Web-App auf diesem Gerät.</p>

  <div style="display: flex; flex-direction: column; gap: 12px; margin: 20px 0;">
    {#each testResults as res}
      <div style="background: #1e1e1e; padding: 12px; border-radius: 8px; border-left: 4px solid {res.status === 'ok' ? '#4caf50' : res.status === 'fail' ? '#f44336' : res.status === 'info' ? '#2196f3' : '#ff9800'}">
        <strong>{res.name}</strong>:
        <span style="color: {res.status === 'ok' ? '#81c784' : res.status === 'fail' ? '#e57373' : res.status === 'info' ? '#64b5f6' : '#ffb74d'}">
          {res.status.toUpperCase()}
        </span>
        <div style="font-size: 13px; color: #aaa; margin-top: 4px; word-break: break-all;">{res.details}</div>
      </div>
    {/each}
  </div>

  <button onclick={copyReport} style="background: #2196f3; color: white; border: none; padding: 12px 24px; border-radius: 8px; font-size: 16px; cursor: pointer; width: 100%;">
    Bericht in Zwischenablage kopieren
  </button>
</div>
