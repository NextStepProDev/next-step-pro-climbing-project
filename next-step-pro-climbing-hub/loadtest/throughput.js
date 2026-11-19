import http from 'k6/http';
import { check } from 'k6';

const BASE = __ENV.BASE_URL || 'http://host.docker.internal:8081';

const CACHED = [
  '/api/courses',
  '/api/news?page=0&size=12',
  '/api/calendar/month/2026-06',
  '/api/instructors?language=pl',
];

export const options = {
  stages: [
    { duration: '20s', target: 500 },
    { duration: '30s', target: 500 },
    { duration: '20s', target: 1000 },
    { duration: '30s', target: 1000 },
    { duration: '20s', target: 2000 }, // full wave
    { duration: '40s', target: 2000 },
    { duration: '10s', target: 0 },
  ],
  thresholds: {
    // Loose thresholds — this is a breaking test, we want to see the WHOLE run.
    http_req_failed: ['rate<0.50'],
    http_req_duration: ['p(95)<5000'],
  },
};

export default function () {
  const res = http.get(BASE + CACHED[Math.floor(Math.random() * CACHED.length)]);
  check(res, { 'status 200': (r) => r.status === 200 });
  // NO sleep — every user hammers as fast as the app answers (max pressure).
}
