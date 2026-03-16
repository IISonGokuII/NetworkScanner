# Fire TV Sleep Timer

Sleep Timer App fuer Amazon Fire TV Stick. Schaltet Fire TV **und** den Fernseher (via HDMI-CEC) nach einer einstellbaren Zeit automatisch aus.

## Features

- **Timer-Auswahl**: 15 / 30 / 45 / 60 / 90 / 120 Minuten
- **Hintergrund-Service**: Timer laeuft weiter, auch wenn du andere Apps nutzt
- **Overlay-Warnung**: 5 Minuten vor Ablauf erscheint eine Einblendung mit der Option zu verlaengern
- **TV-Abschaltung**: Schaltet Fire TV in den Schlafmodus und den TV via HDMI-CEC aus
- **D-Pad Navigation**: Komplett mit der Fire TV Fernbedienung bedienbar
- **Notification**: Zeigt verbleibende Zeit + Stopp/Verlaengern-Buttons in der Benachrichtigung

## Wie es funktioniert

1. App starten und Timer-Dauer mit der Fernbedienung auswaehlen
2. Timer laeuft im Hintergrund als Foreground Service
3. 5 Minuten vor Ablauf: Overlay-Warnung mit Option "+15 Min verlaengern"
4. Bei Ablauf: Fire TV geht in den Schlafmodus, TV wird via HDMI-CEC ausgeschaltet

## Technologie

- **Kotlin** + **Jetpack Compose** (moderne deklarative UI)
- **Foreground Service** mit WakeLock fuer zuverlaessigen Countdown
- **System Alert Window** fuer Overlay ueber anderen Apps
- **HDMI-CEC** via `input keyevent KEYCODE_SLEEP` zum Ausschalten

## Setup

### In Android Studio / AndroidIDE

1. Projekt oeffnen
2. Gradle Sync ausfuehren
3. Auf Fire TV Stick deployen (USB Debugging aktivieren!)

### Berechtigungen

Die App benoetigt:
- `FOREGROUND_SERVICE` - Timer im Hintergrund
- `SYSTEM_ALERT_WINDOW` - Overlay-Warnung
- `WAKE_LOCK` - Timer bleibt aktiv
- `POST_NOTIFICATIONS` - Timer-Benachrichtigung

### Fire TV Einstellungen

Fuer automatische TV-Abschaltung muss **HDMI-CEC** am Fernseher aktiviert sein:
- Samsung: **Anynet+**
- LG: **SimpLink**
- Sony: **BRAVIA Sync**
- Philips: **EasyLink**

## Projektstruktur

```
app/src/main/java/com/sleeptimer/firetv/
  MainActivity.kt          -- Compose UI (Timer-Auswahl + Countdown)
  TimerState.kt            -- Timer-Zustand und Presets
  DeviceController.kt      -- Fire TV + TV Abschaltung (HDMI-CEC)
  service/
    SleepTimerService.kt   -- Foreground Service mit Countdown
  overlay/
    OverlayService.kt      -- Overlay-Warnung (letzte 5 Min)
  receiver/
    TimerActionReceiver.kt -- Notification-Actions
    BootReceiver.kt        -- Boot-Empfaenger
  ui/theme/
    Color.kt               -- Farbdefinitionen
    Theme.kt               -- Material3 Dark Theme
```
