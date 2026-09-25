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

/**
 * Abstand zur iOS-Statusleiste bestimmen.
 *
 * Als Home-Bildschirm-App beginnt die Seite auf iOS je nach Installation unter der
 * Statusleiste (dann ist der Bildschirm so hoch wie die Seite) oder darunter. Im ersten Fall
 * liefert env(safe-area-inset-top) auf iPhones mit Home-Taste trotzdem oft 0 – dann wird
 * die Kopfzeile verdeckt. Deshalb hier selbst nachmessen und mindestens 20px freihalten.
 */
function updateTopInset() {
  const standalone =
    (navigator as any).standalone === true || window.matchMedia('(display-mode: standalone)').matches;
  const isIOS = /iPad|iPhone|iPod/.test(navigator.userAgent) ||
    (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
  // iOS meldet screen.height immer im Hochformat; im Querformat hat das iPhone keine Statusleiste
  const coversStatusBar = window.innerHeight >= screen.height - 1;
  const inset = standalone && isIOS && coversStatusBar ? '20px' : '0px';
  document.documentElement.style.setProperty('--top-inset', inset);
}
updateTopInset();
window.addEventListener('resize', updateTopInset);
window.addEventListener('orientationchange', () => setTimeout(updateTopInset, 300));

// Service Worker registrieren (Offline-Start + Installierbarkeit)
if ('serviceWorker' in navigator && import.meta.env.PROD) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register(`${import.meta.env.BASE_URL}sw.js`).catch((e) => {
      console.warn('Service Worker Registrierung fehlgeschlagen', e);
    });
  });
}

export default app;
