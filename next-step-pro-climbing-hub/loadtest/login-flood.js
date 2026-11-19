import http from 'k6/http';
import { check } from 'k6';

const BASE = __ENV.BASE_URL || 'http://host.docker.internal:8081';

// A random IP per request => each lands in a different rate-limiter "bucket"
// (getClientIp reads X-Forwarded-For) => we bypass the 15/min limit without changing code.
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
    { duration: '20s', target: 100 }, // by now the CPU should long be on its knees
    { duration: '30s', target: 100 },
    { duration: '10s', target: 0 },
  ],
  thresholds: {
    http_req_failed: ['rate<0.50'],
    http_req_duration: ['p(95)<15000'], // loose — we want to see the degradation
  },
};

// Correct credentials => BCrypt.matches returns true => no account lockout (the counter resets).
const payload = JSON.stringify({ email: 'loadtest@example.com', password: 'loadtest123' });

export default function () {
  const res = http.post(`${BASE}/api/auth/login`, payload, {
    headers: { 'Content-Type': 'application/json', 'X-Forwarded-For': randomIp() },
  });
  check(res, { 'status 200': (r) => r.status === 200 });
  // no sleep — every user hammers logins as fast as BCrypt keeps up
}
