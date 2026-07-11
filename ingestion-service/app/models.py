from dataclasses import dataclass
from datetime import datetime

@dataclass
class Source:
    id: int
    name: str
    tme_url: str
    enabled: bool
    last_seen_ref: str | None

@dataclass
class Run:
    id: int
    trigger: str
    started_at: datetime | None
    finished_at: datetime | None
    sources_swept: int
    posts_fetched: int
    passed_prefilter: int
    extracted: int
    candidates_published: int
    gemini_rate_limited: int
    gemini_errors: int
    dropped_past: int
    dropped_invalid: int
    error: str | None
