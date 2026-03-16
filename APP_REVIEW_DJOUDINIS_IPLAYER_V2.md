# App-Review & Audit: Djoudini's IP Player v2

**Datum:** 2026-03-16
**Branch:** `claude/app-review-audit-4FFvP`
**Repository:** IISonGokuII/DjoudinisIPPlayer-v2

---

## 1. Zusammenfassung

**Djoudini's IP Player v2** ist eine ambitionierte, feature-reiche IPTV-Player-App fuer Android (Mobile + Fire TV / Android TV). Die App unterstuetzt Live-TV, VOD, Serien, EPG, Aufnahmen, Multi-View, VPN-Integration und Cloud-Upload von Aufnahmen. Die Codebasis ist durchdacht strukturiert und nutzt moderne Android-Technologien.

### Gesamtbewertung: 7.5 / 10

| Kategorie | Bewertung | Kommentar |
|---|---|---|
| Architektur | 8/10 | Saubere Schichttrennung, gute DI-Nutzung |
| Code-Qualitaet | 7/10 | Solide, einige Verbesserungsmoeglichkeiten |
| Sicherheit | 6/10 | Mehrere Punkte erfordern Aufmerksamkeit |
| Performance | 8/10 | Gute Optimierungen fuer Fire TV |
| UI/UX-Architektur | 8/10 | Dual Mobile/TV mit Compose |
| Test-Abdeckung | 6/10 | Vorhanden, aber lueckenhaft |
| Wartbarkeit | 7/10 | Gute Struktur, etwas Code-Duplikation |

---

## 2. Architektur (8/10)

### Staerken

- **Clean Architecture**: Klare Trennung in `data/`, `domain/`, `presentation/` und `di/` Schichten. Das Repository-Pattern mit Interfaces in `domain/repository/` und Implementierungen in `data/repository/` ist vorbildlich.
- **Hilt DI**: Korrekt konfiguriert mit `@HiltAndroidApp`, `@AndroidEntryPoint` und Modulen (`DatabaseModule`, `NetworkModule`, `RepositoryModule`). Singleton-Scoping ist angemessen.
- **Room Database**: 12 DAOs, gut organisierte Entities, ordentliche Migrationskette (v2-v11). Schema-Export ist aktiviert.
- **MVVM mit StateFlow**: ViewModels nutzen `MutableStateFlow` + `@Immutable` Data Classes fuer den UI-State. Das ist performanter als LiveData fuer Compose.
- **Dual-Platform (Mobile/TV)**: Elegante Loesung mit `isTvDevice`-Flag im `AppNavGraph`, das zwischen Mobile- und TV-Composables umschaltet.

### Schwaechen

- **AppNavGraph-Duplikation**: Jede Route hat ein `if (isTvDevice) { TvScreen() } else { MobileScreen() }`-Block. Bei 20+ Routen ist das viel Boilerplate. Eine Factory oder ein Wrapper-Composable wuerde das reduzieren.
- **SettingsScreen erhalt rohe Dependencies**: Die mobile `SettingsScreen` bekommt `appPreferences`, `playlistRepository` und `watchProgressRepository` als Parameter statt ueber einen ViewModel. Inkonsistent mit dem Rest der App.
- **Fehlende Use Cases**: Die Domain-Schicht hat nur Models und Repository-Interfaces, aber keine Use Cases / Interactors. Fuer die aktuelle Groesse OK, aber bei weiterem Wachstum wird Business-Logik in ViewModels und Repositories fragmentiert.

---

## 3. Code-Qualitaet (7/10)

### Staerken

- **Kotlin-Idiomatik**: Guter Einsatz von `sealed class`, `data class`, Extension Functions, Coroutines und Flow.
- **Coroutine-Nutzung**: `viewModelScope`, `withContext(Dispatchers.IO)`, `coroutineScope` fuer strukturierte Concurrency. `ensureActive()` im Parser fuer Cancellation-Support.
- **M3uParser**: Speicher-effizienter Streaming-Parser mit `BufferedReader`, Chunk-basierter Verarbeitung und Progress-Callbacks. Sehr gut.
- **Retry-Logik**: Exponentielles Backoff in `retryStreamFetch()` und `retryApiCall()` mit korrekter `CancellationException`-Weiterleitung.

