# Konzept: Meine Noten fürs iPad (Web-App)

## 1. Ziel

*Meine Noten* gibt es bisher nur für Android. iPad-Nutzer sollen die App ebenfalls
bekommen – **ohne App Store** und ohne Apple-Entwicklerkonto.

Der Weg: eine **installierbare Web-App (PWA)**. Man öffnet sie einmal in Safari, legt sie
über *Teilen → Zum Home-Bildschirm* ab und startet sie danach wie eine App: ohne
Adressleiste, auch ohne Internet.

Leitlinien, übernommen aus der Android-App:

*   **Beim Spielen sind beide Hände belegt.** Umblättern per Pedal oder mit einer
    blinden Berührung, nichts darf Aufmerksamkeit fordern.
*   **Die Daten bleiben auf dem Gerät.** Kein Konto, kein Server, keine Übertragung von
    Noten. Der Webserver liefert nur die App selbst aus.
*   **Dasselbe Sicherungsformat wie Android.** Eine ZIP-Datei von der Android-App lässt
    sich im iPad einlesen und umgekehrt. Das Format ist die Schnittstelle, gemeinsamer
    Code ist es nicht.

| Punkt | Festlegung |
|---|---|
| Zielgerät | iPad mit aktuellem iPadOS (Safari), Querformat und Hochformat |
| Nebenbei | läuft auch in Chrome/Edge am Rechner – nützlich zum Entwickeln, kein Ziel |
| Weg | eigene Web-App in TypeScript („Weg A“), kein Kotlin Multiplatform |
| Hosting | GitHub Pages, dauerhaft: `https://workflow42.github.io/MeineNoten/app/` |
| Name auf dem Home-Bildschirm | *Meine Noten* |
| Oberfläche | Svelte (keine Vorkenntnisse in Svelte oder React – Svelte ist das einfachere) |
| Testgeräte | Android-Tablets, WebKit am Rechner, iPad nur geliehen oder über Feedback (Abschnitt 10) |
| Sprache | Deutsch zuerst, Englisch vorbereitet |

---

## 2. Umfang

Bewusst kleiner als die Android-App. Maßstab: Ein Gottesdienst lässt sich mit dem iPad
vollständig spielen, und die Sammlung wandert verlustfrei zwischen den Geräten.

### Erste Version

| Bereich | Funktion |
|---|---|
| Lieder | PDF importieren, Titel, Künstler, Version, Genre bearbeiten, löschen |
| Lieder | Songliste mit Suche und Sortierung nach Titel / Künstler |
| Anzeige | PDF seitenweise, seitenverhältnistreu, Nachbarseiten vorgeladen |
| Anzeige | Zwei-Finger-Zoom und Verschieben, pro Seite gespeichert |
| Anzeige | MusicXML (`.xml`, `.musicxml`, `.mxl`) mit OpenSheetMusicDisplay, als durchgehende Ansicht wie auf Android |
| Blättern | Tippzonen unten (Größe wählbar, tauschbar), Bluetooth-Pedal, Randblitz |
| Blättern | Statusleiste mit Titel, „Lied x/n“, „Seite x/n“, Knöpfe ◀ ▶ |
| Setlists | anlegen, Lieder hinzufügen und sortieren, Datum, Notiz |
| Setlists | am Liedende weiter zum nächsten Lied, *Fortsetzen* an Lied und Seite |
| Person | eigener Name, Kennung, Titel „‹Name›s Noten“ |
| Notizen | eigene Notiz pro Lied; fremde Notizen aus Sicherungen lesen, ein-/ausblenden, löschen |
| Sicherung | *Alles sichern*, *Setlist teilen*, *Sicherung einlesen* – gleiches ZIP-Format |
| Sicherung | Vergleichsmaske mit Gruppen und Entscheidung pro Lied (ohne Vorschaubild) |
| Sicherung | Erinnerung nach 30 Tagen, „Letzte Sicherung: vor 12 Tagen“ |
| Anzeige | Bildschirm bleibt an, Design System / Hell / Dunkel |

