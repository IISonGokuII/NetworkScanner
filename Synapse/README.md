# Synapse – Gehirnjogging ohne Werbung

Kostenlose Android-App für Kopfrechnen, Logik, Gedächtnis, Fokus, Raumdenken und Sprache.
**Keine Werbung, kein Abo, kein Konto – und keine Internet-Berechtigung.** Alles läuft offline,
alle Daten bleiben auf dem Handy (auch das Android-Cloud-Backup ist abgeschaltet).

![Synapse – Vorschau](docs/preview.png)

## APK herunterladen

Bei jedem Push, der etwas unter `Synapse/` ändert, baut GitHub Actions die App und veröffentlicht sie als **Release**:

1. Auf GitHub rechts bei **Releases** die neueste Version „Synapse 2.0.x“ öffnen.
2. Die Datei `Synapse-2.0.x.apk` herunterladen und auf dem Handy installieren (Installation aus unbekannten Quellen erlauben).

Neue Versionen lassen sich einfach **über die alte installieren** – der Fortschritt bleibt erhalten.
Die Versionsnummer steigt automatisch mit jedem Build.

## Was drin ist

### 21 Spiele in 6 Bereichen

| Bereich | Spiele |
|---|---|
| Rechnen | Blitzrechnen · Kettenrechnen · Zielzahl · Wochentag · Schätzen |
| Logik | Zahlenfolgen · Muster-Matrix · Schlussfolgern · Waage · Mini-Sudoku |
| Gedächtnis | N-Back (bis Dual 4-Back) · Corsi-Pfad · Zahlen merken · Namen merken · Einkaufsliste |
| Fokus | Farbwort (Stroop) · Schulte-Tabelle |
| Raumdenken | Würfel-Rotation (3D) · Weg im Kopf |
| Sprache | Wortsalat · Was passt nicht? |

- **50 Level pro Spiel**, die sich anpassen – zwischen den Runden und bei vielen Spielen schon **innerhalb einer Runde**
  (3 richtig in Folge → schwerer, 2 falsch → leichter).
- In den ersten Runden eines Spiels geht es doppelt so schnell nach oben, damit niemand lange unterfordert ist.
- Alle Aufgaben werden vom Programm erzeugt: 513 Wörter, 48 Wortkategorien, 24 Arten von Zahlenfolgen.
  Zuletzt gesehene Wörter werden vermieden.

### Einstufung & Leistungstest
- **Einstufungstest (5 Min.)**: Die Schwierigkeit passt sich jeder Antwort an. Danach startet jedes Spiel im passenden Level.
- **Leistungstest (monatlich)**: immer dieselbe Schwierigkeit – der ehrlichste Nachweis, ob du besser wirst.
  Er erzeugt einen Verlauf über die Monate und einen Netzdiagramm-Vergleich „erster Test ↔ heute“.

### Lernen
- **13 Rechentricks** mit Erklärung, Beispiel und Übung (× 11, × 25, Quadrate mit 5 am Ende, Zahlen nahe 100,
  Teilbarkeit …) – „gemeistert“, wenn alle 10 Aufgaben stimmen und du schnell genug bist.
- **Kalender-Trick (Doomsday)**: den Wochentag jedes Datums im Kopf bestimmen.
- **Merktechniken**: Bündeln, Geschichtenmethode, Namen mit Merkmalen verknüpfen.
- **Schwächen-Analyse**: Die App merkt sich, welche Aufgabentypen dir schwerfallen (z. B. Division, Fibonacci-Folgen),
  und bietet gezieltes Üben an.

### Fortschritt
- **Gehirn-Index** (0–1000) mit Veränderung über 7 Tage, 30 Tage und seit Start.
  Neue Spiele fließen erst ab der zweiten Runde ein, damit der Index nicht durch einen Testlauf fällt.
