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
| `AdminNewsPanel` ↔ `AdminCoursesPanel` — rozjazd | 3 000 linii | 975 bajt-identycznych linii. CLAUDE.md ma kryterium wyjścia („czwarty moduł CMS albo druga poprawka dotykająca obu") — **jedna z dwóch poprawek już padła** (plakietka „Szkic", 2026-08-29, tknięta w obu panelach naraz). Następna przekracza próg. |
| `AdminSitePanel`, `AdminTeamMemberPanel` | 2 170 linii | Edytory bez ochrony niezapisanej pracy (świadomie, wg CLAUDE.md) — warto potwierdzić, że nadal świadomie. Inwentarz modali z formularzem **jest** (2026-08-29, patrz „Frontend"), brakuje decyzji. |
| Komponenty kalendarza treningowego — **reszta** | ~2 000 linii | Rdzeń i wszystkie udokumentowane niezmienniki sprawdzone 2026-08-29 (patrz „Frontend"). Nieczytane zostają: `CommentThread`, `GoalsBanner`, `WeightChart`, `TrainingStatsSection`, `TrainingFormModal` — mają własne testy, ale nikt ich nie przeczytał. |
| `gallery` / `news` / `instructors` (backend) | ~1 000 linii | Reference counting plików, sortowanie per język. Powierzchnia HTTP zasondowana (2026-08-29), logika nieczytana. |
| Profil wydajności **serwera** (A1 ARM) | — | Lokalnie zmierzone 2026-08-29 (patrz „Infrastruktura") i czysto, ale to pomiar **Maca**, nie produkcji. Liczby w sekcji „Wydajność" w CLAUDE.md pochodzą z boksu 1 GB x86 sprzed migracji i pozostają nieaktualne. Czego to wymaga: `BASE_URL=https://nextsteppro.pl docker run --rm -i grafana/k6 run - < loadtest/baseline.js`, potem `throughput.js` i `stress-nocache.js` (⚠️ **`BASE_URL` obowiązkowo** — pozostałe skrypty mają w domyślnym porcie `:8081`). Dwie przeszkody, obie do rozstrzygnięcia świadomie: (1) na produkcji **nie da się wyłączyć rate limitu**, więc `throughput`/`stress` zmierzą filtr, a nie aplikację — sensowny jest tylko `baseline` na 5 VU albo osobny kubełek dla adresu testującego; (2) ruch idzie przez Cloudflare, więc mierzy się też CDN. Alternatywa bez dotykania produkcji: ten sam obraz k6 puszczony **na serwerze** przeciw `localhost:8080`, z pominięciem CF i z limitem wyłączonym na czas pomiaru. |
| Nawigacja klawiaturą i czytniki ekranu | — | Kontrast **domknięty** 2026-08-29 (patrz „Frontend"). Kolejność fokusu zebrana automatem na 79 ekranach i nie pokazała pułapek, ale przejścia po siatce kalendarza i praca z czytnikiem ekranu nadal nieoceniane ręcznie. |

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
| Odpowiedzi na bzdurne wejście — **runda druga** | 🔵 przeczytane + testy | 2026-08-29 | Poprzednia runda zamknęła klasę tylko w tych dwóch drzwiach, w które akurat zapukała. **Znaleziono cztery kolejne**, każde osobnym typem wyjątku Springa i każde lądujące w catch-all jako 500 + ERROR ze stackiem: brak części multipart (`MissingServletRequestPartException` — osiągalne z przeglądarki, ucięty upload wygląda tak samo), brak wymaganego `@RequestParam`, zły `Content-Type` (powinno być 415), zły czasownik na istniejącej ścieżce (powinno być 405 z `Allow`). Wszystkie cztery zasondowane na żywej aplikacji przed i po. |
| Authz całej powierzchni HTTP | 🔵 sonda HTTP (pełna) | 2026-08-29 | **826 sond, 313/313 operacji z `/v3/api-docs`**, każda w tożsamościach anonim / obcy zalogowany / zawodnik / admin. Wynik: anonim na `/api/admin` → 196× 401, obcy → 196× 403, **zero 5xx**, zero wycieków (`passwordHash`, `oauthId`, `newsletterUnsubscribeToken`, `privateNote` skanowane w każdej odpowiedzi 200). Zapisy odpalane wyłącznie tożsamościami, które muszą zostać odrzucone, i na nieistniejących id — sonda nie niszczy danych. |
| Encje przekazywane do `@Async` — runda druga | 🔵 przeczytane + naprawione | 2026-08-29 | Wyliczone **wszystkie 38** metod mailowych biorących encję; tylko **5** dereferencuje leniwą asocjację (`Reservation.getUser()/getTimeSlot()`, oba `FetchType.LAZY`). Z sześciu wywołań pięć miało jawne zabezpieczenie (`touchForAsyncMail`, `JOIN FETCH user`, albo ręczne wymuszenie z komentarzem w `deleteTimeSlot`). **`AdminService.cancelReservationByAdmin` nie miało żadnego** — działało wyłącznie dlatego, że wątek mailowy zdążał doczytać w jeszcze otwartej sesji wołającego (sprawdzone na żywo: mail przeszedł, `[mail-1]` wykonał `select … from users`). Poprawność zależna od wyścigu, nie od kontraktu. Naprawione **bez trzeciej kopii**: helper przeniesiony z `private static` w `ReservationService` na metodę encji `Reservation.touchForAsyncMail()` — które asocjacje czytelnik maila potrzebuje, jest własnością wiersza, nie któregokolwiek z serwisów. Oba serwisy wołają teraz to samo. |

### Poprawność backendu

| Obszar | Poziom | Kiedy | Czym |
|---|---|---|---|
| Rozliczenia (V92) — cała funkcja | 🔵 przeczytane + 🟢 bramki | 2026-08-31 | Przeczytane w całości zaraz po napisaniu: `domain/settlement`, `api/admin/settlement`, `SettlementSection`, `AdminSettlementsPanel`. **Jedna realna awaria:** upsert gościa kopiował cel z wiersza gościa, więc rozliczenie wydarzenia lądowało na jego dobowym slocie — pod adresem, którego żaden odczyt wydarzenia nie czyta; kwota była przyjmowana i znikała z ekranu. Naprawione, cel bierze się teraz z żądania, test regresyjny widziany na czerwono. Poza tym: nieindeksowany FK `user_id` pod `ON DELETE CASCADE` (dodany indeks), prefill odpytywany także dla wierszy z już wpisaną kwotą (pominięty), kasowanie gościa ignorujące cel w adresie (zawężone), martwe akcesory encji (usunięte), checkboxy bez nazwy per wiersz i licznik „rozliczeń" liczący w rzeczywistości wpłaty (oba poprawione). Bramki: `SettlementIsolationTest`, `SettlementNotInSharedPayloadTest`, `AdminSettlementQueryCountTest` (sekcja + zakładka) — wszystkie trzy obejrzane na czerwono. **Nieocenione:** zachowanie przy dwóch adminach edytujących tę samą kwotę naraz (upsert wygrywa ostatni, nikt tego nie testował na żywo) i wielkość odpowiedzi `/overview` przy kilkuletniej historii. |
| Strefa czasowa | 🟢 bramka | 2026-08-09 | `NoBareNowTest` (allowlista: `SitemapController`) |
| Bliźniaki slot/event | 🔵 przeczytane | 2026-08-08 | Diff linia po linii. 5 poważnych znalezisk, naprawione w 4.19.0. |
| Eksmisja cache'u kalendarza | 🔵 przeczytane | 2026-08-08 | Każda metoda mutująca vs `@CacheEvict`. |
| Adnotacje tylko na metodach publicznych | 🟢 bramka | 2026-08-09 | `SpringProxyAnnotationsTest` — pułapka „działa, a nie działa". |
| Transakcje i wyścigi | 🔵 przeczytane | 2026-08-08 | Blokady pesymistyczne, upserty na unikatach. |
| N+1 | 🔵 przeczytane | 2026-08-08 | Zapytania w pętlach, leniwe asocjacje w mapperach DTO. |
| Koszt statystyk użytkowników | 🟢 bramka | 2026-08-20 | `AdminUserStatsQueryCountTest` — 4 zapytania przy 30 kontach, budżet jako stała. Widziana na czerwono. **Znaleziono przy okazji:** zakładka dociągała nieużywaną listę kont, a klient trzymał 5-minutowy cache bez invalidacji. |
| Encje przekazywane do `@Async` | 🔵 przeczytane | 2026-08-09 | Wszystkie metody mailowe biorące encję + ich wywołania. 3 znaleziska, naprawione. |
| Escaping HTML w mailach | 🔵 przeczytane | 2026-08-09 | `esc()` w treści, celowo nie w temacie. Czysto. |
| Poczta end-to-end (treść, nie sam fakt wysyłki) | 🔵 przeczytane + sonda | 2026-08-29 | Przejechane przez Mailhoga i **obejrzane w treści**: rejestracja → link → potwierdzenie → logowanie (pełna ścieżka 200), reset hasła, potwierdzenie rezerwacji z ICS, powiadomienia admina, newsletter do 4 subskrybentów, wypis. Czysto: polskie znaki w temacie i treści, logo `cid:logo`, „ważny przez 24 godziny" zgodne ze stałą i z polityką, **ICS escapuje `;` i `,`** (`SUMMARY:Trening\; z przecinkiem\, i średnikiem`), newsletter daje **własny token per odbiorca**, nagłówki `List-Unsubscribe` + `List-Unsubscribe-Post` obecne, `GET` na wypisie **rysuje i nie wypisuje**, `POST` wypisuje w obu wariantach (RFC 8058 one-click i formularz), token działa wielokrotnie. **Znaleziono dwie rzeczy — patrz niżej.** |
| Dokąd naprawdę idzie poczta w dev | 🔵 **znalezione i naprawione** | 2026-08-29 | `bootRun` wczytuje `backend/.env` i wstrzykuje **całą** jego zawartość do środowiska (`build.gradle` 70–85), a ten plik niesie `MAIL_HOST=smtp.gmail.com` z prawdziwymi poświadczeniami. Profil `dev` nie miał sekcji `mail`, więc **każde `./gradlew bootRun` wysyłało pocztę przez skrzynkę właściciela**, przy pustym Mailhogu i logu mówiącym „Email sent". W trakcie audytu wyszły tak 4 wiadomości (na adresy `@example.com`, odbite). Ten sam kanał obsługuje „wyślij do wszystkich", który **nie ma podglądu** i nie da się cofnąć. Naprawione przez czytanie `DEV_MAIL_*` zamiast `MAIL_*` — nazwa jest całą poprawką, bo `MAIL_*` to nazwy z `.env` i z compose'a produkcyjnego. Drugie znalezisko: `app.base-url` nie był nadpisany w dev, więc linki „potwierdź adres" i „zresetuj hasło" celowały w backend (`:8080`) zamiast we front — w devie martwe, na produkcji poprawne (`APP_BASE_URL` w compose). |
| Migracje V1–V79 | 🔵 przeczytane | 2026-08-08 | Wraz z historią indeksów (dwa fałszywe alarmy odrzucone). |
| Migracje V80–V91 | 🔵 przeczytane + sonda | 2026-08-29 | Wszystkie 12 przeczytane, **zero znalezisk**. Sprawdzone: (1) ewolucja CHECK-ów V82→V85→V86→V87 — każda migracja **zdejmuje przed dodaniem**, a V87 świadomie robi to **przed** konwersją danych (docelowe style nie stoją jeszcze na starej liście, więc odwrotna kolejność nie wstałaby); (2) **żywy stan CHECK-ów zestawiony z enumami Javy** — cztery zbiory stylów (SPORT, TRAD, BOULDER, MOUNTAIN_STYLES) zgadzają się co do wartości, nie „mniej więcej"; (3) **10 sond po krawędziach przez API** (góry z wyceną bulderową, trad ze stylem sportowym, skała z A0, boulder z TR, góry z gwiazdkami, `ledPitches > pitches`, skała z polem górskim…) — **każda oddaje 400 z konkretnym komunikatem**, żadna nie schodzi do 409 z bazy, czyli Java waliduje pierwsza, a CHECK-i są realnym zapasem; (4) normalizacja OS/FLASH → `attempts = 1` potwierdzona w bazie; (5) **V81: `NOT NULL DEFAULT gen_random_uuid()` policzył się per wiersz** — 8 kont, 8 różnych tokenów wypisu (wspólny byłby katastrofą: jeden link wypisywałby wszystkich); (6) V80 `position BETWEEN 0 AND 2` zgadza się z `MAX_PER_COMMENT`, a limit 20/trening stoi w serwisie; (7) częściowy indeks feedu z V90 **pasuje do zapytania** (znosi węzeł Sort — sprawdzone przez `enable_seqscan=off`), planner pomija go tylko przy 30 wierszach; (8) **zero osieroconych kolumn** w czterech ruszanych tabelach. |
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
| Ochrona niezapisanej pracy w modalach | 🔵 przeczytane + testy | 2026-08-27 | Przeczytany `Modal.confirmClose` (3 drogi wyjścia), `useDirty`, oba istniejące wzorce liczenia „dirty"; inwentarz **wszystkich** modali z formularzem. Podpięte w formularzach treningowych + nowy `Modal.test.tsx` (11 przypadków — mechanizm nie miał **żadnego** pokrycia mimo 3 użyć produkcyjnych). **Znaleziono trzy rzeczy:** (1) raport „dirty" przez `useEffect` spóźniał się o render, więc wpisanie tytułu i Escape w tym samym ticku kasowały pracę bez pytania — `confirmClose` przyjmuje teraz funkcję pytaną w momencie kliknięcia; RTL tego nie odtwarza, złapane dopiero na żywej aplikacji. (2) Własny przycisk „Anuluj", stojący **obok „Zapisz"**, omijał guard — najbardziej prawdopodobna pomyłka była najbardziej kosztowna. (3) Naprawa (2) wprowadziła regresję w bibliotece szablonów, gdzie „Anuluj" znaczy „wróć do listy", nie „zamknij" — cofnięta, ścieżka odnotowana jako niebramkowana. **Poza zakresem:** `TrainingDetailModal`, modale kalendarza, `AdminSlotsPanel:474` (liczy `isDirty` i nie przekazuje go). |
| Pseudo-markdown (renderer, edytor, 8 miejsc renderu) | 🟢 bramka + 🔵 przeczytane + testy | 2026-08-26 | Przeczytany renderer, helpery pisania i **wszystkie** hosty renderu (także CMS, nie tylko treningi); round-trip escapowania prześledzony per pole; sonda SQL po treściach CMS pod nowe znaczniki linii (0 trafień na dev). **Znaleziono sześć rzeczy, wszystkie naprawione:** (1) `AdminMailPanel` pisze tym samym edytorem, a mail renderuje **drugi renderer w Javie**, który nie znał nowych znaczników — panel nie ma podglądu, więc `##` i `~~` zobaczyliby dopiero subskrybenci w wysyłce nie do cofnięcia; domknięte bramką `MailRichTextParityTest` (widziana czerwona). (2) Wszystkie cztery hosty treningowe straciły `whitespace-pre-wrap`, czyli wyrównanie spacjami w istniejących planach. (3) Pre-wrap wymaga, żeby renderer nie zwracał znaku końca linii — `\r` z tekstu wklejonego z Windows podwajał interlinię. (4) Lista wznowiona po nagłówku renderowała się od 1, pokazując numery, których autor nie napisał. (5) Kontynuacja listy wstawia znacznik programowo, omijając `maxLength` — Enter przy limicie kończył się 400 na zapisie. (6) Kompozytor komentarza miał `rows={1}` i `resize-none`, więc budowanie w nim listy było pisaniem przez dziurkę. **Świadomie zostawione:** `NewsPage`/`VideosPage` przycinają skrót artykułu `max-height` bez wielokropka (line-clamp nie klamruje bloków). |
| Każdy ekran × rola × motyw × szerokość × język | 🔵 obejrzane + sonda | 2026-08-29 | **395 zrzutów: 79 kombinacji (trasa × dozwolona rola) w pięciu wariantach** — dark/1440/pl, light/1440/pl, dark/390/pl, dark/1440/en, dark/1440/es. Na każdym: błędy konsoli, żądania 4xx/5xx, elementy wychodzące poza okno, kontrast policzony z kolorów obliczonych, 12 naciśnięć Tab. Bazowy przebieg obejrzany w całości, reszta przez arkusze stykowe + pełny rozmiar dla oznaczonych. **Znaleziono trzy rzeczy** (patrz trzy wiersze niżej). EN i ES nie pokazały ani jednego przepełnienia ani ucięcia — bramka `i18nParity` pilnuje kluczy, ten przebieg pilnował długości napisów. |
| Kalendarz treningowy — niezmienniki rdzenia | 🔵 przeczytane + 🟡 sonda | 2026-08-29 | **Zero znalezisk.** Przeczytane: `TrainingCalendarSection`, `TrainingBlock`, `TrainingClipboardContext`, `TrainingDaySheet`, hosty miesiąca/tygodnia/kropek. Sprawdzone punkt po punkcie: `renderPassive` obejmuje `tile` i `chip` w **obu** komponentach z `pasteActive` (`TrainingBlock` i `InvitationBlock`), a gęstość `full` celowo zostaje klikalna; wszystkie **trzy** hosty dostają `pasteActive`; `armed` liczone **jedną** wartością, obce wycięcie **kasowane**, schowek czyszczony na zmianie konta; `?cal=` jako prawo z `compactViewport` czytanym dwa razy (reaktywnie i zamrożonym); `keepWithinEntity(…, 2)`; `MONTH_GRID_DAYS = 42`; `pointer-fine:` zamiast gołego `hover:`; znaczniki notatek bramkowane `isCoachView` **dwukrotnie** (pobranie i stemplowanie); brak kolumny „pominięty". **`TrainingDaySheet` świadomie nie dostaje `pasteActive` i to jest poprawne** — przy uzbrojonym schowku kliknięcie w dzień wkleja zamiast otwierać arkusz, więc arkusz nigdy nie jest na ekranie razem ze schowkiem. Backend sondowany przez API: edycja samego tytułu ukończonego treningu **przechodzi** (guard odrzuca zmianę terminu, nie stan), przeniesienie w przyszłość → 409, nieaktualna `version` → 409, brak `version` → bez sprawdzania; `kind` **niezmienny** (PUT z `kind=TRAINING` zostawił wiersz jako TASK), zadanie z godzinami → 400, trening z kaloriami → 400, trening z jedną godziną → 400. |
| Sesja kontra strażnicy tras | 🔵 przeczytane + testy | 2026-08-29 | **Znaleziono, poważne:** niezmiennik „429 ani błąd sieci nie kończy sesji" był dotrzymany **tylko na poziomie tokenów**. Przy każdej nieudanej odpowiedzi `/user/me` `user` zostawał `null`, więc `AdminRoute` robił `Navigate to="/"`, a `ProtectedRoute` `Navigate to="/login"` — oba z `replace`, czyli **kasując adres z historii**. Nawigacja routera nie przechodzi przez `beforeunload` (udokumentowana luka), więc admin piszący artykuł tracił całą niezapisaną pracę bez pytania. Odtworzone w Playwright na trzech trybach awarii (429, brak sieci, 500) — za każdym razem lądowanie na `/` z ważnym tokenem w `localStorage`. Naprawione flagą `sessionUnknown` + ekranem z ponowieniem; 6 nowych testów, widzianych na czerwono. |
| Nieznany adres | 🔵 przeczytane + sonda | 2026-08-29 | **Znaleziono:** brak trasy `path="*"` przy `try_files … /index.html` w nginx dawał **pustą stronę z HTTP 200** — bez nagłówka, stopki i wyjścia, z zerem elementów w ścieżce Tab. Potwierdzone na produkcji (200, 2495 B, zero wystąpień „404" w treści). Soft 404 dla wyszukiwarki i ślepy zaułek dla człowieka. Dodana `NotFoundPage`. |
| Kontrast (WCAG AA) | 🟢 **zero trafień** + 🔵 przeczytane | 2026-08-29 | Zmierzone na żywej aplikacji, nie oszacowane, i **domknięte na zero w obu motywach** (18 stron × 2 motywy, każdy liść tekstowy < 18 px). Stan wyjściowy: ciemny `text-surface-600` = **2.07**, `text-primary-400` = 3.32, `text-surface-500` = 4.00; jasny plakietka **„Szkic" = 1.51**, `text-surface-500` = 3.90, zielone „Opublikowany" = 4.04. **Czego nauczył ten przebieg:** skala jest **odwrócona między motywami**, więc „podnieś o stopień" psuje jasny (linki 6.95 → 5.31, zmierzone) — poprawka musi być per motyw. I trzeba patrzeć na **rolę**, nie na token: `surface-600` to 91 obramowań wobec 30 napisów (token nietknięty, napisy przeniesione), `surface-500` odwrotnie (token podniesiony), `green-500` to 45 teł i zero tekstu (nietknięty, ruszony tylko `green-400`). Przygaszone **zostały** ikony pustych stanów, separatory `·`, puste gwiazdki i stany `disabled` — tam niski kontrast jest treścią, nie usterką. |
| Inwentarz pól tekstowych — gdzie formatowania brakuje | 🔵 przeczytane | 2026-08-26 | Przejrzane **wszystkie 17** pól swobodnego tekstu w aplikacji: kto pisze, ile znaków, gdzie się renderuje, czy ma konsumenta poza HTML-em. Formatowanie dostały opis wydarzenia (3 miejsca renderu + strona publiczna) i notatka RPE. **Znaleziono przy okazji:** frontowy generator ICS nie escapował `\n`, `,`, `;` — każdy wielolinijkowy opis wydarzenia produkował plik `.ics`, który kalendarz odrzuca; bug był starszy niż formatowanie. Reszta pól ma udokumentowany powód, żeby zostać bez formatowania (najczęściej: treść jedzie mailem jako plain text). Komentarz do przejścia (2000 znaków, wcześniej wyłącznie `line-clamp-1`) rozwija się dziś w wierszu i renderuje formatowanie — pole przestało być nieczytelne. |

### Infrastruktura

| Obszar | Poziom | Kiedy | Czym |
|---|---|---|---|
| Kopie zapasowe — czy naprawdę chodzą | 🔵 sprawdzone na produkcji | 2026-08-29 | Read-only po SSH. Osiem dobowych archiwów lokalnie (retencja 7 dni + dzisiejsze), **zero plików `.part`** (czyli żaden przebieg nie padł w połowie), `HEALTHCHECK_URL` ustawiony w `/etc/nsp-backup.env`, cron `0 3 * * *`, dysk 37%. Zdalne archiwum ma **16 dobowych kopii, 2026-08-13 → 2026-08-28** — czyli dwa razy więcej niż retencja lokalna, co jest dowodem, że naprawa `sync` → `copy` faktycznie działa (wcześniej zdalne nie mogło być starsze niż lokalne). Remote potwierdzony jako typ `crypt`. |
| SEO / routing crawlerów na produkcji | 🔵 sonda | 2026-08-29 | `hub/seo-smoke.sh` przeszedł w całości: `robots.txt` z sitemapą i bez `Disallow: /`, `sitemap.xml` z `<urlset>`, scraper social dostaje stub OG, Googlebot dostaje SPA **bez pętli meta-refresh**. Nagłówki: HSTS, CSP bez `unsafe-inline` w `script-src`, `X-Content-Type-Options`, `Referrer-Policy`. HEAD == GET na dziewięciu publicznych ścieżkach (regresja z 2026 nadal zamknięta). **Znaleziono:** nieznany adres oddaje 200 z pustą stroną — soft 404, patrz „Frontend". |
| Zachowanie pod obciążeniem — **lokalnie** | 🟡 zmierzone | 2026-08-29 | k6 z obrazu `grafana/k6` (bez instalowania niczego), backend z `APP_RATE_LIMIT_ENABLED=false`. `baseline` (5 VU): p95 **10,8 ms**, 0% błędów. `throughput` (2000 VU, odczyty z cache): **~6000 req/s**, p95 373 ms, 0,21% błędów. `stress-nocache` (400 VU, ~4400 różnych dni w URL-u, więc cache prawie nigdy nie trafia): p95 **11,6 ms**, avg 3,9 ms, **0% błędów** — ścieżka do bazy nie jest wąskim gardłem. Wszystkie progi przeszły. ⚠️ **To pomiar Maca, nie serwera** — nie zastępuje profilu produkcyjnego i nie unieważnia liczb w CLAUDE.md. **Znaleziono przy okazji:** `baseline.js` celuje domyślnie w `:8080`, a pozostałe skrypty w `:8081`, więc uruchomione bez `BASE_URL` mielą w próżnię i raportują 100% błędów przy `data_received: 0 B` — wygląda to jak awaria aplikacji, a jest literówką w domyślnym porcie. |
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
