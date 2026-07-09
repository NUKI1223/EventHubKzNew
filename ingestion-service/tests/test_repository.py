import os, psycopg, pytest
from app.db import create_schema
from app.repository import Repository

TEST_DSN = os.getenv("TEST_DB_URL", "postgresql://postgres:postgres@localhost:5436/ingestion_db")

@pytest.fixture
def repo():
    conn = psycopg.connect(TEST_DSN, autocommit=False)
    create_schema(conn)
    conn.execute("TRUNCATE sources, ingested_posts, ingestion_runs RESTART IDENTITY CASCADE")
    yield Repository(conn)
    conn.rollback()
    conn.close()

def test_add_and_list_sources(repo):
    s = repo.add_source("KZ Dev", "https://t.me/s/kzdev")
    assert s.id is not None and s.enabled is True
    assert [x.name for x in repo.list_sources()] == ["KZ Dev"]

def test_post_seen_dedup(repo):
    s = repo.add_source("KZ Dev", "https://t.me/s/kzdev")
    assert repo.is_post_seen(s.id, "kzdev/10") is False
    repo.mark_post_seen(s.id, "kzdev/10")
    assert repo.is_post_seen(s.id, "kzdev/10") is True

def test_run_lifecycle(repo):
    rid = repo.start_run("MANUAL")
    repo.finish_run(rid, sources_swept=1, posts_fetched=5, passed_prefilter=3,
                    extracted=2, candidates_published=2, error=None)
    latest = repo.latest_run()
    assert latest.candidates_published == 2 and latest.trigger == "MANUAL"
