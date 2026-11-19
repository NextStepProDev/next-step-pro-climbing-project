import http from 'k6/http';
import { check } from 'k6';

const BASE = __ENV.BASE_URL || 'http://host.docker.internal:8081';

// Popular, cached pages — what a crowd of real visitors clicks.
const CACHED = [
  '/api/courses',
  '/api/news?page=0&size=12',
  '/api/calendar/month/2026-06',
  '/api/instructors?language=pl',
];

function pad(n) { return n < 10 ? '0' + n : '' + n; }
function randomDay() {
  const y = 2018 + Math.floor(Math.random() * 13);
  const m = 1 + Math.floor(Math.random() * 12);
  const d = 1 + Math.floor(Math.random() * 28);
  return `${y}-${pad(m)}-${pad(d)}`;
}

export const options = {
  scenarios: {
    soak: {
      executor: 'constant-arrival-rate', // hold a constant request RATE
      rate: 200,                         // 200 requests per second
      timeUnit: '1s',
      duration: '10m',                   // for 10 minutes
      preAllocatedVUs: 100,
      maxVUs: 400,                       // how many users it MAY enlist to hold the rate
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<1000'],
  },
};

export default function () {
  // 70% of traffic = popular (cache), 30% = a random date (database). A realistic mix.
  const url = Math.random() < 0.7
    ? BASE + CACHED[Math.floor(Math.random() * CACHED.length)]
    : `${BASE}/api/calendar/day/${randomDay()}`;
  const res = http.get(url);
  check(res, { 'status 200': (r) => r.status === 200 });
  // no sleep — constant-arrival-rate controls the pace
}
