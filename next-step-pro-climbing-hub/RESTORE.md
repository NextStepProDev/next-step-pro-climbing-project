# RESTORE — odtworzenie bazy i plików z kopii zapasowej

Runbook do wykonania **pod presją**, więc komendy są dosłowne i w kolejności.
Jak kopie powstają i dlaczego tak — `nsp-backup.sh` + sekcja „Kopie zapasowe" w `CLAUDE.md`.
Odtworzenie całego serwera od zera (Docker, NPM, firewall) — `SERVER-SETUP.md`.

> **Ćwiczenie odtwarzania jest w sekcji 5 i nie dotyka produkcji.** Zrób je raz na kwartał.
> Kopia, z której nigdy nie odtwarzano, jest nieodróżnialna od takiej, która nie działa.

---

## 0. Co gdzie leży

| Co | Gdzie lokalnie (7 dni) | Gdzie zdalnie (90 dni) |
|---|---|---|
| Baza | `/backups/db/<RRRR-MM-DD>.sql.gz` | `gdrive-crypt:db/` |
| Pliki (`/app/uploads`) | `/backups/files/<RRRR-MM-DD>.tar.gz` | `gdrive-crypt:files/` |

- Zdalny remote jest typu `crypt` — pliki są zaszyfrowane **przed** wysłaniem. Bez
  `/root/.config/rclone/rclone.conf` (hasła crypt) **nie odczytasz ich w żaden sposób**,
  także przez przeglądarkę Google Drive. Ten plik jest w managerze haseł; jeśli go nie ma,
  kopii nie ma również.
- Format zrzutu: **plain SQL** (`pg_dump` bez `-F c`), spakowany gzipem → odtwarza `psql`, nie `pg_restore`.
- Kontenery: `nsp-postgres-prod`, `nsp-backend-prod`. Wolumen plików: `nsp-app_uploads_data_prod`.

⚠️ **Odtwarzaj `psql`-em w wersji ≥ serwera, który robił zrzut** (dziś PostgreSQL 18).
PostgreSQL 17.6+ wstawia do zrzutu polecenia `\restrict` / `\unrestrict`; starszy klient
`psql` przerwie na nich z błędem składni w połowie odtwarzania. Poniższe komendy uruchamiają
`psql` **wewnątrz kontenera** `postgres:18-alpine`, więc wersja zgadza się z definicji.
⚠️ Zrzut sprzed migracji na 18 (do 2026-09-05) pochodzi z serwera 17 i wchodzi w klienta 18 bez
problemu — reguła działa w jedną stronę. Odwrotnie nie: zrzutu z 18 nie wlewaj klientem 17.

---

## 1. Wybór kopii

```bash
# Co jest lokalnie
sudo ls -la /backups/db /backups/files

# Co jest zdalnie (90 dni wstecz)
sudo rclone lsl gdrive-crypt:db
sudo rclone lsl gdrive-crypt:files
```

Jeśli potrzebnej daty nie ma lokalnie, ściągnij ją z GDrive (pobranie, nic nie kasuje):

```bash
DATE=2026-08-21
sudo rclone copy "gdrive-crypt:db/${DATE}.sql.gz"    /backups/db/
sudo rclone copy "gdrive-crypt:files/${DATE}.tar.gz" /backups/files/
```

**Sprawdź kopię, ZANIM na niej cokolwiek oprzesz** — dokładnie tym testem, którym
sprawdza ją skrypt kopii (`gunzip -t` tu nie wystarcza, patrz `CLAUDE.md`):

```bash
sudo gunzip -c /backups/db/${DATE}.sql.gz | tail -20 | grep -q 'PostgreSQL database dump complete' \
  && echo "OK: zrzut kompletny" || echo "UWAGA: zrzut obcięty — weź inną datę"

sudo tar tzf /backups/files/${DATE}.tar.gz >/dev/null \
  && echo "OK: archiwum czytelne" || echo "UWAGA: archiwum uszkodzone — weź inną datę"
```

---

## 2. Odtworzenie bazy

⚠️ **To kasuje obecną bazę.** Zanim ruszysz, zrób zrzut stanu bieżącego — nawet
uszkodzonego. Stan sprzed odtworzenia to jedyna rzecz, której nie odzyskasz nigdzie indziej.

