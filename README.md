# Rytm

Osobisty coach treningu, sylwetki i nawyków na Androida i Galaxy Watch 6. Opis projektu: [docs/coach-projekt.md](docs/coach-projekt.md).

## Moduły

| Moduł | Rola |
|---|---|
| `:app-phone` | UI telefonu, WorkManager, integracje |
| `:app-wear` | UI zegarka, kafelki, komplikacje, HRV, trening siłowy |
| `:core-domain` | czysty Kotlin: modele i logika (readiness, load, planer, żywienie, HRV), z testami |
| `:core-data` | Room, repozytoria, Health Connect, synchronizacja |
| `:core-ai` | klient LLM, prompty, limity kosztów |
| `:core-sync` | wspólne DTO i Wearable Data Layer |

## Stan

**Faza 0, rozpoznanie danych.** Instrukcja uruchomienia: [docs/faza-0.md](docs/faza-0.md).

## Build

```
./gradlew test assembleDebug
```

Wymaga JDK 17+ i Android SDK (API 37). Moduł zegarka buduje się bez Samsung Health Sensor SDK (zaślepka);
pomiar HRV wymaga AAR w `app-wear/libs/` (patrz instrukcja fazy 0).

Klucze API (od fazy 4) trzymamy w `local.properties`, nigdy w repo.
