<script lang="ts">
  import { onMount } from 'svelte';
  import type { OpenSheetMusicDisplay } from 'opensheetmusicdisplay';
  import { renderMusicXml } from './osmdRenderer';

  /**
   * MusicXML-Ansicht mit Zwei-Finger-Zoom.
   *
   * Eigene Komponente, damit sie beim Einblenden selbst rendert: Vorher hat nur openSong()
   * gerendert, und nach dem Bearbeiten (Helligkeit umstellen) war der neu eingehängte
   * Container leer, bis man das Lied erneut öffnete.
   *
   * Zoom: Während der Geste wird nur per CSS skaliert (flüssig), beim Loslassen setzt OSMD
   * den Zoom und rendert neu. Dadurch bleibt die Schrift scharf, und die Takte brechen
   * passend zur Bildschirmbreite um statt seitlich aus dem Bild zu laufen.
   */
  let {
    file,
    filter = 'none',
  }: {
    file: File | ArrayBuffer;
    /** CSS-Filter für das Dunkeldesign (normal / dezenter / invertiert). */
    filter?: string;
  } = $props();

  const MIN_ZOOM = 0.5;
  const MAX_ZOOM = 3;

  let scroller = $state<HTMLDivElement | null>(null);
  let sheet = $state<HTMLDivElement | null>(null);
  let error = $state('');
  let loading = $state(true);
  let osmd: OpenSheetMusicDisplay | null = null;
  let zoom = 1;
  let liveScale = $state(1);

  async function render(source: File | ArrayBuffer) {
    if (!sheet) return;
    loading = true;
    error = '';
    try {
      osmd = await renderMusicXml(source, sheet, zoom);
    } catch (e: any) {
      error = `Die Notendatei konnte nicht gelesen werden: ${e?.message ?? e}`;
    } finally {
      loading = false;
    }
  }

  // Neu rendern, wenn ein anderes Lied kommt (Setlist-Weiterblättern hält die Komponente).
  $effect(() => {
    const source = file;
    if (sheet) render(source);
  });

  // Pinch: nur Zwei-Finger-Gesten abfangen, ein Finger scrollt normal.
  let startDist = 0;
  let pinching = false;
  const dist = (e: TouchEvent) =>
    Math.hypot(e.touches[0].clientX - e.touches[1].clientX, e.touches[0].clientY - e.touches[1].clientY);

  function onTouchStart(e: TouchEvent) {
    if (e.touches.length === 2) {
      e.preventDefault();
      pinching = true;
      startDist = dist(e) || 1;
      liveScale = 1;
    }
  }

  function onTouchMove(e: TouchEvent) {
    if (!pinching || e.touches.length < 2) return;
    e.preventDefault();
    const wanted = zoom * (dist(e) / startDist);
    liveScale = Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, wanted)) / zoom;
  }

  function onTouchEnd(e: TouchEvent) {
    if (!pinching || e.touches.length >= 2) return;
    pinching = false;
    const next = Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, zoom * liveScale));
    // Scrollposition relativ halten, damit man nach dem Zoomen an derselben Stelle ist.
    const ratio = scroller ? scroller.scrollTop / Math.max(1, scroller.scrollHeight) : 0;
    liveScale = 1;
    if (Math.abs(next - zoom) < 0.02 || !osmd) return;
    zoom = next;
    osmd.Zoom = zoom;
    osmd.render();
    if (scroller) scroller.scrollTop = ratio * scroller.scrollHeight;
  }

  // Direkt anhängen: Svelte registriert Touch-Handler passiv, dann wirkt preventDefault() nicht.
  onMount(() => {
    const el = scroller!;
    const opts = { passive: false } as AddEventListenerOptions;
    el.addEventListener('touchstart', onTouchStart, opts);
    el.addEventListener('touchmove', onTouchMove, opts);
    el.addEventListener('touchend', onTouchEnd, opts);
    el.addEventListener('touchcancel', onTouchEnd, opts);
    return () => {
      el.removeEventListener('touchstart', onTouchStart);
      el.removeEventListener('touchmove', onTouchMove);
      el.removeEventListener('touchend', onTouchEnd);
      el.removeEventListener('touchcancel', onTouchEnd);
      osmd?.clear();
      osmd = null;
    };
  });
</script>

<div class="scroller" bind:this={scroller} style:filter={filter}>
  {#if error}
    <p class="error">{error}</p>
  {/if}
  {#if loading}
    <p class="loading">Lade …</p>
  {/if}
  <div
    class="sheet"
    bind:this={sheet}
    style:transform={liveScale !== 1 ? `scale(${liveScale})` : ''}
  ></div>
</div>

<style>
  .scroller {
    width: 100%;
    height: 100%;
    overflow: auto;
    -webkit-overflow-scrolling: touch;
    background: white;
    color: black;
    /* Nur Scrollen dem Browser überlassen, Pinch übernimmt die Komponente. */
    touch-action: pan-x pan-y;
  }
  .sheet {
    padding: 16px;
    transform-origin: top center;
    min-height: 100%;
    box-sizing: border-box;
  }
  .loading, .error {
    padding: 24px;
    text-align: center;
    margin: 0;
  }
  .loading { color: #888; }
  .error { color: #b3261e; }
</style>
