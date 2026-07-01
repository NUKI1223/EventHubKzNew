import http from 'k6/http';
import { Rate, Trend, Counter } from 'k6/metrics';

const GW = __ENV.GW || 'http://localhost:8180';
// seed users — bcrypt load is identical regardless of which user logs in
const USERS = [
  'aidar.kasenov@example.kz',
  'dinara.zhumabaeva@example.kz',
];
const PASS = 'password123';
const JH = { headers: { 'Content-Type': 'application/json' } };

const loginFail = new Rate('login_fail');     // 5xx / timeout / refused (any scenario)
const dLogin = new Trend('login_dur', true);
const okLogins = new Counter('logins_ok');

export const options = {
  discardResponseBodies: false,
  scenarios: {
    // ~13k logins compressed into 60s (models a 3-5k-person herd logging in)
    login_spike: {
      executor: 'ramping-arrival-rate',
      startRate: 50, timeUnit: '1s', preAllocatedVUs: 100, maxVUs: 600,
      stages: [
        { target: 100, duration: '10s' },   // warm ramp
        { target: 250, duration: '10s' },   // the crush ramps in
        { target: 250, duration: '40s' },   // hold the herd (~13k logins over the run)
      ],
      exec: 'login',
    },
    // background browse so reads are exercised under the same crush
    browse: {
      executor: 'constant-arrival-rate',
      rate: 500, timeUnit: '1s', duration: '60s',
      preAllocatedVUs: 100, maxVUs: 500,
      exec: 'browse',
    },
  },
  thresholds: {
    login_fail: ['rate==0'],  // PASS criterion: zero hard errors (login + browse)
  },
};

export function login() {
  const email = USERS[Math.floor(Math.random() * USERS.length)];
  const r = http.post(`${GW}/auth/login`, JSON.stringify({ email, password: PASS }), JH);
  dLogin.add(r.timings.duration);
  const hardFail = r.status === 0 || r.status >= 500; // refused/timeout/5xx
  loginFail.add(hardFail);
  if (r.status === 200) okLogins.add(1);
}

const TERMS = ['React','AI','DevOps','Astana','Cloud','Backend','Data','Security'];
export function browse() {
  const q = TERMS[Math.floor(Math.random() * TERMS.length)];
  const r = http.get(`${GW}/api/search?q=${q}`);
  // browse errors also count as hard failures
  loginFail.add(r.status === 0 || r.status >= 500);
}
