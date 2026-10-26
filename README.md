# Pick / Pack — Panel magazynu (Etap 1)

Tabletowa aplikacja Android (landscape) do prezentacji kodów QR szablonów dobierania BaseLinker. Aplikacja **tylko pokazuje** kody — przełączanie kurierów nic nie zmienia w BaseLinkerze.

## Stack

Kotlin · Jetpack Compose · ViewModel · Coroutines · Room · DataStore · ZXing (lokalne QR)

## Wymagania do budowy

- JDK 17+
- Android SDK (platform 35, build-tools 35)
- Ustaw `sdk.dir` w `local.properties` (nie commitowany), np.:

```properties
sdk.dir=/path/to/Android/Sdk
```

## Budowa APK

```bash
./gradlew assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

Release (bez minify w Etapie 1):

```bash
./gradlew assembleRelease
```

Testy jednostkowe (logika wyboru QR, eksport konfiguracji):

```bash
./gradlew test
```

## Co działa w Etapie 1

- Landscape UI (PL): 1 lub 2 duże panele QR, pasek 6 kurierów, zegar, ustawienia, status połączenia
- 2 grupy × 7 wariantów + stan „wszyscy odebrali” (ukryty QR + komunikat)
- Dokładne dopasowanie zestawu aktywnych kurierów (maska 1–7) — nigdy „podobny” wariant
- Natychmiastowe czyszczenie QR przy braku / wyłączeniu / uszkodzeniu wariantu
- Import PNG/JPG (SAF), PDF (render strony), wklejenie treści; enable/disable; podgląd
- Ekran kompletności 14 wariantów + ostrzeżenie o identycznej treści
- Eksport/import JSON konfiguracji **bez** sekretów API
- Persistencja stanu kurierów (DataStore) i wariantów (Room)
- Keep screen on, „Nowy dzień — wszyscy aktywni” z potwierdzeniem, opcjonalny PIN
- Reset dzienny `Europe/Warsaw` (domyślnie wyłączony)
- Tryb **DEMO**: 14 testowych treści, baner „DEMO — nie jest kodem BaseLinkera”; DEMO **nie** zastępuje brakującego kodu produkcyjnego gdy tryb jest wyłączony; w trybie DEMO brak produkcyjnego bierze z DEMO tylko jawnie
- `QrTemplateProvider` — lokalna implementacja Room
- `OrderPoolProvider` — stub (+ symulator liczników w DEMO)
- Startowe wskazówki ID szablonów BL: Pick/Pack 1 = **18472**, Pick/Pack 2 = **18173**

## Poza zakresem (Etap 2/3 — nie blokowane)

Pełna sync API BaseLinker, prawdziwe filtry puli, produkcyjne alerty/TTS.

## Testy wymagające tabletu / kolektora

| Scenariusz | Dlaczego na urządzeniu |
|---|---|
| Skan QR z panelu skanerem magazynowym / kolektorem | Weryfikacja czytelności (quiet zone ≥4, rozmiar, jasność ekranu) |
| Landscape na docelowym tablecie (split 2 QR vs 1 + zakładki) | Gestura, rozmiary przycisków kurierów pod rękawicą |
| Import PNG/JPG/PDF przez SAF z dysku/USB | Uprawnienia URI, render PDF wielostronicowego |
| Keep screen on przez całą zmianę | Polityka zasilania OEM |
| PIN + szybkie wejście w ustawienia na hali | UX pod stresem operacyjnym |

Logikę 8 stanów × 2 grupy pokrywają testy JVM (`QrSelectionLogicTest`).
