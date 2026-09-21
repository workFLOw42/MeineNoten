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

### Umgesetzt und auf dem Gerät verifiziert

| Bereich | Funktion |
|---|---|
| Anzeige | PDF-Rendering via `PdfRenderer`, seitenverhältnistreu |
| Anzeige | Vorausladen der Nachbarseiten, Cache für 5 Seiten |
| Anzeige | MusicXML-Rendering via OpenSheetMusicDisplay (WebView, offline) |
| Anzeige | Textnotizen als eigener Song-Typ (ohne Datei) |
| Blättern | Bluetooth-Pedal (HID-Tastencodes) |
| Blättern | Wischen links/rechts |
| Blättern | Tipp-Zonen: linkes/rechtes Drittel blättert, Mitte schaltet UI |
| Blättern | Seitenleiste unten mit Pfeilen und Seitenzähler |
| Blättern | Grüner Blitz als optische Rückmeldung |
| Komfort | Letzte Seite wird pro Song gespeichert |
| Komfort | Display-Timeout deaktiviert, solange Noten offen sind |
| Daten | Import kopiert Dateien in den App-Speicher |
| Daten | Setlists: anlegen, sortieren, Notizen |
| Daten | Persistenz als JSON via kotlinx.serialization |
| UI | Material 3, adaptiv (Navigation Bar / Rail je nach Ausrichtung) |
| Auslieferung | Release-Build mit R8 verkleinert, signiert, Play-tauglich |
| Auslieferung | Datenschutzerklärung über GitHub Pages veröffentlicht |

### Verworfen

**On-Device OMR (Notenerkennung aus Scans).** War als Phase 2 geplant und existierte als
`OmrWorker` – dieser hat jedoch nie Noten erkannt, sondern nach drei Sekunden Wartezeit
„fertig" gemeldet. Eine Attrappe ohne Funktion.

Die Begründung für den Verzicht: Verlässliche OMR braucht Modelle, die auf einem
Tablet dieser Klasse nicht sinnvoll laufen. Die Erkennungsqualität bei fotografierten
Scans ist zudem so schwankend, dass das Ergebnis vor dem Auftritt jedes Mal geprüft
werden müsste – genau die Aufmerksamkeit, die die App einsparen soll. Ein falsch
erkannter Takt im Gottesdienst ist schlimmer als ein Scan, der eben ein Scan ist.

MusicXML bleibt unterstützt, aber nur als **Import** bereits vorhandener Dateien.
Wer digitale Noten hat, bekommt scharfe Vektordarstellung. Wer Scans hat, behält Scans.

> [!NOTE]
> Sollte Notenerkennung später gewünscht sein, wäre der gangbare Weg ein Server-Dienst
> (z. B. Audiveris) mit manueller Freigabe des Ergebnisses – nicht eine stille
> Umwandlung im Hintergrund.

### Offen

**Automatisches Umblättern per Mikrofon.** Weiterhin die interessanteste Idee, aber
unverändert schwierig: Ein Kirchenraum mit Chor, Orgel und Gemeindegesang ist akustisch
keine Testumgebung. Ein Fehlauslöser mitten im Lied wäre schlimmer als gar keine
Automatik. Bleibt Zukunftsvision, nicht eingeplant.

**Ungenutzte Datenfelder.** `bpm`, `timeSignature` und `totalBars` werden gespeichert,
aber nirgends verwendet. Sie waren Vorarbeit für den Rhythmus-Tracker. Entweder daraus
ein einfaches Metronom bauen oder die Felder entfernen – Entscheidung steht aus.

### In Arbeit

Die folgenden Punkte sind beschlossen und werden als nächstes umgesetzt. Details in
Abschnitt 3 und 4.

*   Lieder über den Bearbeiten-Dialog löschen
*   Listen alphabetisch statt in Einfügereihenfolge
*   Einspaltige Liste statt Kachelraster
*   Setlists nach Datum, neueste oben
*   Konzertdatum über Datumsauswahl statt Freitext
*   Am Liedende in der Setlist zum nächsten Lied weiterblättern
*   Sprungleiste mit Vorschaubildern in der Notenansicht

---

## 2a. Veröffentlichung

Die App wird über Google Play ausgeliefert. Was dafür eingerichtet ist:

| Punkt | Stand |
|---|---|
| Signierung | eigener Keystore, Zugangsdaten in `local.properties` (nicht im Repo) |
| `targetSdk` | 36 – Play-Mindestanforderung |
| Verkleinerung | R8 aktiv, 14,64 MB → 5,18 MB |
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