```bash
DATE=2026-08-21
cd /home/ubuntu/nsp-app

# 2.1 Kopia bezpieczeństwa stanu obecnego (osobna nazwa, nie nadpisuje kopii dobowej)
docker compose -f docker-compose.prod.yml exec -T postgres \
  pg_dump -U nextsteppro nextsteppro | gzip > /backups/db/PRZED-ODTWORZENIEM-$(date +%F-%H%M).sql.gz

# 2.2 Zatrzymaj backend — inaczej trzyma połączenia i DROP DATABASE nie przejdzie
docker compose -f docker-compose.prod.yml stop backend

# 2.3 Pusta baza (łączymy się przez 'postgres', bo kasujemy 'nextsteppro')
docker compose -f docker-compose.prod.yml exec -T postgres \
  psql -U nextsteppro -d postgres -c "DROP DATABASE IF EXISTS nextsteppro;"
docker compose -f docker-compose.prod.yml exec -T postgres \
  psql -U nextsteppro -d postgres -c "CREATE DATABASE nextsteppro OWNER nextsteppro;"

# 2.4 Wlej zrzut. ON_ERROR_STOP=1 jest istotne: bez niego psql leci dalej po błędzie
#     i kończy komunikatem o sukcesie na bazie odtworzonej w połowie.
gunzip -c /backups/db/${DATE}.sql.gz | docker compose -f docker-compose.prod.yml exec -T postgres \
  psql -U nextsteppro -d nextsteppro -v ON_ERROR_STOP=1 -q

echo "psql zakończył się kodem: $?"   # musi być 0

# 2.5 Podnieś backend
docker compose -f docker-compose.prod.yml start backend
```

---

## 3. Odtworzenie plików (zdjęcia, załączniki, materiały)

```bash
DATE=2026-08-21
cd /home/ubuntu/nsp-app

# 3.1 Backend musi stać — trzyma pliki otwarte
docker compose -f docker-compose.prod.yml stop backend

# 3.2 Kopia bezpieczeństwa obecnego wolumenu
docker run --rm -v nsp-app_uploads_data_prod:/data:ro -v /backups/files:/backup alpine \
  tar czf "/backup/PRZED-ODTWORZENIEM-$(date +%F-%H%M).tar.gz" -C /data .

# 3.3 Wyczyść wolumen i rozpakuj archiwum
#     Czyścimy zawartość, nie usuwamy wolumenu — usunięty wolumen odtworzyłby się
#     z prawami roota, a backend chodzi jako non-root i nie zapisałby do niego nic.
docker run --rm -v nsp-app_uploads_data_prod:/data alpine \
  sh -c 'rm -rf /data/* /data/.[!.]* 2>/dev/null; true'
docker run --rm -v nsp-app_uploads_data_prod:/data -v /backups/files:/backup:ro alpine \
  tar xzf "/backup/${DATE}.tar.gz" -C /data

# 3.4 Właściciel plików musi zgadzać się z UID backendu: 1001:1001 (użytkownik `app`).
#     tar rozpakowany jako root zwykle odtwarza właściciela sam — to jest zabezpieczenie
#     na wypadek archiwum spakowanego inaczej. Zły UID nie psuje odczytu (katalogi są
#     0755), więc strona wygląda dobrze, a dopiero pierwszy upload kończy się błędem.
docker run --rm -v nsp-app_uploads_data_prod:/data alpine chown -R 1001:1001 /data

# 3.5 Podnieś backend
docker compose -f docker-compose.prod.yml start backend
```

---

## 4. Weryfikacja po odtworzeniu

**Odtworzenie nie jest zakończone, dopóki te trzy rzeczy się nie zgadzają.**

```bash
# 4.1 Schemat: numer ostatniej migracji Flyway musi odpowiadać wdrożonej wersji aplikacji
docker exec nsp-postgres-prod psql -U nextsteppro -d nextsteppro -c \
  "SELECT MAX(installed_rank) AS migracji, MAX(version) AS ostatnia FROM flyway_schema_history WHERE success;"

# 4.2 Dane: liczby wierszy w kluczowych tabelach.
#     Porównaj z tym, co widziałeś przed awarią (albo z sąsiednim dniem kopii) —
#     "0 wierszy" w users lub reservations to odtworzenie nieudane, nie puste.
docker exec nsp-postgres-prod psql -U nextsteppro -d nextsteppro -c "
  SELECT 'users' t, count(*) FROM users
  UNION ALL SELECT 'reservations',       count(*) FROM reservations
  UNION ALL SELECT 'time_slots',         count(*) FROM time_slots
  UNION ALL SELECT 'events',             count(*) FROM events
  UNION ALL SELECT 'personal_trainings', count(*) FROM personal_trainings
  UNION ALL SELECT 'climbing_ascents',   count(*) FROM climbing_ascents
  UNION ALL SELECT 'photos',             count(*) FROM photos
  UNION ALL SELECT 'activity_logs',      count(*) FROM activity_logs
  ORDER BY 1;"

# 4.3 Pliki: liczba plików w wolumenie i to, czy wiersze w bazie mają swoje pliki na dysku
docker run --rm -v nsp-app_uploads_data_prod:/data:ro alpine \
  sh -c 'echo "plików razem: $(find /data -type f | wc -l)"; du -sh /data; ls /data'
```

