import http from 'k6/http';
import { check, sleep } from 'k6';

// Adres lokalnej apki. Z wnętrza kontenera Dockera "localhost" oznaczałby
// sam kontener, dlatego do hosta (Twojego Maca) odwołujemy się specjalną
// nazwą host.docker.internal. Można nadpisać przez BASE_URL.
const BASE = __ENV.BASE_URL || 'http://host.docker.internal:8080';

export const options = {
  // "stages" = scenariusz natężenia ruchu w czasie.
  // target = ilu wirtualnych użytkowników (VU) ma być w danym momencie.
  stages: [
    { duration: '30s', target: 5 }, // przez 30s rośnij od 0 do 5 VU
    { duration: '30s', target: 5 }, // trzymaj 5 VU przez 30s
    { duration: '10s', target: 0 }, // łagodnie zejdź do 0
  ],

  // "thresholds" = progi zaliczenia. Jeśli przekroczone, k6 oznaczy test
  // jako FAILED — to nasze automatyczne "czy apka się trzyma".
  thresholds: {
    http_req_failed: ['rate<0.01'],   // mniej niż 1% błędów
    http_req_duration: ['p(95)<500'], // 95% odpowiedzi szybciej niż 500 ms
  },
};

// Tę funkcję każdy wirtualny użytkownik wykonuje w kółko, przez cały test.
export default function () {
  const res = http.get(`${BASE}/api/courses`); // jedno żądanie
  check(res, {
    'status 200': (r) => r.status === 200,      // sprawdź, czy odpowiedź OK
  });
  sleep(1); // poczekaj 1s — udajemy człowieka, nie karabin maszynowy
}
