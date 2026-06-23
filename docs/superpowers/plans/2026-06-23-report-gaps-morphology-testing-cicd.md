# Report Gaps: RU Morphology + k6 Load Test + GitHub Actions CI — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close three report claims unsupported by code — Russian search morphology, load testing, and a CI pipeline.

**Architecture:** Three independent blocks. CI is pure files. Morphology swaps the Elasticsearch analyzer on `search-service` text fields and reindexes. Load test is a standalone k6 script run via Docker against the live stack.

**Tech Stack:** Elasticsearch 8.8 built-in `russian` analyzer, Spring Data Elasticsearch, k6 (via `grafana/k6` Docker image), GitHub Actions, Maven (root `eventhubkz-parent`), Node/Vite.

## Global Constraints

- Commits authored solely by the user's account — NO Claude / Co-Authored-By / Anthropic attribution anywhere.
- `.env` MUST NEVER be staged or committed.
- Current branch is `main`; pushes go via `git push origin main:master` (do not push unless the user asks).
- Repo root Maven project is `eventhubkz-parent` (packaging `pom`) aggregating all modules — `mvn -B -ntp verify` at root builds everything.
- Blocks 2 and 3-verify need the running stack (`./start.sh`); stack uses lite profile by default.

---

### Task 1: GitHub Actions CI workflow

**Files:**
- Create: `.github/workflows/ci.yml`

**Interfaces:**
- Consumes: nothing.
- Produces: a CI workflow; no code interfaces.

- [ ] **Step 1: Create the workflow file**

Create `.github/workflows/ci.yml`:

```yaml
name: CI

on:
  push:
    branches: [ master ]
  pull_request:
    branches: [ master ]

jobs:
  backend:
    name: Backend build & tests
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
          cache: maven
      - name: Build and test (all modules)
        run: mvn -B -ntp verify

  frontend:
    name: Frontend build
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: frontend
    steps:
      - uses: actions/checkout@v4
      - name: Set up Node 20
        uses: actions/setup-node@v4
        with:
          node-version: '20'
          cache: npm
          cache-dependency-path: frontend/package-lock.json
      - name: Install dependencies
        run: npm ci
      - name: Build
        run: npm run build
```

- [ ] **Step 2: Validate YAML syntax locally**

Run:
```bash
python3 -c "import yaml,sys; yaml.safe_load(open('.github/workflows/ci.yml')); print('YAML OK')"
```
Expected: `YAML OK`

- [ ] **Step 3: Sanity-check the build commands the workflow will run**

Run (confirms the root build the `backend` job invokes is wired correctly; compile only, fast):
```bash
mvn -B -ntp -q -DskipTests compile
```
Expected: `BUILD SUCCESS` (no module errors). This does not run tests — it only proves the aggregator build resolves.

- [ ] **Step 4: Commit**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: add GitHub Actions workflow (Maven verify + frontend build)"
```

> Note: green/red status is confirmed only after the user pushes to GitHub (`git push origin main:master`); GitHub Actions cannot run locally. If `mvn verify` proves too slow/flaky in CI due to Testcontainers integration tests, narrow the backend step to `mvn -B -ntp test` — but try `verify` first, since integration tests back the report's testing claim.

---

### Task 2: Russian morphology analyzer in search-service

**Files:**
- Modify: `search-service/src/main/java/org/ngcvfb/searchservice/model/EventDocument.java` (lines 28, 31, 34)

**Interfaces:**
- Consumes: existing `searchByKeyword` match query (unchanged).
- Produces: `events` index mapped with the `russian` analyzer on `title`, `shortDescription`, `fullDescription`.

- [ ] **Step 1: Capture BEFORE behavior (baseline)**

With the stack up (`./start.sh --no-front` if not running), run:
```bash
GW=http://localhost:8180
echo "мероприятия:"; curl -s "$GW/api/search/events?query=%D0%BC%D0%B5%D1%80%D0%BE%D0%BF%D1%80%D0%B8%D1%8F%D1%82%D0%B8%D1%8F" | python3 -c "import json,sys;print('hits',json.load(sys.stdin).get('totalElements'))"
echo "разработке:"; curl -s "$GW/api/search/events?query=%D1%80%D0%B0%D0%B7%D1%80%D0%B0%D0%B1%D0%BE%D1%82%D0%BA%D0%B5" | python3 -c "import json,sys;print('hits',json.load(sys.stdin).get('totalElements'))"
```
Expected: low/zero hits (standard analyzer, no stemming) — record the numbers.

- [ ] **Step 2: Change the analyzer on the three text fields**

In `EventDocument.java`, replace each of the three occurrences of:
```java
    @Field(type = FieldType.Text, analyzer = "standard")
```
on `title` (line 28), `shortDescription` (line 31), `fullDescription` (line 34) with:
```java
    @Field(type = FieldType.Text, analyzer = "russian")
