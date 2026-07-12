import logging
import re
import uuid
from datetime import datetime
from app.extractor import Candidate

log = logging.getLogger("validate")

# Must match EventRequest.externalLink's @Pattern on the Java side, otherwise the
# candidate is rejected with a ConstraintViolationException at persist time. The field
# is nullable, so an LLM-extracted link that doesn't match is safer dropped to null.
_URL = re.compile(r"^https?://[A-Za-z0-9.\-]+\.[A-Za-z]{2,}(?:[:/?#][^\s]*)?$")

def _clean_url(u: str | None) -> str | None:
    u = (u or "").strip()
    return u if _URL.match(u) else None

def _clip(s: str, lo: int, hi: int) -> str | None:
    s = (s or "").strip()
    if len(s) < lo:
        return None
    return s[:hi]

def to_valid_candidate(c: Candidate, post_text: str) -> dict | None:
    if c.event_date is None or c.event_date <= datetime.now():
        log.info("drop '%s': bad eventDate=%s", (c.title or "")[:40], c.event_date)
        return None
    title = _clip(c.title, 3, 200)
    if title is None:
        log.info("drop: title too short=%r", c.title)
        return None
    short = _clip(c.short_description, 10, 500) or _clip(post_text, 10, 500)
    if short is None:
        log.info("drop '%s': shortDescription+post both < 10", title)
        return None
    full = _clip(c.full_description, 20, 20000) or _clip(post_text, 20, 20000)
    if full is None:
        log.info("drop '%s': fullDescription+post both < 20", title)
        return None
    location = (c.location or c.city or "").strip()
    if not location:
        log.info("drop '%s': no location/city", title)
        return None
    return {
        "eventId": str(uuid.uuid4()),
        "timestamp": datetime.now().isoformat(),
        "schemaVersion": 1,
        "title": title, "shortDescription": short, "fullDescription": full,
        "eventDate": c.event_date.isoformat(),
        "city": c.city, "location": location[:200], "online": bool(c.online),
        "tags": c.tags, "externalLink": _clean_url(c.external_link),
        "sourceUrl": None, "sourceChannel": None,  # filled by the orchestrator per post
    }