> [!IMPORTANT]
> `compileSdk` steht auf 37 und muss dort bleiben, weil eine eingebundene
> AndroidX-Abhängigkeit (`runtime-saveable`) das verlangt. Das ist unabhängig vom
> `targetSdk` und für Play unproblematisch.

### Erzwungenes Edge-to-Edge ab `targetSdk` 36

Ab API 36 lässt sich die randlose Darstellung nicht mehr abschalten; Inhalte laufen unter
Status- und Navigationsleiste. Die App war darauf vorbereitet: `enableEdgeToEdge()` in
`MainActivity`, und alle Screens geben das `innerPadding` ihres `Scaffold` weiter.

Eine Stelle bleibt bewusst abweichend: In der Notenansicht wird das Padding nur bei
sichtbarer Bedienoberfläche angewendet. Bei ausgeblendeter UI nutzen die Noten die ganze
Fläche – gewollt, weil dort jeder Millimeter Notenhöhe zählt.

---

## 3. Listen und Navigation

### Darstellung der Listen

Beide Listen laufen **einspaltig untereinander**. Das bisherige Kachelraster
(`LazyVerticalGrid`, zwei Spalten im Querformat) wird ersetzt.

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

### Sortierung der Setlists

Nach Konzertdatum, **neuestes oben**. Wer die App aufschlägt, braucht fast immer das
nächste oder letzte Konzert – nicht das von vorletztem Jahr.

Setlists ohne Datum landen unten, untereinander alphabetisch nach Titel.

> [!IMPORTANT]
> Das Datumsfeld ist derzeit freier Text („Ostern", „24.12."). Danach lässt sich nicht
> verlässlich sortieren. Deshalb wird die Eingabe auf eine **Datumsauswahl**
> (`DatePicker`) umgestellt und intern als `yyyy-MM-dd` gespeichert.
>
> Bereits vorhandene Freitext-Einträge werden beim Laden einmalig gedeutet
> (`TT.MM.JJJJ`, `TT.MM.`, `yyyy-MM-dd`). Was sich nicht deuten lässt, bleibt als
> Beschriftung erhalten und die Setlist wird wie „ohne Datum" behandelt – es gehen
> also keine Eingaben verloren.

### Löschen von Liedern

Im Bearbeiten-Dialog der Notenansicht, als drittes Feld neben *Speichern* und
*Abbrechen*. Absichtlich **nicht** in der Liste: Ein Wischen oder Langdruck in der
Liste wäre auf einem Tablet, das man mit der Gitarre in der Hand bedient, zu leicht
versehentlich ausgelöst.

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

Der Liedwechsel wird deutlicher zurückgemeldet als ein Seitenwechsel (längerer Blitz
plus kurze Einblendung des neuen Titels), damit man nicht versehentlich ein Lied
weiterrutscht und es erst beim Anspielen merkt.

> [!NOTE]
> Nur wirksam, wenn das Lied **aus einer Setlist heraus** geöffnet wurde. Aus der
> Songliste geöffnet bleibt das Verhalten unverändert an den Liedgrenzen stehen.
> Technisch über die optionale Angabe der Setlist-Kennung in der Route.

### Sprungleiste in der Notenansicht

Wurde das Lied aus einer Setlist geöffnet, erscheint rechts am Bildschirmrand eine
schmale senkrechte Leiste mit **Vorschaubildern aller Lieder der Setlist** – jeweils
die erste Notenseite, stark verkleinert. Ein Tipp öffnet das betreffende Lied.

Zweck: In einem Gottesdienst wird die Reihenfolge oft spontan geändert oder ein Lied
übersprungen. Ohne Sprungleiste heißt das: zurück zur Setlist, suchen, öffnen.

Eigenschaften:

*   Das aktuelle Lied ist hervorgehoben.
*   Wird mit den übrigen Bedienleisten ein- und ausgeblendet (Tipp in die Bildmitte),
    stört also beim Spielen nicht.
*   Lieder ohne Notenblatt (Typ *Text*) zeigen ein Ersatzsymbol.
*   Vorschaubilder werden verkleinert gerendert und zwischengespeichert.

> [!WARNING]
> Vorschaubilder kosten Speicher und Rechenzeit. Bei langen Setlists werden sie
> daher nur für die gerade sichtbaren Einträge erzeugt und in stark reduzierter
> Auflösung gehalten (Breite rund 120 px).

