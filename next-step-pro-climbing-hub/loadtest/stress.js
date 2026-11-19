import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE = __ENV.BASE_URL || 'http://host.docker.internal:8081';

// A mix of real public endpoints (like real visitor traffic).
const ENDPOINTS = [
  '/api/courses',
  '/api/news?page=0&size=12',
  '/api/calendar/month/2026-06',
  '/api/instructors?language=pl',
];

export const options = {
  // Stepped ramp-up: we hold each level for 30s so RAM has time to grow.
  stages: [
    { duration: '20s', target: 50 },  // level 1
    { duration: '30s', target: 50 },
    { duration: '20s', target: 100 }, // level 2
    { duration: '30s', target: 100 },
    { duration: '20s', target: 200 }, // level 3
    { duration: '30s', target: 200 },
    { duration: '20s', target: 400 }, // level 4 — this is where we expect it to buckle
    { duration: '30s', target: 400 },
    { duration: '10s', target: 0 },
  ],
  thresholds: {
    http_req_failed: ['rate<0.05'],    // we allow up to 5% errors, beyond that it is "on its knees"
    http_req_duration: ['p(95)<1000'], // p95 under 1s = still healthy
  },
  // Do not abort the test when thresholds break — we want to see the WHOLE run to the end.
  noConnectionReuse: false,
};

export default function () {
  const url = BASE + ENDPOINTS[Math.floor(Math.random() * ENDPOINTS.length)];
  const res = http.get(url);
  check(res, { 'status 200': (r) => r.status === 200 });
  sleep(0.5); // a shorter pause than baseline = more pressure
}
