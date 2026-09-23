# Meine Noten

Eine Android-App, die eingescannte und digitale Notenblätter auf dem Tablet anzeigt –
mit freihändigem Umblättern per Bluetooth-Pedal.

Entstanden für die Gitarrenbegleitung im Kirchenchor. Das Kernproblem: Beim Spielen sind
beide Hände belegt. Umblättern muss deshalb ohne Hände funktionieren, oder mit einer
einzigen, blind ausführbaren Berührung.

[Datenschutzerklärung](https://workFLOw42.github.io/MeineNoten/docs/privacy-policy.html)

## Funktionen

* **PDF und MusicXML** – Scans seitengetreu, digitale Noten als scharfe Vektoren
  (`.xml`, `.musicxml`, `.mxl`)
* **Textnotizen** – Liedtexte oder Akkorde ganz ohne Datei
* **Freihändiges Blättern** – Bluetooth-Pedal, Tippzonen oder Knöpfe ◀ ▶ in der Statusleiste
* **Zoom pro Seite** – Zwei-Finger-Zoom, wird pro Notenseite gemerkt
* **Setlists** – Reihenfolge vorbereiten, am Liedende direkt ins nächste Stück, dort
  weitermachen, wo man aufgehört hat
* **Suchen, Filtern, Sortieren** – in Songliste und Setlists, mit Buchstabenleiste
* **Einstellbar** – Statusleiste, Tippzonen, Pedalrichtung, Design (System/Hell/Dunkel)
* **Offline** – keine Internetverbindung, keine Android-Berechtigung, kein Konto

## Anleitung

### Noten hinzufügen

Unten rechts liegen zwei Schaltflächen:

| Symbol | Funktion |
|---|---|
| **+** | PDF oder MusicXML importieren |
| **Stift** | Textnotiz ohne Datei anlegen |

Beim Import wird die Datei in den privaten App-Speicher kopiert. Die Originaldatei kann
danach verschoben oder gelöscht werden, ohne dass die App den Zugriff verliert.

### Umblättern

Während der Anzeige stehen drei Wege parallel zur Verfügung. Ab Werk sind alle aktiv,
in den *Einstellungen* lassen sie sich anpassen:

| Eingabe | Vorwärts | Rückwärts |
|---|---|---|
| Pedal / Tastatur | `Bild ab`, `→`, `↓`, `Leertaste`, `Enter`, `Leiser`-Taste, Medientaste vor | `Bild auf`, `←`, `↑`, `Lauter`-Taste, Medientaste zurück |
| Tippzonen | rechte Hälfte des unteren Drittels | linke Hälfte des unteren Drittels |
| Statusleiste oben | ▶ | ◀ |

Die oberen zwei Drittel bleiben frei zum Zoomen und Verschieben. Wischen blättert bewusst
nicht um, weil es sich mit dem Verschieben einer gezoomten Seite ins Gehege käme.

Die **Statusleiste** bleibt immer sichtbar und zeigt Lied, Position in der Setlist und
Seite. Auf der letzten Seite wird aus ▶ ein ⏭ – der nächste Tipp wechselt das Lied.

Jeder Seitenwechsel wird mit einem kurzen grünen Rand bestätigt. So erkennt man den
Pedaltritt auch bei zwei ähnlich aussehenden Seiten, ohne den Blick von den Noten zu
nehmen.

### Menü

Alles Weitere liegt im **Menü oben links**: Songliste, Setlists, Einstellungen und – bei
geöffnetem Lied – *Bearbeiten*, *Zur Setlist hinzufügen*, *Liedtext anzeigen* und
*Löschen*. Wurde das Lied aus einer Setlist geöffnet, steht darunter die ganze
Reihenfolge zum direkten Springen.

### Bluetooth-Pedal einrichten

Das Pedal wird **in den Android-Einstellungen** gekoppelt, nicht in der App. Geeignet
sind handelsübliche Modelle, die sich als Tastatur (HID) anmelden.

1. Pedal in den Kopplungsmodus bringen
2. Android-Einstellungen → *Verbundene Geräte* → *Neues Gerät koppeln*
3. In der App ein Lied öffnen und einen Tritt testen

Sendet das Pedal einen nicht erkannten Tastencode, lässt sich der tatsächlich gesendete
Code über Logcat auslesen und die Liste in
[SongDetailScreen.kt](app/src/main/java/de/workflow42/meinenoten/ui/screens/SongDetailScreen.kt)
erweitern.

### Setlists

Unter *Setlists* eine neue Liste anlegen, mit Konzertdatum versehen und Lieder über das
Menü an jedem Lied hinzufügen. Listen erscheinen nach Datum sortiert und nach Jahr
gruppiert, das nächste anstehende Programm ist hervorgehoben. Alternativ lässt sich nach
Titel oder *Zuletzt gespielt* sortieren und nach kommenden oder vergangenen Terminen
filtern. Die Suche findet auch Lieder innerhalb der Setlists.

Eine Setlist merkt sich, bei welchem Lied und welcher Seite sie verlassen wurde, und
bietet *Fortsetzen* an. Nach dem letzten Lied beginnt sie wieder von vorn.

Wird ein Lied **aus einer Setlist heraus** geöffnet, blättert man am Ende der letzten
Seite direkt in das nächste Stück – das Pedal funktioniert also über die gesamte Setlist
hinweg. Aus der Songliste geöffnet bleibt die Anzeige an den Liedgrenzen stehen.

### Einstellungen

| Bereich | Einstellbar |
|---|---|
| Statusleiste | Liedtitel, Position „Lied x/n“, Seitenzahl, Knöpfe ◀ ▶, Ankündigung ⏭ |
| Umblättern | Tippzonen an/aus, Größe (unteres Drittel, untere Hälfte, ganze Höhe), Seiten tauschen, grüner Rand, Titel beim Liedwechsel |
| Pedal & Tasten | Lautstärketasten blättern, Richtung umkehren |
| Anzeige | Bildschirm bleibt an, Zoom pro Seite merken, Design |

Die Standardwerte entsprechen dem Verhalten vor Einführung der Einstellungen.

## Technik

| | |
|---|---|
| Sprache | Kotlin |
| Oberfläche | Jetpack Compose, Material 3 |
| Navigation | Navigation 3, Navigationsmenü (Drawer) |
| PDF | `PdfRenderer` (Framework, keine externe Bibliothek) |
| MusicXML | OpenSheetMusicDisplay im WebView, offline als Asset |
| Speicherung | JSON über kotlinx.serialization, Einstellungen per DataStore |
| Start | SplashScreen-API mit nahtlosem Compose-Startbildschirm |
| `minSdk` | 26 (Android 8.0) |
| `targetSdk` | 36 |

Entwickelt und geprüft für ein 8-Zoll-Tablet (Samsung Galaxy Tab S6 Lite), läuft aber
auf Telefonen und größeren Tablets ebenso. Oberfläche auf Deutsch und Englisch.

## Selbst bauen

```bash
git clone https://github.com/workFLOw42/MeineNoten.git
cd MeineNoten
./gradlew :app:assembleDebug
```

Für einen Release-Build wird ein Signaturschlüssel benötigt. Die Zugangsdaten gehören in
`local.properties`, die **nicht** im Repository liegt:

```properties
signing.storeFile=C:/Pfad/zu/deinem.jks
signing.storePassword=...
signing.keyAlias=...
signing.keyPassword=...
```

```bash
./gradlew :app:bundleRelease
```

Die Versionsnummer steht in [version.properties](app/version.properties) und wird nach
jedem erfolgreichen `bundleRelease` automatisch hochgezählt – die Datei gehört deshalb
mit ins Repository.

Release-Builds werden mit R8 verkleinert. Die zugehörigen Keep-Rules stehen in
[rules.keep](app/src/main/keepRules/rules.keep) und sind notwendig – ohne sie würden die
Serialisierung der Daten und die WebView-Brücke zu OpenSheetMusicDisplay brechen.

## Datenschutz

Die App erhebt keine Daten, fordert keine Berechtigungen an und verbindet sich nicht mit
dem Internet. Alle Noten bleiben im privaten Speicher der App auf dem Gerät. Details in
der [Datenschutzerklärung](https://workFLOw42.github.io/MeineNoten/docs/privacy-policy.html).

## Lizenzen

Diese App verwendet [OpenSheetMusicDisplay](https://opensheetmusicdisplay.org/)
(BSD-3-Clause) zur Darstellung von MusicXML.
