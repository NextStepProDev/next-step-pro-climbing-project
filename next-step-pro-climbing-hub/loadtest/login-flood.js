import http from 'k6/http';
import { check } from 'k6';

const BASE = __ENV.BASE_URL || 'http://host.docker.internal:8081';

// Losowy IP per żądanie => każde trafia do innego "kubełka" rate-limitera
// (getClientIp czyta X-Forwarded-For) => omijamy limit 15/min bez zmiany kodu.
function randomIp() {
  const o = () => Math.floor(Math.random() * 256);
  return `${1 + Math.floor(Math.random() * 223)}.${o()}.${o()}.${1 + Math.floor(Math.random() * 254)}`;
}

export const options = {
  stages: [
    { duration: '20s', target: 20 },
    { duration: '30s', target: 20 },
    { duration: '20s', target: 50 },
    { duration: '30s', target: 50 },
    { duration: '20s', target: 100 }, // tu CPU powinno już dawno klęczeć
    { duration: '30s', target: 100 },
    { duration: '10s', target: 0 },
  ],
  thresholds: {
    http_req_failed: ['rate<0.50'],
    http_req_duration: ['p(95)<15000'], // luźno — chcemy zobaczyć degradację
  },
};

// Poprawne dane => BCrypt.matches zwraca true => brak blokady konta (licznik się zeruje).
const payload = JSON.stringify({ email: 'loadtest@example.com', password: 'loadtest123' });

export default function () {
  const res = http.post(`${BASE}/api/auth/login`, payload, {
    headers: { 'Content-Type': 'application/json', 'X-Forwarded-For': randomIp() },
  });
  check(res, { 'status 200': (r) => r.status === 200 });
  // brak sleep — każdy user wali logowaniami tak szybko, jak BCrypt zdąży
}
