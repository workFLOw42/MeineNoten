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
| Daten | Import kopiert Dateien in den App-Speicher |
| Daten | Setlists: anlegen, sortieren, Notizen |
| Daten | Lieder löschen samt Datei und Setlist-Verweisen |
| Daten | Genre als freies Metadatum mit Vorschlags-Chips |
| Daten | Songliste durchsuchen, nach Genre und Setlist filtern, fünf Sortiermodi |
| Daten | Setlists durchsuchen (auch nach enthaltenen Liedern), nach Zeitraum filtern, drei Sortiermodi |
| Daten | Setlists duplizieren, Lied entfernen mit Rückgängig |
| Daten | Persistenz als JSON via kotlinx.serialization, Einstellungen via DataStore |
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
    val notes: String = "",          // kurze Notiz neben den Noten ("Capo 2")
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

Das Design folgt dem System oder ist fest auf Hell bzw. Dunkel gestellt. Die Symbole in
Status- und Navigationsleiste folgen dem **App**-Design, nicht dem des Geräts – sonst
stünden bei „Hell“ auf einem dunkel eingestellten Telefon helle Symbole auf hellem Grund.

Der System-Splash (ab Android 12 nicht abschaltbar) bleibt stehen, bis DataStore die
Einstellungen geliefert hat. So blitzt nie ein Frame im falschen Design auf. Darunter
liegt ein Compose-Startbildschirm, der die Note in exakt gleicher Größe und Lage zeigt;
der Wechsel ist unsichtbar, danach blenden Name und Version ein. Er erscheint nur beim
Kaltstart (nicht beim Drehen oder nach der Dateiauswahl), verschwindet nach 1,2 s und
lässt sich mit einem Tipp überspringen.

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
    │   └── AppSettings.kt         Einstellungen, DataStore
    ├── model/
    │   ├── Song.kt
    │   ├── Setlist.kt
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
        │   └── DateField.kt       Datumsauswahl
        ├── util/
        │   ├── SortUtils.kt       Collator-Sortierung, Sortiermodi Songs
        │   ├── SetlistUtils.kt    Suche, Zeitraum, Sortierung Setlists
        │   └── DateUtils.kt       Datum deuten und anzeigen
        └── screens/
            ├── LaunchScreen.kt
            ├── SongListScreen.kt
            ├── SongDetailScreen.kt    Anzeige + Eingabekonzept
            ├── SetlistScreen.kt       Übersicht und Setlist-Detail
            └── SettingsScreen.kt
```

> [!NOTE]
> Das Setlist-Detail liegt zusammen mit der Übersicht in `SetlistScreen.kt`. Der
> Paketpfad ist `de.workflow42.meinenoten`.

---

## 8. Prüfstand

Stand 1.5.0 (`versionCode` 11):

| Was | Wie geprüft | Ergebnis |
|---|---|---|
| Unit-Tests | `:app:testDebugUnitTest` | 38 bestanden, 0 fehlgeschlagen |
| Release-Bundle | `:app:bundleRelease` | erfolgreich, 5,6 MB |
| Signatur | `jarsigner -verify` | „JAR verifiziert“ |
| Versionszähler | `version.properties` | nach dem Build auf 12 / 1.5.1 erhöht |

Die Unit-Tests decken reine Logik ab: Gruppierung und Sortierung der Songliste
(`SongListGroupingTest`), Suche, Zeitraum und Sortierung der Setlists
(`SetlistListTest`) sowie Zoom-Daten und Rückwärtskompatibilität alter JSON-Dateien
(`SongZoomAndBackwardCompatibilityTest`). Ohne Tests sind die Dateityp-Erkennung im
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


