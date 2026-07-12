from datetime import datetime, timedelta
from app.extractor import Candidate
from app.validate import to_valid_candidate

def _cand(**kw):
    base = dict(title="Go Meetup", short_description="Two talks on Go and tooling",
                full_description="A long enough description of the meetup for validation.",
                event_date=datetime.now()+timedelta(days=5), city="Алматы", location="SmartPoint",
                online=False, tags=["backend"], external_link=None)
    base.update(kw); return Candidate(**base)

def test_valid_candidate_maps_to_dict():
    d = to_valid_candidate(_cand(), "original post text here")
    assert d["title"] == "Go Meetup" and d["online"] is False
    assert d["eventDate"].startswith(str(datetime.now().year))
    assert d["schemaVersion"] == 1 and d["eventId"]

def test_past_date_rejected():
    assert to_valid_candidate(_cand(event_date=datetime.now()-timedelta(days=1)), "x") is None

def test_missing_date_rejected():
    assert to_valid_candidate(_cand(event_date=None), "x") is None

def test_blank_location_falls_back_to_city():
    d = to_valid_candidate(_cand(location=None), "post")
    assert d is not None and d["location"] == "Алматы"

def test_no_location_no_city_rejected():
    assert to_valid_candidate(_cand(location=None, city=None), "post") is None

def test_short_full_description_falls_back_to_post():
    d = to_valid_candidate(_cand(full_description="short"), "a sufficiently long original telegram post body")
    assert d is not None and len(d["fullDescription"]) >= 20


def test_invalid_external_link_nulled():
    d = to_valid_candidate(_cand(external_link="регистрация по ссылке в описании"), "post")
    assert d is not None and d["externalLink"] is None


def test_valid_external_link_kept():
    d = to_valid_candidate(_cand(external_link="https://astanahub.com/event"), "post")
    assert d["externalLink"] == "https://astanahub.com/event"


def test_main_image_url_key_present_and_null():
    d = to_valid_candidate(_cand(), "post")
    assert "mainImageUrl" in d and d["mainImageUrl"] is None  # stamped later by the pipeline


def test_clean_image_url():
    from app.validate import clean_image_url
    assert clean_image_url("https://cdn4.telesco.pe/file/x.jpg") == "https://cdn4.telesco.pe/file/x.jpg"
    assert clean_image_url("not a url") is None
    assert clean_image_url(None) is None


def test_resolve_year_rolls_wrong_year_to_next_occurrence():
    from app.validate import resolve_year
    # post published July 2026, event "27 февраля" (LLM guessed 2026) → next Feb 27 = 2027
    assert resolve_year(datetime(2026, 2, 27, 16, 0), datetime(2026, 7, 10, 12, 0)) == datetime(2027, 2, 27, 16, 0)


def test_resolve_year_keeps_genuinely_past_event():
    from app.validate import resolve_year
    # posted late June, event July 3 → stays July 3 2026 (past relative to now → will be dropped)
    assert resolve_year(datetime(2026, 7, 3, 15, 0), datetime(2026, 6, 28, 10, 0)) == datetime(2026, 7, 3, 15, 0)


def test_resolve_year_future_date_unchanged():
    from app.validate import resolve_year
    assert resolve_year(datetime(2026, 7, 23, 0, 0), datetime(2026, 7, 10, 12, 0)) == datetime(2026, 7, 23, 0, 0)


def test_resolve_year_no_post_date_uses_now():
    from app.validate import resolve_year
    got = resolve_year(datetime(2000, 3, 15, 10, 0), None)  # ancient month/day, no post date
    assert got >= datetime.now()  # rolled to the next future 15 March
