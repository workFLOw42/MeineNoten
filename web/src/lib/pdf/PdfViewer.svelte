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
    onPageViewChange,
    onNext,
    onPrev,
  }: {
    pdfDoc: any;
    pageIndex: number;
    pageView: PageView | undefined;
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
      await renderPdfPageFitted(pdfDoc, pageIndex + 1, canvas, width, height, zoom);
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
  const pointers = new Map<number, { x: number; y: number }>();
  let startDist = 0;
  let startScale = 1;
  let lastMid = { x: 0, y: 0 };
  let moved = false;
  let downAt = { x: 0, y: 0, t: 0 };
  let lastTap = { x: 0, y: 0, t: 0 };
  let tapTimer: ReturnType<typeof setTimeout> | null = null;

  const mid = () => {
    const p = [...pointers.values()];
    return p.length === 1 ? p[0] : { x: (p[0].x + p[1].x) / 2, y: (p[0].y + p[1].y) / 2 };
  };
  const dist = () => {
    const [a, b] = [...pointers.values()];
    return Math.hypot(a.x - b.x, a.y - b.y);
  };

  function onPointerDown(e: PointerEvent) {
    container?.setPointerCapture(e.pointerId);
    pointers.set(e.pointerId, { x: e.clientX, y: e.clientY });
    if (pointers.size === 1) {
      moved = false;
      downAt = { x: e.clientX, y: e.clientY, t: Date.now() };
    }
    if (pointers.size === 2) {
      startDist = dist();
      startScale = scale;
      moved = true;
    }
    lastMid = mid();
  }

  function onPointerMove(e: PointerEvent) {
    if (!pointers.has(e.pointerId)) return;
    pointers.set(e.pointerId, { x: e.clientX, y: e.clientY });
    const m = mid();

    if (pointers.size >= 2 && startDist > 0) {
      const oldScale = scale;
      const newScale = clamp(startScale * (dist() / startDist), MIN, MAX);
      // Um den Mittelpunkt der Finger zoomen
      const rect = container!.getBoundingClientRect();
      const fx = (m.x - rect.left - width / 2) / width;
      const fy = (m.y - rect.top - height / 2) / height;
      const k = newScale / oldScale;
      ox = fx - (fx - ox) * k + (m.x - lastMid.x) / width;
      oy = fy - (fy - oy) * k + (m.y - lastMid.y) / height;
      scale = newScale;
      applyLimits();
    } else if (pointers.size === 1) {
      if (Math.hypot(e.clientX - downAt.x, e.clientY - downAt.y) > 10) moved = true;
      if (moved && scale > 1.01) {
        ox += (m.x - lastMid.x) / width;
        oy += (m.y - lastMid.y) / height;
        applyLimits();
      }
    }
    lastMid = m;
  }

  function commit() {
    onPageViewChange({ scale, offsetXRatio: ox, offsetYRatio: oy });
    const wanted = Math.max(1, Math.round(scale * 2) / 2);
    if (wanted !== renderedZoom) render(wanted);
  }

  function onPointerUp(e: PointerEvent) {
    if (!pointers.has(e.pointerId)) return;
    const wasPinch = pointers.size >= 2;
    pointers.delete(e.pointerId);
    if (pointers.size === 1) {
      lastMid = mid();
      startDist = 0;
      return;
    }
    if (pointers.size > 0) return;

    if (moved || wasPinch) {
      commit();
      return;
    }
    handleTap(e.clientX, e.clientY);
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
      const rect = container!.getBoundingClientRect();
      const rel = (x - rect.left) / rect.width;
      if (rel < 0.3) onPrev();
      else if (rel > 0.7) onNext();
    }, 250);
  }

  function onWheel(e: WheelEvent) {
    if (!e.ctrlKey) return; // Trackpad-Pinch am Mac/PC
    e.preventDefault();
    scale = clamp(scale * Math.exp(-e.deltaY / 100), MIN, MAX);
    applyLimits();
    commit();
  }
</script>

<div
  bind:this={container}
  class="viewer"
  role="presentation"
  onpointerdown={onPointerDown}
  onpointermove={onPointerMove}
  onpointerup={onPointerUp}
  onpointercancel={onPointerUp}
  onwheel={onWheel}
>
  <canvas
    bind:this={canvas}
    style="transform: translate({ox * width}px, {oy * height}px) scale({scale});"
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
