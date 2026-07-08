# AI ingestion-service — design

Date: 2026-07-08. Status: approved by user.

## Goal

Solve the aggregator's cold-start problem: fill the event catalog with real,
current IT events **before** any live organizer publishes, by automatically
reading public Telegram channels, extracting structured events from their posts
with an LLM (filtering out ads and noise), deduplicating, and feeding the results
into the platform's existing moderation queue for an admin to approve.

This is the platform's first Python service and its first external-content
integration. It is the highest-leverage feature for the startup track: it both
seeds the catalog and is a genuine differentiator no local competitor offers.

## Scope decisions (made with the user)

- **Telegram access:** read public channels via their **web mirror** (`t.me/s/<channel>`)
  — no account, no auth, zero account-ban risk. Trade-off accepted: fragile
  (breaks if Telegram changes the preview markup) and unavailable for channels
  that disabled web preview. Chosen over MTProto (which can read any public
  channel but is a ToS gray area with account-ban risk).
- **Sources:** stored in a DB table, managed through a new admin UI (CRUD +
  enable/disable).
- **MVP scope:** Telegram only, but the **full pipeline** including deduplication.
- **Out of scope (later specs):** Instagram and websites (each needs its own
  parser); embedding-based dedup (MVP uses a normalized composite key); auto-approval
  of high-confidence candidates (everything goes through the admin for now).

## Architecture: a separate Python microservice

`ingestion-service` (FastAPI, port **8092**) runs in the same `docker-compose`, on
the same `eventhub-network`, with its own `ingestion_db` (a new database on an
existing Postgres instance, mirroring how `audit_db` reuses `postgres-notifications`).

It does **not** register in Eureka (that is Java/Spring-specific). Consequently the
gateway route to it **cannot use `lb://`** — it targets the container directly by
Docker DNS: `uri: http://ingestion-service:8092` (contrast with the `lb://SERVICE`
form every Java route uses). It talks to the rest of the platform through exactly
two channels, both already ubiquitous here:

- **HTTP** — the admin "run now" trigger reaches it through the API gateway.
- **Kafka** — it publishes extracted candidates to a new topic; `event-service`
  consumes them.

The language boundary runs over the network (a Kafka message format), not inside a
process. No JNI, no embedding. In résumé terms: polyglot microservices, Python
ingestion + Java core over Kafka.

**Python stack:** FastAPI, httpx + BeautifulSoup (fetch/parse `t.me/s`),
APScheduler (cron), Google Gemini via its REST API (same model the Java side uses,
`gemini-2.5-flash-lite`), `prometheus_client` (metrics), psycopg (Postgres),
pytest (tests).

## The pipeline (heart of the service)

For each enabled source, per run:

```
fetch https://t.me/s/<channel>  →  parse posts (text, date, links, post ref/id)
skip posts already seen (ingested_posts by source_id + post_ref)   ← post-level dedup
cheap pre-filter (NO LLM): does the post text contain a date-like pattern
    AND event keywords (митап/конференция/хакатон/воркшоп/регистрация/…)?
    no → ad/noise, drop
Gemini extraction (strict JSON): { isEvent, title, shortDescription, eventDate,
    city, location, online, tags[], externalLink } or { isEvent: false }  ← 2nd ad/noise gate
    null for anything not in the post (no hallucinated fields)
validate: isEvent true; eventDate parseable and in the future; city sane
publish to Kafka topic `event.candidate.found`
record post_ref in ingested_posts; advance the source's last_seen_ref
```

The cheap pre-filter is economically essential: Gemini's free tier is ~1000
requests/day, so running every post through the LLM would exhaust the quota on a
couple of active channels. The pre-filter drops the obvious non-events before the
LLM; only survivors cost a Gemini call. The extraction's `isEvent:false` is the
second gate — it catches noise that looked event-like to the heuristic.

## Data model — `ingestion_db`

- `sources` — `{ id, name, tme_url, enabled, last_seen_ref, created_at, updated_at }`.
  `tme_url` is the `https://t.me/s/<channel>` URL; `last_seen_ref` is the newest
  processed post reference (incremental reads — only newer posts each run).
- `ingested_posts` — `{ id, source_id, post_ref, processed_at }`, unique on
  `(source_id, post_ref)`. Post-level dedup: a given post is extracted at most once.
- `ingestion_runs` — `{ id, trigger (MANUAL|SCHEDULED), started_at, finished_at,
  sources_swept, posts_fetched, passed_prefilter, extracted, candidates_published,
  error }`. Feeds the admin "last run status".

