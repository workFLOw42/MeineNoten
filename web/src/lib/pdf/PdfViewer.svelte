<script lang="ts">
  import { onMount, untrack } from 'svelte';
  import type { PageView } from '../model/types';
  import { renderPdfPageFitted } from './pdfRenderer';

  /**
   * Eine PDF-Seite mit Pinch-Zoom, Verschieben, Doppeltipp und Tippzonen zum Blättern.
   *
   * Zoom-Werte wie in Android (PdfView.kt): scale 1…5, Verschiebung als Anteil der
   * Anzeigegröße, begrenzt auf ±(scale-1)/2. So passen die Werte aus einer Sicherung.
   */
  let {
    pdfDoc,
    pageIndex,
    pageView,
    tapZonesEnabled = true,
    tapZoneSize = 'LOWER_THIRD',
    swapTapZones = false,
    filter = 'none',
    onPageViewChange,
    onNext,
    onPrev,
  }: {
    pdfDoc: any;
    pageIndex: number;
    pageView: PageView | undefined;
    tapZonesEnabled?: boolean;
    tapZoneSize?: 'LOWER_THIRD' | 'LOWER_HALF' | 'FULL_HEIGHT';
    swapTapZones?: boolean;
    /** CSS-Filter für das Dunkeldesign (normal / dezenter / invertiert). */
    filter?: string;
    onPageViewChange: (view: PageView) => void;
    onNext: () => void;
    onPrev: () => void;
  } = $props();

  const MIN = 1;
  const MAX = 5;

  let container = $state<HTMLDivElement | null>(null);
  let canvas = $state<HTMLCanvasElement | null>(null);
  let width = $state(0);
  let height = $state(0);

  let scale = $state(1);
  let ox = $state(0); // offsetXRatio
  let oy = $state(0); // offsetYRatio
  let renderedZoom = 1;
  /** Angezeigte Größe der eingepassten Seite in CSS-Pixeln (unabhängig von der Auflösung). */
  let cssW = $state(0);
  let cssH = $state(0);

  const clamp = (v: number, lo: number, hi: number) => Math.min(hi, Math.max(lo, v));

  function applyLimits() {
    scale = clamp(scale, MIN, MAX);
    const max = (scale - 1) / 2;
    ox = clamp(ox, -max, max);
    oy = clamp(oy, -max, max);
  }

  // Gespeicherten Zoom nur beim Seitenwechsel übernehmen. Alles andere in untrack():
  // sonst läuft der Effekt bei jeder Fingerbewegung erneut und setzt den Zoom zurück.
  $effect(() => {
    pageIndex;
    pdfDoc;
    untrack(() => {
      scale = pageView?.scale ?? 1;
      ox = pageView?.offsetXRatio ?? 0;
      oy = pageView?.offsetYRatio ?? 0;
      applyLimits();
    });
  });

  async function render(zoom: number) {
    if (!pdfDoc || !canvas || width <= 0 || height <= 0) return;
    renderedZoom = zoom;
    try {
      const size = await renderPdfPageFitted(pdfDoc, pageIndex + 1, canvas, width, height, zoom);
      cssW = size.cssWidth;
      cssH = size.cssHeight;
    } catch (e) {
      console.error('PDF-Seite konnte nicht gezeichnet werden', e);
    }
  }

  // Neu zeichnen bei Seite/Größe; nach einer Zoom-Geste schärft commit() nach
  $effect(() => {
    pdfDoc; pageIndex; width; height;
    untrack(() => render(Math.max(1, Math.round(scale * 2) / 2)));
  });

  onMount(() => {
    const ro = new ResizeObserver(entries => {
      const r = entries[0].contentRect;
      width = r.width;
      height = r.height;
    });
    if (container) ro.observe(container);
    return () => ro.disconnect();
  });

  // ---- Gesten ----
  // Touch-Events statt Pointer-Events: e.touches ist immer die vollständige, aktuelle Liste
  // der Finger. Bei Pointer-Events bleiben auf iOS nach abgebrochenen Gesten Finger "hängen",
  // dann rechnet der Zoom mit falschen Abständen und lässt sich nicht mehr verkleinern.
  let startDist = 0;
  let startScale = 1;
  let lastMid = { x: 0, y: 0 };
  let moved = false;
  let gesture = false;
  let downAt = { x: 0, y: 0 };
  let lastTap = { x: 0, y: 0, t: 0 };
  let tapTimer: ReturnType<typeof setTimeout> | null = null;

  const pts = (e: TouchEvent) => Array.from(e.touches, t => ({ x: t.clientX, y: t.clientY }));
  const mid = (p: { x: number; y: number }[]) =>
    p.length === 1 ? p[0] : { x: (p[0].x + p[1].x) / 2, y: (p[0].y + p[1].y) / 2 };
  const dist = (p: { x: number; y: number }[]) => Math.hypot(p[0].x - p[1].x, p[0].y - p[1].y);

  /** Neuer Ausgangspunkt, sobald sich die Zahl der Finger ändert. */
  function resetGesture(e: TouchEvent) {
    const p = pts(e);
    if (p.length === 0) return;
    lastMid = mid(p);
    if (p.length >= 2) {
      startDist = dist(p);
      startScale = scale;
    } else {
      startDist = 0;
    }
  }

  function onTouchStart(e: TouchEvent) {
    e.preventDefault();
    if (e.touches.length === 1 && !gesture) {
      moved = false;
      downAt = { x: e.touches[0].clientX, y: e.touches[0].clientY };
    }
    if (e.touches.length >= 2) moved = true;
    gesture = true;
    resetGesture(e);
  }

  function onTouchMove(e: TouchEvent) {
    e.preventDefault();
    const p = pts(e);
    if (p.length === 0) return;
    const m = mid(p);

    if (p.length >= 2 && startDist > 0) {
      const oldScale = scale;
      const newScale = clamp(startScale * (dist(p) / startDist), MIN, MAX);
      // Um den Mittelpunkt der Finger zoomen
      const rect = container!.getBoundingClientRect();
      const fx = (m.x - rect.left - width / 2) / width;
      const fy = (m.y - rect.top - height / 2) / height;
      const k = newScale / oldScale;
      ox = fx - (fx - ox) * k + (m.x - lastMid.x) / width;
      oy = fy - (fy - oy) * k + (m.y - lastMid.y) / height;
      scale = newScale;
      applyLimits();
    } else if (p.length === 1) {
      if (Math.hypot(p[0].x - downAt.x, p[0].y - downAt.y) > 10) moved = true;
      if (moved && scale > 1.01) {
        ox += (m.x - lastMid.x) / width;
        oy += (m.y - lastMid.y) / height;
        applyLimits();
      }
    }
    lastMid = m;
  }

  function onTouchEnd(e: TouchEvent) {
    e.preventDefault();
    if (e.touches.length > 0) {
      resetGesture(e); // ein Finger bleibt liegen: nahtlos weiter verschieben
      return;
    }
    gesture = false;
    if (scale < 1.03) { scale = 1; ox = 0; oy = 0; } // fast ganz raus = ganze Seite
    if (moved) {
      commit();
      return;
    }
    const t = e.changedTouches[0];
    if (t) handleTap(t.clientX, t.clientY);
  }

  // Maus am Desktop: ziehen verschiebt, Klick blättert (Touch läuft über die Touch-Events)
  let mouseDown = false;
  function onMouseDown(e: MouseEvent) {
    mouseDown = true;
    moved = false;
    downAt = { x: e.clientX, y: e.clientY };
    lastMid = { x: e.clientX, y: e.clientY };
  }
  function onMouseMove(e: MouseEvent) {
    if (!mouseDown) return;
    if (Math.hypot(e.clientX - downAt.x, e.clientY - downAt.y) > 5) moved = true;
    if (moved && scale > 1.01) {
      ox += (e.clientX - lastMid.x) / width;
      oy += (e.clientY - lastMid.y) / height;
      applyLimits();
    }
    lastMid = { x: e.clientX, y: e.clientY };
  }
  function onMouseUp(e: MouseEvent) {
    if (!mouseDown) return;
    mouseDown = false;
    if (moved) commit();
    else handleTap(e.clientX, e.clientY);
  }

  /** Speichert den Zoom und zeichnet bei Bedarf schärfer bzw. wieder in normaler Auflösung. */
  function commit() {
    onPageViewChange({ scale, offsetXRatio: ox, offsetYRatio: oy });
    const wanted = Math.max(1, Math.round(scale * 2) / 2);
    if (wanted !== renderedZoom) render(wanted);
  }

  function handleTap(x: number, y: number) {
    const now = Date.now();
    const isDouble = now - lastTap.t < 300 && Math.hypot(x - lastTap.x, y - lastTap.y) < 40;
    if (isDouble) {
      if (tapTimer) clearTimeout(tapTimer);
      tapTimer = null;
      lastTap = { x: 0, y: 0, t: 0 };
      if (scale > 1.01) {
        scale = 1; ox = 0; oy = 0;
      } else {
        const rect = container!.getBoundingClientRect();
        const fx = (x - rect.left - width / 2) / width;
        const fy = (y - rect.top - height / 2) / height;
        scale = 2;
        ox = -fx; oy = -fy;
        applyLimits();
      }
      commit();
      return;
    }
    lastTap = { x, y, t: now };
    // Einfacher Tipp: kurz warten, ob ein zweiter folgt; dann Tippzone auswerten
    tapTimer = setTimeout(() => {
      tapTimer = null;
      if (!tapZonesEnabled) return;
      const rect = container!.getBoundingClientRect();
      const heightFraction = tapZoneSize === 'FULL_HEIGHT' ? 1.0 : tapZoneSize === 'LOWER_HALF' ? 0.5 : (1 / 3);
      const zoneTop = rect.top + rect.height * (1 - heightFraction);
      if (y < zoneTop) return;

      const leftHalf = (x - rect.left) < (rect.width / 2);
      if (leftHalf !== swapTapZones) onPrev();
      else onNext();
    }, 250);
  }

  function onWheel(e: WheelEvent) {
    if (!e.ctrlKey) return; // Trackpad-Pinch am Mac/PC
    e.preventDefault();
    scale = clamp(scale * Math.exp(-e.deltaY / 100), MIN, MAX);
    applyLimits();
    commit();
  }
  // Direkt anhängen statt ontouchstart={...}: Svelte 5 registriert Touch- und Wheel-Handler
  // passiv, dann wirkt preventDefault() nicht und iOS zoomt/scrollt selbst mit.
  onMount(() => {
    const el = container!;
    const opts = { passive: false } as const;
    el.addEventListener('touchstart', onTouchStart, opts);
    el.addEventListener('touchmove', onTouchMove, opts);
    el.addEventListener('touchend', onTouchEnd, opts);
    el.addEventListener('touchcancel', onTouchEnd, opts);
    el.addEventListener('wheel', onWheel, opts);
    return () => {
      el.removeEventListener('touchstart', onTouchStart);
      el.removeEventListener('touchmove', onTouchMove);
      el.removeEventListener('touchend', onTouchEnd);
      el.removeEventListener('touchcancel', onTouchEnd);
      el.removeEventListener('wheel', onWheel);
      if (tapTimer) clearTimeout(tapTimer);
    };
  });
</script>

<svelte:window onmouseup={onMouseUp} onmousemove={onMouseMove} />

<div
  bind:this={container}
  class="viewer"
  role="presentation"
  onmousedown={onMouseDown}
>
  <!-- style:… statt style="…": Svelte setzt sonst bei jeder Zoomänderung das ganze
       style-Attribut neu und löscht dabei Breite/Höhe. Dann erscheint das Canvas in voller
       Pixelgröße (2–4× zu groß) und lässt sich nicht mehr herauszoomen. -->
  <canvas
    bind:this={canvas}
    style:width={cssW ? `${cssW}px` : null}
    style:height={cssH ? `${cssH}px` : null}
    style:transform={`translate(${ox * width}px, ${oy * height}px) scale(${scale})`}
    style:filter={filter}
  ></canvas>
</div>

<style>
  .viewer {
    position: absolute;
    inset: 0;
    overflow: hidden;
    display: flex;
    align-items: center;
    justify-content: center;
    touch-action: none;
    background: #000;
  }
  canvas {
    transform-origin: center center;
    will-change: transform;
    background: #fff;
    display: block;
  }
</style>
