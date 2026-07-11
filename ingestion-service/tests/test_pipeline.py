import asyncio
from app.pipeline import run_sweep
from app.models import Source

class FakeRepo:
    def __init__(self):
        self.seen=set(); self.published=[]; self.runs=[]; self.last={}; self.processed=[]
    def start_run(self,t): self.runs.append(t); return 1
    def list_sources(self, enabled_only=False): return [Source(1,"KZ","https://t.me/s/kz",True,None)]
    def is_post_seen(self,sid,ref): return ref in self.seen
    def mark_post_seen(self,sid,ref): self.seen.add(ref)
    def update_last_seen(self,sid,ref): self.last[sid]=ref
    def finish_run(self, rid, **c): self.finished=c
    def record_processed(self, run_id, source_id, channel, post_ref, post_url, stage, **k):
        self.processed.append({"ref": post_ref, "stage": stage, **k})

class FakeProducer:
    def __init__(self): self.sent=[]
    def send(self, topic, key, value, headers=None): self.sent.append(value)
    def flush(self): pass

def test_sweep_publishes_valid_event(monkeypatch):
    from app import pipeline
    async def fake_fetch(url, timeout): return "<html/>"
    def fake_parse(html, channel):
        from app.telegram import Post
        return [Post(ref="kz/10", text="Митап по Go 15 сентября 2026 Алматы регистрация", date=None)]
    def fake_extract(text, key, model, http_post=None):
        from app.extractor import Candidate
        from datetime import datetime
        return Candidate("Go Meetup","two talks on go","long enough description of the meetup",
                         datetime(2026,9,15,18), "Алматы","SmartPoint",False,["backend"],None)
    monkeypatch.setattr(pipeline, "parse_posts", fake_parse)
    repo, prod = FakeRepo(), FakeProducer()
    class S: fetch_delay_seconds=0; gemini_delay_seconds=0; http_timeout_seconds=5; gemini_api_key="k"; gemini_model="m"
    counts = asyncio.run(run_sweep(repo, prod, S(), "MANUAL", fetcher=fake_fetch, extractor=fake_extract))
    assert counts["candidates_published"] == 1
    assert prod.sent[0]["title"] == "Go Meetup"
    assert prod.sent[0]["sourceChannel"] == "kz"
    assert prod.sent[0]["sourceUrl"] == "https://t.me/kz/10"

def test_seen_post_skipped(monkeypatch):
    from app import pipeline
    async def fake_fetch(url, timeout): return "<html/>"
    def fake_parse(html, channel):
        from app.telegram import Post
        return [Post(ref="kz/10", text="x", date=None)]
    monkeypatch.setattr(pipeline, "parse_posts", fake_parse)
    repo, prod = FakeRepo(), FakeProducer(); repo.seen.add("kz/10")
    class S: fetch_delay_seconds=0; gemini_delay_seconds=0; http_timeout_seconds=5; gemini_api_key="k"; gemini_model="m"
    counts = asyncio.run(run_sweep(repo, prod, S(), "MANUAL", fetcher=fake_fetch,
                                   extractor=lambda *a, **k: None))
    assert counts["candidates_published"] == 0


def test_transient_extract_error_leaves_post_unseen(monkeypatch):
    from app import pipeline
    async def fake_fetch(url, timeout): return "<html/>"
    def fake_parse(html, channel):
        from app.telegram import Post
        return [Post(ref="kz/10", text="Митап по Go 15 сентября 2026 Алматы регистрация", date=None)]
    def boom(*a, **k): raise RuntimeError("429 quota")
    monkeypatch.setattr(pipeline, "parse_posts", fake_parse)
    repo, prod = FakeRepo(), FakeProducer()
    class S: fetch_delay_seconds=0; gemini_delay_seconds=0; http_timeout_seconds=5; gemini_api_key="k"; gemini_model="m"
    asyncio.run(run_sweep(repo, prod, S(), "MANUAL", fetcher=fake_fetch, extractor=boom))
    assert "kz/10" not in repo.seen  # transient failure must not burn the post


def test_rate_limit_counted(monkeypatch):
    from app import pipeline
    async def fake_fetch(url, timeout): return "<html/>"
    def fake_parse(html, channel):
        from app.telegram import Post
        return [Post(ref="kz/10", text="Митап по Go 15 сентября 2026 Алматы регистрация", date=None)]
    class _Resp: status_code = 429
    class RateLimit(Exception): response = _Resp()
    def boom(*a, **k): raise RateLimit("429")
    monkeypatch.setattr(pipeline, "parse_posts", fake_parse)
    repo, prod = FakeRepo(), FakeProducer()
    class S: fetch_delay_seconds=0; gemini_delay_seconds=0; http_timeout_seconds=5; gemini_api_key="k"; gemini_model="m"
    counts = asyncio.run(run_sweep(repo, prod, S(), "MANUAL", fetcher=fake_fetch, extractor=boom))
    assert counts["gemini_rate_limited"] == 1 and counts["gemini_errors"] == 0
    assert "kz/10" not in repo.seen  # rate-limited post deferred, not burned


def test_past_event_counted_dropped_past(monkeypatch):
    from app import pipeline
    from app.extractor import Candidate
    from datetime import datetime, timedelta
    async def fake_fetch(url, timeout): return "<html/>"
    def fake_parse(html, channel):
        from app.telegram import Post
        return [Post(ref="kz/11", text="Митап по Go 1 января 2020 Алматы регистрация", date=None)]
    def past_extract(*a, **k):
        return Candidate("Old Meetup","short desc here","full description long enough here",
                         datetime.now()-timedelta(days=10), "Алматы","Astana Hub",False,[],None)
    monkeypatch.setattr(pipeline, "parse_posts", fake_parse)
    repo, prod = FakeRepo(), FakeProducer()
    class S: fetch_delay_seconds=0; gemini_delay_seconds=0; http_timeout_seconds=5; gemini_api_key="k"; gemini_model="m"
    counts = asyncio.run(run_sweep(repo, prod, S(), "MANUAL", fetcher=fake_fetch, extractor=past_extract))
    assert counts["extracted"] == 1 and counts["candidates_published"] == 0 and counts["dropped_past"] == 1


def test_records_per_post_stages(monkeypatch):
    from app import pipeline
    from app.extractor import Candidate
    from datetime import datetime, timedelta
    async def fake_fetch(url, timeout): return "<html/>"
    def fake_parse(html, channel):
        from app.telegram import Post
        return [
            Post(ref="kz/1", text="Скидка 50% купи сейчас", date=None),                       # prefilter reject
            Post(ref="kz/2", text="Митап по Go 20 декабря 2099 Алматы регистрация", date=None), # published
        ]
    def fake_extract(text, key, model, http_post=None):
        return Candidate("Go Meetup","short desc here","full description long enough here",
                         datetime.now()+timedelta(days=20), "Алматы","Astana Hub",False,[],None)
    monkeypatch.setattr(pipeline, "parse_posts", fake_parse)
    repo, prod = FakeRepo(), FakeProducer()
    class S: fetch_delay_seconds=0; gemini_delay_seconds=0; http_timeout_seconds=5; gemini_api_key="k"; gemini_model="m"
    asyncio.run(run_sweep(repo, prod, S(), "MANUAL", fetcher=fake_fetch, extractor=fake_extract))
    stages = {p["ref"]: p["stage"] for p in repo.processed}
    assert stages["kz/1"] == "PREFILTER_REJECTED"
    assert stages["kz/2"] == "PUBLISHED"
    published = next(p for p in repo.processed if p["ref"] == "kz/2")
    assert published["title"] == "Go Meetup"
