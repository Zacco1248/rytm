# Rytm – osobisty coach treningu i rozwoju (Android + Galaxy Watch 6)

> **Rytm**: rytm dnia, tygodnia, serca i nawyków.
> Dokument opisuje cel, kontekst, architekturę i plan prac. Służy jako główny kontekst projektu dla Claude.

---

## 1. Cel

Osobista aplikacja (tylko na własny użytek, bez publikacji w sklepie), która działa jak Garmin Coach, ale szerzej:
- **trening**: planuje tydzień wokół stałych sportów i treningu siłowego w domu, dobiera obciążenie do regeneracji,
- **sylwetka**: wspiera rekompozycję (redukcja tłuszczu przy utrzymaniu lub budowie mięśni), łącząc trening, żywienie z Fitatu i trend wagi,
- **nawyki i rozwój osobisty**: śledzi nawyki, poranny check-in samopoczucia, wieczorny przegląd,
- **coach AI**: przede wszystkim **wieczorna analiza dnia** (co się wydarzyło, jak wpłynęło na regenerację, co jutro) oraz raport tygodniowy z zależnościami.

Priorytet: **zrobić to dobrze**, czyli czysta architektura, testowalna logika i dopracowany UX na zegarku. Nie chodzi o szybki prototyp.

---

## 2. Kontekst użytkownika

### Stały tydzień
| Dzień | Aktywność | Uwagi |
|---|---|---|
| Wtorek | Siatkówka | może wypaść |
| Środa | Squash | może wypaść |
| Czwartek | Siatkówka | może wypaść |
| Piątek | Trening personalny (grupowy) | do końca listopada 2026 |
| Pon / Sob / Nd (+ Pt od grudnia) | do zaplanowania przez coacha | siłownia w domu, mobilność, odpoczynek |

### Dodatkowo
- **Grupowe treningi personalne w piątki, do końca listopada 2026**: typ aktywności `PERSONAL_TRAINING`, stały punkt planu (z datą końca). Od grudnia piątek wraca do planu siłowego.
- **Remont domu**: nieregularna praca fizyczna (noszenie, praca nad głową, długie stanie). Musi być **logowana jako obciążenie** i wpływać na plan. Logujemy ją ogólnym „zaloguj inną aktywność” z opisem.

### Cel sylwetkowy
- **Rekompozycja**: lekki deficyt kaloryczny, wysokie białko, progresja w treningu siłowym.
- Kluczowe sygnały: trend wagi (średnia 7-dniowa), tkanka tłuszczowa (jeśli mierzona), białko i kalorie z Fitatu, progresja siłowa.

### Sprzęt
- Telefon z Androidem + **Samsung Galaxy Watch 6** (Wear OS).
- Trening siłowy **w domu**: hantle, mata, ewentualnie gumy oporowe (do dokupienia).
- Squash, siatkówka i treningi personalne nagrywane w **Samsung Health** na zegarku, a Rytm tylko je odczytuje.
- Pomiar tętna w Samsung Health ustawiony **co 10 minut** (lepsze tętno spoczynkowe i analiza nocy).
- **Fitatu** synchronizuje żywienie dwustronnie z Health Connect.
- **Waga Xiaomi** (aplikacja Xiaomi: Mi Fitness / Zepp Life / Xiaomi Home, do ustalenia) jest źródłem wagi i tkanki tłuszczowej. Ścieżka do Rytmu: aplikacja Xiaomi → Health Connect (zweryfikować w fazie 0). Fallback: ręczny wpis w Rytmie lub bezpośredni odczyt wagi po BLE (protokół znany, np. projekt openScale), dopiero jeśli integracja nie zadziała.

---

## 3. Zasady planowania (logika coacha)