### Später

| Funktion | Warum später |
|---|---|
| Textlieder / Liedtext | einfach, aber nicht nötig für den ersten Gottesdienst |
| Vorschaubild in der Vergleichsmaske | pdf.js kann es, kostet aber Arbeitsspeicher |
| Filter nach Genre / Setlist, Buchstabenleiste, weitere Sortiermodi | Komfort |
| Personenfarben, Nummer bei gleichen Namen, Frage „Bist du diese Person?“ | erst nötig, wenn mehrere Personen austauschen |
| Setlists duplizieren, Zeitraumfilter | Komfort |

### Nicht vorgesehen

| Funktion | Grund |
|---|---|
| Lautstärketasten blättern | Safari gibt diese Tasten nicht an Webseiten weiter |
| Android-Sicherung (Google-Konto) | gibt es im Web nicht; Ersatz ist die ZIP-Sicherung |
| Dynamische Farben | gibt es im Web nicht |
| Abgleich über Server / Konto | widerspricht „Daten bleiben lokal“ |

---

## 3. Plattform: was Safari auf dem iPad kann

| Bedarf | Web-Technik | Einschätzung |
|---|---|---|
| Offline starten | Service Worker, alle Dateien vorab zwischengespeichert | zuverlässig |
| App-Gefühl | Web-App-Manifest, `display: standalone`, Symbol, Startbild | zuverlässig |
| Strukturierte Daten | IndexedDB | zuverlässig |
| Große Dateien (PDFs) | Origin Private File System (OPFS) | Lesen überall; Schreiben sicher über `createSyncAccessHandle` im Web Worker – im Prototyp prüfen, Rückfall: Dateien als Blob in IndexedDB |
| Speicher dauerhaft | `navigator.storage.persist()` | siehe *Speicherrisiko* |
| PDF anzeigen | pdf.js (Mozilla) in einem Web Worker | bewährt |
| ZIP lesen/schreiben | fflate | klein, schnell, streamt |
| SHA-256 | `crypto.subtle.digest` | eingebaut |
| Pedal | `keydown` (Pfeiltasten, Bild auf/ab, Leertaste) | übliche Pedale geben sich als Tastatur aus |
| Bildschirm an | Screen Wake Lock API | ab iPadOS 16.4, als Home-Bildschirm-App auf dem Gerät prüfen |
| Datei auswählen | `<input type="file">` | auch aus iCloud Drive, Google Drive, „Dateien“ |
| Sicherung speichern | Web Share API (`navigator.share` mit Datei), sonst Download | Teilen-Menü: „In Dateien sichern“, AirDrop, Messenger |

### Speicherrisiko

Safari löscht Daten von Webseiten, die sieben Tage nicht benutzt wurden. **Web-Apps auf
dem Home-Bildschirm sind davon ausgenommen.** Daraus folgt:

*   Die App erkennt, ob sie im Browser-Tab läuft (`display-mode: standalone`). Im Tab
    zeigt sie eine Anleitung „Zum Home-Bildschirm hinzufügen“ und warnt, dass Daten dort
    verloren gehen können. Importieren ist im Tab erlaubt, aber mit Warnhinweis.
*   Beim ersten Import fordert sie `navigator.storage.persist()` an und zeigt in den
    Einstellungen, ob Speicher dauerhaft ist und wie viel belegt ist
    (`navigator.storage.estimate()`).
*   Die **ZIP-Sicherung ist die eigentliche Absicherung.** Die Erinnerung nach 30 Tagen
    ist hier standardmäßig an. Abschaltbar bleibt sie, aber der Schalter nennt den Grund:
    „Ohne Sicherung gehen Noten verloren, wenn die App gelöscht wird.“

> [!WARNING]
> Wer die Web-App vom Home-Bildschirm löscht, löscht auch alle Noten darin – ohne
> Rückfrage, anders als bei einer App aus dem Store. Das gehört in die Anleitung und in
> den Hinweis beim ersten Start.

