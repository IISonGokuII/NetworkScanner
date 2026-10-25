# LoopForge – Videos endlos verlängern

Android-App, die Videos vom Handy verlängert, indem sie einen Abschnitt nahtlos wiederholt.
Die App sucht den wiederkehrenden Ablauf **automatisch** und setzt Start- und Endpunkt des Loops
bildgenau – du musst nichts selbst schneiden.

**Fertige APK:** [`apk/LoopForge-1.0.apk`](apk/LoopForge-1.0.apk) (Android 10 oder neuer)

## Funktionen

- **Automatische Loop-Erkennung** – jedes Bild wird als Fingerabdruck (32×32 Helligkeit + Farbe)
  gespeichert und mit allen anderen verglichen. Gesucht wird die Stelle, an der der Sprung vom
  Loop-Ende zurück zum Anfang nicht von einem normalen Bildwechsel zu unterscheiden ist. Verglichen
  werden ganze Bildfolgen, damit auch die Bewegungsrichtung passt (ein Pendel, das nach links
  schwingt, wird nicht an eines geklebt, das nach rechts schwingt).
- **Perioden-Erkennung** – periodische Bewegungen (Schaukel, Wellen, Lüfter, Tanzschritt …) werden
  erkannt, z. B. „Wiederholung alle 1,60 s“, und Loops werden auf ganze Perioden geschnitten.
- **Nahtlos-Score** (0–100) für jeden Vorschlag, mehrere Alternativen (bester, längster, kürzester Loop).
- **Wiederholungs-Karte** – interaktive Ähnlichkeitsmatrix des Videos. Leuchtende Punkte sind nahtlose
  Übergänge; antippen wählt den Loop (lange drücken und ziehen zum Scrubben).
- **Feinschliff** – Filmstreifen mit ziehbaren Griffen, ±1-Frame-Tasten, live neu berechneter Score.
- **Nahtstellen-Lupe** mit Blink-Test: letztes und erstes Bild des Loops im Wechsel.
- **Endlos-Vorschau** mit Durchlauf-Zähler.
- **Länge frei wählbar** – Ziel-Länge (15 s bis 1 h) oder feste Anzahl Wiederholungen,
  optional mit Intro (Original bis zum Loop) und Outro (Original nach dem Loop).
- **Ton** bleibt erhalten, mit Mikro-Überblendungen an den Nahtstellen gegen Knackser.
- **Schneller Export** – nur ein einziger Loop-Durchlauf wird neu kodiert (Media3 Transformer, H.264),
  die Wiederholungen werden danach verlustfrei aneinandergehängt. Ein 10-Minuten-Video dauert
  dadurch kaum länger als ein 10-Sekunden-Video. HDR-Videos werden automatisch nach SDR umgewandelt.
- Ergebnis landet in der Galerie unter **Filme/LoopForge**; Teilen direkt aus der App.
- Videos können auch aus der Galerie per **Teilen → LoopForge** geöffnet werden.
- Läuft komplett offline, braucht keine Berechtigungen.

## Installation

1. `apk/LoopForge-1.0.apk` aufs Handy laden.
2. Öffnen und „Installation aus unbekannten Quellen“ für den Browser/Dateimanager erlauben.
3. LoopForge starten → **Video auswählen**.

Die APK ist mit einem Debug-Schlüssel signiert. Eine von GitHub Actions gebaute APK hat einen anderen
Schlüssel – zum Wechseln die alte Version vorher deinstallieren.

## Selbst bauen

```bash
cd VideoLooper
./gradlew testDebugUnitTest assembleRelease
# -> app/build/outputs/apk/release/app-release.apk
```

Benötigt JDK 17+ und das Android SDK (Platform 36). Der Workflow
`.github/workflows/loopforge.yml` baut die APK bei jedem Push auf `VideoLooper/**` und stellt sie
als Artefakt bereit.

## Aufbau

| Datei | Aufgabe |
|---|---|
| `analysis/FrameSampler.kt` | Dekodiert alle Frames per MediaCodec und liest die YUV-Ebenen direkt aus |
| `analysis/LoopEngine.kt` | Ähnlichkeitsmatrix, Loop-Suche, Perioden-Erkennung, Score (reines Kotlin, getestet) |
| `export/LoopExporter.kt` | Rendert Intro/Loop/Outro mit Media3 Transformer, speichert in der Galerie |
| `export/Mp4Concat.kt` | Hängt die kodierten Segmente ohne Neukodierung aneinander |
| `ui/…` | Jetpack-Compose-Oberfläche (Neon-Glas-Design) |
