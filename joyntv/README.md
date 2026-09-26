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

## Bedienung

| Taste | Funktion |
|---|---|
| Steuerkreuz | Mauszeiger bewegen (am Bildrand wird gescrollt) |
| OK | Klicken |
| Zurück | Vorherige Seite / Vollbild verlassen (2× auf Startseite = Beenden) |
| Menü (≡) | Startseite, Neu laden, Hilfe, Beenden |
| Play/Pause | Video starten/pausieren |
| ⏪ / ⏩ | ±30 s im Video, sonst Seite scrollen |
| Im Video-Vollbild: ◀ / ▶ | ±10 s |

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
