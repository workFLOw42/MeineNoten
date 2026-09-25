# App-Konzept: Meine Noten

## 1. Vision & Zielstellung

Eine spezialisierte Android-App für 8-Zoll-Tablets (Samsung Galaxy Tab S6 Lite), die
eingescannte Notenblätter (PDF) für die Gitarrenbegleitung im Kirchenchor anzeigt.

Das Kernproblem: **Beim Gitarrenspiel sind beide Hände belegt.** Umblättern muss also
ohne Hände funktionieren – oder mit einer einzigen, blind ausführbaren Berührung.

Zielkonflikt, der alle Designentscheidungen bestimmt: Während des Spielens darf die App
keine Aufmerksamkeit fordern. Kein Suchen von Schaltflächen, kein Warten auf
Seitenaufbau, keine Fehlbedienung durch versehentliches Antippen.

## 2. Aktueller Stand

### Umgesetzt

| Bereich | Funktion |
|---|---|
| Anzeige | PDF-Rendering via `PdfRenderer`, seitenverhältnistreu |
| Anzeige | Vorausladen der Nachbarseiten, Cache für 5 Seiten |
| Anzeige | MusicXML-Rendering via OpenSheetMusicDisplay (WebView, offline) |
| Anzeige | Textnotizen als eigener Song-Typ (ohne Datei) |
| Anzeige | Zwei-Finger-Zoom (1- bis 5-fach) und Verschieben, pro Seite gespeichert |
| Anzeige | Lied kann Notendatei *und* Liedtext tragen, umschaltbar im Menü |
| Blättern | Bluetooth-Pedal (HID-Tastencodes), Richtung umkehrbar |
| Blättern | Tippzonen im unteren Drittel: linke Hälfte zurück, rechte vor |
| Blättern | Knöpfe ◀ ▶ in der Statusleiste, auf der letzten Seite ⏭ als Ankündigung |
| Blättern | Randblitz als optische Rückmeldung |
| Blättern | Am Liedende in der Setlist zum nächsten Lied weiterblättern |
| Komfort | Setlists merken Lied und Seite, *Fortsetzen* bringt einen dorthin zurück |
| Komfort | Display-Timeout deaktiviert, solange Noten offen sind (abschaltbar) |
| Komfort | Setlist-Reihenfolge zum Springen im Navigationsmenü |
| Komfort | Einstellungsseite für Statusleiste, Tippzonen, Pedal und Anzeige |
| Komfort | Design wählbar: System, Hell, Dunkel |
| Anzeige | Noten im Dunkeldesign pro Lied: Normal, Dezenter oder Invertiert |
| Daten | Import kopiert Dateien in den App-Speicher |
| Daten | Setlists: anlegen, sortieren, Notizen |
| Daten | Lieder löschen samt Datei und Setlist-Verweisen |
| Daten | Genre als freies Metadatum mit Vorschlags-Chips |
| Daten | Songliste durchsuchen, nach Genre und Setlist filtern, fünf Sortiermodi |
| Daten | Setlists durchsuchen (auch nach enthaltenen Liedern), nach Zeitraum filtern, drei Sortiermodi |
| Daten | Setlists duplizieren, Lied entfernen mit Rückgängig |
| Daten | Persistenz als JSON via kotlinx.serialization, Einstellungen via DataStore |
| Person | Eigener Name, zufällige Kennung, Titel „‹Name›s Noten“, Farbe pro Person |
| Person | Notizen pro Person mit Verfasser, fremde einzeln einblendbar |
| Sicherung | *Alles sichern* und *Setlist teilen* als ZIP, mit Prüfsummen |
| Sicherung | *Sicherung einlesen* mit Vergleichsmaske und Schnellwahlen |
| Sicherung | Erinnerung nach 30 Tagen, Urheberrechtshinweis – beide abschaltbar |
| UI | Material 3, Navigationsmenü (Drawer) |
| UI | Buchstabenleiste zum Springen in beiden Listen |
| UI | Startbildschirm, nahtlos an den System-Splash anschließend |
| UI | Deutsch und Englisch |
| Auslieferung | Release-Build mit R8 verkleinert, signiert, Play-tauglich |
| Auslieferung | Datenschutzerklärung über GitHub Pages veröffentlicht |

### Bewusst nicht umgesetzt

**On-Device OMR (Notenerkennung aus Scans).** Verlässliche OMR braucht Modelle, die auf
einem Tablet dieser Klasse nicht sinnvoll laufen. Die Erkennungsqualität bei
fotografierten Scans ist zudem so schwankend, dass das Ergebnis vor dem Auftritt jedes
Mal geprüft werden müsste – genau die Aufmerksamkeit, die die App einsparen soll. Ein
falsch erkannter Takt im Gottesdienst ist schlimmer als ein Scan, der eben ein Scan ist.

MusicXML bleibt unterstützt, aber nur als **Import** bereits vorhandener Dateien.
Wer digitale Noten hat, bekommt scharfe Vektordarstellung. Wer Scans hat, behält Scans.

> [!NOTE]
> Sollte Notenerkennung später gewünscht sein, wäre der gangbare Weg ein Server-Dienst
> (z. B. Audiveris) mit manueller Freigabe des Ergebnisses – nicht eine stille
> Umwandlung im Hintergrund.

### Offen

**Automatisches Umblättern per Mikrofon.** Ein Kirchenraum mit Chor, Orgel und
Gemeindegesang ist akustisch keine Testumgebung. Ein Fehlauslöser mitten im Lied wäre
schlimmer als gar keine Automatik. Zukunftsvision, nicht eingeplant.

**Ungenutzte Datenfelder.** `bpm`, `timeSignature` und `totalBars` werden gespeichert,
aber nirgends verwendet. Entweder daraus ein einfaches Metronom bauen oder die Felder
entfernen – Entscheidung steht aus.

**Eigene Kompositionen** (Abschnitt 5a) sind noch nicht ausgearbeitet.

**Datensicherung und Austausch** (Abschnitt 5b): Stufe 1 ist umgesetzt (ZIP-Sicherung,
Setlist teilen, Vergleichsmaske mit Vorschaubild, Personenfrage und Setlist-Hinweis,
Notizen pro Person). Offen ist nur noch die Prüfung der letzten drei Ergänzungen auf
dem Gerät. Der automatische Abgleich mit Google Drive (Stufe 2) ist nur ein Entwurf.

---

## 2a. Veröffentlichung

Die App wird über Google Play ausgeliefert. Was dafür eingerichtet ist:

| Punkt | Stand |
|---|---|
| Signierung | eigener Keystore, Zugangsdaten in `local.properties` (nicht im Repo) |
| `targetSdk` | 36 – Play-Mindestanforderung |
| Verkleinerung | R8 aktiv, 14,64 MB → 5,6 MB |
| Quellcode | GitHub, `workFLOw42/MeineNoten` |
| Datenschutzerklärung | `docs/privacy-policy.html` über GitHub Pages |
| Store-Texte | Kurz- und Langbeschreibung liegen vor |

### Versionszähler

Jeder Upload braucht einen höheren `versionCode` als alle vorherigen. Einmal
hochgeladene Nummern bleiben dauerhaft belegt – auch wenn das Release verworfen und nie
veröffentlicht wurde.

| `versionCode` | `versionName` | Inhalt |
|---|---|---|
| 1 | 1.0 | verbraucht, `targetSdk` war noch 35 |
| 2 | 1.0.1 | `targetSdk` 36, erstes veröffentlichtes Release |
| 3 | 1.0.2 | R8-Verkleinerung |
| 4 | 1.1.0 | Genre, Filter und Sortierung, Sprungleiste auf dem Telefon, Randblitz |
| 5–10 | 1.1.1–1.1.6 | Setlist-Fortschritt, Zweisprachigkeit, Zoom pro Seite; 10 nicht hochgeladen |
| 11 | 1.5.0 | Navigationsmenü, Einstellungen, Statusleiste, Design-Wahl, Startbildschirm |
| 12 | 1.6.0 | Person, Notizen pro Person, Datei-Prüfsumme |
| 13 | 1.6.1 | Datensicherung als ZIP, Vergleichsmaske, Setlist teilen, Hinweise |
| 14 | 1.6.2 | Vorschau in der Vergleichsmaske, Personenfrage, Setlist-Hinweis |
| 15 | 1.6.3 | Noten im Dunkeldesign pro Lied: Normal, Dezenter, Invertiert |
| 16 | 1.6.4 | MusicXML: Zwei-Finger-Zoom, Dunkeldesign ohne hellen Rand |

Die Nummer steht in `app/version.properties` und wird nach jedem erfolgreichen
`:app:bundleRelease` automatisch erhöht. Die Datei gehört ins Repository, damit sich
keine Nummer über Rechner oder Checkouts hinweg wiederholen kann.

> [!IMPORTANT]
> `compileSdk` steht auf 37 und muss dort bleiben, weil eine eingebundene
> AndroidX-Abhängigkeit (`runtime-saveable`) das verlangt. Das ist unabhängig vom
> `targetSdk` und für Play unproblematisch.

### Edge-to-Edge ab `targetSdk` 36

Ab API 36 lässt sich die randlose Darstellung nicht abschalten; Inhalte laufen unter
Status- und Navigationsleiste. `enableEdgeToEdge()` in `MainActivity`, und alle Screens
geben das `innerPadding` ihres `Scaffold` weiter – auch die Notenansicht.

---

## 2b. Web-App (iPad und iPhone)

Für Mitspielende mit Apple-Geräten gibt es eine Web-App im Ordner `web/`. Sie ist
**kein Ersatz** für die Android-App, sondern ein Begleiter: Sicherungen und geteilte
Setlists aus Android einlesen, Noten anzeigen, in der Setlist blättern. Gepflegt wird
die Sammlung weiterhin auf Android.

### Warum eine Web-App statt einer iOS-App

Eine native iOS-App braucht einen Mac, ein bezahltes Entwicklerkonto und die
App-Store-Prüfung. Eine Web-App läuft in Safari, lässt sich über *Zum Home-Bildschirm*
wie eine App starten und funktioniert danach offline. Für einen kleinen Kreis von
Chormitgliedern ist das der kürzeste Weg.

### Technik

| Bereich | Umsetzung |
|---|---|
| Oberfläche | Svelte 5 (Runes: `$state`, `$derived`, `$effect`), TypeScript, Vite |
| PDF | pdf.js 3.x, Worker lokal mitgebaut (kein CDN) |
| MusicXML | OpenSheetMusicDisplay, direkt im Browser |
| ZIP | JSZip |
| Daten | IndexedDB (`meinenoten_db`): Lieder, Setlists, Einstellungen |
| Notendateien | OPFS, bei fehlendem `createWritable()` (ältere Safari) IndexedDB |
| Offline | Service Worker `public/sw.js`, App-Shell network-first, Assets cache-first |
| Installierbar | `manifest.json`, Symbole 192/512 px |
| Tests | Vitest, `npm test` |
| Auslieferung | GitHub Actions → GitHub Pages unter `/MeineNoten/app/` |

