import { defineConfig } from 'vite';
import { svelte } from '@sveltejs/vite-plugin-svelte';
import { execSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const webDir = fileURLToPath(new URL('.', import.meta.url));

// Zeitstempel des Builds, in den Einstellungen sichtbar: so ist auf dem Gerät erkennbar,
// ob wirklich die neueste Version läuft (und nicht eine aus dem Cache).
const buildStamp = new Date().toISOString().slice(0, 16).replace('T', ' ');

// Webversion = Anzahl der Commits, die web/ verändert haben. Zählt so bei jeder Änderung an
// der Web-App von selbst hoch, ohne dass jemand eine Nummer pflegen muss. (Im GitHub-Workflow
// wird dafür die volle Historie geladen: fetch-depth: 0.)
function webVersion() {
  try {
    return execSync('git rev-list --count HEAD -- .', { cwd: webDir, stdio: ['ignore', 'pipe', 'ignore'] })
      .toString()
      .trim();
  } catch {
    return process.env.GITHUB_RUN_NUMBER ?? '0';
  }
}

export default defineConfig({
  plugins: [svelte()],
  base: '/MeineNoten/app/',
  define: {
    __BUILD_STAMP__: JSON.stringify(buildStamp),
    __WEB_VERSION__: JSON.stringify(webVersion()),
  },
  build: {
    outDir: 'dist',
    sourcemap: true
  }
});
