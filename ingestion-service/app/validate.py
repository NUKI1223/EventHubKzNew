import uuid
from datetime import datetime
from app.extractor import Candidate

def _clip(s: str, lo: int, hi: int) -> str | None:
    s = (s or "").strip()
    if len(s) < lo:
        return None
    return s[:hi]

def to_valid_candidate(c: Candidate, post_text: str) -> dict | None:
    if c.event_date is None or c.event_date <= datetime.now():
        return None
    title = _clip(c.title, 3, 200)
    if title is None:
        return None
    short = _clip(c.short_description, 10, 500) or _clip(post_text, 10, 500)
    if short is None:
        return None
    full = _clip(c.full_description, 20, 20000) or _clip(post_text, 20, 20000)
    if full is None:
        return None
    location = (c.location or c.city or "").strip()
    if not location:
        return None
    return {
        "eventId": str(uuid.uuid4()),
        "timestamp": datetime.now().isoformat(),
        "schemaVersion": 1,
        "title": title, "shortDescription": short, "fullDescription": full,
        "eventDate": c.event_date.isoformat(),
        "city": c.city, "location": location[:200], "online": bool(c.online),
        "tags": c.tags, "externalLink": c.external_link,
        "sourceUrl": None, "sourceChannel": None,  # filled by the orchestrator per post
    }
