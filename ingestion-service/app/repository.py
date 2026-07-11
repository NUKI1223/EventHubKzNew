from app.models import Source, Run

class Repository:
    def __init__(self, conn):
        self.conn = conn

    def add_source(self, name, tme_url) -> Source:
        row = self.conn.execute(
            "INSERT INTO sources(name, tme_url) VALUES (%s,%s) RETURNING id, enabled, last_seen_ref",
            (name, tme_url)).fetchone()
        return Source(id=row[0], name=name, tme_url=tme_url, enabled=row[1], last_seen_ref=row[2])

    def list_sources(self, enabled_only=False) -> list[Source]:
        q = "SELECT id,name,tme_url,enabled,last_seen_ref FROM sources"
        if enabled_only: q += " WHERE enabled = TRUE"
        q += " ORDER BY id"
        return [Source(*r) for r in self.conn.execute(q).fetchall()]

    def set_source_enabled(self, source_id, enabled):
        self.conn.execute("UPDATE sources SET enabled=%s, updated_at=now() WHERE id=%s", (enabled, source_id))

    def is_post_seen(self, source_id, post_ref) -> bool:
        return self.conn.execute(
            "SELECT 1 FROM ingested_posts WHERE source_id=%s AND post_ref=%s", (source_id, post_ref)
        ).fetchone() is not None

    def mark_post_seen(self, source_id, post_ref):
        self.conn.execute(
            "INSERT INTO ingested_posts(source_id, post_ref) VALUES (%s,%s) ON CONFLICT DO NOTHING",
            (source_id, post_ref))

    def update_last_seen(self, source_id, post_ref):
        self.conn.execute("UPDATE sources SET last_seen_ref=%s, updated_at=now() WHERE id=%s", (post_ref, source_id))

    def start_run(self, trigger) -> int:
        return self.conn.execute(
            "INSERT INTO ingestion_runs(trigger) VALUES (%s) RETURNING id", (trigger,)).fetchone()[0]

    def finish_run(self, run_id, **counts):
        self.conn.execute(
            """UPDATE ingestion_runs SET finished_at=now(), sources_swept=%s, posts_fetched=%s,
               passed_prefilter=%s, extracted=%s, candidates_published=%s,
               gemini_rate_limited=%s, gemini_errors=%s, dropped_past=%s, dropped_invalid=%s,
               error=%s WHERE id=%s""",
            (counts.get("sources_swept",0), counts.get("posts_fetched",0), counts.get("passed_prefilter",0),
             counts.get("extracted",0), counts.get("candidates_published",0),
             counts.get("gemini_rate_limited",0), counts.get("gemini_errors",0),
             counts.get("dropped_past",0), counts.get("dropped_invalid",0),
             counts.get("error"), run_id))

    def record_processed(self, run_id, source_id, channel, post_ref, post_url, stage,
                         title=None, event_date=None, city=None, location=None, snippet=None):
        """Upsert the latest outcome of a single post so the admin can audit what the
        AI extracted and why each post was accepted or filtered out."""
        self.conn.execute(
            """INSERT INTO processed_posts
                   (run_id, source_id, channel, post_ref, post_url, stage, title, event_date, city, location, text_snippet)
               VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)
               ON CONFLICT (source_id, post_ref) DO UPDATE SET
                   run_id=EXCLUDED.run_id, channel=EXCLUDED.channel, post_url=EXCLUDED.post_url,
                   stage=EXCLUDED.stage, title=EXCLUDED.title, event_date=EXCLUDED.event_date,
                   city=EXCLUDED.city, location=EXCLUDED.location, text_snippet=EXCLUDED.text_snippet,
                   processed_at=now()""",
            (run_id, source_id, channel, post_ref, post_url, stage, title, event_date, city, location, snippet))

    def list_processed(self, limit=200, stage=None) -> list[dict]:
        q = ("SELECT channel, post_ref, post_url, stage, title, event_date, city, location, "
             "text_snippet, processed_at FROM processed_posts")
        params = []
        if stage:
            q += " WHERE stage=%s"
            params.append(stage)
        q += " ORDER BY processed_at DESC LIMIT %s"
        params.append(limit)
        cols = ["channel","post_ref","post_url","stage","title","event_date","city","location","snippet","processed_at"]
        return [dict(zip(cols, r)) for r in self.conn.execute(q, tuple(params)).fetchall()]

    def latest_run(self) -> Run | None:
        r = self.conn.execute(
            "SELECT id,trigger,started_at,finished_at,sources_swept,posts_fetched,"
            "passed_prefilter,extracted,candidates_published,"
            "gemini_rate_limited,gemini_errors,dropped_past,dropped_invalid,error "
            "FROM ingestion_runs ORDER BY id DESC LIMIT 1"
        ).fetchone()
        return Run(*r) if r else None