---

## 4. Eingabekonzept

Das Blättern ist die einzige Funktion, die während des Spielens gebraucht wird. Deshalb
sind bewusst **mehrere Wege parallel** umgesetzt, statt einen zu erzwingen.

| Eingabe | Vorwärts | Rückwärts |
|---|---|---|
| Tasten / Pedal | `PAGE_DOWN`, `DPAD_RIGHT`, `DPAD_DOWN`, `SPACE`, `ENTER`, `MEDIA_NEXT`, `VOLUME_DOWN` | `PAGE_UP`, `DPAD_LEFT`, `DPAD_UP`, `MEDIA_PREVIOUS`, `VOLUME_UP` |
| Wischen | nach links | nach rechts |
| Tippen | rechtes Bildschirmdrittel | linkes Bildschirmdrittel |
| Leiste unten | Pfeil rechts | Pfeil links |

Zur Tastenliste: Die Lautstärke- und Medientasten sind absichtlich dabei. Viele
preiswerte Seitenwender melden sich beim System nicht als Pfeiltasten, sondern als
genau diese Codes. Wer ein solches Pedal kauft, soll es anschließen können, ohne
vorher die Firmware zu prüfen.

Zu den Tipp-Zonen: Das mittlere Drittel blendet die Bedienleisten ein und aus. Blättern
liegt an den Rändern, weil man dort mit der Greifhand hinkommt, ohne den Blick von den
Noten zu nehmen.

### Optische Rückmeldung

Beim Blättern läuft ein kurzer grüner Schleier über den Bildschirm (50 ms Anstieg,
500 ms Ausklang). Das bestätigt den Pedaltritt auch dann, wenn zwei Seiten ähnlich
aussehen – ohne diese Rückmeldung tritt man im Zweifel ein zweites Mal und ist zwei
Seiten zu weit.

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
    val bpm: Int = 120,              // derzeit ungenutzt
    val timeSignature: String = "4/4",  // derzeit ungenutzt
    val totalBars: Int = 0,          // derzeit ungenutzt
    val lastPage: Int = 0,           // zuletzt gelesene Seite
    val notes: String = ""
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
    val notes: String = ""
)
```

### Änderung am Datumsfeld

`Setlist.date` bleibt technisch ein `String`, wird aber künftig ausschließlich im
Format `yyyy-MM-dd` geschrieben. Gründe für diese Wahl statt eines Zeitstempels:

*   Bestehende JSON-Dateien bleiben lesbar, keine Migration der Struktur nötig.
*   Das Format sortiert sich als Zeichenkette von selbst richtig.
*   Ein Datum ohne Uhrzeit ist hier das fachlich Richtige – ein Konzert hat einen Tag.

Angezeigt wird weiterhin deutsch (`TT.MM.JJJJ`); die Umwandlung passiert nur in der
Oberfläche.

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
Seite außerhalb des Bereichs, MusicXML nicht interpretierbar. Vorher blieb die Fläche
in solchen Fällen weiß, was während einer Probe nicht diagnostizierbar ist.

### Display-Management

`FLAG_KEEP_SCREEN_ON` wird beim Betreten der Notenansicht gesetzt und beim Verlassen
wieder entfernt. Der Bildschirm bleibt also nur an, solange Noten offen sind.

### Sortierung

`java.text.Collator` für Deutsch statt einfacher Zeichenkettenvergleiche. Ohne Collator
sortiert Kotlin nach Unicode-Position, wodurch „Über" hinter „Z" landet und
Großschreibung die Reihenfolge beeinflusst.

Sortiert wird in der Oberfläche, nicht in der Speicherung. Die JSON-Datei behandelt
weiter die Einfügereihenfolge – die Anzeige leitet ihre Reihenfolge jedes Mal neu ab
(`remember` auf der Liste). Damit gibt es keinen Zustand, der auseinanderlaufen kann.

### Verkleinerung des Release-Builds (R8)

Release-Builds laufen mit `isMinifyEnabled` und `isShrinkResources`. Der Effekt ist
deutlich, weil die App nur einen Bruchteil der eingebundenen Compose-, Material3-,
Adaptive- und Navigation3-Bibliotheken nutzt:

| | ohne R8 | mit R8 |
|---|---|---|
| AAB | 14,64 MB | **5,18 MB** |
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
    `setlists.json` nicht mehr lesen – ein stiller Datenverlust bei vorhandenen Nutzern.

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
    ├── MainActivity.kt
    ├── data/
    │   └── SongRepository.kt      Import, JSON-Persistenz, Löschen
    ├── model/
    │   ├── Song.kt
    │   └── Setlist.kt
    └── ui/
        ├── MainApp.kt             Navigation, adaptives Layout
        ├── Navigator.kt           Backstack
        ├── NavigationState.kt     Routen
        ├── components/
        │   ├── PdfView.kt         Rendering, Cache, Vorausladen
        │   ├── MusicXmlView.kt    WebView-Brücke zu OSMD
        │   ├── SetlistStrip.kt    Sprungleiste mit Vorschaubildern
        │   └── DateField.kt       Datumsauswahl
        ├── util/
        │   ├── SortUtils.kt       Collator-Sortierung
        │   └── DateUtils.kt       Datum deuten und anzeigen
        └── screens/
            ├── SongListScreen.kt
            ├── SongDetailScreen.kt    Anzeige + Eingabekonzept
            └── SetlistScreen.kt       Übersicht und Setlist-Detail
```

