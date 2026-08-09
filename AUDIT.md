# Rejestr pokrycia audytu

Po co ten plik: audyt to **próbkowanie według hipotez**, nie skan. Bez zapisu, co już zostało
zbadane i czym, każdy kolejny przegląd startuje od zera, trafia w inne miejsca i wygląda, jakby
kod psuł się w nieskończoność. Ten plik zamienia „przejrzyj wszystko" (nierealne) w „przejrzyj to,
co ma status **nietknięte**" (skończone).

**Zasada:** kończąc audyt jakiegoś obszaru, zmień jego wiersz. Nie dopisuj tu znalezisk ani historii
— od tego jest `git log`. Tu żyje wyłącznie **stan pokrycia**.

Poziomy pewności, od najmocniejszego:

| Poziom | Znaczenie |
|---|---|
| 🟢 **bramka** | Sprawdzane mechanicznie przy każdym pushu. Nie wymaga niczyjej uwagi i nie może wrócić. |
| 🔵 **przeczytane** | Ktoś przeczytał kod ze zrozumieniem. Ważne do następnej większej zmiany w tym miejscu. |
| 🟡 **sonda** | Grep/skrypt pod konkretne hipotezy. Łapie tylko to, czego szukano. |
| ⚪ **nietknięte** | Nikt tam nie był. **Tu zacznij następny audyt.** |

---

## ⚪ Nietknięte — kolejka na następny raz

Kolejność wg ryzyka, nie wg wielkości.

