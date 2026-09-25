/// <reference types="vite/client" />
import * as pdfjsLib from 'pdfjs-dist';
// Worker wird von Vite mitgebaut und lokal ausgeliefert (kein CDN → funktioniert offline)
import pdfWorkerUrl from 'pdfjs-dist/build/pdf.worker.min.js?url';

pdfjsLib.GlobalWorkerOptions.workerSrc = pdfWorkerUrl;

/** iOS Safari verweigert Canvas über ~16,7 Mio. Pixel; mit Abstand darunter bleiben. */
const MAX_CANVAS_PIXELS = 12_000_000;

export async function loadPdfDocument(file: File | ArrayBuffer): Promise<pdfjsLib.PDFDocumentProxy> {
  const data = file instanceof File ? await file.arrayBuffer() : file;
  const loadingTask = pdfjsLib.getDocument({ data });
  return await loadingTask.promise;
}

const runningTasks = new WeakMap<HTMLCanvasElement, { cancel: () => void }>();

/**
 * Zeichnet eine Seite eingepasst in [containerWidth]×[containerHeight] (CSS-Pixel).
 *
 * Die CSS-Größe des Canvas entspricht immer der eingepassten Seite; [zoom] erhöht nur die
 * Auflösung, damit die Seite beim Heranzoomen (per CSS-transform) scharf bleibt.
 */
export async function renderPdfPageFitted(
  pdfDoc: pdfjsLib.PDFDocumentProxy,
  pageNumber: number, // 1-based
  canvas: HTMLCanvasElement,
  containerWidth: number,
  containerHeight: number,
  zoom: number = 1
): Promise<{ cssWidth: number; cssHeight: number }> {
  runningTasks.get(canvas)?.cancel();

  const page = await pdfDoc.getPage(pageNumber);
  const base = page.getViewport({ scale: 1 });
  const fit = Math.min(containerWidth / base.width, containerHeight / base.height);
  const cssWidth = Math.floor(base.width * fit);
  const cssHeight = Math.floor(base.height * fit);

  let pixelScale = fit * (window.devicePixelRatio || 1) * Math.max(1, zoom);
  const area = base.width * base.height * pixelScale * pixelScale;
  if (area > MAX_CANVAS_PIXELS) pixelScale *= Math.sqrt(MAX_CANVAS_PIXELS / area);

  const viewport = page.getViewport({ scale: pixelScale });

  // In ein Zwischen-Canvas zeichnen, damit beim Nachschärfen nichts weiß aufblitzt
  const buffer = document.createElement('canvas');
  buffer.width = Math.floor(viewport.width);
  buffer.height = Math.floor(viewport.height);
  const bctx = buffer.getContext('2d');
  if (!bctx) throw new Error('Could not get 2d context');
  bctx.fillStyle = '#FFFFFF';
  bctx.fillRect(0, 0, buffer.width, buffer.height);

  const task = page.render({ canvasContext: bctx, viewport });
  runningTasks.set(canvas, task);
  try {
    await task.promise;
  } catch (e: any) {
    if (e?.name === 'RenderingCancelledException') return { cssWidth, cssHeight };
    throw e;
  } finally {
    if (runningTasks.get(canvas) === task) runningTasks.delete(canvas);
  }

  canvas.width = buffer.width;
  canvas.height = buffer.height;
  canvas.style.width = `${cssWidth}px`;
  canvas.style.height = `${cssHeight}px`;
  canvas.getContext('2d')?.drawImage(buffer, 0, 0);
  return { cssWidth, cssHeight };
}
