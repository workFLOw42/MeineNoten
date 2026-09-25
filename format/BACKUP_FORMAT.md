# Sicherungsformat von Meine Noten (Format-Version 1)

Dieses Dokument beschreibt den verbindlichen Aufbau von Sicherungs- und
Austausch-ZIPs für die Android-App sowie die Web-App von *Meine Noten*.

Beide Plattformen lesen und schreiben exakt dieses Format. Änderungen am Datenmodell,
die nicht abwärtskompatibel sind, erhöhen die `formatVersion` im `manifest.json`.

---

## 1. Verzeichnisstruktur im ZIP

```text
[Datum]_[Art]_[Gerät]_[Name].zip
├── manifest.json      Metadaten der Sicherung und Dateiprüfsummen
├── songs.json         Liste aller gesicherten Lieder
├── setlists.json      Liste aller gesicherten Setlists
├── settings.json      App-Einstellungen (nur bei type = "KOMPLETT")
└── files/             Ordner für Notendateien
    ├── <song.id>.pdf
    ├── <song.id>.xml
    ├── <song.id>.musicxml
    └── <song.id>.mxl
```

- **Keine Kompression für PDFs:** Dateien im Ordner `files/` werden im ZIP nur gespeichert (`STORED`), nicht komprimiert (`DEFLATED`), um Rechenzeit und Speicher beim Export/Import zu sparen.
- **Relative Dateipfade:** In `songs.json` verweist `fileUri` auf den relativen Pfad im ZIP, z. B. `files/uuid-1234.pdf`.

---

## 2. Dateien im Detail

### `manifest.json` (`BackupManifest`)

```json
{
  "formatVersion": 1,
  "appVersion": "1.6.2",
  "type": "KOMPLETT",
  "createdAt": 1740000000000,
  "deviceName": "Galaxy Tab S9",
  "authorId": "user-uuid-abc",
  "authorName": "Florian",
  "title": "Komplett",
  "fileHashes": {
    "files/uuid-1234.pdf": "ba7816bf8f01cfea..."
  }
}
```

- `formatVersion`: Ganzzahl (`1`).
- `type`: `"KOMPLETT"` (Vollständige Sicherung) oder `"SETLIST"` (Teilsicherung einer Setlist).
- `createdAt`: Epoch-Millisekunden als Zahl.
- `authorId`: UUID des Autors (wird beim Einlesen einer Komplettsicherung auf Wunsch übernommen).
- `fileHashes`: Map von relativem Pfad zu SHA-256 Prüfsumme (Hex-String, Kleinbuchstaben).

### `songs.json` (Liste von `Song`)

Jedes Element der Liste enthält:

```json
{
  "id": "uuid-1234",
  "title": "Großer Gott, wir loben dich",
  "artist": "Ignaz Franz",
  "version": "GL 380",
  "fileUri": "files/uuid-1234.pdf",
  "sourceType": "PDF",
  "genre": "Lied",
  "bpm": 120,
  "timeSignature": "4/4",
  "totalBars": 0,
  "pageViews": {
    "0": {
      "scale": 1.5,
      "offsetXRatio": 0.0,
      "offsetYRatio": 0.0
    }
  },
  "legacyNotes": "",
  "songNotes": [
    {
      "authorId": "user-uuid-abc",
      "authorName": "Florian",
      "text": "Capo 2",
      "editedAt": 1740000000000
    }
  ],
  "lyrics": "",
  "lastOpenedAt": 1740000000000,
  "fileHash": "ba7816bf8f01cfea..."
}
```

- `sourceType`: `"PDF"`, `"MUSIC_XML"`, oder `"TEXT"`.
- `pageViews`: JSON-Objekt mit Seitenindex als String-Schlüssel (`"0"`, `"1"`).
- `songNotes`: Liste von Notizen pro Person.
- **Unbekannte Felder:** Reader (egal ob Android oder Web) müssen unbekannte Felder beim Deserialisieren ignorieren und beim erneuten Schreiben unverändert mitnehmen.

### `setlists.json` (Liste von `Setlist`)

```json
[
  {
    "id": "setlist-uuid-1",
    "title": "Gottesdienst am Sonntag",
    "date": "2026-03-05",
    "songIds": ["uuid-1234"],
    "notes": "Mit Orgel",
    "lastSongId": null,
    "lastPage": 0,
    "lastPlayedAt": 0
  }
]
```

### `settings.json` (`SerializableAppSettings`, nur bei `KOMPLETT`)

Enthält Benutzereinstellungen (Tippzonen, Design, Ansichtsoptionen, aber *nicht* `userId` oder `userName`).

---

## 3. Kompatibilitätsregeln

1. **UTF-8:** Alle JSON-Dateien sind zwingend in UTF-8 kodiert.
2. **Fehlende Felder:** Beim Einlesen fehlende Felder werden mit sinnvollen Standardwerten belegt (Kotlin/TypeScript Standard constructors).
3. **Alte Einzelnotizen:** Ältere Sicherungen mit dem Feld `notes` statt `songNotes` werden beim Einlesen automatisch als eigene Notiz interpretiert (`legacyNotes`).
