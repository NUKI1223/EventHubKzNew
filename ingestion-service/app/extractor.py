from dataclasses import dataclass
from datetime import datetime
import json
import httpx

API_URL = "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent"

SYSTEM = (
    "Ты извлекаешь IT-мероприятие из поста Telegram. Верни СТРОГО JSON. "
    "Если пост не анонс мероприятия (реклама, новость, мем) — верни {\"isEvent\": false}. "
    "Поля: isEvent(bool), title, shortDescription, fullDescription, eventDate(ISO 8601 или null), "
    "city, location, online(bool), tags(массив строк), externalLink. "
    "НЕ выдумывай данные, которых нет в посте — ставь null. Дату указывай только если она явно есть."
)

@dataclass
class Candidate:
    title: str
    short_description: str
    full_description: str
    event_date: datetime | None
    city: str | None
    location: str | None
    online: bool
    tags: list[str]
    external_link: str | None

def _default_post(url, headers, json):  # noqa: A002
    r = httpx.post(url, headers=headers, json=json, timeout=30)
    r.raise_for_status()  # 429/5xx raise → treated as transient by the caller (post retried)
    return r.json()

def extract_event(text, api_key, model, http_post=_default_post) -> Candidate | None:
    body = {
        "systemInstruction": {"parts": [{"text": SYSTEM}]},
        "contents": [{"role": "user", "parts": [{"text": text}]}],
        "generationConfig": {"responseMimeType": "application/json", "temperature": 0.1,
                             "maxOutputTokens": 800, "thinkingConfig": {"thinkingBudget": 0}},
    }
    # A transient failure (network, HTTP 429 quota, 5xx) propagates so the pipeline can
    # retry the post on a later sweep instead of burning it. Only a *reached* response
    # that is unparseable / not-an-event returns None (a real "skip this post" decision).
    resp = http_post(API_URL.format(model=model), {"x-goog-api-key": api_key}, body)
    try:
        raw = resp["candidates"][0]["content"]["parts"][0]["text"]
        data = json.loads(raw)
    except Exception:
        return None
    if not data.get("isEvent"):
        return None
    ed = data.get("eventDate")
    try:
        event_date = datetime.fromisoformat(ed) if ed else None
        if event_date is not None and event_date.tzinfo is not None:
            event_date = event_date.replace(tzinfo=None)  # EventRequest.eventDate is naive LocalDateTime
    except (TypeError, ValueError):
        event_date = None
    tags = data.get("tags") or []
    if not isinstance(tags, list):
        tags = []
    return Candidate(
        title=data.get("title") or "", short_description=data.get("shortDescription") or "",
        full_description=data.get("fullDescription") or "", event_date=event_date,
        city=data.get("city"), location=data.get("location"), online=bool(data.get("online")),
        tags=[str(t) for t in tags], external_link=data.get("externalLink"))