### 3.1 Szablon tygodnia (punkt wyjścia)
- **Pon**: siła A, góra (push/pull z hantlami) + core
- **Wt**: siatkówka
- **Śr**: squash
- **Czw**: siatkówka
- **Pt**: do końca listopada **trening personalny**; od grudnia siła B, dół + core
- **Sob**: siła C, całe ciało z naciskiem na dół (do końca listopada), lub remont lub aktywność dowolna (zależnie od gotowości)
- **Nd**: mobilność (20–30 min, mata), spacer, odpoczynek

### 3.2 Reguły
1. **Brak ciężkich nóg** dzień przed siatkówką lub squashem.
2. **Dzień po meczu**: preferowana mobilność lub lekka górna partia.
3. **Mecz wypadł**: coach proponuje zamiennik (dodatkowa sesja siłowa, interwały z hantlami lub mobilność) z uwzględnieniem tygodniowego obciążenia.
4. **Ciężka „inna aktywność”** (np. remont; od 2 h lub sRPE ≥ 400) liczy się jak trening. Opcjonalne pole „co obciążyło” (plecy, barki, nogi, ręce, całe ciało; „remont” domyślnie zaznacza plecy i barki) steruje kolejnym dniem: nogi → siła na górę, plecy/barki/ręce → dół (albo lekka sesja, gdy jutro mecz), całe ciało → mobilność. Bez pola: ogólne ostrzeżenie i maks. umiarkowana intensywność.
5. **Niska gotowość** (patrz 3.4): trening zastępowany lżejszą wersją albo odpoczynkiem. Stałe sporty zostają, ale z ostrzeżeniem.
6. Min. **1 dzień pełnego odpoczynku** w tygodniu.
7. **Rekompozycja**: min. 2 sesje siłowe w tygodniu (w tym personalne). Jeśli w tygodniu wypadły, coach priorytetyzuje siłę nad dodatkowym cardio.

### 3.3 Obciążenie (load)
- Każda aktywność dostaje **sRPE = RPE (1–10) × minuty**.
- RPE: dla treningów z Samsung Health użytkownik podaje je po treningu jednym dotknięciem na zegarku. Fallback: szacunek z tętna (strefy, TRIMP).
- **Obciążenie ostre** = suma z 7 dni, **przewlekłe** = średnia tygodniowa z 28 dni.
- **ACWR** (ostre / przewlekłe): strefa bezpieczna ~0,8–1,3. Powyżej 1,5 coach wymusza lżejsze dni.

### 3.4 Gotowość (readiness score 0–100)
Składowe (wagi konfigurowalne):
- **sen**: długość, regularność (pora zaśnięcia vs średnia), etapy snu jeśli dostępne w HC,
- **tętno spoczynkowe w nocy**: liczone samodzielnie z próbek HR podczas snu (najniższa średnia 30-minutowa) vs baseline z 7 dni,
- **HRV poranne**: 60-sekundowy pomiar na zegarku przy check-inie (patrz 5.4) vs baseline z 7 dni; jeśli brak, wagi się przeliczają,
- **subiektywny check-in**: energia i nastrój (1–5), bolące miejsca,
- **obciążenie z poprzedniego dnia** i ACWR.

