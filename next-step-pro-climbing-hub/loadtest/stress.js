import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE = __ENV.BASE_URL || 'http://host.docker.internal:8081';

// Mieszanka realnych publicznych endpointów (tak jak ruch prawdziwych gości).
const ENDPOINTS = [
  '/api/courses',
  '/api/news?page=0&size=12',
  '/api/calendar/month/2026-06',
  '/api/instructors?language=pl',
];

export const options = {
  // Schodkowy ramp-up: na każdym poziomie trzymamy 30s, żeby RAM zdążył urosnąć.
  stages: [
    { duration: '20s', target: 50 },  // poziom 1
    { duration: '30s', target: 50 },
    { duration: '20s', target: 100 }, // poziom 2
    { duration: '30s', target: 100 },
    { duration: '20s', target: 200 }, // poziom 3
    { duration: '30s', target: 200 },
    { duration: '20s', target: 400 }, // poziom 4 — tu spodziewamy się zgięcia
    { duration: '30s', target: 400 },
    { duration: '10s', target: 0 },
  ],
  thresholds: {
    http_req_failed: ['rate<0.05'],    // dopuszczamy do 5% błędów, potem to już "klęk"
    http_req_duration: ['p(95)<1000'], // p95 poniżej 1s = jeszcze zdrowo
  },
  // Nie przerywaj testu gdy progi pękną — chcemy zobaczyć CAŁY przebieg do końca.
  noConnectionReuse: false,
};

export default function () {
  const url = BASE + ENDPOINTS[Math.floor(Math.random() * ENDPOINTS.length)];
  const res = http.get(url);
  check(res, { 'status 200': (r) => r.status === 200 });
  sleep(0.5); // krótsza pauza niż baseline = mocniejszy nacisk
}