### Schwaechen

- **PlayerViewModel ist zu gross** (~950 Zeilen): Enthaelt Channel-Zapping, Sleep-Timer, Auto-Play, Audio-Delay, Aspect-Ratio, Favorites, Track-Selection und Channel-Number-Input. Sollte in kleinere Komponenten aufgeteilt werden (z.B. `SleepTimerManager`, `ChannelZappingManager`).
- **PlaylistRepositoryImpl ist zu gross** (~850 Zeilen): Mischt Sync-Pipeline, EPG-Sync, CRUD und DTO-Mapping. Die Fetch-Methoden (`fetchLiveChannels`, `fetchVodItems`, `fetchSeriesItems`) sind fast identisch - hier fehlt eine generische Abstraktion.
- **Code-Kommentare mischen Deutsch und Englisch**: `// OPTIMIERUNG: Parallele API-Aufrufe` neben `// Batch insert for better performance`. Sollte vereinheitlicht werden.
- **Magic Strings**: Content-Types wie `"vod"`, `"series"`, `"channel"`, `"episode"` werden als rohe Strings in der Navigation verwendet statt als Enum-Konstanten.

---

## 4. Sicherheit (6/10) - WICHTIGSTE FINDINGS

### Kritisch

1. **`google-services.json` im Repository**: Die Datei enthaelt den Firebase API-Key (`AIzaSyCePHt5ODJciphi5aNfNjO-nFbPiWhzdVU`) und Project-IDs. Obwohl Firebase-API-Keys clientseitig sind und eingeschraenkt werden koennen, ist es Best Practice, diese Datei NICHT ins oeffentliche Repository zu committen. Sie fehlt in der `.gitignore`.
   - **Empfehlung**: `app/google-services.json` zur `.gitignore` hinzufuegen. Fuer CI/CD als Secret-Umgebungsvariable bereitstellen.

2. **Cleartext Traffic erlaubt**: `android:usesCleartextTraffic="true"` im Manifest. IPTV-Streams laufen haeufig ueber HTTP, aber das oeffnet die App fuer Man-in-the-Middle-Angriffe auf ALLE Verbindungen (inkl. API-Calls, Auth-Tokens).
   - **Empfehlung**: Statt global `usesCleartextTraffic="true"` eine `network_security_config.xml` mit Domain-spezifischen Ausnahmen verwenden.

3. **Xtream-Credentials in URLs**: Stream-URLs werden als `$serverUrl/live/$username/$password/$id.$ext` konstruiert. Credentials stehen im Klartext in der URL und werden in Room gespeichert.
   - **Empfehlung**: Das ist leider Xtream-API-Standard und nicht vermeidbar. Aber: Stelle sicher, dass diese URLs NICHT in Logs oder Crash-Reports auftauchen. Der `HttpLoggingInterceptor` mit Level `BASIC` loggt URLs - das sollte im Release-Build auf `NONE` stehen.

4. **OAuth-Tokens in DataStore**: Google Drive und OneDrive Access/Refresh-Tokens werden in `DataStore<Preferences>` gespeichert, das nicht verschluesselt ist.
   - **Empfehlung**: `EncryptedSharedPreferences` oder `androidx.security:security-crypto` fuer sensitive Tokens verwenden.

5. **WebDAV-Password im Klartext**: Das WebDAV-Passwort wird direkt in DataStore gespeichert ohne Verschluesselung.

### Mittel

6. **HttpLoggingInterceptor immer aktiv**: Auch im Release-Build wird `HttpLoggingInterceptor.Level.BASIC` verwendet. Das loggt Request-URLs, die bei Xtream die Credentials enthalten.
   - **Empfehlung**: Logging nur in Debug-Builds aktivieren: `if (BuildConfig.DEBUG) Level.BASIC else Level.NONE`.

7. **CrashHandler schreibt Crash-Reports auf internen Speicher**: Das ist OK, aber der Dialog zeigt den absoluten Dateipfad (`crashFile.absolutePath`). Das koennte internen Speicherort leaken.

8. **RecordingService User-Agent**: Verwendet `"VLC/3.0.20 LibVLC/3.0.20"` als User-Agent - das ist User-Agent-Spoofing. Nicht sicherheitsrelevant, aber potentiell problematisch.