Das Datenformat ist **dasselbe wie in Android**: `songs.json`, `setlists.json`,
`settings.json` und das Manifest der ZIP-Sicherung werden gelesen und geschrieben wie
dort. Die Lesefunktionen in `serialization.ts` füllen fehlende Felder mit denselben
Standardwerten wie kotlinx.serialization, damit Sicherungen beider Seiten austauschbar
bleiben. Die Zoom-Werte (`scale`, `offsetXRatio`, `offsetYRatio`) sind wie in
`PdfView.kt` definiert, sodass gespeicherte Ausschnitte auf beiden Geräten passen.

### Umgesetzt

| Bereich | Funktion |
|---|---|
| Anzeige | PDF seitenverhältnistreu, gestochen scharf auch beim Heranzoomen |
| Anzeige | Zwei-Finger-Zoom (1- bis 5-fach), Verschieben, Doppeltipp, pro Seite gespeichert |
| Anzeige | MusicXML über OSMD |
| Anzeige | Textnotizen (`TEXT`-Lieder) und umschaltbare Liedtexte |
| Anzeige | Notizen-Symbol in der Notenansicht: eigene Notiz & fremde Notizen mit Personenfarben einblendbar |
| Blättern | Tastatur und Bluetooth-Pedal (Pfeiltasten, Bild auf/ab, Leertaste) mit Richtungsumkehr |
| Blättern | Tippzonen unten: Größe wählbar (Drittel, Hälfte, Voll), Richtung tauschbar |
| Blättern | Statusleiste: Position, Seite, Knöpfe ◀ ▶ und Ankündigung ⏭ / ⏮ vor Liedwechsel |
| Blättern | Grüner Randblitz bei Seiten- und Liedwechsel, Titel-Banner bei Liedwechsel |
| Blättern | Am Liedende in der Setlist zum nächsten Lied, rückwärts auf dessen letzte Seite |
| Setlist | Liste in Reihenfolge, *Von vorne*, *Weiter bei …* (Lied und Seite gemerkt) |
| Setlists | anlegen, bearbeiten, duplizieren, löschen, Lieder per Suche hinzufügen, reihum umordnen (▲/▼) und entfernen |
| Setlists | Suche, Zeitraum-Filter (*Alle*, *Kommende*, *Vergangene*), 3 Sortiermodi (*Datum*, *Titel*, *Zuletzt gespielt*), *Nächster Auftritt* hervorgehoben |
| Songliste | Suche, Genre-Filter, Setlist-Filter, 5 Sortiermodi (*Künstler*, *Titel*, *Zuletzt geöffnet*, *Genre*, *Setlist-Reihenfolge*), Buchstabenleiste (A-Z) |
| Daten | Import von PDF und MusicXML |
| Daten | Lied bearbeiten: Titel, Künstler, Fassung, Genre, BPM, Liedtext, eigene Notiz |
| Daten | Lied löschen samt Notendatei und Verweisen in Setlists |
| Sicherung | *Alles sichern* als ZIP im Android-Format |
| Sicherung | *Setlist teilen* als Setlist-ZIP mit Urheberrechtshinweis und Web Share API (native Freigabe an AirDrop, WhatsApp, Mail) |
| Sicherung | *Sicherung einlesen* mit Vergleichsmaske und Aktion pro Lied |
| Sicherung | Identität übernehmen, Notizen anderer Personen zusammenführen |
| Sicherung | Setlist-Verweise folgen umbenannten Lied-IDs (*Beide behalten*) |
| Komfort | Display bleibt an (Wake Lock mit automatischer Re-Aktivierung bei `visibilitychange`), Titel „‹Name›s Noten“ |
| Komfort | Vollständige Einstellungsseite (Statusleiste, Blättern, Pedal, Anzeige, Person, Notizen anderer, Sicherung) |
| Komfort | Design-Wahl: System, Hell, Dunkel (`themeMode`) |
| Anzeige | Noten im Dunkeldesign pro Lied: Normal, Dezenter, Invertiert (`darkMode`) |
| Komfort | Browser-Speicherpersistenz angefragt (`navigator.storage.persist()`) |
| Diagnose | Selbsttest (Tipp auf die Versionszeile): IndexedDB, OPFS, Wake Lock, PDF, Tasten |

### Besonderheiten von Safari auf iOS

Die meisten Stolpersteine der Web-App sind iOS-spezifisch:

| Problem | Lösung |
|---|---|
| Canvas über ~16,7 Mio. Pixel bleibt leer | Auflösung auf 12 Mio. Pixel begrenzt |
| `user-scalable=no` wird ignoriert | `gesture*`-Ereignisse abfangen, Zoom nur in der Notenansicht |
| Statusleiste verdeckt Kopfzeile als Home-App | Abstand selbst messen (`--top-inset`), mindestens 20 px |
| Unschärfe-Streifen unter der Statusleiste | Bereich freihalten (`--edge-blur`, 26 px) |
| OPFS vorhanden, aber nicht schreibbar | Ausweich auf IndexedDB (`idb://`-Verweis) |
| `$state`-Proxys nicht klonbar (`DataCloneError`) | vor dem Speichern in reines Objekt umwandeln |
| Alte Version aus dem Cache | Cache-Name im Service Worker bei jeder Auslieferung erhöhen |
| Wake Lock geht nach App-Wechsel verloren | bei `visibilitychange` neu anfordern, wenn App sichtbar wird |
| Website-Daten-Löschung durch Safari | `navigator.storage.persist()` beim Start aufrufen |

> [!IMPORTANT]
> Safari kann Website-Daten nach längerer Nichtnutzung löschen. Die Web-App ist deshalb
> ausdrücklich **nicht** der Ort, an dem Noten dauerhaft liegen sollen. Die maßgebliche
> Sammlung bleibt auf Android, das iPad bekommt bei Bedarf eine frische Sicherung.

### Versionierung

Die Webversion ist die Anzahl der Commits, die `web/` verändert haben – sie zählt von
selbst hoch. Dazu kommt ein Build-Zeitstempel (UTC) im Tooltip der Versionszeile. So
ist auf dem Gerät sofort sichtbar, ob wirklich der neueste Stand läuft. Der Workflow
lädt dafür die volle Git-Historie (`fetch-depth: 0`).

### Noch nicht umgesetzt

| Bereich | Fehlt |
|---|---|
| Blättern | Umblättern in MusicXML |
| Sprache | Englisch (Web-App ist derzeit deutsch) |

---

## 3. Listen und Navigation

### Darstellung der Listen

Beide Listen laufen **einspaltig untereinander**, kein Kachelraster.

Begründung: In einem Raster muss das Auge zeilen- *und* spaltenweise suchen. Bei einer
sortierten Liste genügt ein Blick von oben nach unten. Beim Suchen eines Liedes kurz
vor dem Einsatz ist das der Unterschied zwischen Finden und Blättern.

### Sortierung der Lieder

Alphabetisch nach **Künstler, dann Titel**. Ist kein Künstler eingetragen, zählt der
Titel als Sortierschlüssel – das Lied rutscht also an die alphabetisch passende Stelle
und nicht in einen Block am Ende.

| Künstler | Titel | Sortierschlüssel |
|---|---|---|
| (leer) | Alles tanzt | `Alles tanzt` |
| Albert Frey | Groß und wunderbar | `Albert Frey` → `Groß und wunderbar` |
| (leer) | Bewahre uns Gott | `Bewahre uns Gott` |

Ergibt die Reihenfolge: *Albert Frey – Groß und wunderbar*, *Alles tanzt*,
*Bewahre uns Gott*.

Sortiert wird sprachbewusst (`Collator` für Deutsch), damit Umlaute richtig einsortiert
werden: „Über" gehört zu „U", nicht hinter „Z".

### Filtern und Sortieren der Songliste

Über der Liste sitzen ein Suchfeld und eine einzeilige, waagerecht scrollbare Leiste. Die
Suche findet Titel, Künstler, Version, Genre und Notiz. Die Leiste trägt drei
Bedienelemente:

| Element | Form | Warum diese Form |
|---|---|---|
| Sortierung | Menü | fünf Modi, gegenseitig ausschließend – als Chips würden vier davon nur Platz kosten |
| Setlist-Filter | Menü | Setlist-Titel sind lang und würden die Genre-Chips vom Bildschirm drängen |
| Genre-Filter | Menü | bei vielen vergebenen Genres sprengten Chips die Zeile |

Die fünf Sortiermodi:

| Modus | Ordnung |
|---|---|
| **Künstler** (Standard) | Künstler, dann Titel |
| **Titel** | nur Titel – für Lieder, die man über die erste Zeile kennt, nicht über den Komponisten |
| **Zuletzt** | zuletzt geöffnete zuerst |
| **Genre** | nach Genre gruppiert, mit Zwischenüberschriften, innerhalb alphabetisch |
| **Reihenfolge** | die Spielfolge der gewählten Setlist |

Bei alphabetischer Sortierung steht rechts eine **Buchstabenleiste**; Tippen oder Ziehen
springt zum Buchstaben, ein Umlaut zählt zu seinem Grundbuchstaben.

Beide Filter sind **einfach-, nicht mehrfachauswahl**. Drei Genres zu kombinieren ist
eine Katalogisierungsaufgabe, keine musikalische; die dafür nötigen dreistufigen Chips
sind im Vorbeigehen nicht ablesbar.

> [!NOTE]
> Filter und Sortierung sind **Ansichtszustand, keine Daten**. Sie überleben das Drehen
> des Geräts (`rememberSaveable`), werden aber nicht gespeichert. Die App öffnet also
> immer auf der vollständigen Liste – ein vergessener Filter, der beim nächsten Start
> die halbe Sammlung verbirgt, wäre während einer Probe schwer zu durchschauen.

### Zusammenspiel von Filter und Sortierung

Drei Kopplungen, die stillschweigend das Richtige tun:

*   **Setlist gewählt → Sortierung springt auf „Reihenfolge".** Wer eine Setlist filtert,
    will sie fast immer in ihrer Spielfolge sehen.
*   **Setlist entfernt → Sortierung fällt auf „Künstler" zurück**, falls sie auf
    „Reihenfolge" stand. Die Ordnung, auf die sie sich bezog, gibt es dann nicht mehr.
*   **„Reihenfolge" erscheint nur bei aktivem Setlist-Filter** im Menü. Ohne Setlist gibt
    es keine Reihenfolge, der man folgen könnte.

