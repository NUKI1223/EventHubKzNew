import asyncio
from datetime import datetime, timedelta
from app.pipeline import run_sweep
from app.models import Source
from app.extractor import Candidate
from app.telegram import Post


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

class S:
    fetch_delay_seconds=0; gemini_delay_seconds=0; http_timeout_seconds=5
    gemini_api_key="k"; gemini_model="m"; batch_size=8

async def _fetch(url, timeout): return "<html/>"

def _future_cand(title="Go Meetup"):
    return Candidate(title, "two talks on go", "long enough description of the meetup",
                     datetime.now()+timedelta(days=30), "Алматы", "SmartPoint", False, ["backend"], None)


def test_sweep_publishes_valid_event(monkeypatch):
    from app import pipeline
    monkeypatch.setattr(pipeline, "parse_posts",
        lambda html, ch: [Post(ref="kz/10", text="Митап по Go 15 сентября 2099 Алматы регистрация", date=None)])
    repo, prod = FakeRepo(), FakeProducer()
    counts = asyncio.run(run_sweep(repo, prod, S(), "MANUAL", fetcher=_fetch,
                                   batch_extractor=lambda texts, k, m: [_future_cand()]))
    assert counts["candidates_published"] == 1
    assert prod.sent[0]["title"] == "Go Meetup"
    assert prod.sent[0]["sourceChannel"] == "kz"
    assert prod.sent[0]["sourceUrl"] == "https://t.me/kz/10"


def test_seen_post_skipped(monkeypatch):
    from app import pipeline
    monkeypatch.setattr(pipeline, "parse_posts", lambda html, ch: [Post(ref="kz/10", text="x", date=None)])
    repo, prod = FakeRepo(), FakeProducer(); repo.seen.add("kz/10")
    counts = asyncio.run(run_sweep(repo, prod, S(), "MANUAL", fetcher=_fetch,
                                   batch_extractor=lambda texts, k, m: [None]))
    assert counts["candidates_published"] == 0


def test_transient_batch_error_leaves_post_unseen(monkeypatch):
    from app import pipeline
    monkeypatch.setattr(pipeline, "parse_posts",
        lambda html, ch: [Post(ref="kz/10", text="Митап по Go 15 сентября 2099 Алматы регистрация", date=None)])
    def boom(*a, **k): raise RuntimeError("429 quota")
    repo, prod = FakeRepo(), FakeProducer()
    asyncio.run(run_sweep(repo, prod, S(), "MANUAL", fetcher=_fetch, batch_extractor=boom))
    assert "kz/10" not in repo.seen  # transient batch failure must not burn the post


def test_rate_limit_counted(monkeypatch):
    from app import pipeline
    monkeypatch.setattr(pipeline, "parse_posts",
        lambda html, ch: [Post(ref="kz/10", text="Митап по Go 15 сентября 2099 Алматы регистрация", date=None)])
    class _Resp: status_code = 429
    class RateLimit(Exception): response = _Resp()
    def boom(*a, **k): raise RateLimit("429")
    repo, prod = FakeRepo(), FakeProducer()
    counts = asyncio.run(run_sweep(repo, prod, S(), "MANUAL", fetcher=_fetch, batch_extractor=boom))
    assert counts["gemini_rate_limited"] == 1 and counts["gemini_errors"] == 0
    assert "kz/10" not in repo.seen
    assert repo.processed[0]["stage"] == "RATE_LIMITED"


def test_past_event_counted_dropped_past(monkeypatch):
    from app import pipeline
    monkeypatch.setattr(pipeline, "parse_posts",
        lambda html, ch: [Post(ref="kz/11", text="Митап по Go 1 января 2020 Алматы регистрация", date=None)])
    past = Candidate("Old Meetup","short desc here","full description long enough here",
                     datetime.now()-timedelta(days=10), "Алматы","Astana Hub",False,[],None)
    repo, prod = FakeRepo(), FakeProducer()
    counts = asyncio.run(run_sweep(repo, prod, S(), "MANUAL", fetcher=_fetch,
                                   batch_extractor=lambda texts, k, m: [past]))
    assert counts["extracted"] == 1 and counts["candidates_published"] == 0 and counts["dropped_past"] == 1


def test_batch_covers_multiple_posts_in_one_call(monkeypatch):
    """Two prefilter-passing posts → a SINGLE batch call returns both results."""
    from app import pipeline
    monkeypatch.setattr(pipeline, "parse_posts", lambda html, ch: [
        Post(ref="kz/1", text="Скидка 50% купи сейчас", date=None),                        # prefilter reject
        Post(ref="kz/2", text="Митап по Go 20 декабря 2099 Алматы регистрация", date=None),  # event
        Post(ref="kz/3", text="Воркшоп по Kotlin 5 ноября 2099 Астана регистрация", date=None),  # event
    ])
    calls = []
    def fake_batch(texts, k, m):
        calls.append(list(texts))
        return [_future_cand("Go Meetup"), _future_cand("Kotlin Workshop")]
    repo, prod = FakeRepo(), FakeProducer()
    counts = asyncio.run(run_sweep(repo, prod, S(), "MANUAL", fetcher=_fetch, batch_extractor=fake_batch))
    assert len(calls) == 1 and len(calls[0]) == 2         # both events in ONE call
    assert counts["candidates_published"] == 2
    stages = {p["ref"]: p["stage"] for p in repo.processed}
    assert stages["kz/1"] == "PREFILTER_REJECTED"
    assert stages["kz/2"] == "PUBLISHED" and stages["kz/3"] == "PUBLISHED"


def test_batch_respects_batch_size(monkeypatch):
    from app import pipeline
    posts = [Post(ref=f"kz/{i}", text="Митап 20 декабря 2099 Алматы регистрация", date=None) for i in range(5)]
    monkeypatch.setattr(pipeline, "parse_posts", lambda html, ch: posts)
    class S2(S): batch_size = 2
    calls = []
    def fake_batch(texts, k, m):
        calls.append(len(texts)); return [_future_cand() for _ in texts]
    repo, prod = FakeRepo(), FakeProducer()
    asyncio.run(run_sweep(repo, prod, S2(), "MANUAL", fetcher=_fetch, batch_extractor=fake_batch))
    assert calls == [2, 2, 1]  # 5 posts chunked by 2
