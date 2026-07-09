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
    candidates_published: int
    finished_at: datetime | None
