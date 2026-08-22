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
| `RateLimitFilter` (pokrycie ścieżek) | 🟢 bramka | 2026-08-12 | `RateLimitCoverageTest`. Poprzedni przegląd pytał tylko o symetrię prefiksów **wśród ścieżek już objętych filtrem** i odpowiedział „pułapka trailing-slash nie dotyczy pozostałych baz" — prawdziwie, ale nie o to szło: `/api/training-requests`, `/api/calendar`, `/api/files`, publiczny CMS i całe OAuth2 nie były objęte **w ogóle**, więc pytanie o symetrię ich nie dosięgało. Filtr limituje teraz domyślnie, a bramka sprawdza każdą bazę kontrolera. |
| Linki z maili (GET ze skutkiem ubocznym) | 🔵 przeczytane + testy | 2026-08-13 | **Znaleziono:** wypisanie z newslettera działo się przy samym otwarciu linku, więc robiły to skanery bramek pocztowych zamiast odbiorcy. Rozdzielone na GET (strona) + POST (akcja). Pozostałe linki z maili (weryfikacja adresu, reset hasła) celują w trasy frontu i akcję robią POST-em, więc zwykły fetch skanera dostaje samą skorupę HTML — ale `VerifyEmailPage` woła weryfikację z `useEffect` na wejściu, więc skaner **renderujący JS** spali token przed kliknięciem (skutek: „link nieprawidłowy" przy koncie już aktywnym). Nietknięte. |
| `JwtAuthenticationFilter` | 🔵 przeczytane + testy | 2026-08-09 | Fail-closed na każdej ścieżce; cache + eviction otestowane. |
| OAuth2 (`OAuth2UserService`) | 🔵 przeczytane + testy | 2026-08-09 | **Znaleziono:** linkowanie konta bez sprawdzania `email_verified`. Naprawione. |
| Sekrety w repo | 🟡 sonda | 2026-08-08 | Skan wzorców kluczy + `.gitignore`. Czysto. |
| Nagłówki / CSP | 🔵 przeczytane | 2026-08-08 | `nginx.conf`; CSP bez `unsafe-inline` w `script-src`. |
| Upload plików (path traversal) | 🔵 przeczytane | 2026-08-08 | Regex na nazwie i folderze. |
| Dziennik przejść — authz i powierzchnia publiczna | 🔵 przeczytane + sonda HTTP | 2026-08-14 | Wszystkie endpointy `/api/ascents` i `/api/admin/ascents` sondowane po HTTP jako anonim, obcy user i admin: publiczne jest **wyłącznie** `/recent`, własność wierszy sprawdzana lookupem po (id, właściciel), cudzy wpis odpowiada „nie istnieje". Opt-out RODO w klauzuli WHERE, publiczne DTO bez pól prywatnych, cache czyszczony na zapisie, zmianie flagi **i obu ścieżkach kasowania konta**. **Znaleziono:** brak jakiejkolwiek ścieżki zdjęcia cudzego wpisu z listy publicznej — patrz „Czego bramki NIE złapią". |
| Statystyki bazy użytkowników (`/api/admin/user-stats`) | 🔵 przeczytane + testy | 2026-08-20 | Autoryzacja (dwie bramki + kolejność reguł `SecurityConfig`), powierzchnia DTO, kubełek rate limitu, brak parametrów wejściowych. **Znaleziono:** `countAthletesWithPlan()` liczyło plany osób po odebraniu flagi zawodnika — dane poza zasięgiem `requireFlaggedAthlete`, do tego liczba przewyższająca mianownik. Naprawione + test. |
| Prywatne notatki admina (`/api/admin/notes`) | 🟢 bramka + 🔵 przeczytane + testy | 2026-08-21 | Bramka `PrivateNoteIsolationTest` (typ notatki nieosiągalny poza `domain/adminnote` i `api/admin/note`) + `PrivateNoteNotInSharedPayloadTest` (asercja na **zserializowanym** `CalendarRangeDto` obu ról). **Znaleziono trzy rzeczy:** (1) bramka izolacji nie łapała `import ...domain.adminnote.*;` — a wildcard import własnego pakietu domenowego to **istniejący styl w tym repo** (3 serwisy), więc najbardziej prawdopodobne obejście przechodziło; przepisana na granice słów + wykrywanie pakietu, zweryfikowana czerwona na 3 kształtach. (2) `deleteNote` stało za bramką zawodnika, więc odebranie flagi czyniło notatki o jego treningach **nieusuwalnymi i niewidocznymi naraz** — dane osobowe bez ścieżki usunięcia; kasowanie jest teraz bezbramkowe (scope `(autor, cel)` sam z siebie nie może nic wypuścić). (3) Front przycinał podgląd do 6 **linii**, a przycisk „pokaż całość" pokazywał od 300 **znaków** — notatka wypunktowana na 10 krótkich linii gubiła ogon bez żadnej kontroli; mierzone `scrollHeight`, nie zgadywane. |
| Odpowiedzi na bzdurne wejście (400 vs 500) | 🔵 przeczytane + testy | 2026-08-14 | Sonda po całym API, nie tylko po nowej funkcji. **Znaleziono:** nie-UUID w ścieżce, nieznana stała enumu w query i niepoprawny JSON w ciele wpadały w catch-all → 500 + ERROR ze stackiem. Dwa handlery dopisane, komunikat ogólny (nie echuje wartości od klienta). |

### Poprawność backendu

| Obszar | Poziom | Kiedy | Czym |
|---|---|---|---|
| Strefa czasowa | 🟢 bramka | 2026-08-09 | `NoBareNowTest` (allowlista: `SitemapController`) |
| Bliźniaki slot/event | 🔵 przeczytane | 2026-08-08 | Diff linia po linii. 5 poważnych znalezisk, naprawione w 4.19.0. |
| Eksmisja cache'u kalendarza | 🔵 przeczytane | 2026-08-08 | Każda metoda mutująca vs `@CacheEvict`. |
| Adnotacje tylko na metodach publicznych | 🟢 bramka | 2026-08-09 | `SpringProxyAnnotationsTest` — pułapka „działa, a nie działa". |
| Transakcje i wyścigi | 🔵 przeczytane | 2026-08-08 | Blokady pesymistyczne, upserty na unikatach. |
| N+1 | 🔵 przeczytane | 2026-08-08 | Zapytania w pętlach, leniwe asocjacje w mapperach DTO. |
| Koszt statystyk użytkowników | 🟢 bramka | 2026-08-20 | `AdminUserStatsQueryCountTest` — 4 zapytania przy 30 kontach, budżet jako stała. Widziana na czerwono. **Znaleziono przy okazji:** zakładka dociągała nieużywaną listę kont, a klient trzymał 5-minutowy cache bez invalidacji. |
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

Druga klasa, której żadna bramka nie dotknie, to **decyzje produktowe o skutkach prawnych**.
Audyt 2026-08-14 wyciągnął jedną otwartą: publiczna lista „Ostatnie przejścia" pokazuje imię,
nazwisko i tekst pisany przez **każdego zalogowanego**, a jedynym wyłącznikiem był przełącznik
w ustawieniach **autora**. Operator serwisu nie miał w aplikacji żadnej drogi, żeby zdjąć cudzy
wpis — zostawała prośba do autora albo `psql` na produkcji.

**Zamknięte 2026-08-22 (V90).** Admin zdejmuje z listy **pojedynczy wpis**
(`climbing_ascents.hidden_from_public_at`). Wpis zostaje w dzienniku autora i w jego statystykach
— zniknął z cudzej tablicy ogłoszeń, nie z własnego zeszytu. Autor nie ma na to pola żadnej
kontrolki, więc przełączanie własnej widoczności zdjęcia nie cofa; przywrócić może tylko admin.

⚠️ **Rozwiązanie zapisane tu wcześniej jako „najtańsze" — pozwolić adminowi przestawiać cudze
`ascents_public` — jest BŁĘDNE i nie wolno do niego wracać.** Kosztuje dwa razy. Po pierwsze, ta
kolumna od 2026-08-21 bramkuje także podgląd dziennika w panelu, więc admin zdejmujący kogoś
z listy oślepiłby własny widok tej osoby (poza zawodnikami 1:1, których przepuszcza flaga
`is_athlete`) — czyli straciłby z oczu dokładnie tego, kogo właśnie moderował. Po drugie, autor
widzi ten przełącznik w swoich Ustawieniach i cofnąłby zdjęcie jednym kliknięciem. Moderacja,
którą moderowany cofa, nie jest moderacją. To dwa różne czasowniki („rezygnuję" vs „zdejmuję")
i tylko pozornie mieszczą się w jednym polu.

Nadal otwarte, świadomie: przy uporczywym nadużyciu zdejmowanie idzie wpis po wpisie. Eskalacja
to rozmowa z autorem, a w ostateczności usunięcie konta — droga, która już istnieje.