### Wichtig: eine Web-App ist eine Adresse

Daten gehören zu **Protokoll + Domain**. Zieht die App später um, sieht sie ihre alten
Daten nicht mehr; Umzug geht dann nur über eine ZIP-Sicherung.

**Festgelegt:** `https://workflow42.github.io/MeineNoten/app/`, dauerhaft. Daraus folgt:

*   Die Adresse und der Pfad `/app/` ändern sich nicht mehr, auch nicht bei einer
    Umbenennung des Repositorys (die würde den Pfad ändern).
*   Die Datenschutzerklärung unter `/MeineNoten/` teilt sich den Ursprung
    `workflow42.github.io` mit der App. Das ist unkritisch, solange dort nur statische
    Seiten ohne Skripte liegen – andere Web-Apps unter demselben Konto würden sonst
    denselben Speicher sehen.

---

## 4. Technik

| Schicht | Wahl | Grund |
|---|---|---|
| Sprache | TypeScript (strict) | Typen für das Sicherungsformat |
| Oberfläche | Svelte 5 + SvelteKit (statischer Export) | wenig Laufzeit-Code, schnell auf dem iPad, näher an HTML und damit leichter zu lernen |
| Build | Vite | Standard, schnell |
| PWA | `vite-plugin-pwa` (Workbox) | Service Worker und Manifest |
| PDF | `pdfjs-dist` | bewährt, rendert im Worker |
| MusicXML | `opensheetmusicdisplay` | dieselbe Bibliothek wie in der Android-App (`assets/osmd/`), dort schon im WebView erprobt |
| ZIP | `fflate` | streamend, ohne Abhängigkeiten |
| Datenbank | `idb` (dünne Hülle um IndexedDB) | Promises statt Callbacks |
| Tests | Vitest (Logik), Playwright mit WebKit (Oberfläche) | WebKit ist Safaris Engine |
| Hosting | GitHub Pages über GitHub Actions | kostenlos, statisch |

> [!NOTE]
> Svelte statt React, weil keine Vorkenntnisse in einem von beiden bestehen: Eine
> Svelte-Komponente ist im Kern eine HTML-Datei mit etwas Skript, React verlangt mehr
> eigene Denkweise (Hooks, JSX). Dazu ist die App kleiner und startet schneller. Der
> Nachteil – weniger Beispiele im Netz als bei React – wiegt bei einer App dieser Größe
> wenig. Daten- und Sicherungsschicht sind ohnehin reines TypeScript und hängen nicht
> an Svelte.

### Projektort

Ein eigenes Verzeichnis `web/` im bestehenden Repository `MeineNoten`:

*   Konzept, Sicherungsformat und Testdateien liegen dann nebeneinander.
*   Ein Commit kann Format-Änderung auf beiden Seiten gemeinsam enthalten.
*   Gradle ignoriert `web/`, die Android-Builds bleiben unberührt.

```text
MeineNoten/
├── app/                     Android
├── docs/                    Datenschutzerklärung (GitHub Pages)
├── format/                  Sicherungsformat: Beschreibung + Beispiel-ZIPs
│   ├── BACKUP_FORMAT.md
│   └── samples/
└── web/                     iPad-Web-App
    ├── src/
    │   ├── lib/model/       Song, Setlist, SongNote, Manifest, Settings
    │   ├── lib/logic/       Vergleich, Notizen zusammenführen, Genitiv, Sortierung
    │   ├── lib/storage/     IndexedDB, OPFS
    │   ├── lib/backup/      ZIP lesen und schreiben
    │   ├── lib/pdf/         pdf.js, Seitencache
    │   └── routes/          Songliste, Setlists, Notenansicht, Einstellungen, Vergleich
    └── tests/
```

---

## 5. Sicherungsformat als gemeinsame Schnittstelle

Das ZIP-Format der Android-App (Konzept.md, Abschnitt 5b) wird **unverändert**
übernommen und in `format/BACKUP_FORMAT.md` als verbindliche Beschreibung festgehalten.
Beide Apps müssen sich daran halten; Änderungen erhöhen `formatVersion`.

