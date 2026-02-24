# NetScanner Pro — Netzwerk-Scanner App

## 12 Funktionen

1. **Geräte-Info** — IP-Adresse, MAC, WLAN-Details, öffentliche IP, Gateway, DNS
2. **WLAN Scanner** — Alle WLANs in Reichweite mit Signalstärke, Frequenz & Sicherheit
3. **Port Scanner** — Offene Ports auf beliebigen Hosts scannen (mit Service-Erkennung)
4. **Ping** — Erreichbarkeit von Hosts testen mit Statistiken
5. **Traceroute** — Netzwerk-Route zu einem Ziel verfolgen
6. **DNS Lookup** — DNS-Auflösung + Reverse DNS
7. **Subnetz Scanner** — Alle Geräte im lokalen Netzwerk finden (IP, MAC, Hostname)
8. **Wake on LAN** — PCs per Magic Packet aufwecken
9. **Speed Test** — Download-Geschwindigkeit messen mit Live-Anzeige
10. **HTTP Header** — HTTP-Response-Header von Websites analysieren
11. **Whois Lookup** — Domain-Registrierungsinformationen abfragen
12. **Verbindungs-Monitor** — Netzwerk-Verbindungsdetails überwachen

## Setup in AndroidIDE

### Methode 1: Neues Projekt erstellen und Dateien ersetzen
1. Erstelle ein neues Projekt in AndroidIDE mit den Einstellungen:
   - Name: NetworkScanner
   - Package: com.mycompany.networkscanner
   - Language: Kotlin
   - Min SDK: API 29
   - Template: Xml Activity
   - Build Config: Kotlin DSL
2. Entpacke die ZIP-Datei
3. Ersetze die generierten Dateien mit den Dateien aus der ZIP

### Methode 2: ZIP direkt verwenden
1. Entpacke die ZIP in dein AndroidIDE Projekte-Verzeichnis
2. Öffne das Projekt in AndroidIDE
3. Sync Gradle und baue die App

### Wichtige Dateien:
```
app/build.gradle.kts          — Dependencies
app/src/main/AndroidManifest.xml — Berechtigungen
app/src/main/java/.../         — Kotlin-Quellcode
app/src/main/res/layout/       — UI-Layouts
app/src/main/res/values/       — Farben, Strings, Themes
app/src/main/res/menu/         — Navigation
```

## Berechtigungen
- INTERNET — Netzwerk-Zugriff
- ACCESS_NETWORK_STATE — Netzwerk-Status
- ACCESS_WIFI_STATE — WLAN-Informationen
- CHANGE_WIFI_STATE — WLAN-Scan
- ACCESS_FINE_LOCATION — WLAN-Scan (Android 10+)
