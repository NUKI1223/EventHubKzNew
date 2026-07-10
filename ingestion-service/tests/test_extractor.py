import json
from app.extractor import extract_event, Candidate

def _gemini_reply(payload: dict) -> dict:
    # shape of Gemini generateContent response
    return {"candidates": [{"content": {"parts": [{"text": json.dumps(payload)}]}}]}

def test_extracts_event_fields():
    def fake_post(url, headers, json):  # noqa: A002
        return _gemini_reply({
            "isEvent": True, "title": "Go Meetup", "shortDescription": "Two talks",
            "fullDescription": "Go community meetup with two talks and networking in Almaty.",
            "eventDate": "2026-09-15T18:00:00", "city": "Алматы", "location": "SmartPoint",
            "online": False, "tags": ["backend"], "externalLink": "https://ev.kz/go"})
    c = extract_event("митап по go 15 сентября алматы", "k", "m", http_post=fake_post)
    assert isinstance(c, Candidate)
    assert c.title == "Go Meetup" and c.city == "Алматы" and c.online is False
    assert c.event_date.year == 2026 and c.tags == ["backend"]

def test_non_event_returns_none():
    def fake_post(url, headers, json):  # noqa: A002
        return _gemini_reply({"isEvent": False})
    assert extract_event("скидка 50%", "k", "m", http_post=fake_post) is None

def test_unparseable_returns_none():
    def fake_post(url, headers, json):  # noqa: A002
        return {"candidates": [{"content": {"parts": [{"text": "not json"}]}}]}
    assert extract_event("x", "k", "m", http_post=fake_post) is None


def test_tz_aware_date_normalized_to_naive():
    def fake_post(url, headers, json):  # noqa: A002
        return _gemini_reply({
            "isEvent": True, "title": "T", "shortDescription": "short desc",
            "fullDescription": "full description long enough here",
            "eventDate": "2026-09-15T18:00:00+05:00", "city": "Almaty",
            "location": "X", "online": False, "tags": [], "externalLink": None})
    c = extract_event("x", "k", "m", http_post=fake_post)
    assert c.event_date is not None
    assert c.event_date.tzinfo is None and c.event_date.year == 2026 and c.event_date.hour == 18


def test_transient_http_error_propagates():
    import pytest
    def boom(url, headers, json):  # noqa: A002
        raise RuntimeError("HTTP 429 quota exhausted")
    with pytest.raises(RuntimeError):
        extract_event("x", "k", "m", http_post=boom)