## Candidate → moderation queue (reuses EventRequest)

`event-service` consumes `event.candidate.found` and creates an
`EventRequest(status=PENDING)` — the same queue human organizers submit into, so
the existing admin panel and `approveRequest` → `Event` path work unchanged.

Changes to `EventRequest` (event-service):
- Add `source` enum (`MANUAL` default / `AI_INGEST`), `sourceUrl` (link to the
  original post), `sourceChannel` (human-readable channel name).
- `requesterId` becomes nullable (an AI candidate has no organizer).
- **Guard:** on approve/reject of an `AI_INGEST` request (`requesterId == null`),
  do **not** publish the `event-request.reviewed` Kafka notification — there is no
  organizer to notify. The audit trail still records the review (set 2 / audit
  service is unaffected — those topics are separate).

**Event-level dedup** lives in the `event-service` consumer, where the Event and
EventRequest data already are: before creating an `EventRequest`, compute a
normalized key `lower(trim(title)) + eventDate::date + lower(city)` and check it
against existing `Event`s and PENDING `EventRequest`s; if it matches, skip
(the same event posted across five channels collapses to one candidate).
The ingestion-service does only post-level dedup; the platform-wide event-level
dedup is the consumer's job.

`event.candidate.found` payload (new DTO in `eventhubkz-common`): title,
shortDescription, eventDate, city, location, online, tags, externalLink,
sourceUrl, sourceChannel.

## Triggers and admin UI

- **"Run now"** — a button in the new admin Sources tab → `POST /api/ingestion/run`
  through the gateway (admin-only, `X-User-Role` check, same pattern as other admin
  endpoints) → the service runs a sweep and returns the run summary.
- **Scheduled** — APScheduler inside the service runs a sweep on a cron schedule
  (default daily; configurable via env).
- **New admin "Sources" tab** in `AdminDashboard`: CRUD over sources (add /
  enable / disable), a "run now" button, and the last-run status from
  `ingestion_runs`. Ingested candidates appear in the existing "Requests" tab,
  visually marked "found by parser from channel X" with a link to the original
  post (`sourceUrl`). RU/KK localized.

Gateway routes: `POST /api/ingestion/run` and the sources CRUD
(`/api/ingestion/sources/**`) → `ingestion-service`, all admin-only via
`AuthenticationFilter` + role check.

## Observability and politeness

- **Metrics** (`prometheus_client`, scraped like the Java services): posts
  fetched, passed pre-filter, extracted, deduped, candidates published, Gemini
  errors, per run.
- **Politeness to Telegram:** a delay between source fetches, request timeouts,
  and exponential backoff on transient failures. Read only public channels.
- **Resilience:** a failing source doesn't abort the whole run (per-source
  try/except, recorded in `ingestion_runs.error`); Gemini failure on one post
  skips that post, not the run.

## Testing

- **Python (pytest):**
  - HTML parser: a saved `t.me/s` fixture page → the expected list of posts
    (text, date, ref).
  - Pre-filter: event-like text passes, ad/noise text is dropped.
  - Gemini extraction: mocked LLM response → strict JSON parsing, `null` handling,
    `isEvent:false` path (no hallucinated fields leak through).
  - Dedup key normalization.
  - Incremental reads: posts at/below `last_seen_ref` are skipped.
- **Java (event-service):** consumer creates `EventRequest(source=AI_INGEST)`;
  event-level dedup skips a duplicate; no `event-request.reviewed` notification is
  published when `requesterId` is null.
- **Integration:** end-to-end with a fixture channel → candidate → EventRequest.

## Pitfalls carried from the assessment (design mitigations)

- **Telegram fragility / coverage:** `t.me/s` markup can change and some channels
  disable web preview — accepted MVP limitation; the parser is isolated in one
  module so a markup fix is localized, and a per-source failure is logged not fatal.
- **LLM cost:** the pre-filter gates the LLM; extraction uses `flash-lite`.
- **Hallucinated fields:** strict JSON, `null` for absent data, validation
  (future date, sane city) before publishing.
- **Duplicates:** two-layer dedup (post-level in ingestion, event-level in the
  consumer).
- **Moderation load:** candidates land in the existing PENDING queue marked with
  their source; auto-approval is deliberately out of scope so the admin stays in
  control.
- **Legal/attribution:** every candidate keeps `sourceUrl`/`sourceChannel`;
  republishing others' content and images is an accepted MVP risk for a
  pet-project, revisited before a public launch.