```text
20261004_Komplett_iPad_Florian.zip
├── manifest.json      formatVersion, appVersion, type, createdAt, deviceName,
│                      authorId, authorName, title, fileHashes
├── songs.json         Liste von Song, fileUri relativ ("files/<id>.pdf")
├── setlists.json      Liste von Setlist
├── settings.json      nur bei type = KOMPLETT
└── files/<id>.<ext>   PDF oder MusicXML
```

### Regeln, die die Web-App genau einhalten muss

| Punkt | Festlegung (aus dem Android-Code) |
|---|---|
| JSON | UTF-8, unbekannte Felder ignorieren, fehlende Felder = Standardwert |
| Feldnamen | wie serialisiert, z. B. `songNotes` für Notizen, `notes` = alte Einzelnotiz (nur lesen) |
| `type` | `"KOMPLETT"` oder `"SETLIST"` |
| `sourceType` | `"PDF"`, `"MUSIC_XML"`, `"TEXT"` |
| `tapZoneSize` | `"LOWER_THIRD"`, `"LOWER_HALF"`, `"FULL_HEIGHT"` |
| `themeMode` | `"SYSTEM"`, `"LIGHT"`, `"DARK"` |
| `pageViews` | Objekt mit Seitenindex als **String-Schlüssel** (`"0"`, `"1"`), Werte `scale`, `offsetXRatio`, `offsetYRatio` |
| Zeitstempel | Epoch-Millisekunden als Zahl (`createdAt`, `editedAt`, `lastOpenedAt`, `lastPlayedAt`) |
| Prüfsumme | SHA-256 der Datei, Kleinbuchstaben-Hex, in `fileHashes["files/<id>.pdf"]` und `Song.fileHash` |
| IDs | UUID v4 als String (`crypto.randomUUID()`) |
| Unbekannte Felder | beim **Weiterschreiben erhalten**, nicht verwerfen |

Der letzte Punkt ist für die Web-App neu wichtig: Sie kennt nicht alle Felder der
Android-App (z. B. `bpm`, `lyrics`, Einstellungen zur Lautstärketaste). Liest sie eine
Sicherung ein und schreibt später selbst eine, müssen diese Felder unverändert mitgehen.
Deshalb speichert sie jedes Lied als **vollständiges JSON-Objekt** und ändert nur die
Felder, die sie kennt. So wandern Einstellungen wie `volumeKeysTurnPages` über das iPad
zurück aufs Android-Tablet, ohne verloren zu gehen.

### Personenfarbe

Die Farbe hängt an `String.hashCode()` der Kennung (Java-Algorithmus:
`h = 31 * h + Zeichen`, 32 Bit mit Überlauf) und der festen Palette aus acht Tönen. Die
Web-App bildet genau das nach, damit dieselbe Person auf beiden Geräten dieselbe Farbe
hat – relevant erst mit den Personenfarben („Später“), aber von Anfang an so vorgesehen.

### Gemeinsame Testdateien

`format/samples/` enthält kleine Beispiel-Sicherungen, von der Android-App erzeugt:

| Datei | Inhalt |
|---|---|
| `komplett_minimal.zip` | 2 Lieder, 1 Setlist, Einstellungen |
| `setlist.zip` | 1 Setlist mit 3 Liedern |
| `notizen_personen.zip` | Notizen von drei Personen, zwei davon „Anna“ |
| `alt_formatVersion1_einzelnotiz.zip` | alte Einzelnotiz im Feld `notes` |

Beide Seiten testen dagegen: Die Web-App liest jede Datei, schreibt sie neu, und das
Ergebnis muss sich von der Android-App einlesen lassen (Hin- und Rückweg). Auf Android
kommt ein entsprechender Test hinzu, der Web-erzeugte Dateien liest.

---

## 6. Speicherung auf dem Gerät

