import psycopg

SCHEMA = """
CREATE TABLE IF NOT EXISTS sources (
    id BIGSERIAL PRIMARY KEY,
    name TEXT NOT NULL,
    tme_url TEXT NOT NULL UNIQUE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    last_seen_ref TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS ingested_posts (
    id BIGSERIAL PRIMARY KEY,
    source_id BIGINT NOT NULL REFERENCES sources(id) ON DELETE CASCADE,
    post_ref TEXT NOT NULL,
    processed_at TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (source_id, post_ref)
);
CREATE TABLE IF NOT EXISTS ingestion_runs (
    id BIGSERIAL PRIMARY KEY,
    trigger TEXT NOT NULL,
    started_at TIMESTAMP NOT NULL DEFAULT now(),
    finished_at TIMESTAMP,
    sources_swept INT DEFAULT 0,
    posts_fetched INT DEFAULT 0,
    passed_prefilter INT DEFAULT 0,
    extracted INT DEFAULT 0,
    candidates_published INT DEFAULT 0,
    gemini_rate_limited INT DEFAULT 0,
    gemini_errors INT DEFAULT 0,
    dropped_past INT DEFAULT 0,
    dropped_invalid INT DEFAULT 0,
    error TEXT
);
"""

# Idempotent self-migration for the per-run breakdown columns on tables that
# predate them (nullable-with-default adds are safe on populated tables).
MIGRATIONS = """
ALTER TABLE ingestion_runs ADD COLUMN IF NOT EXISTS gemini_rate_limited INT DEFAULT 0;
ALTER TABLE ingestion_runs ADD COLUMN IF NOT EXISTS gemini_errors INT DEFAULT 0;
ALTER TABLE ingestion_runs ADD COLUMN IF NOT EXISTS dropped_past INT DEFAULT 0;
ALTER TABLE ingestion_runs ADD COLUMN IF NOT EXISTS dropped_invalid INT DEFAULT 0;
"""

def connect(dsn: str) -> psycopg.Connection:
    return psycopg.connect(dsn, autocommit=True)

def create_schema(conn: psycopg.Connection) -> None:
    with conn.cursor() as cur:
        cur.execute(SCHEMA)
        cur.execute(MIGRATIONS)
