import http from 'k6/http';
import { check, sleep } from 'k6';

const GW = __ENV.GW || 'http://localhost:8180';

export const options = {
  stages: [
    { duration: '30s', target: 10 },
    { duration: '1m', target: 50 },
    { duration: '1m', target: 100 },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500'],
  },
};

// event ids known to exist in seed data
const EVENT_IDS = [3, 4, 5, 9, 10, 14, 17];
const QUERIES = ['React', 'DevOps', 'Data Engineering', 'мероприятия'];

export default function () {
  // 1) list
  const list = http.get(`${GW}/api/events`, { tags: { name: 'list' } });
  check(list, { 'list 200': (r) => r.status === 200 });

  // 2) card
  const id = EVENT_IDS[Math.floor(Math.random() * EVENT_IDS.length)];
  const card = http.get(`${GW}/api/events/${id}`, { tags: { name: 'card' } });
  check(card, { 'card 200': (r) => r.status === 200 });

  // 3) search
  const q = encodeURIComponent(QUERIES[Math.floor(Math.random() * QUERIES.length)]);
  const search = http.get(`${GW}/api/search/events?query=${q}`, { tags: { name: 'search' } });
  check(search, { 'search 200': (r) => r.status === 200 });

  sleep(1);
}
