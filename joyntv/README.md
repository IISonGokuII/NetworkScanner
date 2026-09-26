# Joyn Österreich – Fire TV App

Kleine Android-TV-App für den Amazon Fire TV Stick, die direkt **www.joyn.at**
(die österreichische Joyn-Seite) im Vollbild öffnet – mit Mauszeiger für die
Fernbedienung.

## Installation auf dem Fire TV Stick

1. Fire TV: **Einstellungen → Mein Fire TV → Entwickleroptionen →
   Apps unbekannter Herkunft** aktivieren (bei neueren Geräten: für die App
   „Downloader“ erlauben).
   *Entwickleroptionen nicht sichtbar?* Einstellungen → Mein Fire TV → Info →
   7× auf den Gerätenamen klicken.
2. Aus dem Amazon Appstore die App **Downloader** installieren.
3. In Downloader diese Adresse eingeben:
   `https://github.com/IISonGokuII/NetworkScanner/releases/download/joyntv-latest/JoynAT-FireTV.apk`
4. Installieren → die App erscheint als **„Joyn Österreich“** unter „Deine Apps“.

Die APK wird von GitHub Actions (`.github/workflows/joyntv.yml`) automatisch
gebaut und im Release `joyntv-latest` veröffentlicht.

## Bedienung mit der Fernbedienung

Die App bedient sich wie eine normale TV-App: Das Steuerkreuz springt von
Kachel zu Kachel bzw. Menüpunkt zu Menüpunkt, das gewählte Element hat einen
weißen Rahmen.

| Taste | Funktion |
|---|---|
| Steuerkreuz | Zwischen Kacheln, Buttons und Menüpunkten springen (Reihen scrollen mit) |
| OK | Auswählen / Öffnen |
| Zurück | Vorherige Seite (2× auf der Startseite = Beenden) |
| Menü (≡) | Schnellmenü: Startseite, Suche, Live-TV, Serien, Filme, Neu laden, Mauszeiger, Beenden |
| ⏪ / ⏩ | Außerhalb eines Videos: Seite nach oben / unten |
| OK **lange drücken** | Mauszeiger ein/aus – Notlösung für Stellen, die sich per Steuerkreuz nicht erreichen lassen |

**Beim Video** (erkannt, sobald ein Video den Großteil des Bildes füllt):

| Taste | Funktion |
|---|---|
| OK oder Play/Pause | Pause / weiter |
| ◀ / ▶ | 10 s zurück / vor (gedrückt halten = schneller, bis 60 s) |
| ⏪ / ⏩ | 30 s zurück / vor |
| ▲ / ▼ | Player-Knöpfe (Untertitel, Folgen, Vollbild …) auswählen |
| Zurück | Von den Player-Knöpfen zurück zum Video bzw. Vollbild verlassen |

Beim Spulen und Pausieren erscheint unten eine Zeitleiste.

**Suche:** Menü → Suche, der Fokus liegt direkt im Suchfeld – OK drücken,
dann erscheint die Fire-TV-Tastatur.

## Hinweise

- Die App ist ein Browser-Wrapper (WebView) für joyn.at, kein offizieller
  Joyn-Client. Sie gibt sich als Desktop-Chrome aus, damit joyn.at den
  vollwertigen Web-Player liefert.
- Joyn-Streams sind DRM-geschützt (Widevine). Die App erlaubt das der Seite;
  ob ein Stream abspielt, hängt vom Fire-TV-Modell und der WebView-Version ab.
- Die Inhalte von joyn.at sind nur in Österreich abrufbar – die App ändert
  nichts am Standort.

## Selbst bauen

```bash
gradle :joyntv:assembleRelease
# -> joyntv/build/outputs/apk/release/joyntv-release.apk
```
