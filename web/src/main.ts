import { mount } from 'svelte';
import App from './App.svelte';

const app = mount(App, {
  target: document.getElementById('app')!,
});

// iOS Safari ignoriert user-scalable=no: Seiten-Zoom per Geste unterbinden,
// damit Pinch-Zoom nur in der Notenansicht wirkt
for (const type of ['gesturestart', 'gesturechange', 'gestureend']) {
  document.addEventListener(type, (e) => e.preventDefault(), { passive: false });
}

// Service Worker registrieren (Offline-Start + Installierbarkeit)
if ('serviceWorker' in navigator && import.meta.env.PROD) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register(`${import.meta.env.BASE_URL}sw.js`).catch((e) => {
      console.warn('Service Worker Registrierung fehlgeschlagen', e);
    });
  });
}

export default app;
