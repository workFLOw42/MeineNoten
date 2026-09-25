import { OpenSheetMusicDisplay } from 'opensheetmusicdisplay';
import JSZip from 'jszip';

export async function renderMusicXml(
  file: File | ArrayBuffer,
  container: HTMLElement
): Promise<void> {
  container.innerHTML = '';
  let xmlString = '';

  const buffer = file instanceof File ? await file.arrayBuffer() : file;
  const fileName = file instanceof File ? file.name.toLowerCase() : '';

  if (fileName.endsWith('.mxl') || isZip(buffer)) {
    // Compressed MusicXML (.mxl)
    const zip = new JSZip();
    const contents = await zip.loadAsync(buffer);
    // Find container.xml or first .xml/.musicxml file
    let scoreFile: string | null = null;
    let containerXml = contents.file('META-INF/container.xml');
    if (containerXml) {
      const containerText = await containerXml.async('text');
      const match = containerText.match(/full-path="([^"]+)"/);
      if (match) scoreFile = match[1];
    }
    if (!scoreFile) {
      const xmlEntry = Object.keys(contents.files).find(
        name => !name.startsWith('__MACOSX') && !name.startsWith('META-INF') && (name.endsWith('.xml') || name.endsWith('.musicxml'))
      );
      if (xmlEntry) scoreFile = xmlEntry;
    }
    if (scoreFile && contents.file(scoreFile)) {
      xmlString = await contents.file(scoreFile)!.async('text');
    } else {
      throw new Error('Keine Notendatei im .mxl-Container gefunden');
    }
  } else {
    const decoder = new TextDecoder('utf-8');
    xmlString = decoder.decode(buffer);
  }

  const osmd = new OpenSheetMusicDisplay(container, {
    autoResize: true,
    drawTitle: true,
    backend: 'svg',
  });

  await osmd.load(xmlString);
  osmd.render();
}

function isZip(buffer: ArrayBuffer): boolean {
  const arr = new Uint8Array(buffer);
  return arr.length > 4 && arr[0] === 0x50 && arr[1] === 0x4b && arr[2] === 0x03 && arr[3] === 0x04;
}