---

## 5. Performance (8/10)

### Staerken

- **Paralleles Fetching**: `syncXtreamSelectedStreams()` nutzt `async/await` fuer gleichzeitige API-Aufrufe pro Kategorie. Sehr gut fuer grosse Playlists.
- **Chunk-basiertes M3U-Parsing**: 500 Items pro Chunk mit Progress-Callbacks. Vermeidet OOM bei grossen Playlists.
- **Coil ImageLoader**: Korrekt konfiguriert mit Memory-Cache (25%), Disk-Cache (100MB), Hardware-Bitmaps und deaktivierten Cache-Headers. Optimal fuer IPTV-Logos.
- **Room Batch-Inserts**: `insertAll()` statt einzelner Inserts. Performance-relevant bei tausenden Kanaelen.
- **OkHttp Connection-Pool**: 10 Connections, 5 Minuten Keep-Alive. Sinnvoll fuer IPTV-Server.
- **EPG-Refresh alle 60s**: Korrekt mit `viewModelScope.launch` und `delay()`, kein unnoetigeer Overhead.

### Schwaechen

- **AppNavGraph laedt alle Screen-Composables**: Compose Navigation compiliert alle Composables in der NavHost-Definition. Bei 20+ Screens koennte das die initiale Compose-Phase verlangsamen.
- **SplashScreen 3 Sekunden fest**: Die Splash-Duration ist hardcoded (`3000L`). Besser waere es, auf die tatsaechliche Initialisierung zu warten (WorkManager, Hilt, DB).
- **readTimeout 180 Sekunden**: Sehr hoch. Wenn ein Server nicht antwortet, blockiert das die Coroutine 3 Minuten lang.

---

## 6. UI/UX-Architektur (8/10)

### Staerken

- **Compose fuer Mobile + TV**: Separate Composables fuer Mobile (`presentation/ui/mobile/`) und TV (`presentation/ui/tv/`). TV-Screens nutzen `androidx.tv.material3` fuer D-PAD-Navigation und Focus-Management.
- **Fokussierbare Komponenten**: `FocusableCard`, `FocusableTextField` - wichtig fuer Fire TV UX.
- **Theme-System**: `DjoudinisTheme` mit Dark/Light/System-Modi. Persistiert in DataStore.
- **Channel-Zapping**: D-PAD Up/Down fuer Kanalwechsel mit Wrap-Around. Channel-Number-Input fuer direkte Kanalnummer. Sehr TV-freundlich.
- **Mehrsprachigkeit**: Strings in DE, FR, TR und EN. Gute Internationalisierung.

### Schwaechen

- **PlayerScreen nur fuer Mobile**: Es gibt keinen `TvPlayerScreen` - der `PlayerScreen` wird auf beiden Plattformen verwendet. Das koennte auf TV suboptimal sein (Overlay-Controls, Touch-Gesten).
- **Onboarding nur fuer neue Nutzer**: Kein Weg, das Onboarding erneut aufzurufen oder die Playlist-Quelle zu wechseln ohne Reset.

---

## 7. Test-Abdeckung (6/10)

### Vorhanden
- **Unit Tests**: 14 Test-Dateien fuer ViewModels, Logic-Klassen und Entities. Gut abgedeckt:
  - `PlayerViewModelTest`, `PlayerPlaybackLogicTest`, `PlayerContentStateFactoryTest`
  - `ConferenceEventClassifierTest`, `ConferencePriorityRulesTest`
  - `RecordingActionLogicTest`, `RecordingsListLogicTest`
  - `ContentListSortLogicTest`, `VpnPairingPackageCodecTest`
- **Instrumented Tests**: 4 Tests fuer `SplashActivity` (Loading, Recreate, ShareIntent, basic).

### Fehlend
- **Keine Repository-Tests**: `PlaylistRepositoryImpl`, `ChannelRepositoryImpl` sind komplex und untestet.
- **Keine DAO-Tests**: Room-DAOs sollten mit In-Memory-DB getestet werden.
- **Keine Parser-Tests**: `M3uParser` und `XmltvParser` sind kritische Komponenten ohne Unit Tests.
- **Keine Network-Tests**: Keine Mocks fuer `XtreamApi`-Aufrufe.
- **Keine UI-Tests**: Keine Compose-UI-Tests fuer Screens.

