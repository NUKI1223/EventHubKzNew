import json
import pytest
from app.extractor import extract_event, extract_events_batch, Candidate

BASE = "http://llm"


def _reply(payload) -> dict:
    # OpenAI-compatible chat/completions response shape
    return {"choices": [{"message": {"content": json.dumps(payload)}}]}


def test_extracts_event_fields():
    def fake_post(url, headers, json):  # noqa: A002
        return _reply({
            "isEvent": True, "title": "Go Meetup", "shortDescription": "Two talks",
            "fullDescription": "Go community meetup with two talks and networking in Almaty.",
            "eventDate": "2026-09-15T18:00:00", "city": "Алматы", "location": "SmartPoint",
            "online": False, "tags": ["backend"], "externalLink": "https://ev.kz/go"})
    c = extract_event("митап по go 15 сентября алматы", BASE, "k", "m", http_post=fake_post)
    assert isinstance(c, Candidate)
    assert c.title == "Go Meetup" and c.city == "Алматы" and c.online is False
    assert c.event_date.year == 2026 and c.tags == ["backend"]


def test_non_event_returns_none():
    def fake_post(url, headers, json):  # noqa: A002
        return _reply({"isEvent": False})
    assert extract_event("скидка 50%", BASE, "k", "m", http_post=fake_post) is None


def test_unparseable_returns_none():
    def fake_post(url, headers, json):  # noqa: A002
        return {"choices": [{"message": {"content": "not json"}}]}
    assert extract_event("x", BASE, "k", "m", http_post=fake_post) is None


def test_tz_aware_date_normalized_to_naive():
    def fake_post(url, headers, json):  # noqa: A002
        return _reply({
            "isEvent": True, "title": "T", "shortDescription": "short desc",
            "fullDescription": "full description long enough here",
            "eventDate": "2026-09-15T18:00:00+05:00", "city": "Almaty",
            "location": "X", "online": False, "tags": [], "externalLink": None})
    c = extract_event("x", BASE, "k", "m", http_post=fake_post)
    assert c.event_date is not None
    assert c.event_date.tzinfo is None and c.event_date.year == 2026 and c.event_date.hour == 18


def test_transient_http_error_propagates():
    def boom(url, headers, json):  # noqa: A002
        raise RuntimeError("HTTP 429 quota exhausted")
    with pytest.raises(RuntimeError):
        extract_event("x", BASE, "k", "m", http_post=boom)


def test_batch_extracts_and_maps_by_index():
    def fake_post(url, headers, json):  # noqa: A002
        return _reply({"events": [
            {"index": 0, "isEvent": True, "title": "Go Meetup",
             "shortDescription": "two talks", "fullDescription": "long enough description here",
             "eventDate": "2026-09-15T18:00:00", "city": "Алматы", "location": "SmartPoint",
             "online": False, "tags": ["go"], "externalLink": None},
            {"index": 1, "isEvent": False},
        ]})
    res = extract_events_batch(["go meetup post", "ad spam"], BASE, "k", "m", http_post=fake_post)
    assert len(res) == 2
    assert isinstance(res[0], Candidate) and res[0].title == "Go Meetup" and res[0].city == "Алматы"
    assert res[1] is None


def test_batch_index_out_of_order():
    def fake_post(url, headers, json):  # noqa: A002
        return _reply({"events": [
            {"index": 1, "isEvent": True, "title": "Second"},
            {"index": 0, "isEvent": False},
        ]})
    res = extract_events_batch(["a", "b"], BASE, "k", "m", http_post=fake_post)
    assert res[0] is None
    assert res[1].title == "Second"


def test_batch_transient_propagates():
    def boom(url, headers, json):  # noqa: A002
        raise RuntimeError("429")
    with pytest.raises(RuntimeError):
        extract_events_batch(["a", "b"], BASE, "k", "m", http_post=boom)


def test_batch_unparseable_returns_all_none():
    def fake_post(url, headers, json):  # noqa: A002
        return {"choices": [{"message": {"content": "not json"}}]}
    assert extract_events_batch(["a", "b"], BASE, "k", "m", http_post=fake_post) == [None, None]


def test_batch_empty_no_call():
    assert extract_events_batch([], BASE, "k", "m", http_post=lambda *a: None) == []
