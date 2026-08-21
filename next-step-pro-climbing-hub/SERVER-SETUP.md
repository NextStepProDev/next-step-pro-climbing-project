# Serwer produkcyjny Climbing — provisioning & disaster recovery

Kompletny runbook odtworzenia produkcji na czystym serwerze (Ubuntu, amd64 lub ARM/aarch64).
Warstwę systemową stawia automat `provision-server.sh`; sekrety i dane wgrywa się ręcznie
(nie mogą być w git). Wszystkie skrypty są w tym katalogu (`hub/`).

## Co jest zautomatyzowane vs ręczne

| Warstwa | Jak | Źródło |
|---|---|---|
| Docker, swap, rclone, cron, backup, logrotate, firewall CF | **`sudo bash provision-server.sh`** | repo (`hub/`) |
| `docker-compose.prod.yml` | kopiuj na serwer | repo (`hub/`) |
| `.env` (hasła DB/JWT/mail/OAuth) | ręcznie | **manager haseł** |
| `rclone.conf` (token GDrive + hasła crypt) | ręcznie | **manager haseł / stary serwer** |
| `/etc/nsp-backup.env` (`HEALTHCHECK_URL`) | ręcznie | **manager haseł / healthchecks.io** |
| NPM `data/` + `letsencrypt/` (proxy hosty + certy) | kopiuj lub re-issue | stary serwer / Let's Encrypt |
| Dane: baza + wolumen uploadów | pg_dump/restore + tar | stary serwer / backup GDrive |

## Skrypty w `hub/`

- **`provision-server.sh`** — orkiestrator całości (idempotentny, `sudo bash`)
- **`setup-swap.sh`** — 2 GB swap, swappiness 10
- **`nsp-backup.sh`** + **`nsp-backup.logrotate`** — codzienny backup: `pg_dump` + tar uploadów → `rclone copy` na zaszyfrowany Google Drive (`gdrive-crypt:`). Retencja **7 dni lokalnie, 90 dni na GDrive** (archiwa starzeją się niezależnie). Każdy artefakt jest weryfikowany przed nadaniem właściwej nazwy, a wynik przebiegu raportowany do healthchecks.io. Cron root `0 3 * * *`. **Odtwarzanie: `RESTORE.md`.**
- **`cf-origin-firewall.sh`** + **`cf-origin-firewall.service`** — hardening: port 443 tylko z zakresów IP Cloudflare, 81 (panel NPM) zablokowany, 80 otwarty (renewal certów). Reaplikuje się przy każdym boocie (usługa `After=docker`).

---

## A. Provisioning nowego serwera (od zera)

1. **Utwórz instancję** (Oracle A1 ARM 1/6 lub inną), publiczny IPv4 ON, klucz SSH `ssh-key-2026-03-29.key`, porty 80/443 otwarte w Security List. Zaloguj się: `ssh -i <klucz> ubuntu@<IP>`.

2. **Wgraj `hub/` na serwer** (lub sklonuj repo), wejdź do katalogu i:
   ```bash
   sudo bash provision-server.sh
   ```
   Stawia Docker + swap + rclone + cron + backup + firewall. Wypisze na końcu listę kroków ręcznych.

3. **Wyloguj się i zaloguj ponownie** (grupa `docker` zadziała bez `sudo`).

4. **Sekrety i config:**
   ```bash
   mkdir -p ~/nsp-app
   # docker-compose.prod.yml — z repo hub/
   # .env — z managera haseł (POSTGRES_*, JWT_SECRET, MAIL_*, OAUTH2_GOOGLE_*, GHCR_OWNER, VERSION, ADMIN_EMAIL)
   sudo mkdir -p /root/.config/rclone
   # rclone.conf -> /root/.config/rclone/rclone.conf (chmod 600), z managera haseł
   sudo rclone listremotes --long     # sanity: gdrive: drive + gdrive-crypt: crypt
   sudo rclone lsd gdrive-crypt:      # sanity: widać foldery db/ files/ = token+crypt OK

   # Monitor kopii — bez tego nieudany backup jest cichy (cron mailuje roota donikąd).
   # URL checka z healthchecks.io; plik tworzy provision-server.sh, tu wypełniasz wartość.
   sudo sh -c 'echo HEALTHCHECK_URL=\"https://hc-ping.com/<uuid>\" > /etc/nsp-backup.env'
   sudo chmod 600 /etc/nsp-backup.env
   ```

5. **Reverse proxy (NPM) — wstaje PIERWSZY** (tworzy sieć `first-aid-kit_default`):
   ```bash
   # ~/nginx-proxy-manager/ : docker-compose.yml + data/ + letsencrypt/ (kopia ze starego serwera)
   cd ~/nginx-proxy-manager && docker compose up -d
   ```
   Jeśli brak certów — wystaw nowe w panelu NPM (`<IP>:81`) po przełączeniu DNS, albo Cloudflare DNS-01.

6. **Aplikacja:**
   ```bash
   cd ~/nsp-app && docker compose -f docker-compose.prod.yml up -d
   ```