Na koniec — aplikacja, nie tylko baza:

```bash
# Backend zdrowy
curl -fsS https://nextsteppro.pl/actuator/health || docker logs --tail 50 nsp-backend-prod

# Publiczne endpointy odpowiadają 200 (ten sam zestaw, co smoke test deployu)
for p in "/" "/api/courses?language=pl" "/api/instructors?language=pl" "/api/calendar/month/$(date +%Y-%m)"; do
  printf '%s -> %s\n' "$p" "$(curl -sS -o /dev/null -w '%{http_code}' "https://nextsteppro.pl${p}")"
done
```

I ręcznie w przeglądarce: **otwórz galerię i jedno zdjęcie kursu** — to jedyny sposób
sprawdzenia, że baza i wolumen plików pochodzą ze spójnej pary. Baza odtworzona z 21., a pliki
z 14. dadzą komplet zielonych odpowiedzi wyżej i połamane obrazki na stronie.

---

## 5. Ćwiczenie odtwarzania (bez dotykania produkcji)

Odpala **osobny, tymczasowy** kontener Postgresa, wlewa do niego kopię, liczy wiersze
i kasuje kontener. Produkcja nie jest w żaden sposób ruszana — inny kontener, własny
katalog danych, żadnego portu na zewnątrz (wszystko idzie przez `docker exec`).

```bash
DATE=2026-08-21

# 1. Tymczasowy Postgres. Bez -p i bez wolumenu: nie publikujemy portu, którego
#    nie potrzebujemy, a dane giną razem z kontenerem (--rm).
docker run -d --rm --name nsp-restore-drill \
  -e POSTGRES_USER=nextsteppro -e POSTGRES_PASSWORD=drill -e POSTGRES_DB=nextsteppro \
  postgres:18-alpine

# 2. Poczekaj, aż wstanie
until docker exec nsp-restore-drill pg_isready -U nextsteppro -q; do sleep 1; done

# 3. Wlej kopię
gunzip -c /backups/db/${DATE}.sql.gz \
  | docker exec -i nsp-restore-drill psql -U nextsteppro -d nextsteppro -v ON_ERROR_STOP=1 -q
echo "psql zakończył się kodem: $?"   # 0 = kopia nadaje się do odtworzenia

# 4. Policz wiersze
docker exec nsp-restore-drill psql -U nextsteppro -d nextsteppro -c "
  SELECT 'users' t, count(*) FROM users
  UNION ALL SELECT 'reservations',     count(*) FROM reservations
  UNION ALL SELECT 'climbing_ascents', count(*) FROM climbing_ascents
  UNION ALL SELECT 'migracje',         count(*) FROM flyway_schema_history WHERE success
  ORDER BY 1;"

# 5. Posprzątaj
docker stop nsp-restore-drill
```

Archiwum plików sprawdzisz bez rozpakowywania czegokolwiek:

```bash
tar tzf /backups/files/${DATE}.tar.gz | head -20
tar tzf /backups/files/${DATE}.tar.gz | wc -l    # liczba plików w archiwum
```

**Zapisz wynik ćwiczenia** (data, wersja kopii, liczby wierszy) — przy następnej awarii
to jedyna liczba, z którą będziesz mieć co porównać.

---

## 6. Migracja majora Postgresa (17 → 18 i każda następna)

Major nie jest podbitką tagu: katalog danych z jednego majora **nie wystartuje** pod następnym.
Kopie to `pg_dump` w plain SQL, czyli zrzut logiczny — i to jest cała droga przejścia.

⚠️ **W 18 zmieniła się ścieżka danych w obrazie**: `PGDATA` to `/var/lib/postgresql/18/docker`,
a deklarowany `VOLUME` przeniósł się na `/var/lib/postgresql`. Wolumen zamontowany pod starą
ścieżką `/var/lib/postgresql/data` przestaje być katalogiem danych — kontener **wstaje**, `initdb`
tworzy pusty klaster obok i aplikacja odpowiada 200 na pustej bazie. Dlatego compose zmienia
**ścieżkę montowania razem z obrazem**, a wolumen dostaje **nową nazwę** (`..._pg18`): stary
zostaje nietknięty i jest planem wycofania.

