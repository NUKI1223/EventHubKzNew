import asyncio
from app.pipeline import run_sweep
from app.models import Source

class FakeRepo:
    def __init__(self):
        self.seen=set(); self.published=[]; self.runs=[]; self.last={}
    def start_run(self,t): self.runs.append(t); return 1
    def list_sources(self, enabled_only=False): return [Source(1,"KZ","https://t.me/s/kz",True,None)]
    def is_post_seen(self,sid,ref): return ref in self.seen
    def mark_post_seen(self,sid,ref): self.seen.add(ref)
    def update_last_seen(self,sid,ref): self.last[sid]=ref
    def finish_run(self, rid, **c): self.finished=c

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
    class S: fetch_delay_seconds=0; http_timeout_seconds=5; gemini_api_key="k"; gemini_model="m"
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
    class S: fetch_delay_seconds=0; http_timeout_seconds=5; gemini_api_key="k"; gemini_model="m"
    counts = asyncio.run(run_sweep(repo, prod, S(), "MANUAL", fetcher=fake_fetch,
                                   extractor=lambda *a, **k: None))
    assert counts["candidates_published"] == 0
