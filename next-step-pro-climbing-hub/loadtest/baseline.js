import http from 'k6/http';
import { check, sleep } from 'k6';

// The local app's address. From inside a Docker container "localhost" would mean
// the container itself, so the host (your Mac) is reached through the special
// name host.docker.internal. Can be overridden with BASE_URL.
const BASE = __ENV.BASE_URL || 'http://host.docker.internal:8080';

export const options = {
  // "stages" = the traffic intensity scenario over time.
  // target = how many virtual users (VUs) there should be at a given moment.
  stages: [
    { duration: '30s', target: 5 }, // ramp from 0 to 5 VUs over 30s
    { duration: '30s', target: 5 }, // hold 5 VUs for 30s
    { duration: '10s', target: 0 }, // ease down to 0
  ],

  // "thresholds" = pass criteria. If exceeded, k6 marks the test
  // as FAILED — our automatic "is the app holding up".
  thresholds: {
    http_req_failed: ['rate<0.01'],   // fewer than 1% errors
    http_req_duration: ['p(95)<500'], // 95% of responses faster than 500 ms
  },
};

// Every virtual user runs this function over and over, for the whole test.
export default function () {
  const res = http.get(`${BASE}/api/courses`); // one request
  check(res, {
    'status 200': (r) => r.status === 200,      // check the response is OK
  });
  sleep(1); // wait 1s — we imitate a person, not a machine gun
}
