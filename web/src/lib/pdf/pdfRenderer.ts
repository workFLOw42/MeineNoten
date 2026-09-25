/// <reference types="vite/client" />
import * as pdfjsLib from 'pdfjs-dist';
// Worker wird von Vite mitgebaut und lokal ausgeliefert (kein CDN → funktioniert offline)
import pdfWorkerUrl from 'pdfjs-dist/build/pdf.worker.min.js?url';

pdfjsLib.GlobalWorkerOptions.workerSrc = pdfWorkerUrl;

export async function loadPdfDocument(file: File | ArrayBuffer): Promise<pdfjsLib.PDFDocumentProxy> {
  const data = file instanceof File ? await file.arrayBuffer() : file;
  const loadingTask = pdfjsLib.getDocument({ data });
  return await loadingTask.promise;
}

export async function renderPdfPageToCanvas(
  pdfDoc: pdfjsLib.PDFDocumentProxy,
  pageNumber: number, // 1-based
  canvas: HTMLCanvasElement,
  scale: number = 1.0,
  offsetXRatio: number = 0.0,
  offsetYRatio: number = 0.0
): Promise<{ pageCount: number; width: number; height: number }> {
  const page = await pdfDoc.getPage(pageNumber);
  const viewport = page.getViewport({ scale: scale * window.devicePixelRatio });

  canvas.width = viewport.width;
  canvas.height = viewport.height;
  canvas.style.width = '100%';
  canvas.style.height = '100%';
  canvas.style.objectFit = 'contain';

  const context = canvas.getContext('2d');
  if (!context) throw new Error('Could not get 2d context');

  context.save();
  context.fillStyle = '#FFFFFF';
  context.fillRect(0, 0, canvas.width, canvas.height);

  const renderContext = {
    canvasContext: context,
    viewport: viewport,
  };

  await page.render(renderContext).promise;
  context.restore();

  return {
    pageCount: pdfDoc.numPages,
    width: viewport.width,
    height: viewport.height,
  };
}