| Daten | Ort | Schlüssel |
|---|---|---|
| Lieder | IndexedDB, Store `songs` | `id` |
| Setlists | IndexedDB, Store `setlists` | `id` |
| Einstellungen | IndexedDB, Store `settings`, ein Eintrag | `"app"` |
| Notendateien | OPFS, `files/<id>.<ext>` | Dateiname |
| Seitenbilder | nur im Arbeitsspeicher, höchstens 5 Seiten | – |

*   `fileUri` heißt in der Web-App intern immer `files/<id>.<ext>`, also genau wie im
    ZIP. Beim Export entfällt damit das Umrechnen der Pfade.
*   Lied und Datei bleiben eine Einheit: Beim Import wird zuerst die Datei in OPFS
    geschrieben, dann das Lied gespeichert. Beim Löschen umgekehrt. Beim Start prüft
    die App, ob zu jedem Lied die Datei da ist, und räumt verwaiste Dateien auf.
*   `userId` wird beim ersten Start erzeugt (`crypto.randomUUID()`).
*   Einstellungen, die nur Android kennt, stehen im selben Eintrag und werden
    mitgesichert (siehe Abschnitt 5).

---

## 7. Bildschirme

Die Aufteilung folgt der Android-App, damit Wechsler sich zurechtfinden.

| Bildschirm | Inhalt |
|---|---|
| Songliste | Titel „‹Name›s Noten“, Suche, Sortierung, Knopf *Noten importieren* (PDF, MusicXML) |
| Lied bearbeiten | Titel, Künstler, Version, Genre, eigene Notiz |
| Setlists | Liste mit Datum, *Fortsetzen*, neue Setlist |
| Setlist | Lieder sortieren (Ziehen), hinzufügen, entfernen, Notiz, *Teilen* |
| Notenansicht | Vollbild, Statusleiste, Tippzonen, Notizen einblendbar |
| Einstellungen | Person, Statusleiste, Blättern, Anzeige, Datensicherung, Speicherstatus |
| Vergleich | Herkunft, Gruppen (identisch / andere Angaben / andere Fassung / neu), Entscheidung pro Lied, Setlists, Einstellungen |
| Erster Start | Anleitung „Zum Home-Bildschirm“, Hinweis auf Sicherung |

Navigation: seitliches Menü wie auf Android, auf dem iPad im Querformat dauerhaft
sichtbar. Bedienelemente mindestens 44 × 44 pt (Apple-Richtlinie).

### Notenansicht im Detail

*   pdf.js rendert die aktuelle Seite in ein `<canvas>` in Geräteauflösung
    (`devicePixelRatio`), die Nachbarseiten im Hintergrund.
*   **Obergrenze Arbeitsspeicher:** Safari beendet Tabs, die zu viel Canvas-Speicher
    belegen. Pro Seite gilt eine Obergrenze von rund 16 Megapixeln; beim Zoomen wird
    nicht die ganze Seite größer gerendert, sondern nur der sichtbare Ausschnitt scharf
    nachgezeichnet.
*   Zoom 1- bis 5-fach, Verschieben, gespeichert als `pageViews` mit denselben
    Verhältniswerten wie Android – so bleibt der Ausschnitt beim Gerätewechsel gleich.
*   Tippzonen und Wischen über Pointer Events; die Standardgesten von Safari (Doppeltipp-
    Zoom, Zurück-Wischen) werden in der Notenansicht mit `touch-action: none` abgeschaltet.
*   Pedal: `ArrowRight`, `ArrowDown`, `PageDown`, `Space`, `Enter` = vor;
    `ArrowLeft`, `ArrowUp`, `PageUp` = zurück – dieselbe Zuordnung wie Android, Richtung
    umkehrbar.
*   Wake Lock beim Öffnen anfordern, bei Rückkehr in die App erneut (iPadOS gibt ihn beim
    Wechsel ab).

---

## 8. Sichern und Einlesen

**Alles sichern / Setlist teilen**

