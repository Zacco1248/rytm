# Faza 0: rozpoznanie danych, instrukcja uruchomienia

Cel fazy 0 (sekcja 11 w [coach-projekt.md](coach-projekt.md)): sprawdzić na prawdziwych danych, co trafia do Health Connect,
i czy poranny 60-sekundowy pomiar HRV z Galaxy Watch 6 daje sensowny RMSSD. Wynik to tabela „co mamy / czego brak”
i korekta wag readiness przed fazą 1.

W fazie 0 są dwie mini-aplikacje:

| Moduł | Urządzenie | Co robi |
|---|---|---|
| `:app-phone` | telefon | uprawnienia Health Connect, zrzut rekordów z 30 dni (lub 90, jeśli HC pozwala na historię), raport Markdown + surowy JSON |
| `:app-wear` | Galaxy Watch 6 | pomiar 5 s rozgrzewki + 60 s IBI przez Samsung Health Sensor SDK, RMSSD z odrzucaniem artefaktów, zapis surowych danych |

Logika (analiza zrzutu, RMSSD) siedzi w `:core-domain` i ma testy jednostkowe: `./gradlew :core-domain:test`.

---

## 1. Przygotowanie

- **Android Studio** w wersji obsługującej AGP 9.3 (najnowsza stabilna). JDK 21 z Android Studio wystarczy.
- Otwórz katalog repo jako projekt i poczekaj na sync Gradle.
- Telefon: *Ustawienia → Informacje o telefonie → Informacje o oprogramowaniu →* 7× „Numer wersji”, potem w *Opcjach programisty* włącz debugowanie USB.

Gotowe APK telefonu można też pobrać z GitHub Actions (artefakt `apk-debug` przy każdym buildzie). APK zegarka z CI
jest zbudowane **bez** Sensor SDK, więc do pomiaru HRV trzeba zbudować je lokalnie (punkt 3).

## 2. Telefon: zrzut z Health Connect

1. Upewnij się, że źródła piszą do Health Connect:
   - **Samsung Health** → Ustawienia → Health Connect: synchronizacja włączona.
   - **Fitatu** → ustawienia integracji: Health Connect włączony.
   - **Xiaomi Home** → sprawdź, czy w *Health Connect → Aplikacje* jest Xiaomi Home z uprawnieniem do zapisu wagi.
     Jeśli Xiaomi Home nie ma integracji z HC, sprawdź Mi Fitness albo Zepp Life (obie obsługują wagi Xiaomi i HC).
2. Uruchom `app-phone` na telefonie (Run ▶ w Android Studio).
3. **Nadaj uprawnienia** → zaznacz wszystko (w tym „dostęp do historii” i „w tle”, jeśli się pokażą).
4. **Zrzuć dane z 30 dni**. Odczyt trwa od kilku sekund do minuty.
5. Na ekranie pojawi się raport. **Udostępnij raport i zrzut JSON**, np. na Dysk albo do projektu, do analizy.

Pliki zostają też w `Android/data/com.zacco.rytm/files/phase0/` na telefonie.

> Zrzut zawiera dane zdrowotne (tętno, sen, waga, posiłki). Nie wrzucaj go do repo.

### Co sprawdza raport

- **Tętno**: czy próbki są faktycznie co ~10 min (mediana odstępu, odsetek odstępów 8–12 min) i ile próbek przypada na godzinę snu.
  Do tętna spoczynkowego (najniższa 30-minutowa średnia w nocy) potrzeba co najmniej ~4 próbek na godzinę.
- **Sen**: liczba nocy, czy są etapy (LIGHT/DEEP/REM), typowa długość.
- **Treningi**: typy HC i tytuły z podziałem na źródła i dni tygodnia, czyli czy squash, siatkówka i trening personalny dają się rozróżnić.
- **Duplikaty**: treningi i posiłki z różnych źródeł nakładające się w czasie (pod filtrowanie po `dataOrigin`).
- **Żywienie**: dni z danymi, średnie kcal i białko z Fitatu.
- **Waga i tkanka tłuszczowa**: czy są i z jakiej aplikacji (pytanie o Xiaomi Home).
- **Tętno spoczynkowe i HRV w HC**: oczekujemy braku (wtedy liczymy je sami).

## 3. Zegarek: PoC pomiaru HRV

### 3.1 Samsung Health Sensor SDK

