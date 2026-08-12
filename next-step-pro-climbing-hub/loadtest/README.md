# Testy obciążeniowe (k6)

Skrypty biją w lokalny backend — adres z `BASE_URL` (domyślnie `http://host.docker.internal:8080`
w `baseline.js`, `:8081` w pozostałych).

## Backend musi startować z wyłączonym rate limitem

`RateLimitFilter` limituje **per adres IP**, a k6 jedzie z jednego adresu — wszystkie wirtualne
maszyny wyglądają dla filtra jak jeden bardzo natarczywy klient. Odkąd filtr limituje domyślnie
(wszystko pod `/api`, a nie tylko wypisane prefiksy), dotyczy to **publicznych odczytów**, na których
stoi większość tych skryptów: `/api/calendar/**` ma 60/min, `/api/courses`, `/api/news`,
`/api/instructors` — 120/min. `baseline.js`, `throughput.js`, `stress.js`, `stress-nocache.js`
i `soak.js` przekraczają to w pierwszych sekundach.

```bash
APP_RATE_LIMIT_ENABLED=false ./gradlew bootRun   # albo ta sama zmienna w env kontenera
```

Objawem zapomnianej flagi jest lawina **429** w raporcie i próg `http_req_failed` na czerwono przy
zerowym obciążeniu CPU — apka nic nie liczyła, filtr odsyłał.

`login-flood.js` jest wyjątkiem: losuje `X-Forwarded-For` per żądanie i tą drogą i tak dostaje
osobny kubełek. Działa to lokalnie, bo bez Cloudflare filtr spada na XFF; **na produkcji ta sztuczka
nie przechodzi**, tam kluczem jest `CF-Connecting-IP`, którego origin za firewallem CF nie przyjmie
od klienta.

Flaga jest **wyłącznie** do pomiarów. Nie ustawiać jej w profilu `dev` na stałe: lokalne klikanie ma
trafiać na te same limity co produkcja, inaczej pierwsze 429 zobaczymy od użytkownika.

## Skrypty

| Plik | Co mierzy |
|---|---|
| `baseline.js` | normalny ruch, 5 VU — punkt odniesienia |
| `throughput.js` | przepustowość odczytów, które trafiają w cache |
| `stress.js` | rosnące obciążenie na mieszance publicznych endpointów |
| `stress-nocache.js` | to samo, ale z ~4400 różnych dni w URL-u, więc cache prawie nigdy nie trafia i każde żądanie idzie do bazy |
| `soak.js` | długi przebieg na cache'owanych stronach — degradacja w czasie |
| `login-flood.js` | koszt BCrypt na logowaniu |

Uruchomienie: `k6 run loadtest/baseline.js`.