1.  Prüfen, dass jedes Lied (außer Textliedern) seine Datei hat; sonst Lied nennen und
    fragen.
2.  ZIP mit fflate im Worker bauen. PDFs **gespeichert, nicht komprimiert**, JSON
    komprimiert – wie Android. Fortschritt „Lied 34 von 87“.
3.  Dateiname nach demselben Schema: `JJJJMMTT_[Art]_[Gerät]_[Name].zip`. Gerätename:
    selbst vergeben in den Einstellungen, sonst `iPad`.
4.  Teilen-Menü öffnen (`navigator.share({ files })`). Von dort *In Dateien sichern*,
    AirDrop, Mail, Messenger. Ohne Web Share: Download.
5.  `lastBackupAt` erst setzen, wenn das Teilen nicht abgebrochen wurde.

> [!NOTE]
> Anders als bei Android entsteht die ZIP-Datei zuerst **komplett im Speicher**, bevor
> das Teilen-Menü sie übernimmt; Safari kann nicht in eine gewählte Datei streamen. Bei
> 300 MB Sammlung ist das auf einem iPad mit 4 GB machbar, aber die Grenze liegt näher.
> Prüfen im Prototyp; notfalls ZIP als Datei in OPFS aufbauen und von dort teilen.

**Sicherung einlesen**

1.  Datei wählen, ZIP im Worker entpacken, `manifest.json` prüfen, Prüfsummen
    nachrechnen.
2.  Vergleich mit dem eigenen Bestand – dieselbe Einteilung wie
    `BackupLogic.analyzeBackupSongs`: identisch, gleiche Datei mit anderen Angaben,
    mögliche andere Fassung (gleicher Titel + Künstler), neu.
3.  Vergleichsmaske, Entscheidung pro Lied: *Meins behalten*, *Sicherung übernehmen*,
    *Beide behalten*, *Überspringen*; fremde Notizen zusammenführen (`mergedWith`).
4.  Erst nach *Jetzt importieren* wird geschrieben.

Die Vergleichs- und Notizlogik wird aus Kotlin **Funktion für Funktion** nach
TypeScript übertragen, zusammen mit den Unit-Tests aus `BackupLogicTest` und
`BackupRepositoryTest`. Dieselben Testfälle auf beiden Seiten sind die Absicherung
gegen schleichendes Auseinanderlaufen.

---

## 9. Auslieferung, Datenschutz, Updates

| Punkt | Festlegung |
|---|---|
| Adresse | `https://workflow42.github.io/MeineNoten/app/`, dauerhaft (Abschnitt 3) |
| Name | *Meine Noten* auf dem Home-Bildschirm (`name` und `short_name` im Manifest, `apple-mobile-web-app-title`) |
| Build | GitHub Action: bei Push auf `main` mit Änderung in `web/` bauen, testen, veröffentlichen |
| Datenschutzerklärung | bestehende um einen Abschnitt *Web-App* ergänzen |
| Tracking | keines, keine externen Schriften oder CDNs – alles wird mit ausgeliefert |
| Updates | Service Worker lädt neue Version im Hintergrund, Hinweis „Neue Version – neu laden“; **nie während die Notenansicht offen ist** |
| Versionsnummer | eigene Zählung (`web 0.1.0`), im Manifest als `appVersion` |

Inhalt des Zusatzes zur Datenschutzerklärung:

*   GitHub (USA) sieht beim Laden der App die IP-Adresse und den Browser; Noten, Namen
    und Notizen werden nie übertragen.
*   Alle Daten liegen im Speicher des Browsers auf dem Gerät.
*   Nach dem ersten Laden funktioniert die App ohne Verbindung; beim Start mit Internet
    wird nur auf eine neue Version geprüft.

> [!IMPORTANT]
> Ein Update darf das Datenmodell nur **vorwärts** umbauen, mit IndexedDB-Versions-
> nummer und Migration, und nie Daten verwerfen. Bei einer Store-App kann man notfalls
> eine alte Version zurückspielen; bei einer Web-App haben alle Nutzer sofort die neue.

