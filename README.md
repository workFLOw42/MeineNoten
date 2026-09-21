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
* **Freihändiges Blättern** – Bluetooth-Pedal, Wischen, Tipp-Zonen oder Leiste unten
* **Setlists** – Reihenfolge vorbereiten, am Liedende direkt ins nächste Stück
* **Bühnentauglich** – Display bleibt an, letzte Seite wird pro Lied gemerkt
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

Während der Anzeige stehen vier Wege parallel zur Verfügung – alle gleichzeitig aktiv,
nichts muss eingestellt werden:

| Eingabe | Vorwärts | Rückwärts |
|---|---|---|
| Pedal / Tastatur | `Bild ab`, `→`, `↓`, `Leertaste`, `Enter`, `Lauter`-/`Leiser`-Taste, Medientasten | `Bild auf`, `←`, `↑`, Medientaste zurück |
| Wischen | nach links | nach rechts |
| Tippen | rechtes Bildschirmdrittel | linkes Bildschirmdrittel |
| Leiste unten | Pfeil rechts | Pfeil links |

Ein **Tipp in die Bildmitte** blendet die Bedienleisten ein und aus. Jeder Seitenwechsel
wird mit einem kurzen grünen Aufblitzen bestätigt – so erkennt man den Pedaltritt auch
bei zwei ähnlich aussehenden Seiten, ohne den Blick von den Noten zu nehmen.

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
Symbol rechts in der Songliste hinzufügen. Listen erscheinen nach Datum sortiert, das
nächste Ereignis oben.

Wird ein Lied **aus einer Setlist heraus** geöffnet, blättert man am Ende der letzten
Seite direkt in das nächste Stück – das Pedal funktioniert also über die gesamte Setlist
hinweg. Aus der Songliste geöffnet bleibt die Anzeige an den Liedgrenzen stehen.

## Technik

| | |
|---|---|
| Sprache | Kotlin |
| Oberfläche | Jetpack Compose, Material 3 |
| Navigation | Navigation 3, adaptives Layout |
| PDF | `PdfRenderer` (Framework, keine externe Bibliothek) |
| MusicXML | OpenSheetMusicDisplay im WebView, offline als Asset |
| Speicherung | JSON über kotlinx.serialization |
| `minSdk` | 26 (Android 8.0) |
| `targetSdk` | 36 |

Entwickelt und geprüft für ein 8-Zoll-Tablet (Samsung Galaxy Tab S6 Lite), läuft aber
auf Telefonen und größeren Tablets ebenso – das Layout wechselt je nach Fenstergröße
zwischen Navigationsleiste und Navigationsschiene.

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
