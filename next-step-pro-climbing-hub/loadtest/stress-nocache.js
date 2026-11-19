import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE = __ENV.BASE_URL || 'http://host.docker.internal:8081';

function pad(n) { return n < 10 ? '0' + n : '' + n; }

// A random day in 2018-2030 => ~4400 different URLs.
// A pool this large means the cache (TTL 15 min) almost never hits
// => almost every request MUST go to the database.
function randomDay() {
  const y = 2018 + Math.floor(Math.random() * 13);
  const m = 1 + Math.floor(Math.random() * 12);
  const d = 1 + Math.floor(Math.random() * 28);
  return `${y}-${pad(m)}-${pad(d)}`;
}
function randomMonth() {
  const y = 2018 + Math.floor(Math.random() * 13);
  const m = 1 + Math.floor(Math.random() * 12);
  return `${y}-${pad(m)}`;
}

export const options = {
  stages: [
    { duration: '20s', target: 50 },
    { duration: '30s', target: 50 },
    { duration: '20s', target: 100 },
    { duration: '30s', target: 100 },
    { duration: '20s', target: 200 },
    { duration: '30s', target: 200 },
    { duration: '20s', target: 400 },
    { duration: '30s', target: 400 },
    { duration: '10s', target: 0 },
  ],
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<1000'],
  },
};

export default function () {
  // 50/50 day vs month, always a different date => cache miss => a database query.
  const url = Math.random() < 0.5
    ? `${BASE}/api/calendar/day/${randomDay()}`
    : `${BASE}/api/calendar/month/${randomMonth()}`;
  const res = http.get(url);
  check(res, { 'status 200': (r) => r.status === 200 });
  sleep(0.5);
}
