import { defineConfig } from 'vite';
import { svelte } from '@sveltejs/vite-plugin-svelte';

// Zeitstempel des Builds, in den Einstellungen sichtbar: so ist auf dem Gerät erkennbar,
// ob wirklich die neueste Version läuft (und nicht eine aus dem Cache).
const buildStamp = new Date().toISOString().slice(0, 16).replace('T', ' ');

export default defineConfig({
  plugins: [svelte()],
  base: '/MeineNoten/app/',
  define: {
    __BUILD_STAMP__: JSON.stringify(buildStamp),
  },
  build: {
    outDir: 'dist',
    sourcemap: true
  }
});