> [!NOTE]
> Das Setlist-Detail liegt zusammen mit der Übersicht in `SetlistScreen.kt`, es gibt
> keine separate `SetlistDetailScreen.kt`. Der Paketpfad ist `de.workflow42.meinenoten`
> (früher `com.example.meinenoten`).

---

## 8. Prüfstand

| Was | Wie geprüft | Ergebnis |
|---|---|---|
| Übersetzen | `:app:assembleDebug` | erfolgreich |
| Installation | Emulator (Tab S6 Lite, 1200×2000) | erfolgreich |
| Blättern per Wischen | mehrseitiges PDF, Sichtprüfung | Seite wechselt |
| Blättern per Tipp-Zone | linkes Drittel | Seite wechselt zurück |
| Seitenzähler | Kopfzeile und Leiste unten | zeigt korrekt „1 / 2" |
| Laufzeitfehler | Logcat | keine Fehler, keine Abstürze |

### Prüfung des verkleinerten Release-Builds

R8-Fehler treten grundsätzlich nur im Release-Build auf, deshalb separat geprüft:

| Was | Wie geprüft | Ergebnis |
|---|---|---|
| `targetSdk` | gemergtes Manifest gelesen | `targetSdkVersion="36"` |
| `versionCode` | gemergtes Manifest gelesen | `versionCode="3"` |
| Signatur | `jarsigner -verify` | „JAR-Datei verifiziert." |
| Modelle behalten | `mapping.txt` | `Song`, `Setlist`, `SongSource` unverändert |
| Feldnamen behalten | `mapping.txt` | `getArtist -> getArtist` usw. |
| JS-Brücke behalten | `seeds.txt` | `onStatus(String, String)` enthalten |
| Start | Release-APK auf Emulator | läuft, keine `ClassNotFound` |
| Oberfläche | Sichtprüfung | Navigation und Liste korrekt |
| Schreiben von JSON | Lied angelegt | erscheint in der Liste |
| **Lesen von JSON** | App neu gestartet | Lied weiterhin vorhanden |

Der letzte Punkt ist der wichtigste: Er belegt, dass die Keep-Rules greifen und
bestehende Datenbestände nach dem Umstieg auf R8 lesbar bleiben.

> [!WARNING]
> **Nicht auf echter Hardware geprüft:** Das Bluetooth-Pedal lässt sich im Emulator
> nicht simulieren. Die Tastencodes sind nach bestem Wissen breit abgedeckt, der
> Nachweis mit einem echten Gerät fehlt aber. Bei Problemen zeigt Logcat, welchen
> Code das Pedal tatsächlich sendet.

> [!WARNING]
> **MusicXML im Release-Build nicht geprüft.** Für die Anzeige fehlte eine Testdatei.
> Die Keep-Rule für `@JavascriptInterface` ist über `seeds.txt` nachgewiesen, die
> tatsächliche Darstellung im verkleinerten Build wurde aber nicht gesehen. Das sollte
> mit einer `.musicxml`-Datei nachgeholt werden.

Automatisierte Tests existieren derzeit nur als Projektvorlagen
(`ExampleUnitTest`, `ExampleInstrumentedTest`) und prüfen keine App-Logik.
Sinnvolle erste Kandidaten wären die Dateityp-Erkennung im Repository und das
Entpacken von `.mxl`, da beide reine Logik ohne UI sind.