Im Modus „Reihenfolge" führt die Positionsnummer die Zeile an – so werden Lieder in der
Probe benannt („wir machen die Vier").

> [!IMPORTANT]
> Ein über den Setlist-Filter geöffnetes Lied bekommt die Setlist-Kennung mit. Sonst
> würden Pedal-Weiterblättern und Sprungliste fehlen, obwohl sichtbar eine Setlist
> gefiltert ist – die Liste würde einen Kontext anzeigen, den die Notenansicht nicht
> kennt.

Ein Filter kann das überleben, worauf er zeigt: eine gelöschte Setlist, ein Genre, das
auf seinem letzten Lied umbenannt wurde. Beide Filter werden deshalb bei jeder Anzeige
gegen die vorhandenen Werte geprüft und stillschweigend verworfen, wenn ihr Ziel weg ist
– andernfalls stünde man vor einer leeren Liste ohne erkennbaren Ausweg.

Die leere Liste unterscheidet zwei Fälle, weil sie verschiedene Handlungen verlangen:
„Noch keine Lieder vorhanden." (importieren) gegenüber „Keine Lieder passen zum Filter."
(Filter lösen).

### Sortierung der Setlists

Standard ist das Konzertdatum, **neuestes oben**, gruppiert nach Jahr. Wer die App
aufschlägt, braucht fast immer das nächste oder letzte Konzert – nicht das von
vorletztem Jahr. Das **nächste anstehende** Programm ist farbig hervorgehoben.

Setlists ohne Datum landen unten, untereinander alphabetisch nach Titel.

Weitere Sortierungen: *Titel* und *Zuletzt gespielt*. Dazu ein Zeitraumfilter
(*Kommend* / *Vergangen*) und eine Suche, die Titel, Notiz und die **Lieder** der Setlist
durchsucht – „In welchem Programm war noch Amazing Grace?“.

Im Setlist-Detail lassen sich Lieder per Pfeil verschieben und entfernen. Entfernen
fragt nicht nach, sondern bietet für einige Sekunden *Rückgängig* an – der Schritt ist
billig umzukehren, eine Rückfrage wäre bei jedem Aufräumen lästig.

Das Konzertdatum wird über eine **Datumsauswahl** (`DatePicker`) eingegeben und als
`yyyy-MM-dd` gespeichert. Freitext-Einträge aus älteren Dateien (`TT.MM.JJJJ`, `TT.MM.`)
werden beim Laden gedeutet; was sich nicht deuten lässt, bleibt als Beschriftung
erhalten und die Setlist gilt als „ohne Datum“.

### Löschen von Liedern

Über das Menü am Lied (drei Punkte in der Songliste, Navigationsmenü in der
Notenansicht), als letzter, rot abgesetzter Eintrag. Absichtlich **keine** Wischgeste
und kein Langdruck: Auf einem Tablet, das man mit der Gitarre in der Hand bedient, wären
die zu leicht versehentlich ausgelöst.

Gelöscht werden:

1.  die kopierte Datei im App-Speicher,
2.  der Eintrag in der Songliste,
3.  alle Verweise in Setlists.

Punkt 3 ist wichtig – bleibt ein Verweis auf ein gelöschtes Lied stehen, würde die
Setlist eine Lücke zeigen, deren Ursache nicht erkennbar ist.

Vorgeschaltet ist eine Rückfrage, die den Liedtitel nennt. Ist das Lied in Setlists
enthalten, wird das in der Rückfrage mit aufgeführt.

### Durchspielen einer Setlist

Wer aus einer Setlist heraus ein Lied öffnet, spielt in der Regel die ganze Liste
durch. Deshalb:

*   **Vorblättern auf der letzten Seite** öffnet das nächste Lied der Setlist (Seite 1).
*   **Zurückblättern auf Seite 1** öffnet das vorige Lied auf seiner *letzten* Seite.
*   Am Anfang bzw. Ende der Setlist passiert nichts – kein Umlauf.

Damit funktioniert das Pedal über die gesamte Setlist hinweg, ohne dass man zwischen
zwei Liedern zum Tablet greifen muss.

Der Liedwechsel wird deutlicher zurückgemeldet als ein Seitenwechsel (kurze Einblendung
des neuen Titels), damit man nicht versehentlich ein Lied weiterrutscht und es erst beim
Anspielen merkt. Auf der letzten Seite kündigt ⏭ in der Statusleiste den Wechsel an.

> [!NOTE]
> Nur wirksam, wenn das Lied **aus einer Setlist heraus** geöffnet wurde. Aus der
> Songliste geöffnet bleibt das Verhalten unverändert an den Liedgrenzen stehen.
> Technisch über die optionale Angabe der Setlist-Kennung in der Route.

### Springen innerhalb der Setlist

Wurde das Lied aus einer Setlist geöffnet, zeigt das **Navigationsmenü** unter den
Lied-Aktionen die ganze Reihenfolge („4. Großer Gott, wir loben dich“). Ein Tipp öffnet
das betreffende Lied, immer auf Seite 1; das aktuelle ist markiert.

Zweck: In einem Gottesdienst wird die Reihenfolge oft spontan geändert oder ein Lied
übersprungen. Ohne Sprungliste heißt das: zurück zur Setlist, suchen, öffnen. Titel
statt bloßer Positionsnummern sind bei spontanen Änderungen schneller zu lesen.

### Navigationsmenü

Songliste, Setlists und Einstellungen liegen in einem **Drawer** hinter dem Menüsymbol
oben links. Die Notenansicht trägt so außer ihrer Statusleiste keine dauerhafte
Bedienfläche, und das Menü ist auf Telefon und Tablet gleich.

Bei geöffnetem Lied enthält es zusätzlich *Bearbeiten*, *Zur Setlist hinzufügen*,
*Liedtext/Noten anzeigen* (nur wenn beides vorhanden) und *Löschen*. Abschnitte ohne
Ziel erscheinen nicht.

In der Notenansicht ist die Wischgeste zum Öffnen abgeschaltet – sie würde mit dem
Verschieben einer gezoomten Seite kollidieren. Dort öffnet nur das Symbol.

### Erkennbarkeit des aktuellen Liedes

Während des Spielens muss ohne Nachdenken ablesbar sein, in welchem Lied und auf welcher
Seite man sich befindet. Das übernimmt die **Statusleiste**, die dauerhaft sichtbar
bleibt:

*   erste Zeile: Position in der Setlist und Seite („Lied 4/12 · Seite 2/3“),
*   zweite Zeile: der Liedtitel.

Jeder Teil lässt sich in den Einstellungen abschalten. Bleibt nur der Titel übrig,
rückt er in die erste Zeile und wird größer, damit keine leere Zeile stehen bleibt.

Die Statusleiste lässt sich bewusst nicht ausblenden: Sie zeigt genau das, was beim
Spielen gebraucht wird, und ein versehentlicher Tipp dürfte ausgerechnet diese
Information nicht verstecken.

### Startseite beim Öffnen

Beim Öffnen eines Liedes entscheidet der Weg, über den es geöffnet wurde:

| Geöffnet über | Startseite | Begründung |
|---|---|---|
| Songliste | Seite 1 | Nachschlagen, nicht Fortsetzen eines Auftritts |
| Setlist, Lied angetippt | Seite 1 | Auftritt: das Lied wird von vorne angestimmt |
| Setlist, *Fortsetzen* | gespeicherte Seite | nach einer Unterbrechung genau dort weiter |
| Sprungliste im Menü | Seite 1 | wie Setlist |
| Vorblättern aus dem Vorgänger | Seite 1 | der Durchlauf folgt der Partitur |
| Zurückblättern aus dem Nachfolger | letzte Seite | man blättert dorthin, wo man hinsieht |

Der Fortschritt (`lastSongId`, `lastPage`) liegt an der **Setlist**, nicht am Lied:
Dasselbe Lied kann in mehreren Programmen stehen, und Nachschlagen in der Songliste darf
die Position eines halb gespielten Gottesdienstes nicht verschieben. Ist die letzte
Seite des letzten Liedes erreicht, wird der Fortschritt gelöscht – beim nächsten Mal
beginnt das Programm oben.

> [!WARNING]
> Eine automatisch gemerkte letzte Seite pro Lied wäre beim Auftritt eine Falle: Nach
> einer Probe, in der das Lied auf Seite 2 endete, stünde beim Gottesdienst die falsche
> Seite auf dem Pult. Deshalb ist Fortsetzen immer eine ausdrückliche Wahl.

---

## 4. Eingabekonzept

Das Blättern ist die einzige Funktion, die während des Spielens gebraucht wird. Deshalb
sind bewusst **mehrere Wege parallel** umgesetzt, statt einen zu erzwingen.

| Eingabe | Vorwärts | Rückwärts |
|---|---|---|
| Tasten / Pedal | `PAGE_DOWN`, `DPAD_RIGHT`, `DPAD_DOWN`, `SPACE`, `ENTER`, `MEDIA_NEXT`, `VOLUME_DOWN` | `PAGE_UP`, `DPAD_LEFT`, `DPAD_UP`, `MEDIA_PREVIOUS`, `VOLUME_UP` |
| Tippzonen | rechte Hälfte unten | linke Hälfte unten |
| Statusleiste | ▶ (⏭ vor Liedwechsel) | ◀ (⏮ vor Liedwechsel) |

Zur Tastenliste: Die Lautstärke- und Medientasten sind absichtlich dabei. Viele
preiswerte Seitenwender melden sich beim System nicht als Pfeiltasten, sondern als
genau diese Codes. Wer ein solches Pedal kauft, soll es anschließen können, ohne
vorher die Firmware zu prüfen. Wer die Lautstärketasten für die Lautstärke braucht,
schaltet sie ab – sie werden dann nicht verbraucht und erreichen das System. *Richtung
umkehren* dreht alle Tasten auf einmal um, für verkehrt verdrahtete Pedale oder den
anderen Fuß.

Zu den Tippzonen: Sie liegen im **unteren Drittel** (einstellbar: untere Hälfte, ganze
Höhe), links zurück, rechts vor, auf Wunsch getauscht. Der obere Bereich bleibt frei für
Zoomen und Verschieben, wo versehentliche Berührungen passieren.

**Wischen blättert nicht um.** Es käme dem Verschieben einer gezoomten Seite in die
Quere – wer eine Zeile ins Bild zieht, will nicht umblättern. Aus demselben Grund gibt
es keinen Doppeltipp-Zoom: Ein Doppeltipp-Detektor muss jeden einzelnen Tipp rund
300 ms zurückhalten, was die Tippzonen träge machen würde.

Die Knöpfe in der Statusleiste sind mindestens 56 dp groß, weil sie mitten im Spiel und
oft ohne Hinsehen getroffen werden. Auf der letzten Seite zeigt ▶ als ⏭ an, dass der
nächste Tipp das Lied wechselt – bevor er passiert.

### Zoom pro Seite

Zwei-Finger-Zoom von 1- bis 5-fach, mit einem Finger verschiebbar, an den Rändern
begrenzt. Zoom und Ausschnitt werden **pro Seite** am Lied gespeichert (`pageViews`),
verzögert um 300 ms, damit das Speichern die Geste nicht ruckeln lässt. Gescannte Noten
haben oft pro Seite einen anderen Rand; ein einziger Zoom fürs ganze Lied passt deshalb
selten. *Zoom zurücksetzen* steht im Bearbeiten-Dialog; mit *Zoom pro Seite merken* aus
gilt der Zoom nur, solange das Lied offen ist.

### Optische Rückmeldung

Jeder Seitenwechsel wird optisch bestätigt (50 ms Anstieg, 500 ms Ausklang). Das ist
nötig, weil zwei Notenseiten einander sehr ähnlich sehen – ohne Rückmeldung tritt man
im Zweifel ein zweites Mal und ist zwei Seiten zu weit.

Die Rückmeldung ist ein **Randblitz**: ein 6 dp breiter grüner Rahmen um die
Notenfläche. Er hängt nicht an einer Setlist und wirkt deshalb auch beim Üben eines
einzelnen Liedes. Abschaltbar in den Einstellungen. Bewusst nur ein Rahmen und kein
Schleier über den ganzen Bildschirm: Die 6 dp sind im peripheren Blickfeld erkennbar,
ohne je eine Notenzeile zu verdecken – genau in dem Moment, in dem sie gebraucht wird.

Der Liedwechsel ist der größere Sprung und wird deshalb zusätzlich benannt – der Titel
des neuen Liedes erscheint für 1,5 Sekunden in der Bildmitte (ebenfalls abschaltbar).

---

## 5. Datenmodell

```kotlin
@Serializable
enum class SongSource { PDF, MUSIC_XML, TEXT }

@Serializable
data class Song(
    val id: String,
    val title: String,
    val artist: String = "",
    val version: String = "",        // z. B. "Akustik", "Chor"
    val fileUri: String,             // leer bei SongSource.TEXT
    val sourceType: SongSource = SongSource.PDF,
    val genre: String = "",          // frei, z. B. "Gospel", "Advent"
    val bpm: Int = 120,              // derzeit ungenutzt
    val timeSignature: String = "4/4",  // derzeit ungenutzt
    val totalBars: Int = 0,          // derzeit ungenutzt
    val lastPage: Int = 0,           // Altfeld, wird nur noch gelesen
    val pageViews: Map<Int, PageView> = emptyMap(),  // Zoom/Ausschnitt pro Seite
    val notes: String = "",          // kurze Notiz neben den Noten ("Capo 2"); wird zu List<SongNote>, siehe 5b
    val lyrics: String = "",         // Liedtext/Akkorde, unabhängig von fileUri
    val lastOpenedAt: Long = 0,      // für Sortierung "Zuletzt"
) {
    /** Künstler, sonst Titel – bestimmt die Position in der Liste. */
    val sortKey: String get() = artist.ifBlank { title }
}

@Serializable
data class Setlist(
    val id: String,
    val title: String,
    val date: String = "",           // "yyyy-MM-dd", leer = ohne Termin
    val songIds: List<String>,       // geordnet
    val notes: String = "",
    val lastSongId: String? = null,  // Fortschritt: Lied …
    val lastPage: Int = 0,           // … und Seite, zum Fortsetzen
    val lastPlayedAt: Long = 0L,     // für Sortierung "Zuletzt gespielt"
)
```

Einstellungen liegen **nicht** in diesen JSON-Dateien, sondern als `AppSettings` in
Preferences-DataStore. Sie sind Gerätevorlieben, keine Notendaten.

### Datumsfeld

`Setlist.date` ist technisch ein `String` im Format `yyyy-MM-dd`. Gründe für diese Wahl
statt eines Zeitstempels:

*   Bestehende JSON-Dateien bleiben lesbar, keine Migration der Struktur nötig.
*   Das Format sortiert sich als Zeichenkette von selbst richtig.
*   Ein Datum ohne Uhrzeit ist hier das fachlich Richtige – ein Konzert hat einen Tag.

Angezeigt wird als `TT.MM.JJJJ`; die Umwandlung passiert nur in der Oberfläche.

### Genre

`genre` ist **freier Text**, keine Aufzählung. Zwei Gründe:

1.  Die hier nützlichen Kategorien sind zur Hälfte **Anlässe** („Advent", „Taufe",
    „Trauung") und nicht musikalische Stile. Eine feste Liste würde diese Mischung nie
    vollständig treffen.
2.  Jede neue Kategorie bräuchte sonst eine Code-Änderung und ein Play-Release – für
    eine Eingabe, die eine Sekunde dauert.

Freier Text hat einen Haken: „Gospel" und „gospel" spalten eine Kategorie still in zwei.
Der Filter findet dann nie beide, während die Liste korrekt aussieht. Zwei Maßnahmen
dagegen:

*   Unter dem Eingabefeld stehen die **bereits vergebenen Genres als Chips**. Ein Tipp
    übernimmt die vorhandene Schreibweise – Wiederverwenden ist damit weniger Arbeit als
    Neutippen. Ein Tipp auf den aktiven Chip leert das Feld wieder, ohne Tastatur.
*   Eingaben werden mit `trim()` bereinigt, damit ein versehentliches Leerzeichen keine
    zweite, optisch identische Kategorie erzeugt.

Das Genre ist an allen drei Eingabestellen verfügbar: Import, manuelles Anlegen und
Bearbeiten-Dialog. In der Liste steht es als gedämpfte zweite Zeile – sichtbar beim
Suchen nach „irgendwas für Advent", ohne dem Titel Aufmerksamkeit zu nehmen. Beim
Gruppieren nach Genre entfällt es dort, weil die Zwischenüberschrift es schon sagt.

> [!NOTE]
> Wie alle Felder mit Standardwert ist `genre` rückwärtskompatibel: Bestehende
> `songs.json` ohne dieses Feld bleiben lesbar, eine Migration entfällt.

---

## 5a. Eigene Kompositionen

BISHER_PLATZHALTER

---

## 5b. Datensicherung und Austausch

Noten sind Arbeit: gescannt, zugeschnitten, benannt, gezoomt, zu Programmen
zusammengestellt. Ein verlorenes oder zurückgesetztes Tablet darf das nicht mitnehmen.

### Ausgangslage

| Daten | Ort | Größe |
|---|---|---|
| Lieder | `files/songs.json` | klein |
| Setlists | `files/setlists.json` | klein |
| Einstellungen | DataStore `settings` | winzig |
| PDFs | `files/songs/<id>.pdf` | groß, Scans oft 1–5 MB je Lied |
| MusicXML | `files/musicxml/<id>.*` | mittel |

### Grundsatz: Lied und Notendatei sind eine Einheit

Ein Lied ohne seine Notendatei ist wertlos, eine Notendatei ohne ihr Lied ein namenloser
Scan. Jede Sicherung erfasst beide **gemeinsam** oder gar nicht. Daraus folgt:

*   **Die Android-Sicherung bleibt, wie sie ist.** Sie sichert alles in einem Stück oder
    gar nichts: Über 25 MB entfällt sie ganz. Lieder ohne Noten kann sie also nicht
    erzeugen. Verlässlich ist sie aber nur für kleine Sammlungen (siehe unten).
*   **Keine halben Lieder.** *Alles sichern* nimmt die ganze Sammlung mit, *Setlist
    teilen* genau eine Setlist samt allen ihren Liedern – aber nie ein Lied ohne
    Datei oder eine Datei ohne Lied.
*   **Auch das Einlesen behandelt Lied und Datei als Paar.** Ein
    Lied wird nur übernommen, wenn seine Datei mitkommt und die Prüfsumme stimmt.

| Stufe | Was | Internet | Wann |
|---|---|---|---|
| 1 | Sicherung und Austausch als ZIP: *Alles sichern*, *Setlist teilen*, Vergleichsmaske, Notizen pro Person | nein | umgesetzt (1.6.1) |
| 2 | Automatischer Abgleich mit Google Drive | ja, optional | offen |

Stufe 1 ist damit mehr als eine Sicherung: Mit Setlist-Datei, Vergleichsmaske und
Notizen pro Person ist sie bereits **Zusammenarbeit ohne Internet**. Die Datei reist
über Messenger, E-Mail oder einen geteilten Drive-Ordner, die App gleicht beim Einlesen
ab.

Stufe 2 automatisiert nur noch den Transport. Sie kommt später oder wird anders gelöst;
die App bleibt bis dahin ohne Internet-Berechtigung. Was unten zu Stufe 2 steht, ist
daher ein **Entwurf**, keine Festlegung.

### Android-Sicherung: unverändert lassen

Die Regeldateien bleiben Vorlage, die Android-Sicherung erfasst also weiterhin alles.
Das passt zum Grundsatz, denn sie arbeitet mit Momentaufnahmen des ganzen
App-Speichers: Lieder und Dateien stammen immer aus demselben Moment.

| Sammlung | Verhalten |
|---|---|
| unter 25 MB | vollständig gesichert, etwa einmal nächtlich, ohne Zutun |
| über 25 MB | keine neue Sicherung; die letzte erfolgreiche bleibt im Google-Konto liegen |
| Umzug Gerät zu Gerät | ohne Größengrenze, alles wandert mit |

Auf die Regeln die Notendateien auszuschließen, wäre der eigentliche Fehler: Dann
bliebe die Sicherung zwar unter 25 MB, hätte aber Lieder ohne Noten.

> [!NOTE]
> Wurde die Grenze überschritten, kann Android auf einem neuen Gerät einen **alten, aber
> stimmigen** Stand einspielen. Das schadet nicht: Die anschließend eingespielte
> ZIP-Sicherung zeigt in der Vergleichsmaske, was sich seitdem geändert hat.

Datenschutzerklärung und Store-Text bleiben gültig.

### Person und Notizen

Sobald Sammlungen zwischen Menschen wandern, muss eine Notiz sagen, von wem sie ist.

#### Name und Kennung

In den Einstellungen gibt es ein freies Feld *Dein Name*. Unabhängig davon erzeugt die
App beim ersten Start eine unsichtbare, zufällige **Personen-Kennung**. Sie ist das
eigentliche Unterscheidungsmerkmal; der Name ist nur Anzeige.

| Fall | Anzeige | Unterscheidung |
|---|---|---|
| kein Name eingetragen | **Meine Noten** | Kennung |
| Name geändert („Flo“ → „Florian“) | neuer Name, auch an alten Notizen | Kennung bleibt |
| zwei Personen gleichen Namens | beide „Anna“, aber in verschiedenen Farben | Kennung |
| „Anna S.“ und „Anna B.“ | wie eingetragen | Kennung |

Der Name ist freier Text, Punkte, Leerzeichen und Kürzel sind erlaubt, nur `trim()` wird
angewandt.

**Farben.** Jede Person bekommt eine Farbe, **abgeleitet aus der Kennung**, nicht aus dem
Namen – so haben zwei Annas sicher verschiedene Farben, und dieselbe Person hat auf allen
Geräten dieselbe. Die Farben stammen aus einer festen Palette von etwa acht Tönen. Die
Akzentfarbe der App bestimmt weiterhin das Gerät (dynamische Farben ab Android 12);
die eigene Personenfarbe erscheint nur dort, wo andere die eigenen Notizen sehen.

**Unterscheidung wählbar.** Wie Personen auseinandergehalten werden, legt man in den
Einstellungen unter *Notizen anderer* fest. Die drei Schalter sind unabhängig und
frei kombinierbar:

| Einstellung | Wirkung | Standard |
|---|---|---|
| **Farbpunkt** | farbiger Punkt vor dem Namen | aus |
| **Name farbig** | der Name selbst in der Farbe der Person | an |
| **Nummer bei gleichen Namen** | „Anna · 1“, „Anna · 2“, nur wenn ein Name mehrfach vorkommt | aus |

Sind alle drei aus, sehen zwei Annas gleich aus – das ist dann eine bewusste Wahl,
etwa wenn man sie ohnehin als „Anna S.“ und „Anna B.“ eingetragen hat. Intern bleiben
sie über die Kennung getrennt.

Die Nummer richtet sich nach der Reihenfolge, in der die Personen zum ersten Mal auf
dem Gerät aufgetaucht sind, und bleibt danach fest. Sonst hätte dieselbe Anna je nach
Lied mal die 1, mal die 2.

> [!NOTE]
> Farbiger Text muss auf jedem Hintergrund lesbar sein. Die Palette hat deshalb je Ton
> eine helle und eine dunkle Variante mit ausreichendem Kontrast (mindestens 4,5 : 1
> nach WCAG) für das jeweilige Design; der Farbpunkt nutzt denselben Ton. Bei
> Farbfehlsichtigkeit unterscheiden Farben allein nicht sicher – dafür ist die Nummer
> gedacht.

**Startbildschirm und Titel.** Ist ein Name eingetragen, steht dort, wo heute *Meine
Noten* steht, **„‹Name›s Noten“** – im Startbildschirm und oben in der Songliste. Der
App-Name unter dem Symbol auf dem Homescreen bleibt *Meine Noten*; ihn kann eine App
nicht zur Laufzeit ändern. Im Englischen „‹Name›'s Sheets“. Die deutsche
Genitivregel wird beachtet: „Annas Noten“, aber „Hans’ Noten“ und „Max’ Noten“ bei
Namen auf *s*, *x*, *z*. Bei einem sehr langen Namen fällt die Anzeige auf *Meine
Noten* zurück, statt abgeschnitten zu werden.

#### Mehrere Notizen pro Lied

`Song.notes` wird von einem Text zu einer Liste von Notizen mit Verfasser:

```kotlin
@Serializable
data class SongNote(
    val authorId: String,      // Personen-Kennung, fest
    val authorName: String,    // Name beim letzten Bearbeiten, nur Anzeige
    val text: String,
    val editedAt: Long = 0,    // letzte Bearbeitung, entscheidet zwischen zwei Fassungen derselben Person
)
```

*   Jede Person hat pro Lied **höchstens eine** Notiz. Die eigene ist bearbeitbar wie
    heute.
*   Fremde Notizen lassen sich lesen, ausblenden und löschen, aber **nicht bearbeiten**
    – sonst stünde ein fremder Name unter dem eigenen Text.
*   Kommt beim Einlesen eine Notiz derselben Person in neuerer Fassung (`editedAt`),
    ersetzt sie die alte; das ist die einzige Stelle, an der ohne Nachfrage
    überschrieben wird, weil Verfasser und Reihenfolge eindeutig sind. Eine *ältere*
    Fassung wird ignoriert.
*   In der Notenansicht steht die eigene Notiz wie heute. Fremde sind standardmäßig
    **ausgeblendet** und pro Person einschaltbar – beim Spielen stören fünf Notizen mehr,
    als sie helfen.

**Übergang.** Beim ersten Start der neuen Version wird eine vorhandene Notiz zur eigenen
(`authorId` = eigene Kennung). Die JSON-Datei liest das alte Textfeld weiterhin, damit
auch ältere Sicherungen einlesbar bleiben.

Dasselbe Verfahren lässt sich später auf Setlist-Notizen übertragen; für den Anfang
bleiben diese ein einzelner Text.

### Stufe 1: Sicherung und Austausch als ZIP

Ein Knopf, eine Datei. In den Einstellungen unter *Datensicherung*:

*   **Alles sichern** – die vollständige Sammlung samt Einstellungen.
*   **Setlist teilen** – im Menü einer Setlist: die Setlist, ihre Lieder und deren
    Notendateien, ohne Einstellungen (siehe *Setlist-Datei*).
*   **Sicherung einlesen** – eine solche Datei auswählen, egal welcher Art.
*   Darunter: „Letzte Sicherung: vor 12 Tagen“.

Beim Sichern öffnet sich die Dateiauswahl des Systems (`ACTION_CREATE_DOCUMENT`). Ziel
kann der Download-Ordner, ein USB-Stick oder Google Drive sein – die Drive-App tritt in
der Auswahl als Speicherort auf und lädt selbst hoch. Der Weg braucht **keine
Berechtigung** und kein Internet: Die App schreibt nur in die eine Datei, die man ihr
gerade gegeben hat.

#### Dateiname

Vorgeschlagen wird `JJJJMMTT_[Art]_[Gerät]_[Name].zip`:

```text
20261004_Komplett_Florians-Tablet_Florian.zip
20261004_Setlist-Erntedank_Pixel-8_Anna-S.zip
20261004_Komplett_Galaxy-Tab-S9_Meine-Noten.zip
```

| Teil | Quelle |
|---|---|
| Datum | Tag der Sicherung, sortiert sich im Ordner von selbst |
| Art | `Komplett` oder `Setlist-<Titel>` |
| Gerät | selbst vergebener Gerätename, sonst das Modell |
| Name | eingetragener Name (Abschnitt *Person*), sonst `Meine-Noten` |

Leerzeichen werden zu `-`, Zeichen, die Dateisysteme nicht vertragen (`/ \ : * ? " < > |`),
entfallen; Umlaute bleiben. Der Name ist nur ein Vorschlag und darf umbenannt werden –
maßgeblich sind deshalb die Angaben im `manifest.json`, nicht der Dateiname.

Während der Sicherung zeigt ein Fortschrittsdialog „Lied 34 von 87“. Sie läuft im
Hintergrund weiter, wenn man den Bildschirm verlässt; währenddessen sind Import und
Löschen gesperrt, damit Lieder und Dateien in der Sicherung zueinander passen.

#### Was enthalten ist

| Bereich | Inhalt |
|---|---|
| Lieder | alle Felder: Titel, Künstler, Version, Genre, alle Notizen mit Verfasser, Liedtext, Zoom und Ausschnitt pro Seite (`pageViews`), Datei-Prüfsumme, zuletzt geöffnet |
| Notendateien | jede PDF- und MusicXML-Datei, die zu einem Lied gehört |
| Setlists | Reihenfolge, Datum, Notiz, Fortschritt (`lastSongId`, `lastPage`), zuletzt gespielt |
| Einstellungen | sämtliche Werte aus `AppSettings`: Statusleiste, Tippzonen, Randblitz, Pedaltasten, Bildschirm an, Zoom merken, Design, Name |

Nicht enthalten ist nur, was sich neu ergibt: zwischengespeicherte Seitenbilder und
temporäre Dateien.

> [!IMPORTANT]
> Neue Felder und Einstellungen müssen **automatisch** mitgesichert werden, sonst geht
> später still etwas verloren. Deshalb werden `Song` und `Setlist` über ihre
> vorhandene Serialisierung geschrieben, und `AppSettings` bekommt eine eigene
> serialisierbare Form mit Standardwerten. Ein Unit-Test prüft, dass eine Sicherung nach
> dem Wiederherstellen dieselben Daten ergibt (Hin- und Rückweg).

#### Aufbau der Datei

Ein gewöhnliches ZIP, damit es sich im Notfall auch am Rechner öffnen lässt:

```text
20261004_Komplett_Florians-Tablet_Florian.zip
├── manifest.json      Format-Version, App-Version, Art, Zeitpunkt, Gerät, Person
│                      (Kennung und Name), Dateiliste mit Prüfsummen
├── songs.json
├── setlists.json
├── settings.json      nur bei „Komplett“; lesbares JSON, nicht die DataStore-Datei
└── files/
    ├── <id>.pdf
    └── <id>.musicxml
```

In der Sicherung stehen **relative** Dateinamen (`files/<id>.pdf`), nicht die heutigen
`file://`-Pfade. Diese enthalten den Speicherort des Geräts und wären auf einem anderen
Gerät oder für einen zweiten Nutzer falsch. Beim Wiederherstellen wird `fileUri` neu
gebildet.

Vor dem Schreiben wird geprüft, dass jedes Lied (außer reinen Textliedern) seine Datei
hat. Fehlt eine, nennt die App das betroffene Lied und fragt, ob ohne es gesichert
werden soll – eine Sicherung, die still ein Lied ohne Noten enthält, wäre die
schlechtere Wahl.

PDFs werden im ZIP nur **gespeichert**, nicht komprimiert: Sie sind es schon, ein zweiter
Durchgang kostet Zeit und bringt nichts. Geschrieben wird als Datenstrom, damit auch
eine Sammlung von mehreren hundert Megabyte nicht in den Arbeitsspeicher muss.

#### Einlesen: die Vergleichsmaske

Eine Sicherung wird nie blind eingespielt. Die App liest sie zuerst in einen
Zwischenordner, prüft die Prüfsummen und zeigt dann eine **vollständige Liste** aller
Lieder und Setlists aus der Datei – gruppiert danach, wie sie zum eigenen Bestand
passen. Oben stehen Herkunft und Umfang („Komplett · Florians Tablet · Florian ·
04.10.2026 – 87 Lieder, 14 Setlists“) und die Zähler je Gruppe.

Zugeordnet wird in dieser Reihenfolge, der erste Treffer gilt:

| Gruppe | Erkannt an | Vorauswahl |
|---|---|---|
| **Identisch** | gleicher Datei-Hash, alle Angaben gleich | überspringen, ist schon da |
| **Gleiche Noten, andere Angaben** | gleicher Datei-Hash, aber z. B. anderer Titel, anderes Genre, neue Notizen – auch bei anderer Kennung, also *anders abgelegt* | meins behalten, fremde Notizen anbieten |
| **Mögliche andere Fassung** | gleicher Titel und Künstler, anderer Datei-Hash | fragen |
| **Neu** | nichts davon | importieren |

Bei den beiden mittleren Gruppen zeigt die Maske die **Unterschiede Feld für Feld**
(„Titel: *Großer Gott* ↔ *Großer Gott, wir loben dich*“) und die erste Seite beider
Fassungen als Vorschaubild nebeneinander, dazu Seitenzahl und Dateigröße. Ob zwei
Scans dieselben Noten sind, sieht man am Bild sofort, am Titel nicht. Pro Lied:

*   **Meins behalten** – nur die ausgewählten fremden Notizen kommen hinzu.
*   **Aus der Sicherung übernehmen** – Angaben *und* Datei, nie nur eines von beiden.
*   **Beide behalten** – das eingelesene Lied bekommt eine neue Kennung, *Version* wird
    vorbelegt („aus Sicherung Anna S.“), damit die beiden in der Liste unterscheidbar
    sind.

> [!NOTE]
> Ein Datei-Hash beweist **Gleichheit**, aber nicht Verschiedenheit: Derselbe Scan kann
> nach erneutem Speichern oder Komprimieren andere Bytes haben. Deshalb entscheidet die
> App bei verschiedenem Hash nie selbst, sondern zeigt die Vorschau.

Den **Zoom pro Seite** übernimmt die App nur bei neuen Liedern oder bei ausdrücklichem
„Aus der Sicherung übernehmen“. Er ist persönlich; das eigene Lied behält seinen.

Schnellwahlen oben: *Alles wie vorgeschlagen*, *Nur Neue*, *Alles ersetzen*. Wer die
eigene Sicherung zurückholt, tippt einmal und ist fertig. *Alles ersetzen* löscht
vorher den Bestand auf dem Gerät und verlangt eine Bestätigung.

**Setlists** verweisen auf Lied-Kennungen der Sicherung. Wird ein Lied als *identisch*
übersprungen oder *meins behalten*, wird der Verweis auf das **eigene** Lied umgebogen –
die Setlist bleibt vollständig, ohne Dublette. Wählt man ein Lied ab, das eine
ausgewählte Setlist braucht, weist die Maske darauf hin („Setlist *Erntedank* enthält
dieses Lied“).

**Einstellungen** stehen als eigene Zeile in der Liste, bei *Komplett* abgewählt außer
bei *Alles ersetzen* – auf einem zweiten Gerät mit anderer Pedalbelegung wäre ein
stilles Überschreiben lästig. Der eingetragene Name wird nie übernommen; er gehört zum
Gerät, nicht zur Sammlung.

Übernommen wird erst nach *Einlesen* und dann in einem Schritt. Eine abgebrochene
Wiederherstellung hinterlässt keinen halben Bestand.

#### Setlist-Datei

*Setlist teilen* erzeugt eine Datei mit genau einer Setlist, ihren Liedern und deren
Notendateien – Lied und Datei bleiben auch hier ein Paar. Einstellungen sind nicht
enthalten.

Beim Einlesen zeigt die Maske dieselben Gruppen, zugeschnitten auf die Frage „Habe ich
alles für Sonntag?“:

| Zeichen | Bedeutung | Wirkung |
|---|---|---|
| ✓ | vorhanden | Setlist verweist auf das eigene Lied |
| ≈ | vorhanden, anders abgelegt | Setlist verweist auf das eigene Lied, Unterschiede sichtbar |
| ? | mögliche andere Fassung | Wahl wie oben |
| + | fehlt | wird importiert |

Damit ist ein Großteil der Zusammenarbeit im Chor **ohne Internet** erledigt: Die
Chorleitung verschickt die Setlist-Datei per Messenger, jedes Mitglied liest sie ein
und bekommt nur, was fehlt – plus ihre Notizen.

> [!WARNING]
> Gekaufte Noten dürfen meist nicht weitergegeben werden. *Setlist teilen* zeigt vor dem
> Erstellen einen Hinweis darauf. Er lässt sich per Checkbox *Nicht wieder anzeigen*
> abstellen und unter *Einstellungen → Hinweise* wieder einschalten. Die Verantwortung
> liegt beim Nutzer; die App prüft das nicht und kann es nicht.

#### Erinnerung

Ohne Automatik wird Sicherung vergessen. Ist die letzte Sicherung älter als 30 Tage,
erscheint in der Songliste ein dezenter Hinweis mit *Später* und *Jetzt sichern*.
Die Checkbox *Nicht wieder anzeigen* schaltet ihn dauerhaft ab (wieder einschaltbar
unter *Einstellungen → Hinweise*). **Nie** in der Notenansicht – dort stört jede
Meldung.

> [!NOTE]
> „Nicht wieder anzeigen“ ist in beiden Fällen eine **Checkbox**, kein eigener Knopf:
> Sie ergänzt die Entscheidung, statt selbst eine zu sein. Beide Knöpfe werten sie aus.

#### Aufbau der Vergleichsmaske

Info-Karte, Schnellwahl und *Jetzt importieren* stehen **fest oben**; nur die Liste der
Lieder, Setlists und Einstellungen darunter scrollt. So ist der eine Import-Knopf
immer erreichbar, ein zweiter am Listenende entfällt.

Die Dateiauswahl zum Speichern und Öffnen gehört zu Android, nicht zur App. Sie hat
keinen Zurück-Pfeil der App; abgebrochen wird mit der Zurück-Geste des Systems. Eine
eigene Dateiauswahl würde Speicher-Berechtigungen und den Verzicht auf Drive und
USB-Stick kosten und ist deshalb nicht vorgesehen.

### Stufe 2 (Entwurf): Automatischer Abgleich mit Google Drive

> [!NOTE]
> Noch nicht eingeplant. Stufe 1 deckt Sicherung und Austausch ab; Stufe 2 würde nur
> den Knopfdruck ersparen. Ob sie so kommt, später anders gelöst wird oder entfällt,
> entscheidet sich erst, wenn Stufe 1 im Alltag erprobt ist. Der folgende Entwurf hält
> fest, was dabei zu beachten wäre.

Ein Schalter *Automatisch in Google Drive sichern*. Erst beim Einschalten meldet man
sich an; wer ihn nie berührt, merkt von der Internetanbindung nichts.

#### Anmeldung und Speicherort

*   Anmeldung über den `AuthorizationClient` der Google Play-Dienste.
*   Berechtigungsumfang **`drive.appdata`**: ein versteckter, app-eigener Ordner im Drive
    des Nutzers. Die App sieht keine anderen Dateien, und Google stuft den Umfang als
    *nicht sensibel* ein – keine Sicherheitsprüfung der App durch Google nötig.
*   Der Ordner zählt zum Speicherkontingent des Nutzers. Er ist nur über die App
    erreichbar; zum Mitnehmen an den Rechner dient Stufe 1.

> [!NOTE]
> Für die Zusammenarbeit reicht `drive.appdata` nicht, weil der Ordner nicht geteilt
> werden kann. Das ist gewollt: Sicherung und Zusammenarbeit bekommen getrennte
> Freigaben, damit wer nur sichern will, nicht mehr erlaubt als nötig.

#### Nur Änderungen übertragen

Jede Datei wird einzeln abgelegt, nicht als ZIP – sonst müsste bei jeder Änderung
die ganze Sammlung hoch. Ein **Stand-Verzeichnis** (`index.json`) hält pro Datei die
Prüfsumme fest.

| Ereignis | Übertragen |
|---|---|
| Lied importiert | eine Notendatei + `songs.json` |
| Titel geändert, Zoom verschoben | nur `songs.json` |
| Setlist umgestellt | nur `setlists.json` |
| Lied gelöscht | Notendatei wird in Drive **nicht sofort** gelöscht (siehe unten) |

Bei jedem Lauf vergleicht die App die Prüfsummen mit dem letzten erfolgreich
übertragenen Stand und lädt nur, was abweicht. Die Notendateien ändern sich fast nie;
nach der Erstsicherung sind Läufe typischerweise wenige Kilobyte.

Auch hier bleiben Lied und Datei ein Paar: Zuerst werden die Notendateien übertragen,
erst danach `songs.json`. Bricht ein Lauf ab, kennt der Stand in Drive also nie ein Lied,
dessen Datei fehlt.

#### Wann gesichert wird

Geplant über **WorkManager** mit den Bedingungen *WLAN* (`UNMETERED`) und *Akku nicht
fast leer*:

*   **bei geöffneter App**: etwa eine Minute nach der letzten Änderung, damit eine Reihe
    von Bearbeitungen einen einzigen Lauf ergibt;
*   **zusätzlich täglich** als Absicherung, falls die App vorher geschlossen wurde.

Ein Lauf wird **nie während der Notenansicht** gestartet und läuft dort bei Bedarf
pausiert weiter, damit die Seitenwechsel nicht mit einem Upload um Leistung
konkurrieren.

Im Einstellungsbereich steht der Zustand: „Gesichert vor 3 Minuten“, „Wartet auf
WLAN“, „Fehler: Anmeldung abgelaufen“. Ein Fehler, der länger als eine Woche besteht,
wird wie die Erinnerung aus Stufe 1 in der Songliste gemeldet.

#### Gelöschtes aufbewahren

Eine automatische Sicherung, die jedes Löschen sofort nachvollzieht, sichert auch
jeden Fehler. Gelöschte Lieder bleiben deshalb **30 Tage** in Drive erhalten und lassen
sich in dieser Zeit wiederherstellen; erst dann wird ihre Datei entfernt.

Von `songs.json` und `setlists.json` werden zusätzlich die letzten sieben Tagesstände
behalten – sie sind klein, und eine versehentlich geleerte Setlist lässt sich so
zurückholen.

#### Wiederherstellen

Auf einem neuen Gerät: App installieren, Schalter einschalten, anmelden. Die App
erkennt einen vorhandenen Stand und fragt, ob er übernommen werden soll. Dann gilt
dasselbe wie bei Stufe 1 (Vergleichsmaske, erst Zwischenordner).

#### Folgen der Internetanbindung

> [!WARNING]
> Mit Stufe 2 fordert die App die Berechtigung `INTERNET` an und nutzt die Google
> Play-Dienste. Das ändert Aussagen, die heute öffentlich gemacht werden:
>
> *   **Datenschutzerklärung**: Abschnitte „Keine Netzwerkverbindung“, „Berechtigungen“,
>     „Drittanbieter-Dienste“ neu fassen – Daten gehen nur in das Drive des Nutzers,
>     nie an den Entwickler.
> *   **Datensicherheit in der Play Console**: Übertragung an Google Drive auf Wunsch
>     des Nutzers angeben.
> *   **Store-Text**: „Vollständig offline“ wird zu „offline nutzbar, Sicherung in
>     Google Drive optional“.

Die Berechtigung `INTERNET` ist eine Installationsberechtigung und lässt sich nicht
erst beim Einschalten anfordern. Zu halten ist trotzdem: **Ohne eingeschalteten Schalter
verlässt kein Byte das Gerät.** Die WebView für MusicXML bleibt mit
`blockNetworkLoads` abgeschottet.

Soll die Grundversion ganz ohne `INTERNET` bleiben, geht das nur über zwei
Produktvarianten (Gradle-Flavors). Das verdoppelt die Prüfarbeit vor jedem Release und
wird deshalb nicht angestrebt.

### Ausblick: Zusammenarbeit

Im Kirchenchor gibt es wiederkehrende Fragen, die eine Sicherung schon halb beantwortet:
„Welche Lieder am Sonntag?“, „Schick mir die Noten“. Die Idee ist ein **geteilter Ordner**
in Google Drive, in den die Chorleitung eine Setlist mit ihren Noten legt und aus dem
die anderen sie übernehmen.

Was Stufe 1 und 2 dafür schon vorbereiten sollten:

*   **Relative Dateinamen und stabile Kennungen** – ein Lied muss sich auf fremden
    Geräten wiedererkennen lassen.
*   **Datei-Hash und Vergleichsmaske** – eine geteilte Setlist darf die eigene Sammlung
    nicht überschreiben.
*   **Personen-Kennung und Notizen mit Verfasser** – bereits in Stufe 1 enthalten.
*   **Änderungszeitpunkt pro Lied und Setlist** (`updatedAt`) – erst hier nötig: Gleichen
    mehrere Geräte laufend ab, muss ohne Maske entschieden werden können, was neuer ist.
*   **Getrennte Freigabe** – Zusammenarbeit fordert `drive.file` für einen
    ausgewählten Ordner an, zusätzlich und nur dann, wenn man sie nutzt.

Noch nicht entschieden, und für diese Stufe auch nicht nötig: ob geteilte Setlists
nur gelesen oder von allen bearbeitet werden. Urheberrecht an gekauften Noten gilt wie
bei der Setlist-Datei.

### Neue Datenfelder

```kotlin
data class Song(
    // …
    val notes: List<SongNote> = emptyList(),  // statt String, siehe „Person und Notizen“
    val fileHash: String = "",               // SHA-256 der Notendatei, leer = noch nicht berechnet
)

data class AppSettings(
    // …
    val userName: String = "",   // leer = „Meine Noten“
    val userId: String,          // beim ersten Start erzeugt, nie geändert
    val lastBackupAt: Long = 0,  // für die Erinnerung
    val noteAuthorDot: Boolean = false,        // Farbpunkt vor dem Namen
    val noteAuthorColoredName: Boolean = true, // Name in der Farbe der Person
    val noteAuthorNumber: Boolean = false,     // „Anna · 1“ bei gleichen Namen
    val showBackupReminder: Boolean = true,    // Erinnerung nach 30 Tagen
    val showCopyrightWarning: Boolean = true,  // Hinweis vor „Setlist teilen“
)
```

`fileHash` wird beim Import und beim Austausch der Datei gesetzt; bestehende Lieder
bekommen ihn beim ersten Sichern nachgetragen, damit das Einlesen großer Sammlungen
nicht jedes Mal alle Dateien neu durchrechnen muss. Er erlaubt später auch einen
Hinweis beim normalen Import („Diese PDF ist schon als *Großer Gott* vorhanden“) und
bildet in Stufe 2 die Grundlage für „nur Änderungen übertragen“.

`userId` sichert die App nicht mit in die Einstellungen der Sicherung zurück, sondern
nur ins Manifest: Wer eine eigene Komplettsicherung auf einem neuen Gerät einliest, wird
gefragt, ob er *diese Person* ist – dann übernimmt das neue Gerät Kennung und Name, und
die eigenen Notizen bleiben eigene.

Erst mit Stufe 2 kommt `updatedAt` an `Song` und `Setlist` hinzu (Standardwert `0` =
unbekannt). Nicht als Änderung zählen dann `lastOpenedAt`, `lastPlayedAt` und der
Fortschritt – sonst würde jedes Öffnen eines Liedes eine neue Version erzeugen.

### Umsetzungsreihenfolge

> [!NOTE]
> **Arbeitsstand** – wird während der Umsetzung gepflegt, damit nach einer Unterbrechung
> gleich weitergearbeitet werden kann. `[ ]` offen · `[/]` in Arbeit · `[x]` umgesetzt.

1.  `[x]` Person: Name, Kennung, Farben, „‹Name›s Noten“.
    *   `[x]` `userName`, `userId`, `lastBackupAt`, `noteAuthor*` in `AppSettings`;
        Kennung per `SettingsRepository.ensureUserId()` beim Start erzeugt
    *   `[x]` Feld *Dein Name* in den Einstellungen (Abschnitt *Person*)
    *   `[x]` Titel „‹Name›s Noten“ (Genitivregel, Rückfall ab 21 Zeichen) in
        Startbildschirm und Menü (`PersonUtils.kt`, `AppTitle.kt`, `PersonTitleTest`).
        Die Songliste zeigt oben „Lieder (n)“, nicht den App-Namen – dort entfällt es.
    *   `[x]` Farbpalette pro Kennung (8 Töne, hell/dunkel), Schalter *Notizen anderer*.
        Die Schalter wirken erst mit Schritt 2.
    *   `[x]` Akzentfarbe: entschieden – das Gerät bestimmt sie (dynamische Farben),
        die Personenfarbe wird nicht als Akzent genutzt
2.  `[x]` Notizen als Liste mit Verfasser, samt Übergang der bisherigen Notiz.
    *   `[x]` `SongNote` (`model/SongNote.kt`), `Song.notes` als Liste (JSON-Feld
        `songNotes`); das alte Textfeld `notes` wird als `legacyNotes` weiter gelesen
    *   `[x]` Übergang: alte Notiz wird zur eigenen, sobald die Kennung da ist
        (`Song.migrateLegacyNote`, ausgelöst in `MainApp`)
    *   `[x]` Bearbeiten-Dialog: eigene Notiz bearbeitbar, fremde lesen und löschen;
        unveränderter Text behält seinen Zeitstempel
    *   `[x]` Notenansicht: eigene Notiz sichtbar, fremde pro Person einschaltbar
        (Schalter, bleibt für die Sitzung erhalten)
    *   `[x]` Verfasseranzeige mit Punkt / farbigem Namen / Nummer (`NoteAuthorLabel`,
        `authorNumbers`); Zusammenführen je Verfasser (`mergedWith`) für Schritt 4
    *   `[x]` Unit-Tests `SongNoteTest` (53 Tests gesamt, alle grün)
3.  `[x]` `fileHash` beim Import setzen und nachtragen.
    *   `[x]` `Song.fileHash` (SHA-256, hex), `FileHash` streamt die Datei
    *   `[x]` gesetzt bei Import und *Ersetzen*, geleert bei *Entfernen*
    *   `[x]` beim Start im Hintergrund nachgetragen (`computeMissingHashes`),
        Liste wird nach Kennung zusammengeführt; `FileHashTest` (55 Tests grün)
    *   Nebenbei behoben: Beim Ersetzen durch eine Datei mit gleicher Endung wurde
        die frisch kopierte Datei wieder gelöscht (gleicher Pfad).
4.  `[x]` Stufe 1: *Alles sichern*, *Setlist teilen*, Vergleichsmaske, Erinnerung. Mit
    Unit-Tests für Hin- und Rückweg, die Einteilung in Gruppen und das Umbiegen der
    Setlist-Verweise.
    *   `[x]` Serialisierbare Form von `AppSettings` (`SerializableAppSettings`,
        `toSerializable()` / `toAppSettings()`; `userId` und `lastBackupAt` bleiben lokal)
    *   `[x]` ZIP schreiben: *Alles sichern* (`BackupRepository.createFullBackupZip`:
        Manifest, relative Pfade, Prüfsummen, PDFs nur gespeichert, Dateiname)
    *   `[x]` Fortschrittsdialog „Lied 34 von 87: ‹Titel›“; Import und Löschen sind
        währenddessen gesperrt
    *   `[x]` *Setlist teilen* samt Urheberrechtshinweis (`CopyrightDialog`, Menü in
        Setlist-Liste und -Ansicht)
    *   `[x]` Einlesen in Zwischenordner, Prüfsummen prüfen
    *   `[x]` Einteilung in Gruppen, Umbiegen der Setlist-Verweise – als reine
        Funktionen ohne `Context` in `BackupLogic`
    *   `[x]` Vergleichsmaske mit Schnellwahlen, Übernahme in einem Schritt
        (`BackupCompareScreen`); Kopfbereich mit Import-Knopf fest, Liste scrollt
    *   `[x]` Erinnerung in der Songliste (älter als 30 Tage)
    *   `[x]` Abschnitt *Hinweise* in den Einstellungen: Erinnerung und
        Urheberrechtshinweis abschaltbar; „Nicht wieder anzeigen“ als Checkbox
    *   `[x]` Unit-Tests: Dateiname, Hin-/Rückweg der Einstellungen
        (`BackupRepositoryTest`), Gruppeneinteilung und Setlist-Verweise
        (`BackupLogicTest`) – 60 Tests grün
    *   `[x]` Vorschaubild der ersten Seite beider Fassungen in der Vergleichsmaske, dazu
        Seitenzahl und Dateigröße (`BackupRepository.loadScorePreview`, `ScorePreviewBox`;
        wird beim Scrollen nachgeladen und beim Verlassen der Maske verworfen)
    *   `[x]` Frage „Bist du diese Person?“ beim Einlesen einer Komplettsicherung mit
        fremder Kennung (`BackupLogic.shouldAskForIdentity`); bei *Ja* übernimmt das Gerät
        Kennung und Name, bisherige eigene Notizen wandern mit
        (`BackupLogic.reassignNoteAuthor`). Die Antwort lässt sich bis zum Import über
        einen Schalter in der Liste ändern.
    *   `[x]` Hinweis, wenn ein abgewähltes Lied von einer ausgewählten Setlist
        gebraucht wird: am Lied („Setlist *Erntedank* enthält dieses Lied“) und an der
        Setlist („1 Lied ist abgewählt“) (`BackupLogic.songsMissingFromSelectedSetlists`)
    *   `[x]` Unit-Tests für Hinweis, Personenfrage und Umschreiben der Notizen –
        64 Tests grün
    *   `[ ]` Auf dem Gerät prüfen: Vorschau, Personenfrage, Setlist-Hinweis
5.  Stufe 2 erst, wenn Stufe 1 im Alltag erprobt ist – sie nutzt dasselbe Format und
    dieselbe Zuordnung, nur mit anderem Transport.

Die Android-Sicherung wird dabei nicht angefasst.

---

## 6. Technische Entscheidungen

### PDF-Darstellung

`PdfRenderer` aus dem Android-Framework, keine externe Bibliothek. Gerendert wird mit
1,5-facher Bildschirmbreite (maximal 3000 px), damit Zoomen scharf bleibt, ohne den
Speicher zu überfordern.

Entscheidend für das Spielgefühl ist das **Vorausladen**: Beim Öffnen einer Seite werden
die beiden Nachbarseiten im Hintergrund mitgerendert. Ein Tritt aufs Pedal zeigt die
neue Seite dann sofort, statt erst nach dem Rendern. Der Cache hält fünf Seiten; beim
Überlauf fliegen die vom aktuellen Standort am weitesten entfernten heraus.

### MusicXML-Darstellung

OpenSheetMusicDisplay in einem WebView. Die Bibliothek liegt als Asset in
`app/src/main/assets/osmd/` – die WebView hat Netzwerkzugriff und Dateizugriff
deaktiviert, die Darstellung funktioniert also vollständig offline und ohne Rechte.

Unterstützt werden `.xml`, `.musicxml` und `.mxl`. Letzteres ist ein ZIP-Container und
wird beim Laden entpackt.

### Dateityp-Erkennung beim Import

Auswertung des **Dateinamens** über `OpenableColumns.DISPLAY_NAME`, erst danach als
Rückfall der MIME-Typ. Grund: Viele Datei-Anbieter melden `application/octet-stream`
für alles. Bei reiner MIME-Prüfung landeten MusicXML-Dateien als PDF im System und
ließen sich nicht öffnen.

### Fehlerbehandlung

Beide Anzeigen melden Probleme im Klartext auf dem Bildschirm – lesbare Datei fehlt,
Seite außerhalb des Bereichs, MusicXML nicht interpretierbar. Eine weiße Fläche wäre
während einer Probe nicht diagnostizierbar.

### Display-Management

`FLAG_KEEP_SCREEN_ON` wird beim Betreten der Notenansicht gesetzt und beim Verlassen
wieder entfernt. Der Bildschirm bleibt also nur an, solange Noten offen sind. Mit
*Bildschirm bleibt an* aus gilt der normale System-Timeout.

### Design und Startbildschirm

**Noten im Dunkeldesign.** Die Noten folgen dem Design der App: im hellen Design immer
unverändert, im dunklen so, wie es beim Lied unter *Bearbeiten* eingestellt ist –
*Normal*, *Dezenter* (abgedunkeltes Papier) oder *Invertiert* (helle Noten auf dunklem
Grund). Steht das Design auf *System*, wechselt das Gerät tagsüber und abends von selbst.
Pro Lied statt global, weil nicht jedes Blatt das verkraftet: Farbige Scans oder Fotos
sehen invertiert schlecht aus, reine Notensätze gut. Umgesetzt als Farbfilter beim
Zeichnen (Android `ColorFilter`, Web CSS `filter`), damit zwischengespeicherte Seiten beim
Wechsel nicht neu gerendert werden müssen. Das Feld `darkMode` wird mitgesichert; fehlt
es in einer Sicherung oder ist der Wert unbekannt, gilt *Normal*.

Das Design folgt dem System oder ist fest auf Hell bzw. Dunkel gestellt. Die Symbole in
Status- und Navigationsleiste folgen dem **App**-Design, nicht dem des Geräts – sonst
stünden bei „Hell“ auf einem dunkel eingestellten Telefon helle Symbole auf hellem Grund.

Der System-Splash (ab Android 12 nicht abschaltbar) bleibt stehen, bis DataStore die
Einstellungen geliefert hat. So blitzt nie ein Frame im falschen Design auf. Darunter
liegt ein Compose-Startbildschirm, der die Note in exakt gleicher Größe und Lage zeigt;
der Wechsel ist unsichtbar, danach blenden Name und Version ein. Er erscheint nur beim
Kaltstart (nicht beim Drehen oder nach der Dateiauswahl), verschwindet nach 1,2 s und
lässt sich mit einem Tipp überspringen.

Geplant: Ist in den Einstellungen ein Name eingetragen, zeigt der Startbildschirm
„‹Name›s Noten“ statt *Meine Noten* (Abschnitt 5b, *Person und Notizen*).

### Sprachen

Alle Texte liegen in Ressourcen: Englisch als Standard (`values`), Deutsch in
`values-de`. Die Sortierung bleibt unabhängig von der Sprache deutsch (Collator), damit
die Reihenfolge der Sammlung nicht mit der Gerätesprache springt.

### Sortierung

`java.text.Collator` für Deutsch statt einfacher Zeichenkettenvergleiche. Ohne Collator
sortiert Kotlin nach Unicode-Position, wodurch „Über" hinter „Z" landet und
Großschreibung die Reihenfolge beeinflusst.

Sortiert wird in der Oberfläche, nicht in der Speicherung. Die JSON-Datei behält
die Einfügereihenfolge – die Anzeige leitet ihre Reihenfolge jedes Mal neu ab
(`remember` auf der Liste). Damit gibt es keinen Zustand, der auseinanderlaufen kann.

### Verkleinerung des Release-Builds (R8)

Release-Builds laufen mit `isMinifyEnabled` und `isShrinkResources`. Der Effekt ist
deutlich, weil die App nur einen Bruchteil der eingebundenen Compose-, Material3-,
Adaptive- und Navigation3-Bibliotheken nutzt:

| | ohne R8 | mit R8 |
|---|---|---|
| AAB | 14,64 MB | **5,6 MB** |
| APK | – | 2,81 MB |
| DEX | ~50 MB in 3 Dateien | 2,7 MB in 1 Datei |

Zwei Stellen der App sind für R8 nicht analysierbar und brauchen zwingend Keep-Rules in
`app/src/main/keepRules/rules.keep`:

1.  **Die WebView-Brücke.** `MusicXmlView` registriert ein anonymes Objekt als
    `AndroidOsmd`; dessen `onStatus()` wird ausschließlich aus JavaScript heraus
    aufgerufen. R8 sieht keinen Aufrufer und würde die Methode entfernen – die
    MusicXML-Anzeige bliebe dann im Ladezustand hängen.
2.  **Die Datenmodelle.** `Song`, `Setlist` und `SongSource` werden als JSON persistiert.
    Würde R8 deren Felder umbenennen, ließen sich bestehende `songs.json` und
    `setlists.json` nicht lesen – ein stiller Datenverlust bei vorhandenen Nutzern.

> [!WARNING]
> Diese Regeln sind nicht optional und dürfen nicht „aufgeräumt" werden. Ihr Fehlen
> fällt im Debug-Build **nicht** auf, weil dort nicht verkleinert wird. Nach Änderungen
> am Datenmodell oder an der WebView-Brücke muss der Release-Build erneut auf einem
> Gerät geprüft werden.

Zusätzlich bleiben `SourceFile` und `LineNumberTable` erhalten, damit Stacktraces in der
Play Console lesbar sind. Die Zuordnungsdatei (`mapping.txt`) wird von AGP automatisch
ins AAB eingebettet.

---

## 7. Projektstruktur

```
app/src/main/
├── assets/osmd/              OpenSheetMusicDisplay (offline)
├── keepRules/rules.keep      R8-Regeln für Serialisierung und WebView-Brücke
└── java/de/workflow42/meinenoten/
    ├── MainActivity.kt            Splash, Design, Startbildschirm
    ├── data/
    │   ├── SongRepository.kt      Import, JSON-Persistenz, Löschen
    │   ├── AppSettings.kt         Einstellungen, DataStore, serialisierbare Form
    │   ├── BackupRepository.kt    ZIP schreiben und einlesen, Übernahme
    │   ├── BackupLogic.kt         Dateiname, Gruppen, Setlist-Verweise (ohne Context)
    │   └── FileHash.kt            SHA-256 einer Datei
    ├── model/
    │   ├── Song.kt
    │   ├── SongNote.kt            Notiz mit Verfasser
    │   ├── Setlist.kt
    │   ├── BackupModels.kt        Manifest, Vergleichs- und Importdaten
    │   └── PageView.kt            Zoom und Ausschnitt einer Seite
    └── ui/
        ├── MainApp.kt             Navigation, Drawer, Dialoge
        ├── AppDrawer.kt           Inhalt des Navigationsmenüs
        ├── Navigator.kt           Backstack
        ├── NavigationState.kt     Routen
        ├── components/
        │   ├── PdfView.kt         Rendering, Cache, Vorausladen, Zoom
        │   ├── MusicXmlView.kt    WebView-Brücke zu OSMD
        │   ├── SongFilterBar.kt   Such-, Filter- und Sortierleiste Songs
        │   ├── SetlistFilterBar.kt  dasselbe für Setlists
        │   ├── AlphabetIndex.kt   Buchstabenleiste zum Springen
        │   ├── SongActions.kt     Menü, Bearbeiten- und Löschen-Dialog
        │   ├── VersionFooter.kt   Versionszeile
        │   ├── SetlistStrip.kt    ungenutzt, kann entfernt werden
        │   ├── GenreChips.kt      Vorschläge vergebener Genres
        │   ├── NoteAuthor.kt      Verfasserzeile einer Notiz (Punkt, Farbe, Nummer)
        │   ├── CopyrightDialog.kt Hinweis vor „Setlist teilen“
        │   └── DateField.kt       Datumsauswahl
        ├── util/
        │   ├── SortUtils.kt       Collator-Sortierung, Sortiermodi Songs
        │   ├── SetlistUtils.kt    Suche, Zeitraum, Sortierung Setlists
        │   ├── PersonUtils.kt     Titel „‹Name›s Noten“, Personenfarbe
        │   └── DateUtils.kt       Datum deuten und anzeigen
        └── screens/
            ├── LaunchScreen.kt
            ├── SongListScreen.kt
            ├── SongDetailScreen.kt    Anzeige + Eingabekonzept
            ├── SetlistScreen.kt       Übersicht und Setlist-Detail
            ├── BackupCompareScreen.kt Vergleichsmaske beim Einlesen
            └── SettingsScreen.kt
```

> [!NOTE]
> Das Setlist-Detail liegt zusammen mit der Übersicht in `SetlistScreen.kt`. Der
> Paketpfad ist `de.workflow42.meinenoten`.

### Web-App

```
web/
├── vite.config.js            Basispfad, Webversion, Build-Zeitstempel
├── public/
│   ├── sw.js                 Service Worker (Offline, Cache-Name je Auslieferung)
│   └── manifest.json         Home-Bildschirm-App
├── src/
│   ├── main.ts               Start, iOS-Gesten, Statusleisten-Abstand, SW-Registrierung
│   ├── App.svelte            alle Seiten, Navigation, Sicherung einlesen
│   ├── routes/SelfTest.svelte  Diagnose
│   └── lib/
│       ├── model/types.ts    Song, Setlist, Einstellungen (wie Android)
│       ├── storage/db.ts     IndexedDB, OPFS mit IndexedDB-Ausweich
│       ├── pdf/              pdf.js-Rendering, Viewer mit Zoom und Tippzonen
│       ├── musicxml/         OSMD
│       └── logic/            Serialisierung, Sicherung schreiben/einlesen, Suche
└── tests/                    Vitest, u. a. mit einer echten Android-Sicherung
```

Ausgeliefert wird über `.github/workflows/deploy-web.yml` bei jedem Push auf `main`,
der `web/` oder `docs/` berührt.

---

## 8. Prüfstand

Stand 1.6.1 (`versionCode` 13):

| Was | Wie geprüft | Ergebnis |
|---|---|---|
| Unit-Tests | `:app:testDebugUnitTest` | 64 bestanden, 0 fehlgeschlagen |
| Release-Bundle | `:app:bundleRelease` | erfolgreich |
| Versionszähler | `version.properties` | nach dem Build auf 14 / 1.6.2 erhöht |
| Sicherung (Release-Build) | Alles sichern, App-Daten löschen, Sicherung einlesen | erfolgreich: alle Lieder, Noten, Setlists zurück |

Die Unit-Tests decken reine Logik ab: Gruppierung und Sortierung der Songliste
(`SongListGroupingTest`), Suche, Zeitraum und Sortierung der Setlists
(`SetlistListTest`), Zoom-Daten und Rückwärtskompatibilität alter JSON-Dateien
(`SongZoomAndBackwardCompatibilityTest`), Notizen pro Person und Titel
(`SongNoteTest`, `PersonTitleTest`), Prüfsumme (`FileHashTest`) sowie Dateiname,
Einstellungen, Gruppeneinteilung und Setlist-Verweise der Sicherung
(`BackupRepositoryTest`, `BackupLogicTest`). Ohne Tests sind die Dateityp-Erkennung im
Repository und das Entpacken von `.mxl`.

### Prüfung eines Release-Builds

R8-Fehler treten nur im Release-Build auf. Vor jedem Upload deshalb:

| Was | Wie prüfen | Erwartet |
|---|---|---|
| `targetSdk`, `versionCode` | gemergtes Manifest | 36, neuer Code |
| Signatur | `jarsigner -verify` | „JAR verifiziert“ |
| Modelle behalten | `mapping.txt` | `Song`, `Setlist`, `SongSource`, `PageView` unverändert |
| Feldnamen behalten | `mapping.txt` | `getArtist -> getArtist` usw. |
| JS-Brücke behalten | `seeds.txt` | `onStatus(String, String)` enthalten |
| Start | Release-APK auf Gerät | läuft, keine `ClassNotFound` |
| **Lesen von JSON** | Lied anlegen, App neu starten | Lied weiterhin vorhanden |
| Sicherung | Alles sichern, App-Daten löschen, Sicherung einlesen | alle Lieder, Noten, Setlists zurück |
| Update | über die Vorversion installieren | bestehende Daten lesbar |

Der Lese-Test ist der wichtigste: Er belegt, dass die Keep-Rules greifen und bestehende
Datenbestände lesbar bleiben.

> [!WARNING]
> **Für 1.5.0 noch offen:** `mapping.txt` für die neuen Felder (`lastPlayedAt`,
> `pageViews`, `lyrics`, `lastOpenedAt`), Neustart des Release-Builds mit vorhandenen
> Daten und ein Update über die Vorversion.

> [!WARNING]
> **Nicht auf echter Hardware geprüft:** Das Bluetooth-Pedal lässt sich im Emulator
> nicht simulieren. Die Tastencodes sind breit abgedeckt, der Nachweis mit einem echten
> Gerät fehlt. Bei Problemen zeigt Logcat, welchen Code das Pedal tatsächlich sendet.

> [!WARNING]
> **MusicXML im Release-Build nicht gesehen.** Die Keep-Rule für `@JavascriptInterface`
> ist über `seeds.txt` nachgewiesen, die Darstellung im verkleinerten Build aber nicht.
> Mit einer `.musicxml`-Datei nachholen.


