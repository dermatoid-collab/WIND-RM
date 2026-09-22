# WIND-RM

App Android nativa (Kotlin + Jetpack Compose) che porta lungo un percorso ciclistico le
funzionalità principali di app come **MyWindSock** / **Epic Ride Weather**: importi un
percorso, imposti orario di partenza e velocità media, e ottieni meteo, vento, qualità
dell'aria e luce diurna previsti punto per punto lungo tutto il tragitto.

## Funzionalità

- **Importazione percorsi**
  - da file **GPX** locale (`Storage Access Framework`, nessun permesso richiesto)
  - da **Strava** (OAuth2, richiede le tue credenziali API — vedi sotto)
- **Dettaglio percorso**: mappa OpenStreetMap con il tracciato, scelta di orario di partenza
  (ora / pianificato) e velocità media (precompilata dal passo reale se il GPX/Strava contiene
  i timestamp)
- **Previsione lungo il percorso**: il tempo di percorrenza è stimato punto per punto e per
  ciascuno viene interrogato il meteo orario, con:
  - Temperatura e percepita
  - Probabilità/intensità di precipitazione e copertura nuvolosa
  - Vento e raffiche, più una mappa con **frecce di direzione del vento** lungo il tracciato
  - Altimetria (dislivello, distanza, velocità)
  - Luce diurna e indice UV (alba/tramonto, alba/tramonto civile)
  - Qualità dell'aria (AQI europeo, PM2.5, PM10, ozono)
  - Umidità e punto di rugiada
  - Slider per "scorrere" il tempo e vedere i valori istantanei nei quadranti in alto
  - Condivisione di un riepilogo testuale della previsione

## Stack tecnico

- **UI**: Jetpack Compose + Material 3, grafici e quadranti disegnati a mano con `Canvas`
  (nessuna libreria di charting esterna)
- **Mappa**: [osmdroid](https://github.com/osmdroid/osmdroid) (tile OpenStreetMap, nessuna API key)
- **Meteo/qualità aria**: [Open-Meteo](https://open-meteo.com/) (gratuito, nessuna API key)
- **Rete**: Retrofit + kotlinx.serialization + OkHttp
- **Persistenza**: Room (percorsi salvati), DataStore Preferences (token Strava)
- **Navigazione**: Navigation Compose
- **DI**: nessun framework (Hilt/Koin) — un semplice `AppContainer` scritto a mano
  ([`di/AppContainer.kt`](app/src/main/java/com/windrm/app/di/AppContainer.kt)), adeguato
  alle dimensioni del progetto

## Struttura del progetto

```
app/src/main/java/com/windrm/app/
├── model/           # Route, RoutePoint, WeatherPoint, AirQualityPoint, RouteForecastResult
├── gpx/              GpxParser (XmlPullParser, no dipendenze esterne)
├── domain/           RouteSampler, ArrivalTimeCalculator, HourlySeries (interpolazione), GeoUtils
├── db/                Room: RouteEntity, RouteDao, AppDatabase
├── remote/
│   ├── openmeteo/     OpenMeteoApi + DTO
│   └── strava/        StravaApi, StravaAuthManager (OAuth2), StravaTokenStore
├── repository/        RouteRepository, WeatherRepository, StravaRepository
├── di/                AppContainer
└── ui/
    ├── routes/        Elenco percorsi (tab Recenti / Strava)
    ├── routedetail/   Dettaglio percorso: mappa, orario, velocità
    ├── forecast/       Schermata previsione (grafici, mappa vento, slider)
    ├── components/     Grafici e quadranti riutilizzabili, mappa con overlay vento
    └── theme/          Tema Material 3
```

## Come ottenere un APK

### Opzione A — GitHub Actions (nessuna installazione necessaria)

Il workflow [`.github/workflows/build-apk.yml`](.github/workflows/build-apk.yml) compila un
APK debug ad ogni push (o manualmente da **Actions → Build APK → Run workflow**), usando i
runner Ubuntu di GitHub che hanno già l'Android SDK preinstallato:

1. Vai sulla tab **Actions** del repository.
2. Apri l'esecuzione più recente di **Build APK** (o lancene una nuova con "Run workflow").
3. A build completata, scarica l'artifact **windrm-debug-apk** in fondo alla pagina: contiene
   `app-debug.apk`, installabile direttamente su un telefono Android (serve abilitare
   "Origini sconosciute"/"Installa app sconosciute" per l'app che lo apre).

Per far funzionare anche **"Connetti Strava"** nell'APK compilato da GitHub, aggiungi due
*repository secrets* (Settings → Secrets and variables → Actions) chiamati
`STRAVA_CLIENT_ID` e `STRAVA_CLIENT_SECRET`: il workflow li scrive da solo in
`local.properties` prima della build. Senza questi secret l'APK si compila comunque,
semplicemente la tab Strava resterà "non configurata".

### Opzione B — Android Studio

1. Apri la cartella del progetto con **Android Studio** (Koala o successivo) — JDK 17 e
   Android SDK con `compileSdk 34` vengono gestiti automaticamente da Android Studio.
2. (Opzionale, per l'import da Strava) copia `local.properties.template` in
   `local.properties` e compila:
   ```properties
   STRAVA_CLIENT_ID=...
   STRAVA_CLIENT_SECRET=...
   ```
   Crea l'app sviluppatore su <https://www.strava.com/settings/api>; come
   **Authorization Callback Domain** imposta `strava-callback` (il redirect usato è
   `windrm://strava-callback`). Senza queste credenziali l'app funziona comunque: la tab
   Strava mostrerà un avviso e l'import resterà disponibile solo da file GPX.
3. Esegui su un device/emulatore con **Android 8.0 (API 26)** o superiore.

> **Nota per chi continua lo sviluppo da questa sessione Claude Code:** l'ambiente
> containerizzato in cui è stato scritto questo progetto non ha accesso a `dl.google.com`
> (il repository Maven di Google), quindi non è stato possibile eseguire una build Gradle
> reale né un `gradle sync` in questa sessione per validare la compilazione: il codice è
> stato scritto e rivisto manualmente con la massima attenzione, ma la prima esecuzione del
> workflow GitHub Actions (che gira su runner con accesso pieno a Internet) è il primo test
> di compilazione reale — controlla il log di quella run se qualcosa non va.

## Possibili sviluppi futuri

- Persistenza offline dell'ultima previsione calcolata per percorso
- Notifiche push quando le condizioni meteo di un percorso pianificato peggiorano
- Import Garmin Connect (oggi disponibile solo via file GPX esportato manualmente)
- Confronto multi-percorso ("quale dei miei giri preferiti ha meno vento contrario oggi?")
- Test automatici (parsing GPX, calcolo distanza/tempo di arrivo, interpolazione oraria)