```
Leave `tags` (Keyword), `location`, and all other fields unchanged.

- [ ] **Step 3: Rebuild and redeploy search-service**

Run:
```bash
mvn -q clean package -DskipTests -pl search-service -am
docker compose build --no-cache search-service
docker rm -f search-service
docker compose up -d search-service
```
Expected: `BUILD SUCCESS`, container recreated.

- [ ] **Step 4: Recreate the index with the new mapping and reindex**

The analyzer change is a mapping change — the existing index keeps the old mapping until dropped. Run:
```bash
set -a; . ./.env; set +a
GW=http://localhost:8180
# 1) drop the old index
docker exec elasticsearch curl -s -X DELETE 'http://localhost:9200/events' >/dev/null
# 2) wait for search-service to recreate the index with the russian mapping, then reindex with admin token
sleep 5
TOKEN=$(curl -s -X POST "$GW/auth/login" -H 'Content-Type: application/json' \
  -d '{"email":"dinara.zhumabaeva@example.kz","password":"password123"}' | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
curl -s -X POST "$GW/api/events/reindex" -H "Authorization: Bearer $TOKEN" -o /dev/null -w 'reindex HTTP %{http_code}\n'
sleep 3
# 3) confirm the mapping now uses the russian analyzer
docker exec elasticsearch curl -s 'http://localhost:9200/events/_mapping?pretty' | grep -A1 '"title"'
```
Expected: reindex HTTP 200; mapping shows `"analyzer" : "russian"` on `title`.

- [ ] **Step 5: Verify AFTER behavior (acceptance)**

Run:
```bash
GW=http://localhost:8180
echo "мероприятия:"; curl -s "$GW/api/search/events?query=%D0%BC%D0%B5%D1%80%D0%BE%D0%BF%D1%80%D0%B8%D1%8F%D1%82%D0%B8%D1%8F" | python3 -c "import json,sys;print('hits',json.load(sys.stdin).get('totalElements'))"
echo "разработке:"; curl -s "$GW/api/search/events?query=%D1%80%D0%B0%D0%B7%D1%80%D0%B0%D0%B1%D0%BE%D1%82%D0%BA%D0%B5" | python3 -c "import json,sys;print('hits',json.load(sys.stdin).get('totalElements'))"
echo "React (regression):"; curl -s -o /dev/null -w 'HTTP %{http_code}\n' "$GW/api/search/events?query=React"
echo "Data Engineering (regression):"; curl -s -o /dev/null -w 'HTTP %{http_code}\n' "$GW/api/search/events?query=Data%20Engineering"
```
Expected: «мероприятия»/«разработке» now return MORE hits than the Step 1 baseline (stemming works); both regression queries return HTTP 200.

- [ ] **Step 6: Commit**

```bash
git add search-service/src/main/java/org/ngcvfb/searchservice/model/EventDocument.java
git commit -m "feat(search): use Russian analyzer for event text fields (morphology/stemming)"
```

---

### Task 3: k6 load test

**Files:**
- Create: `load-test/events-load.js`
- Create: `load-test/README.md`

**Interfaces:**
- Consumes: live gateway at `http://localhost:8180` and seeded events.
- Produces: a runnable k6 script; no code interfaces.

- [ ] **Step 1: Create the k6 script**

Create `load-test/events-load.js`:

```javascript
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
```

- [ ] **Step 2: Create the README**

Create `load-test/README.md`:

```markdown
# Нагрузочное тестирование (k6)

Скрипт `events-load.js` нагружает ключевые публичные read-эндпоинты EventHub.kz:
список мероприятий, карточку события, полнотекстовый поиск.

## Требования
- Поднятый стек: `./start.sh` (gateway на http://localhost:8180), залитые тестовые данные.
- k6 — запускается через Docker, локальная установка не нужна.

## Запуск

```bash
docker run --rm -i --network host grafana/k6 run - < load-test/events-load.js
```

Другой адрес gateway:
```bash
docker run --rm -i --network host -e GW=http://localhost:8180 grafana/k6 run - < load-test/events-load.js
```

## Результат
k6 печатает сводку: `http_req_duration` (avg / p90 / p95), `http_reqs` (RPS),
`http_req_failed`. Значения p95 и RPS используются для замены «модельных»
значений в таблице 1.2 отчёта на измеренные. Профиль нагрузки (stages) и
пороги (thresholds p95<500ms, ошибки<1%) заданы в начале скрипта.
```

- [ ] **Step 3: Run the load test against the live stack**

With the stack up, run:
```bash
docker run --rm -i --network host grafana/k6 run - < load-test/events-load.js
```
Expected: k6 runs through the stages and prints a summary. Record `http_req_duration p(95)` and `http_reqs` rate (RPS). Thresholds may pass or fail — record the real numbers either way (they replace the modeled Table 1.2 values).

- [ ] **Step 4: Commit**

```bash
git add load-test/events-load.js load-test/README.md
git commit -m "test: add k6 load test for key read endpoints"
```

---

## Self-Review

**Spec coverage:**
- Block 1 (RU morphology) → Task 2 (analyzer swap + reindex + before/after verify). ✓
- Block 2 (k6 load test) → Task 3 (script + README + run). ✓
- Block 3 (GitHub Actions CI) → Task 1 (workflow + local YAML/compile validation). ✓
- "реакт→React" explicitly out of scope per spec — no task, correct.

**Placeholder scan:** No TBD/TODO; all steps contain concrete code/commands. ✓

**Type/command consistency:** analyzer string `"russian"` consistent across Task 2; gateway URL `http://localhost:8180` consistent; reindex endpoint `POST /api/events/reindex` matches code; admin seed creds match `users.sql`. ✓