---

## 8. Build & CI (7/10)

### Staerken
- **GitHub Actions Workflow**: `build-apk.yml` fuer automatisierte APK-Builds.
- **ProGuard korrekt konfiguriert**: Room, Moshi, Retrofit, Media3 und App-Entities werden behalten.
- **Version Catalog**: `libs.versions.toml` mit zentraler Versionsverwaltung.
- **Release-Signing**: Conditional Signing Config aus `local.properties`. Sicher.

### Schwaechen
- **Keine Lint-Checks in CI**: `abortOnError = true` ist gesetzt, aber unklar ob CI den Lint-Step ausfuehrt.
- **Viele `.bat`-Push-Scripts im Root**: 20+ `push_*.bat`-Dateien deuten auf manuellen Deployment-Prozess hin. Sollten durch CI/CD ersetzt werden.
- **Doppelte Serialisierung**: Sowohl Moshi als auch Kotlinx Serialization sind als Dependencies vorhanden. Eine davon ist ueberfluessig.

---

## 9. Spezifische Empfehlungen

### Sofort umsetzen (Quick Wins)

1. **`google-services.json` aus Git entfernen und zur `.gitignore` hinzufuegen**
2. **HttpLoggingInterceptor im Release-Build deaktivieren**:
   ```kotlin
   .addInterceptor(
       HttpLoggingInterceptor().apply {
           level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
                   else HttpLoggingInterceptor.Level.NONE
       }
   )
   ```
3. **`network_security_config.xml`** erstellen statt `usesCleartextTraffic="true"`
4. **Push-Batch-Scripts entfernen** und durch CI/CD ersetzen

### Mittelfristig

5. **PlayerViewModel aufteilen**: Sleep-Timer, Channel-Zapping, Auto-Play in separate Manager-Klassen extrahieren
6. **AppNavGraph vereinfachen**: Factory-Pattern fuer TV/Mobile Screen-Auswahl
7. **M3uParser und XmltvParser testen**: Unit Tests mit Sample-Dateien
8. **EncryptedDataStore fuer Tokens**: OAuth-Tokens und WebDAV-Credentials verschluesseln
9. **Moshi ODER Kotlinx Serialization**: Eine der beiden entfernen

### Langfristig

10. **Modularisierung**: Die App ist gross genug fuer Feature-Module (`:player`, `:vpn`, `:recording`)
11. **Domain Use Cases**: Business-Logik aus ViewModels/Repositories in Use Cases extrahieren
12. **Compose Navigation Type-Safety**: Kotlin 2.0 Compose Navigation mit type-safe Routes

---

## 10. Besondere Highlights

Die App hat einige beeindruckende Features, die ueber einen Standard-IPTV-Player hinausgehen:

- **Conference-Modus**: Automatischer Kanalwechsel basierend auf Fussball-Spielzeiten mit football-data.org API-Integration. Einzigartig.
- **VPN-Integration**: WireGuard-Tunnel direkt in der App mit Boot-Receiver, Kill-Switch und QR-Code-Pairing zwischen Mobile und TV.
- **Cloud-Aufnahme-Upload**: Automatischer Upload von Aufnahmen zu Google Drive, OneDrive oder WebDAV.
- **Multi-View**: Mehrere Streams gleichzeitig anzeigen.
- **Catchup/Timeshift**: Unterstuetzung fuer IPTV-Catchup mit konfigurierbaren Tagen.
- **EPG-Grid**: Elektronische Programmzeitschrift mit tvg-id Normalisierung.

---

## Fazit

Djoudini's IP Player v2 ist eine **professionell aufgebaute, feature-reiche IPTV-App** mit solider Architektur und guter Performance-Optimierung fuer Fire TV. Die wichtigsten Verbesserungsbereiche sind **Sicherheit** (Credentials-Handling, Logging, verschluesselte Speicherung) und **Test-Abdeckung** (insbesondere Parser und Repositories). Die Codebasis ist wartbar und erweiterbar, wuerde aber von einer Aufteilung der groesseren Klassen und Modularisierung profitieren.