1. Pobierz SDK z [developer.samsung.com/health/sensor](https://developer.samsung.com/health/sensor/overview.html)
   (konto Samsung, akceptacja licencji). Potrzebna wersja **1.3.0 lub nowsza** (tracker `HEART_RATE_CONTINUOUS`).
2. Skopiuj plik `samsung-health-sensor-api-*.aar` do `app-wear/libs/`.
   Plik jest w `.gitignore`: licencja Samsunga nie pozwala go rozpowszechniać, więc nie trafia do repo.
3. Sync Gradle. Build sam wykryje AAR i podmieni zaślepkę na `SamsungIbiSource`.

> Kod `SamsungIbiSource` nie jest kompilowany w CI (brak AAR). Jeśli przy pierwszym buildzie z AAR pojawi się błąd
> kompilacji (np. inna nazwa stałej w Twojej wersji SDK), wklej mi komunikat.

### 3.2 Zegarek w trybie deweloperskim

1. *Ustawienia → Informacje o zegarku → Oprogramowanie →* 5× „Wersja oprogramowania”.
2. *Opcje programisty*: włącz **Debugowanie ADB** i **Debugowanie przez Wi-Fi** (zegarek i komputer w tej samej sieci).
3. W Android Studio: *Pair Devices Using Wi-Fi* albo w terminalu:
   ```
   adb pair <IP>:<port-parowania>   # kod z ekranu zegarka
   adb connect <IP>:<port>
   ```
4. **Tryb deweloperski Health Platform** (wymagany przez Sensor SDK bez partnerstwa z Samsungiem):
   *Ustawienia → Aplikacje → Health Platform*, stuknij szybko ~10× w tytuł „Health Platform”, aż pojawi się `[Dev mode]`.

### 3.3 Pomiar

1. Uruchom `app-wear` na zegarku, zezwól na dostęp do czujników tętna.
2. Usiądź, ręka oparta i rozluźniona, zegarek ciasno na nadgarstku (nad kością). Oddychaj normalnie.
3. **Start** → 5 s przygotowania → 60 s pomiaru. Ekran nie gaśnie w trakcie.
4. Wynik: RMSSD, ln(RMSSD), średnie tętno, ile uderzeń przeszło filtry i jakość (dobra / akceptowalna / słaba).

Plan na fazę 0: **5–7 porannych pomiarów** po przebudzeniu, plus 2–3 pomiary jeden po drugim w jednej sesji
(powtarzalność). Jeśli masz pas piersiowy albo inną aplikację HRV, jeden równoległy pomiar dla porównania bardzo pomoże.

### 3.4 Pobranie plików z zegarka

Każdy pomiar zapisuje JSON z wynikiem, wszystkimi IBI i statusami sensora:

```
adb shell run-as com.zacco.rytm ls files/hrv
adb exec-out run-as com.zacco.rytm cat files/hrv/hrv-<znacznik>.json > hrv-<znacznik>.json
```

Logcat (tag `RytmHrv`) pokazuje ścieżkę i wynik każdego pomiaru.

### Jak liczony jest RMSSD

`core-domain/.../hrv/HrvCalculator.kt`:

1. odrzucamy IBI oznaczone przez sensor jako błędne (status ≠ 0) i spoza 300–2000 ms,
2. odrzucamy IBI odbiegające o ponad 20% od mediany 4 sąsiadów (zgubione lub dodatkowe uderzenie),
3. RMSSD liczymy tylko z par kolejnych uderzeń, które obie przeszły filtry (bez sklejania różnic przez dziurę),
4. wynik wymaga min. 30 par; jakość „dobra” przy ≥90% zaakceptowanych uderzeń, „akceptowalna” przy ≥75%.

Progi są w `HrvCalculator.Config` i skorygujemy je po obejrzeniu Twoich danych.

## 4. Co odesłać

- `rytm-hc-raport-*.md` i `rytm-hc-dump-*.json` z telefonu,
- kilka plików `hrv-*.json` z zegarka,
- krótka notka: w które dni z ostatnich 30 był squash, siatkówka i trening personalny (żeby sprawdzić, jak Samsung je zapisuje),
- czy waga z Xiaomi Home pojawiła się w raporcie.

Na tej podstawie przygotuję tabelę „co mamy / czego brak” i korektę wag readiness (sekcja 3.4) przed fazą 1.