7. **Firewall** (jeśli provision odłożył start bo docker nie stał):
   ```bash
   sudo systemctl start cf-origin-firewall
   sudo iptables -S DOCKER-USER    # sanity: reguły CF + DROP 443/81
   ```

---

## B. Migracja danych ze starego serwera

```bash
# Baza (do PUSTEJ bazy na nowym — wcześniej: DROP SCHEMA public CASCADE; CREATE SCHEMA public;)
ssh old 'docker exec nsp-postgres-prod sh -c "pg_dump -U \$POSTGRES_USER -d \$POSTGRES_DB"' \
  | ssh new 'docker exec -i nsp-postgres-prod sh -c "psql -U \$POSTGRES_USER -d \$POSTGRES_DB"'

# Wolumen uploadów (zdjęcia)
ssh old 'docker run --rm -v nsp-app_uploads_data_prod:/v alpine tar -czf - -C /v .' \
  | ssh new 'docker run --rm -i -v nsp-app_uploads_data_prod:/v alpine tar -xzf - -C /v'
```
Backend przez Flyway zwaliduje odtworzony schemat (żadnych migracji do wykonania).

---

## C. Odtworzenie z BACKUPU (gdy starego serwera już nie ma)

Backupy są na Google Drive (`gdrive-crypt:`), zaszyfrowane, **90 ostatnich dni**.

**Pełny runbook odtwarzania (z weryfikacją i ćwiczeniem na tymczasowym kontenerze):
[`RESTORE.md`](./RESTORE.md).** Poniżej sam skrót do postawienia nowego serwera.

```bash
# 1. Seed lokalny z GDrive
sudo rclone copy gdrive-crypt: /backups

# 2. Sprawdź kopię, ZANIM ją wlejesz (`gunzip -t` tu nie wystarcza — patrz RESTORE.md)
sudo gunzip -c /backups/db/<DATA>.sql.gz | tail -20 | grep -q 'PostgreSQL database dump complete' \
  && echo OK || echo "zrzut obcięty — weź inną datę"

# 3. Baza z wybranego dumpa (ON_ERROR_STOP: bez tego psql kończy sukcesem
#    na bazie odtworzonej w połowie)
gunzip -c /backups/db/<DATA>.sql.gz \
  | docker exec -i nsp-postgres-prod sh -c 'psql -U $POSTGRES_USER -d $POSTGRES_DB -v ON_ERROR_STOP=1'

# 4. Uploady z tarballa Z TEJ SAMEJ DATY co baza
docker run --rm -i -v nsp-app_uploads_data_prod:/v alpine tar -xzf - -C /v \
  < /backups/files/<DATA>.tar.gz
docker run --rm -v nsp-app_uploads_data_prod:/v alpine chown -R 1001:1001 /v
```

---

## D. Weryfikacja (po wszystkim)

```bash
# Przez Cloudflare (realny ruch)
curl -sI https://nextsteppro.pl/ | head -1
curl -s -o /dev/null -w "%{http_code}\n" https://nextsteppro.pl/api/settings/home

# Firewall działa (bezpośredni 443 spoza CF = timeout, przez CF = 200)
curl --resolve nextsteppro.pl:443:<NEW_IP> --connect-timeout 8 https://nextsteppro.pl/ ; # -> timeout = dobrze

# Backup działa (ręczny test — zapisze świeży backup na GDrive i pingnie monitor)
sudo /usr/local/bin/nsp-backup.sh && sudo tail -8 /var/log/nsp-backup.log

# Monitor kopii jest podpięty (pusto = nieudany backup będzie cichy)
sudo grep -q '^HEALTHCHECK_URL=' /etc/nsp-backup.env && echo "monitor OK" || echo "BRAK MONITORA"

# Rotacja logu kopii jest wpięta
logrotate -d /etc/logrotate.d/nsp-backup 2>&1 | tail -3
```

---

## Pułapki (gotchas)

- **`rules.v4` NIE kopiować między serwerami** — zawiera host-specific nazwy mostków Docker (`br-...`). Firewall CF odtwarza reguły dynamicznie przez usługę systemd; `netfilter-persistent` trzyma tylko baseline INPUT.
- **NPM przed aplikacją** — frontend podpina się do sieci `first-aid-kit_default`, którą tworzy NPM (`external: true` w compose aplikacji).
- **Kopie idą na GDrive przez `rclone copy`, nigdy `sync`** — `sync` mirroruje lokalny katalog, więc lokalne przycinanie po 7 dniach kasowałoby też zdalne archiwum i 90-dniowa retencja nie mogłaby istnieć. Skutek uboczny: seed `/backups` na nowym serwerze przestał być krokiem krytycznym (pusty katalog nie kasuje już niczego zdalnie), ale nadal warto go zrobić, żeby mieć z czego odtwarzać lokalnie.
- **Cloudflare proxy ON** → cutover = zmiana rekordu A na nowe IP (TTL nieistotny, edge przełącza się od razu). Origin przyjmuje 443 tylko od Cloudflare (firewall).
- **A1 to ARM** — obrazy z GHCR muszą być multi-arch (CI z `platforms: linux/amd64,linux/arm64`).
- **Obraz A1 nie ma `cron`** domyślnie — `provision-server.sh` go instaluje.