Algorytm ma być **czystą funkcją Kotlin z testami jednostkowymi**, bez zależności od Androida. Pierwsze 7–14 dni to tryb kalibracji (baseline'y się budują, UI to komunikuje).

---

## 4. Sylwetka i żywienie (rekompozycja)

- **Źródło**: Fitatu → Health Connect (`NutritionRecord`): kalorie, białko, tłuszcz, węglowodany.
- **Waga i tkanka tłuszczowa**: Health Connect (`WeightRecord`, `BodyFatRecord`); w aplikacji **średnia krocząca 7 dni**, nigdy pojedynczy pomiar.
- **Cele** (ustawiane ręcznie w aplikacji): dzienne kcal, białko w g/kg masy ciała, docelowe tempo zmiany wagi tygodniowo.
- **Logika tygodniowa**:
  - waga spada zbyt szybko i siła spada → sugestia zwiększenia kalorii,
  - waga stoi 2–3 tygodnie, a siła rośnie → OK (typowa rekompozycja), zostaw,
  - białko poniżej celu przez większość tygodnia → wyraźna informacja w raporcie.
- Rytm **nie liczy kalorii sam** i nie dubluje Fitatu. Tylko czyta i interpretuje.
- Bez porad medycznych; ton wspierający, bez ocen.

---

## 5. Nawyki i rozwój osobisty

- **Definicje nawyków**: nazwa, częstotliwość (codziennie / X razy w tygodniu / konkretne dni), typ (tak/nie, liczba, minuty).
- **Odhaczanie z zegarka** (kafelek), szczegóły i edycja na telefonie.
- **Poranny check-in** (zegarek, około 70 s łącznie):
  1. 60-sekundowy pomiar HRV (siedząc, w spoczynku),
  2. energia 1–5, nastrój 1–5,
  3. opcjonalnie bolące miejsca (barki, łokieć, kolana, plecy, łydki, kostki).
- **Wieczorny przegląd (główna funkcja AI)**, około 21:00, powiadomienie na zegarku i pełny widok na telefonie:
  - podsumowanie dnia: aktywności, obciążenie, kroki, białko i kcal, nawyki, tętno w ciągu dnia,
  - interpretacja: „dzisiejszy squash + remont = wysoki load, jutro lżej”,
  - rekomendacja na jutro + pora snu,
  - opcjonalnie jedno zdanie refleksji od użytkownika (wpada do analizy tygodniowej).
- **Raport tygodniowy** (niedziela wieczór): trendy, korelacje (sen vs dzień meczu, nawyki vs nastrój, białko vs regeneracja), postęp sylwetki, 1–3 konkretne sugestie na kolejny tydzień.

---

## 6. Architektura

### 6.1 Podział ról
- **Telefon = mózg**: odczyt danych, obliczenia, planowanie, LLM, baza danych.
- **Zegarek = interfejs i krótkie pomiary**: wyświetlanie planu, szybkie wprowadzanie danych, poranne HRV, trening siłowy na żywo.
- Komunikacja: **Wearable Data Layer API** (DataClient dla stanu planu, MessageClient dla akcji).

### 6.2 Stack
- Kotlin, Coroutines, Flow
- **Telefon**: Jetpack Compose, Material 3
- **Zegarek**: Compose for Wear OS, **Tiles** (ProtoLayout), **Complications**, Health Services (ExerciseClient), **Samsung Health Sensor SDK** (tylko poranne HRV)
- DI: Hilt
- Baza: Room (telefon), lekki cache na zegarku (DataStore)
- Tło: WorkManager (synchronizacja z HC co około 1 h, plan rano, przegląd wieczorem)
- Sieć: Ktor client + kotlinx.serialization
- Testy: JUnit5 / kotest dla modułu domain

### 6.3 Moduły Gradle
```
:app-phone        – UI telefonu, WorkManager, integracje
:app-wear         – UI zegarka, kafelki, komplikacje, HRV, trening siłowy
:core-domain      – czysty Kotlin: modele, readiness, load, planner, nutrition logic, reguły (bez Androida!)
:core-data        – Room, repozytoria, Health Connect, synchronizacja
:core-ai          – klient LLM, prompty, parsowanie JSON, limity kosztów
:core-sync        – wspólne DTO i Data Layer (telefon <-> zegarek)
```

### 6.4 Źródła danych (stan zweryfikowany na uprawnieniach Samsung Health w HC)

Samsung Health **zapisuje** do Health Connect m.in.: ćwiczenia (ExerciseSession), kroki, moc, odległość, pułap tlenowy (VO2max), sumę spalonych kalorii, szybkość, odżywianie, tętno, ciśnienie krwi, glukozę, SpO2, tkankę tłuszczową, wagę, BMR, wzrost, sen.

**Brak w HC: HRV i tętna spoczynkowego.** Rozwiązanie:
- tętno spoczynkowe liczymy sami z `HeartRateRecord` w oknie snu (`SleepSessionRecord`),
- HRV mierzymy sami: poranny 60-sekundowy pomiar przez **Samsung Health Sensor SDK** (tętno z interwałami IBI) → RMSSD, z odrzucaniem artefaktów. Jednorazowy krótki pomiar, więc pomijalny wpływ na baterię. Sensor SDK działa w trybie deweloperskim, co wystarcza do użytku własnego.

| Źródło | Co | Faza |
|---|---|---|
| Health Connect | sen (+ etapy, jeśli są), tętno, kroki, treningi, kalorie, VO2max, waga, tkanka tłuszczowa, żywienie (Fitatu) | 1 |
| Własne obliczenia | tętno spoczynkowe w nocy, load, ACWR, readiness | 1 |
| Własne dane | check-in, RPE, nawyki, inne aktywności (np. remont) | 1–2 |
| Sensor SDK (zegarek) | poranne HRV (IBI → RMSSD) | 2 |
| Health Services (zegarek) | tętno i kalorie podczas treningu siłowego | 3 |
| Samsung Health Data SDK | ewentualne braki (opcjonalnie) | 5 |

**Uwagi techniczne Health Connect:**
- Domyślnie aplikacja czyta dane tylko z **30 dni przed przyznaniem uprawnień**. Dla dłuższej historii trzeba poprosić o `READ_HEALTH_DATA_HISTORY`.
- Odczyt w tle wymaga `READ_HEALTH_DATA_IN_BACKGROUND` (WorkManager).
- Używać **Changes API** (changes token) zamiast pełnego odczytu przy każdej synchronizacji.
- Deduplikacja: Fitatu i Samsung mogą zapisywać te same treningi lub kalorie, więc filtrowanie po `dataOrigin`.

**Zasada baterii**: żadnego ciągłego zbierania surowych danych z sensorów w tle. HR co 10 min robi Samsung Health, a my to tylko czytamy.

---

## 7. Modele danych (szkic)

```kotlin
enum class ActivityType { VOLLEYBALL, SQUASH, STRENGTH, PERSONAL_TRAINING, MOBILITY, CARDIO, RENOVATION, WALK, OTHER }

data class Activity(
    val id: String,
    val type: ActivityType,
    val start: Instant,
    val durationMin: Int,
    val rpe: Int?,            // 1–10
    val avgHr: Int?,
    val kcal: Int?,
    val source: Source,       // HEALTH_CONNECT, WATCH_APP, MANUAL
    val notes: String? = null
) { val load: Int? get() = rpe?.let { it * durationMin } }

data class DailyCheckIn(val date: LocalDate, val energy: Int, val mood: Int, val soreAreas: Set<BodyArea>, val hrvRmssd: Double?)

data class DailyMetrics(
    val date: LocalDate,
    val sleepMin: Int?, val sleepStart: Instant?, val sleepEnd: Instant?,
    val nightRestingHr: Int?, val steps: Int?, val totalKcal: Int?
)

data class NutritionDay(val date: LocalDate, val kcal: Int, val proteinG: Double, val fatG: Double, val carbsG: Double)
data class BodyMetrics(val date: LocalDate, val weightKg: Double?, val bodyFatPct: Double?)

data class Readiness(val date: LocalDate, val score: Int, val components: Map<String, Int>, val flags: List<String>, val calibrating: Boolean)

data class PlannedDay(
    val date: LocalDate,
    val kind: DayKind,                   // FIXED_SPORT, STRENGTH, PERSONAL_TRAINING, MOBILITY, REST, FLEX
    val title: String,
    val intensity: Intensity,            // LOW, MODERATE, HIGH
    val workout: StrengthWorkout?,
    val rationale: String
)

data class Habit(val id: String, val name: String, val schedule: HabitSchedule, val kind: HabitKind, val target: Int?)
data class HabitLog(val habitId: String, val date: LocalDate, val value: Int)

data class StrengthWorkout(val name: String, val exercises: List<PlannedExercise>)
data class PlannedExercise(val name: String, val sets: Int, val reps: IntRange, val weightKg: Double?, val tempo: String?, val restSec: Int)
data class LoggedSet(val exercise: String, val reps: Int, val weightKg: Double, val rir: Int?)
```

---

## 8. Trening siłowy w domu

- Sprzęt: **regulowane hantle 2–8 kg na każdą (para) lub do 14 kg jako pojedyncza hantla**, mata, (planowane) gumy oporowe. Dostępne ciężary konfigurowalne w ustawieniach (żeby dodać sprzęt później).
- **Punkt startowy (około pół roku temu)**: biceps 10–12 kg na rękę × 12–15 powtórzeń, brzuszki 20–25. Po przerwie start od około 70% dawnych obciążeń; pierwsze 2 tygodnie to kalibracja (RIR 2–3, bez serii do upadku).
- **Ograniczenie**: 8 kg na rękę szybko będzie za mało na dół ciała i plecy. Dlatego:
  - ćwiczenia na dół i plecy bazują na **pojedynczej hantli 14 kg** (goblet squat, wiosłowanie jednorącz, RDL na jednej nodze, bułgarskie przysiady z hantlą w goblet),
  - para 2×8 kg na barki, ramiona, klatkę w wariantach jednostronnych i z tempem,
  - gumy jako dodatkowy opór (pompki z gumą, face pulle, rotatory).
- **Progresja przy ograniczonym ciężarze** (kolejność): powtórzenia w zakresie (double progression) → wolniejsze tempo (np. 3-1-1) → pauzy → warianty jednostronne (bułgarskie przysiady, wiosłowanie jednorącz) → serie z gumą → dopiero potem cięższe hantle.
- 3 szablony (A góra, B dół, C całe ciało), 35–50 min, z rozgrzewką.
- Prewencja pod squash i siatkówkę w każdym planie: rotatory barku (guma), łydki i stopy, core antyrotacyjny, kolana (jednostronne).
- Na zegarku: ćwiczenie, seria, ciężar, timer przerwy z wibracją, tętno, zapis serii (bezel do zmiany ciężaru i powtórzeń), RIR po serii (opcjonalnie).

---

## 9. Zegarek – funkcje

1. **Kafelek „Dziś”**: typ dnia, intensywność, gotowość, krótkie uzasadnienie.
2. **Kafelek „Nawyki”**: lista na dziś, odhaczanie jednym dotknięciem.
3. **Komplikacja**: wynik gotowości (0–100) na tarczy.
4. **Poranny check-in z HRV**: powiadomienie po przebudzeniu.
5. **RPE po treningu**: po wykryciu nowego treningu w HC telefon wysyła na zegarek prośbę „Jak ciężko? 1–10”.
6. **Zaloguj inną aktywność**: czas (1 h / 2 h / 4 h) + RPE + krótki opis, co to było (np. remont) + opcjonalnie „co obciążyło”. Zastępuje osobny „log remontu” (decyzja 2026-10-31).
7. **Wieczorne podsumowanie**: skrót na zegarku, pełne na telefonie.
8. **Trening siłowy** (faza 3).
9. **Wyjątki planu**: „mecz odwołany” lub „trening personalny odwołany”, co uruchamia przeplanowanie.

---

## 10. Integracja LLM

- Dostawca: **Claude API**, model konfigurowalny (mniejszy do codziennych podsumowań, mocniejszy do raportu tygodniowego).
- Wywołania:
  - **wieczorem (najważniejsze)**: analiza dnia + rekomendacja na jutro,
  - **rano**: krótkie uzasadnienie planu dnia po check-inie,
  - **niedziela**: raport tygodniowy + propozycja korekt planu i żywienia.
- Input: **zagregowane metryki** (dzienne i tygodniowe), nie surowe próbki tętna.
- **Wyjście w JSON** z określonym schematem, walidowane. Przy błędzie fallback do tekstu z silnika reguł.
- **LLM nie decyduje o obciążeniu samodzielnie**: planer regułowy jest źródłem prawdy, a LLM tłumaczy i sugeruje. Sugestie zmian planu wymagają akceptacji.
- **Limit kosztów**: miesięczny budżet w ustawieniach, licznik tokenów w bazie, po przekroczeniu tylko reguły.
- Klucz API: `local.properties` → BuildConfig (nie commitować) lub EncryptedSharedPreferences. Nigdy w repo.
- Interfejs `CoachLlm`, żeby można było podpiąć lokalny model na domowym serwerze.

---

## 11. Plan prac (fazy)

### Faza 0 – rozpoznanie danych
- Mini-aplikacja: uprawnienia HC + zrzut realnych rekordów z 30 dni (sen i etapy, HR, czy częstotliwość próbek jest faktycznie co 10 min, typy ExerciseSession dla squasha, siatkówki i treningów personalnych, rekordy Fitatu, duplikaty).
- Proof of concept: 60-sekundowe IBI przez Sensor SDK na Galaxy Watch 6 → RMSSD.
- Wynik: tabela „co mamy / czego brak” → korekta wag readiness.

### Faza 1 – MVP telefon
- Moduły, Room, synchronizacja z HC (Changes API, WorkManager).
- Ręczne logowanie: check-in, RPE, inna aktywność (z opisem).
- `core-domain`: tętno spoczynkowe w nocy, load, ACWR, readiness, planer regułowy, logika żywienia, **z testami**.
- Ekrany „Dziś”, „Tydzień”, „Sylwetka”.

### Faza 2 – zegarek
- Data Layer sync, kafelek „Dziś”, kafelek „Nawyki”, komplikacja, check-in z HRV, RPE, log innej aktywności.

### Faza 3 – trening siłowy na zegarku
- Szablony A/B/C, prowadzenie serii, timer, Health Services, progresja.

### Faza 4 – LLM
- Wieczorna analiza, poranne uzasadnienie, raport tygodniowy, limit kosztów.

### Faza 5 – dodatki
- Wykresy trendów, eksport danych, lokalny LLM, Samsung Health Data SDK (jeśli faza 0 wykaże braki).

---

## 12. Konwencje

- Logika biznesowa **tylko w `core-domain`**, czysty Kotlin, 100% testowalny.
- UI bez logiki, ViewModel + UiState (StateFlow).
- Czas: `java.time`, strefa Europe/Warsaw; „dzień” liczony od przebudzenia; tydzień od poniedziałku.
- Język UI: **polski**. Kod i nazwy: angielski.
- Każda faza kończy się działającym buildem i krótkim podsumowaniem zmian.

---

## 13. Otwarte pytania

Odpowiedzi z 2026-10-31:

1. ~~Model wagi Xiaomi i aplikacja~~: **Xiaomi Home**. Czy waga trafia do Health Connect, sprawdza raport fazy 0.
2. ~~Lista startowych nawyków~~: sen i zmęczenie mierzymy check-inem i z zegarka; nawyki do odhaczania na start: **„Bez ekranu po 23:30”** (tak/nie, codziennie), **„10 min mobilności”** (minuty, codziennie), **„Ogólna aktywność”** = kroki z Health Connect, cel 6000 dziennie (odhaczana automatycznie).
3. ~~Cel snu~~: zaśnięcie **24:00–01:00**, długość **7–8 h**.
4. ~~Cele żywieniowe~~: z Fitatu (pod rekompozycję): **2300 kcal, białko 161 g, tłuszcz 72 g, węglowodany 253 g**. To domyślne cele w aplikacji, z możliwością edycji.
5. Ewentualny zakup: gumy oporowe (taśmy z uchwytami + mini bandy); w perspektywie cięższe hantle regulowane, jeśli progresja stanie. *(otwarte)*

Profil: 80 kg, 187 cm, praca biurowa siedząca, spacery z psem (do 6–7 tys. kroków w dni robocze, w weekendy więcej).
