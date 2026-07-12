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
