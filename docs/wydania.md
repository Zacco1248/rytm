# Wydania APK

Po każdym pushu na `main` CI buduje podpisane APK telefonu i zegarka i wystawia je
w [Releases](https://github.com/Zacco1248/rytm/releases/latest) jako
`rytm-telefon-0.1.N.apk` i `rytm-zegarek-0.1.N.apk` (N = numer builda, a zarazem
`versionCode`, więc każde wydanie instaluje się jako aktualizacja poprzedniego).

Repo jest publiczne, więc wydania też. APK nie zawiera sekretów: klucz AI każdy
wpisuje na swoim telefonie.

## Klucz podpisu

Wszystkie wydania podpisuje ten sam klucz (PKCS12, alias `rytm`, to samo hasło
dla magazynu i klucza). Klucz nie trafia do repo, tylko do sekretów Actions:

| Sekret | Zawartość |
| --- | --- |
| `RYTM_KEYSTORE_BASE64` | plik `.jks` zakodowany base64 |
| `RYTM_KEYSTORE_PASSWORD` | hasło |

Bez sekretów job `release` buduje APK niepodpisane i pomija publikację
(ostrzeżenie w logu). Zgubienie klucza oznacza jednorazową reinstalację
aplikacji (z utratą danych lokalnych), dlatego trzymaj kopię `.jks` i hasła
w menedżerze haseł.

Nowy klucz, gdyby był potrzebny:

```sh
keytool -genkeypair -keystore rytm-release.jks -storetype PKCS12 -alias rytm \
  -keyalg RSA -keysize 4096 -validity 36500 -dname "CN=Rytm"
base64 -w0 rytm-release.jks   # -> RYTM_KEYSTORE_BASE64
```

## Instalacja

- **Telefon:** Releases → najnowsze wydanie → `rytm-telefon-….apk` → zainstaluj
  (zezwól przeglądarce lub aplikacji GitHub na instalację nieznanych aplikacji).
- **Zegarek:** `rytm-zegarek-….apk` instaluje się przez ADB po Wi-Fi
  (`adb install`), z komputera albo z telefonu aplikacją obsługującą ADB.
- Wersja debug z artefaktów CI ma inny podpis niż wydania. Przed pierwszym
  wydaniem odinstaluj ją raz z telefonu i zegarka, później aktualizacje
  instalują się na poprzedniej wersji. Telefon i zegarek muszą mieć ten sam
  podpis, żeby Data Layer je połączył, więc instaluj je z tego samego wydania.