---

## 10. Vorgehen in Schritten

| Schritt | Inhalt | Prüfung |
|---|---|---|
| 0 | `format/BACKUP_FORMAT.md` und Beispiel-ZIPs aus der Android-App | Beschreibung deckt alle Felder |
| 1 | **Prototyp:** PDF und MusicXML importieren, speichern, anzeigen, Tippzonen, Pedal, Wake Lock, Home-Bildschirm, Selbsttest-Seite | Android-Tablet, WebKit am Rechner, dann **ein** geliehenes iPad (Abschnitt 10a) |
| 2 | Datenmodell, IndexedDB, Songliste, Lied bearbeiten | Vitest |
| 3 | Notenansicht vollständig: Statusleiste, Zoom pro Seite, Randblitz, Nachbarseiten | Android-Tablet, WebKit, große Scans |
| 4 | Setlists mit Weiterblättern und *Fortsetzen* | Probe auf Android-Tablet |
| 5 | Sicherung schreiben und einlesen, Vergleichsmaske | Beispiel-ZIPs, Hin- und Rückweg mit Android |
| 6 | Einstellungen, Person, Notizen, Erinnerung, erster Start | – |
| 7 | Auslieferung: GitHub Action, Datenschutz, Anleitung | zweiter iPad-Termin, dann Testgruppe |

Schritt 1 entscheidet über den ganzen Weg: Wenn Speichern oder Umblättern auf dem iPad
nicht verlässlich ist, wird das vor der eigentlichen Arbeit sichtbar.

---

## 10a. Testen ohne eigenes iPad

Es gibt kein eigenes iPad. Gebaut wird deshalb weitgehend „blind“; das Konzept sorgt
dafür, dass die wenigen iPad-Termine möglichst viel klären.

### Was ohne iPad prüfbar ist

| Prüfweg | Deckt ab | Deckt nicht ab |
|---|---|---|
| **Android-Tablets** (Chrome) | Bedienung mit Fingern, Tippzonen, Pedal, Zoom, Layout auf 8–11 Zoll, Offline-Start, Home-Bildschirm | Safari-Eigenheiten |
| **Playwright mit WebKit** am Rechner | Safaris Engine: Layout, IndexedDB, OPFS, ZIP, pdf.js, automatisierte Abläufe | iPadOS-Speicherregeln, Home-Bildschirm, Wake Lock, echte Geräteleistung |
| **Vitest** | Logik: Format, Vergleich, Notizen, Genitiv, Sortierung | Oberfläche |
| **Beispiel-ZIPs** | Austausch mit der Android-App in beide Richtungen | – |

WebKit am Rechner ist nicht dasselbe wie Safari auf dem iPad, kommt aber nahe genug, um
die meisten Fehler vor dem ersten iPad-Termin zu finden.

### Was nur ein echtes iPad zeigt

| Punkt | Warum |
|---|---|
| Daten überleben Neustart und 8 Tage Pause | Speicherregeln von iPadOS |
| „Zum Home-Bildschirm“, Start ohne Adressleiste, Symbol, Name | nur auf iPadOS |
| Bildschirm bleibt an | Wake Lock als Home-Bildschirm-App |
| Bluetooth-Pedal | Tastencodes unter iPadOS |
| Große Scans, starker Zoom | Speichergrenze für Canvas |
| Sichern über das Teilen-Menü | Web Share mit Dateien |

### Selbsttest-Seite

Damit ein geliehenes iPad in zehn Minuten möglichst viel verrät, bekommt die App eine
versteckte Seite **Selbsttest** (Einstellungen → über die Versionsnummer). Sie prüft
automatisch und zeigt je Punkt ✅ oder ❌:

*   iPadOS- und Safari-Version, Bildschirmgröße, `devicePixelRatio`
*   läuft als Home-Bildschirm-App ja/nein, Speicher dauerhaft ja/nein, belegt/frei
*   OPFS schreiben und lesen, IndexedDB, Wake Lock, Web Share mit Datei
*   Test-PDF rendern, Zeit für die erste Seite, größte Canvas-Fläche ohne Absturz
*   Test-MusicXML rendern
*   Pedal-Feld: jede gedrückte Taste mit Namen und Code

Das Ergebnis lässt sich mit einem Knopf als Text kopieren oder teilen – per Messenger
zurück an dich. So hilft auch jemand ohne technisches Wissen, und das Ergebnis ist
vergleichbar.

### iPad-Termine planen

| Termin | Zweck | Dauer |
|---|---|---|
| 1 – nach Schritt 1 | Selbsttest, App installieren, ein PDF importieren, blättern, Pedal | 20 Minuten |
| 1 + 8 Tage | Nachfragen: Ist das PDF noch da? (iPad bleibt beim Freund) | 1 Minute |
| 2 – nach Schritt 7 | Kompletter Ablauf: Sicherung von Android einlesen, Setlist spielen, sichern | 30 Minuten |

Der Test „nach 8 Tagen noch da“ braucht das iPad nicht bei dir: Die App bleibt beim
Freund installiert, er öffnet sie nach einer Woche und meldet, ob das Lied noch da ist.

### Danach: Testgruppe

Wer im Chor oder Bekanntenkreis ein iPad hat, bekommt die Adresse und eine kurze
Anleitung. Rückmeldungen über den Selbsttest-Text oder einen Knopf *Problem melden*,
der eine vorbereitete E-Mail mit Selbsttest-Ergebnis öffnet – ohne dass Daten die App
automatisch verlassen.

> [!NOTE]
> Gegenüber einer Store-App ist das Blind-Bauen hier weniger riskant, als es klingt:
> Ein Fehler lässt sich innerhalb von Minuten beheben und ist beim nächsten Start bei
> allen – ohne Prüfung durch Apple. Wichtig ist nur, dass kein Update Daten beschädigt
> (Abschnitt 9).

---

## 11. Risiken

| Risiko | Folge | Gegenmaßnahme |
|---|---|---|
| Safari löscht Daten | Sammlung weg | Home-Bildschirm-Pflicht, `persist()`, Erinnerung, Hinweis im Tab |
| Web-App gelöscht | Sammlung weg, ohne Rückfrage | Warnung beim ersten Start, Sicherung |
| Speicher beim Rendern | App stürzt beim Zoomen ab | Obergrenze pro Canvas, Ausschnitt-Rendering |
| Große ZIP im Speicher | Sichern scheitert bei großer Sammlung | im Prototyp messen, Ausweg über OPFS |
| Wake Lock unzuverlässig | Bildschirm geht aus | auf dem Gerät prüfen; Hinweis, Auto-Sperre in iPadOS zu verlängern |
| Format läuft auseinander | Sicherungen passen nicht | `BACKUP_FORMAT.md`, gemeinsame Beispiel-ZIPs, Tests auf beiden Seiten |
| Adresse ändert sich | Daten scheinbar weg | Adresse von Anfang an festlegen |
| Update mitten im Gottesdienst | Neuladen während des Spielens | Update nur außerhalb der Notenansicht |
| Kein eigenes iPad | iPad-Fehler fallen spät auf | WebKit-Tests, Selbsttest-Seite, geplante iPad-Termine (Abschnitt 10a) |
| MusicXML-Bibliothek groß (1,3 MB) | längeres erstes Laden | erst beim Öffnen eines MusicXML-Lieds nachladen, danach offline im Cache |

---

## 12. Entschiedene Fragen

| Frage | Entscheidung |
|---|---|
| Adresse | GitHub Pages, dauerhaft (Abschnitt 3) |
| Testgerät | kein eigenes iPad – Teststrategie in Abschnitt 10a |
| Svelte oder React | Svelte (Abschnitt 4) |
| Name auf dem Home-Bildschirm | *Meine Noten* |
| MusicXML | in der ersten Version enthalten (Abschnitt 2) |