```bash
cd /home/ubuntu/nsp-app
STAMP=$(date +%F-%H%M)

# 1. Świeży zrzut ze starego serwera (jeszcze działającego)
docker compose -f docker-compose.prod.yml exec -T postgres \
  pg_dump -U nextsteppro nextsteppro | gzip > /backups/db/PRZED-MIGRACJA-${STAMP}.sql.gz
gunzip -c /backups/db/PRZED-MIGRACJA-${STAMP}.sql.gz | tail -20 \
  | grep -q 'PostgreSQL database dump complete' && echo "OK: zrzut kompletny"

# 2. PRÓBA GENERALNA — nowy major w tymczasowym kontenerze, produkcja nietknięta.
#    Nie przechodź dalej, dopóki to nie wyjdzie z kodem 0.
docker volume create nsp-migracja-drill
docker run -d --rm --name nsp-migracja-drill -v nsp-migracja-drill:/var/lib/postgresql \
  -e POSTGRES_USER=nextsteppro -e POSTGRES_PASSWORD=drill -e POSTGRES_DB=nextsteppro postgres:18-alpine
until docker exec nsp-migracja-drill pg_isready -U nextsteppro -q; do sleep 1; done
gunzip -c /backups/db/PRZED-MIGRACJA-${STAMP}.sql.gz \
  | docker exec -i nsp-migracja-drill psql -U nextsteppro -d nextsteppro -v ON_ERROR_STOP=1 -q
echo "psql zakończył się kodem: $?"   # musi być 0
docker exec nsp-migracja-drill psql -U nextsteppro -d nextsteppro -c \
  "SELECT count(*) users FROM users;"
docker stop nsp-migracja-drill && docker volume rm nsp-migracja-drill

# 3. Przełączenie (compose z nowym obrazem, ścieżką i nazwą wolumenu jest już wgrany deployem)
docker compose -f docker-compose.prod.yml stop backend
docker compose -f docker-compose.prod.yml up -d postgres     # initdb w nowym wolumenie
until docker exec nsp-postgres-prod pg_isready -U nextsteppro -q; do sleep 1; done

# 4. Odtworzenie
gunzip -c /backups/db/PRZED-MIGRACJA-${STAMP}.sql.gz | docker compose -f docker-compose.prod.yml \
  exec -T postgres psql -U nextsteppro -d nextsteppro -v ON_ERROR_STOP=1 -q
echo "psql zakończył się kodem: $?"   # musi być 0

# 5. Backend z powrotem, potem CAŁA weryfikacja z sekcji 4
docker compose -f docker-compose.prod.yml start backend
docker exec nsp-postgres-prod psql -U nextsteppro -d nextsteppro -c "SELECT version();"
```

**Wycofanie** (kilka minut, dane nietknięte): przywróć w compose poprzedni obraz, starą ścieżkę
`:/var/lib/postgresql/data` i starą nazwę wolumenu, potem `up -d`. Stary klaster leży tam
przez cały czas.

⚠️ **Stary wolumen kasuj dopiero po kilku dniach** pracy na nowym majorze i po co najmniej jednej
udanej kopii dobowej z niego. Do tego czasu jest Twoim jedynym wyjściem awaryjnym.

---

## 7. Gdy coś nie działa

| Objaw | Przyczyna | Co zrobić |
|---|---|---|
| `syntax error at or near "\restrict"` | `psql` starszy niż serwer, który robił zrzut | Odtwarzaj `psql`-em w kontenerze `postgres:18-alpine` (sekcja 2.4) |
| `database "nextsteppro" is being accessed by other users` | Backend trzyma połączenia | `docker compose -f docker-compose.prod.yml stop backend` przed DROP |
| `psql` kończy 0, ale aplikacja rzuca błędami schematu | Zrzut wlany bez `ON_ERROR_STOP=1` — połowa poleceń przepadła | Powtórz od 2.3 z `ON_ERROR_STOP=1` |
| Strona działa, obrazki połamane | Baza i pliki z różnych dni | Odtwórz oba z **tej samej daty** |
| Strona działa, ale wgranie nowego pliku rzuca błędem | Zły właściciel wolumenu | `chown -R 1001:1001` (3.4) |
| `rclone` nie widzi plików na GDrive | Brak `rclone.conf` z hasłami crypt | Wgraj `/root/.config/rclone/rclone.conf` z managera haseł, `chmod 600` |
| Kopii z potrzebnego dnia nie ma nigdzie | Kopie milczały od dłuższego czasu | Sprawdź `/var/log/nsp-backup.log` i monitor healthchecks.io — patrz `CLAUDE.md`, „Cisza jest awarią" |
