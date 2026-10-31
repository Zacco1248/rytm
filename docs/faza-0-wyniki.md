# Faza 0: wyniki (zrzut z 2026-10-31, 30 dni)

Podsumowanie raportu z Health Connect. Surowy zrzut zostaje poza repo.

## Co mamy / czego brak

| Dane | Stan | Wniosek dla Rytmu |
|---|---|---|
| Sen (Samsung Health) | ✅ z etapami (AWAKE, LIGHT, DEEP, REM) | Długość snu = suma etapów bez AWAKE. **18 z 30 nocy**: luki to ładowanie zegarka w nocy. Gotowość bez snu przelicza wagi na resztę; użytkownik spróbuje ładować w dzień. |
| Tętno | ✅ gęste: ok. 1 próbka/min w nocy, 1 Hz w treningu | Tętno spoczynkowe z nocy jest wiarygodne. Waga RHR w gotowości 0,20 → **0,25**. |
| Tętno spoczynkowe / HRV w HC | ❌ (zgodnie z planem) | RHR liczymy sami. HRV dopiero z pomiaru na zegarku; do tego czasu jego waga przechodzi na resztę. Waga HRV 0,20 → **0,15**. |
| Treningi | ✅ siatkówka (wt, czw), squash (śr), OTHER_WORKOUT (pt), spacery | **Trening personalny = OTHER_WORKOUT w piątek**: mapujemy go na PERSONAL_TRAINING w dni personalnego. Spacery z psem (ok. 40 w miesiącu, mediana 17 min) liczą się do obciążenia, ale nie „zaliczają” dnia z planu. |
| Kroki | ✅ z trzech źródeł (telefon, Google Fit, Samsung Health) | Agregat HC usuwa duplikaty według priorytetu źródeł. W Health Connect warto ustawić Samsung Health jako pierwsze źródło. |
| Kalorie całkowite | ✅ Samsung Health i Google Fit | Jak wyżej. |
| Żywienie (Fitatu) | ❌ | Fitatu zapisuje do HC tylko w wersji Premium. Decyzja (2026-10-31): **szybki ręczny wpis** kcal i białka w Rytmie; synchronizacja nie nadpisuje ręcznych dni. |
| Waga / tkanka tłuszczowa (Xiaomi Scale S400, Xiaomi Home) | ❌ | Xiaomi Home nie zapisuje do HC, a S400 nie przesyła pomiarów do Mi Fitness. Na start **ręczny wpis ważenia** w Rytmie. Opcja: ScaleLauncher (F-Droid) czyta S400 po Bluetooth i zapisuje do HC. |
| VO2max | ✅ (1 pomiar) | Na razie nieużywane. |

## Obserwacje do dalszych faz

- Średnie tętno w meczach jest umiarkowane (ok. 105–135), więc szacunek RPE z tętna zaniża obciążenie względem odczuć.
  **RPE po treningu jest potrzebne**: szybki wpis w Rytmie (telefon), potem na zegarku (faza 2).
- Squash bywa zapisany jako 1 h 45 min (z przerwami). Czas sesji bierzemy z HC bez korekty; RPE wyrówna różnice.
