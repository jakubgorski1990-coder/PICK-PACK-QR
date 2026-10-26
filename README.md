# Pick / Pack — Panel magazynu

Tabletowa aplikacja Android (landscape) do prezentacji kodów QR szablonów dobierania BaseLinker. Aplikacja **tylko pokazuje** kody — przełączanie kurierów nic nie zmienia w BaseLinkerze.

## Stack

Kotlin · Jetpack Compose · ViewModel · Coroutines · Room · DataStore · ZXing · EncryptedSharedPreferences (token)

## Budowa APK

```bash
# local.properties → sdk.dir=...
./gradlew assembleDebug
./gradlew test
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

## Konfiguracja BaseLinker (Etap 2)

1. **Token** — Ustawienia → „Konfiguracja API / filtrów / sync” → wklej token z panelu BL (*Account & other → My account → API*). Zapis w Android Keystore / EncryptedSharedPreferences. **Nigdy** w repo, logach ani eksporcie JSON.
2. **Template ID (14 wariantów)** — Kompletność 14: pole Template ID. Prefill pełnych zestawów: Pick/Pack 1 maska „wszyscy” = **18472**, Pick/Pack 2 = **18173**. Pozostałe 12 ID wpisuje admin.
3. **Treść QR** — lokalnie: `BLPPT_` + ID dopełnione do 6 cyfr (np. `BLPPT_018472`). Override treści / import obrazu nadal możliwy.
4. **Licznik** — wybierz `status_id` (lista z `getOrderStatusList`), zmapuj `delivery_method_id` / fragment nazwy → kurier, ustaw interwał (domyślnie 60 s). Sync: `getOrders` + paginacja, dedup `order_id`, bez nakładających się sync, backoff.

## Ograniczenia API

- Brak oficjalnej metody na szablony dobierania / ich QR → treść z reguły BLPPT_, nie ze scrapingu.
- `getOrderPickPackHistory` **nie** jest licznikiem puli szablonu.
- Filtry API: status, confirmed, źródło, delivery na rekordzie zamówienia — **bez** rezerwacji pod konkretny szablon → UI pokazuje **„Szacowana liczba w puli”**.
- Max 100 zamówień / request; limit ~100 req/min.
- Bez PII klientów na ekranie / w cache licznika.

## Weryfikacja licznika vs BL

Porównaj „Szacowana liczba” z ręcznym filtrem w managerze BL (te same statusy + metody dostawy aktywnych kurierów). Różnice są oczekiwane, jeśli szablon dobierania ma dodatkowe reguły niedostępne w API.

## Zakładka IMIENNE

- U góry: **Pick / Pack** | **IMIENNE** — także **swipe w lewo/prawo**.
- Siatka QR (model na ≥12 i więcej) — kody **maksymalnie duże**, nazwy małe; wszystko na jednym landscape.
- Treść: `BLPPT_` + template ID dopełnione do 6 cyfr (wpisz samo `18472`).
- **Dodawanie osoby:** Ustawienia → sekcja IMIENNE → Imię/nazwa + Template ID → Dodaj. Edycja / kolejność / usuwanie w tej samej sekcji.
- Po **15 s** bezczynności na IMIENNE (konfigurowalne) auto-powrót do Pick/Pack. Interakcja resetuje timer.
- Persist Room; eksport/import JSON uwzględnia listę imiennych (bez tokenu). DEMO nie podstawia braków produkcyjnych.

## Etap 1 (nadal)

Landscape PL, 6 kurierów, dokładne maski 1–7, import PNG/JPG/PDF, DEMO, PIN, persistencja.

## Poza zakresem (Etap 3)

Pełne alerty progowe 5/10/15/20 + TTS.

## Testy wymagające tabletu

Skan BLPPT_ kolektorem, landscape, swipe IMIENNE, timeout 15s, sync Wi‑Fi, import SAF, keep-screen-on, PIN.