- **Verlaufskurve** gesamt oder pro Bereich (Finger auf die Kurve legen zeigt die Tageswerte).
- **Gehirnprofil** als Netzdiagramm (jetzt vs. vor 30 Tagen) und ein **Trainingskalender**.
- **Wochenziel** (z. B. 5 Tage) auf dem Startbildschirm und ein **Wochenrückblick** mit Vergleich zur Vorwoche.
- Pro Spiel Kurven für Level, Trefferquote und Antwortzeit; dazu Highlights und **33 Erfolge**.

### Oberfläche & Alltag
- **Neuronen-Netz** als Startbildschirm: Jeder Bereich leuchtet, je mehr du ihn trainierst.
  In der Mitte liegt der Ring fürs Tagestraining (3 oder 5 Spiele).
- Modi: Training, Sprint (60 Sekunden), Zen (ohne Zeitdruck), dazu ein **Duell** zu zweit an einem Handy.
- **Unterbrochene Runden** (Anruf, App geschlossen) lassen sich fortsetzen.
- **Profile** für mehrere Personen auf einem Handy, jedes mit eigenem Fortschritt.
- **Barrierefreiheit**:
  - Farbschwäche-Modus (Farben mit einem Kontrast-Prüfskript für Rot-Grün-Schwäche geprüft)
  - „Bewegung reduzieren“
  - Beschriftungen für Screenreader
  - Tasten wachsen mit großer Schrift mit
- **Erinnerungen** (täglich, nur wenn du noch nicht trainiert hast) und eine Sonntags-Zusammenfassung.
  Die Erinnerungen kommen auch im Stromsparmodus.
- **Sicherung**: per Hand exportieren und importieren oder **automatisch täglich** in eine Datei deiner Wahl.
- Widget „Aufgabe des Tages“, dunkles und helles Design.

## Eigener, privater Signierschlüssel (optional)

Standardmäßig wird mit dem Schlüssel aus dem Repo (`app/synapse-debug.keystore`) signiert. Ist das Repo öffentlich,
könnte theoretisch jemand eine eigene APK als „Update“ signieren. Mit einem privaten Schlüssel passiert das nicht:

1. Schlüssel erzeugen (am PC, z. B. mit Android Studio oder `keytool`):
   `keytool -genkeypair -keystore synapse-release.keystore -alias synapse -keyalg RSA -keysize 2048 -validity 10000`
2. Datei in Base64 umwandeln: `base64 -w0 synapse-release.keystore`
3. Auf GitHub unter **Settings → Secrets and variables → Actions** vier Secrets anlegen:
   - `SYNAPSE_KEYSTORE_BASE64` (der Base64-Text)
   - `SYNAPSE_KEYSTORE_PASSWORD`
   - `SYNAPSE_KEY_ALIAS` (`synapse`)
   - `SYNAPSE_KEY_PASSWORD`

**Wichtig:** Ein neuer Schlüssel bedeutet, dass Android die neue APK nicht über die alte installieren lässt.
Deshalb erst in der App unter *Mehr → Sichern* eine Sicherung anlegen, dann die alte App deinstallieren,
die neue installieren und die Sicherung wiederherstellen.

## Selbst bauen

```bash
cd Synapse
./gradlew testDebugUnitTest   # 48 Tests: alle Generatoren, Statistik, Tricks, Fortsetzen
./gradlew assembleRelease     # APK: app/build/outputs/apk/release/app-release.apk
```

Technik: Kotlin 2.0, Jetpack Compose (Material 3), SQLite (Version 2 mit automatischer Migration), Gradle 8.9, minSdk 29.

## Ehrlicher Hinweis

Gehirntraining macht dich vor allem in den geübten Aufgaben besser. Ob das auf den Alltag übertragbar ist,
ist wissenschaftlich umstritten. Gut belegt hilft:
- Abwechslung
- Aufgaben am Rand des eigenen Könnens
- Regelmäßigkeit
- Schlaf und Bewegung

Deshalb setzt die App auf viele verschiedene Spiele, echte Techniken zum Lernen und einen ehrlichen Leistungstest.
Ein „Gehirnalter“ verspricht sie nicht.