| Obszar | Rozmiar | Dlaczego to ma znaczenie |
|---|---|---|
| `AdminNewsPanel` ↔ `AdminCoursesPanel` — rozjazd | 3 000 linii | 975 bajt-identycznych linii. CLAUDE.md ma kryterium wyjścia („czwarty moduł CMS albo druga poprawka dotykająca obu") — nikt nie sprawdził, czy zostało przekroczone. |
| `AdminSitePanel`, `AdminTeamMemberPanel` | 2 170 linii | Edytory bez ochrony niezapisanej pracy (świadomie, wg CLAUDE.md) — warto potwierdzić, że nadal świadomie. |
| Komponenty kalendarza treningowego | ~3 500 linii | Najgęstsze testy w repo, więc niski priorytet — ale nieczytane. |
| `gallery` / `news` / `instructors` (backend) | ~1 000 linii | Reference counting plików, sortowanie per język. |
| Zachowanie pod obciążeniem | — | Harness k6 jest (`hub/loadtest/`), profil mierzony 2026-06 na starym boxie. Nieaktualny po migracji na A1. |
| Dostępność poza modalami | — | Kontrast, nawigacja klawiaturą po siatce kalendarza, czytniki ekranu. |

---

## Stan pokrycia

### Bezpieczeństwo

| Obszar | Poziom | Kiedy | Czym |
|---|---|---|---|
| Authz endpointów zawodnika (IDOR) | 🔵 przeczytane | 2026-08-08 | Wyliczone **wszystkie** metody publiczne 6 serwisów + guard każdej. Zero IDOR. |
| `@PreAuthorize` na adminie | 🟢 bramka | 2026-08-09 | `AdminEndpointsSecuredTest` |
| `SecurityConfig` (permitAll, CORS, GET+HEAD) | 🔵 przeczytane | 2026-08-08 | — |
| `RateLimitFilter` (symetria prefiksów) | 🔵 przeczytane | 2026-08-08 | Sprawdzone też, czy pułapka trailing-slash dotyczy pozostałych baz. Nie dotyczy. |
| `JwtAuthenticationFilter` | 🔵 przeczytane + testy | 2026-08-09 | Fail-closed na każdej ścieżce; cache + eviction otestowane. |
| OAuth2 (`OAuth2UserService`) | 🔵 przeczytane + testy | 2026-08-09 | **Znaleziono:** linkowanie konta bez sprawdzania `email_verified`. Naprawione. |
| Sekrety w repo | 🟡 sonda | 2026-08-08 | Skan wzorców kluczy + `.gitignore`. Czysto. |
| Nagłówki / CSP | 🔵 przeczytane | 2026-08-08 | `nginx.conf`; CSP bez `unsafe-inline` w `script-src`. |
| Upload plików (path traversal) | 🔵 przeczytane | 2026-08-08 | Regex na nazwie i folderze. |

### Poprawność backendu

| Obszar | Poziom | Kiedy | Czym |
|---|---|---|---|
| Strefa czasowa | 🟢 bramka | 2026-08-09 | `NoBareNowTest` (allowlista: `SitemapController`) |
| Bliźniaki slot/event | 🔵 przeczytane | 2026-08-08 | Diff linia po linii. 5 poważnych znalezisk, naprawione w 4.19.0. |
| Eksmisja cache'u kalendarza | 🔵 przeczytane | 2026-08-08 | Każda metoda mutująca vs `@CacheEvict`. |
| Adnotacje tylko na metodach publicznych | 🟢 bramka | 2026-08-09 | `SpringProxyAnnotationsTest` — pułapka „działa, a nie działa". |
| Transakcje i wyścigi | 🔵 przeczytane | 2026-08-08 | Blokady pesymistyczne, upserty na unikatach. |
| N+1 | 🔵 przeczytane | 2026-08-08 | Zapytania w pętlach, leniwe asocjacje w mapperach DTO. |
| Encje przekazywane do `@Async` | 🔵 przeczytane | 2026-08-09 | Wszystkie metody mailowe biorące encję + ich wywołania. 3 znaleziska, naprawione. |
| Escaping HTML w mailach | 🔵 przeczytane | 2026-08-09 | `esc()` w treści, celowo nie w temacie. Czysto. |
| Migracje V1–V79 | 🔵 przeczytane | 2026-08-08 | Wraz z historią indeksów (dwa fałszywe alarmy odrzucone). |
| Parytet `messages*.properties` | 🟢 bramka | 2026-08-09 | `MessageBundleParityTest` |
| Enum ↔ `ACTION_CONFIG` | 🟢 bramka | 2026-08-09 | `ActivityActionTypeParityTest` |

### Frontend

| Obszar | Poziom | Kiedy | Czym |
|---|---|---|---|
| `api/client.ts` (`fetchApi`, refresh, retry) | 🔵 przeczytane | 2026-08-08 | — |
| Brak surowego `fetch` poza `fetchApi` | 🟢 bramka | 2026-08-09 | `noRawFetch.test.ts` |
| Parytet i18n PL/EN/ES | 🟢 bramka | 2026-08-09 | `i18nParity.test.ts` (normalizuje sufiksy mnogie) |
| Martwe inwalidacje kluczy | 🟢 bramka | 2026-08-09 | `queryKeys.test.ts` — złapało 3 martwe w `AdminSitePanel` |
| Obsługa błędów mutacji | 🔵 przeczytane | 2026-08-09 | **Znaleziono:** 77 ze 159 mutacji nie raportowało błędu w żaden sposób. Domknięte centralnie `MutationCache` w `main.tsx`. |
| Sprzątanie efektów (listenery, timery) | 🟡 sonda | 2026-08-09 | Skrypt liczący add/remove i set/clear. 3 timery naprawione. |
| Dyscyplina typów (`any`, `ts-ignore`) | 🟡 sonda | 2026-08-08 | Zero wystąpień. |
| Dostępność modali | 🔵 przeczytane | 2026-08-09 | Wspólny `Modal` (23 użycia) + 4 własne trapy. `<img>` bez `alt`: 0. |
| Wzorzec `MAPA[klucz].pole` bez fallbacku | 🟡 sonda | 2026-08-09 | Występował wyłącznie w `AdminActivityPanel`; klasa zamknięta. |

### Infrastruktura

| Obszar | Poziom | Kiedy | Czym |
|---|---|---|---|
| CI (backend, frontend, deploy) | 🔵 przeczytane | 2026-08-08 | E2E dodane do CI 4.19.0. |
| `docker-compose.prod.yml` | 🔵 przeczytane | 2026-08-08 | Backend poza siecią NPM (niezmiennik z CLAUDE.md) potwierdzony. |
| Indeksy DB vs wzorce zapytań | 🟡 sonda | 2026-08-08 | Inwentarz indeksów + kolumny FK. |

---

## Czego bramki NIE złapią

Uczciwie, żeby ten plik nie dawał fałszywego spokoju: bramki pokrywają pytania w formie
„nigdzie nie ma X". Nie pokrywają błędów **semantycznych** — takich jak „`confirmEventOffer`
nie sprawdza po locku stanu, w jakim wydarzenie jest teraz". Żaden linter tego nie znajdzie
i ta klasa będzie wracać.

Jedyna realna dźwignia na nią to **zmniejszenie liczby miejsc, w których może wystąpić** —
czyli likwidacja bliźniactwa slot/event. Dopóki są dwie kopie, jest dwa razy więcej okazji.
