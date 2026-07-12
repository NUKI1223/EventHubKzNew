from dataclasses import dataclass
from datetime import datetime
import json
import httpx

API_URL = "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent"

# Shared field spec + extraction quality guidance (used by both single and batch prompts).
_FIELDS = (
    "Поля события: title (краткое понятное название без эмодзи и хэштегов), "
    "shortDescription (1–2 предложения о сути), fullDescription (развёрнутое описание из текста поста), "
    "eventDate (ISO 8601, со временем если указано; null если даты в посте нет), "
    "city (город: Алматы, Астана и т.п. — НЕ адрес и НЕ площадка), location (место/площадка/адрес), "
    "online (bool), tags (массив тем/технологий), externalLink (ссылка на регистрацию, если есть). "
    "Если пост НЕ анонс будущего мероприятия (реклама, вакансия, новость, мем, отчёт о прошедшем) — "
    "верни isEvent:false. НЕ выдумывай данные, которых нет в посте — ставь null."
)


def _year_rule() -> str:
    today = datetime.now().date().isoformat()
    return (
        f" Сегодня {today}. Если в посте указаны день и месяц без года — выбери БЛИЖАЙШУЮ БУДУЩУЮ дату "
        "(в этом году, если она ещё не прошла; иначе в следующем). "
        "Никогда не возвращай прошедшую дату для анонса будущего мероприятия."
    )


def _single_system() -> str:
    return ("Ты извлекаешь одно IT-мероприятие из поста Telegram. Верни СТРОГО JSON-объект с полем isEvent(bool). "
            + _FIELDS + _year_rule())


def _batch_system() -> str:
    return ("Ты извлекаешь IT-мероприятия из НЕСКОЛЬКИХ постов Telegram (они пронумерованы: ### ПОСТ 0, ### ПОСТ 1, ...). "
            "Верни СТРОГО JSON-массив: по одному объекту на КАЖДЫЙ пост, с обязательными полями "
            "index (номер поста из заголовка) и isEvent(bool). " + _FIELDS + _year_rule())


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
    r = httpx.post(url, headers=headers, json=json, timeout=60)
    r.raise_for_status()  # 429/5xx raise → treated as transient by the caller (posts retried)
    return r.json()


def _body(system_text: str, user_text: str, max_tokens: int = 800) -> dict:
    return {
        "systemInstruction": {"parts": [{"text": system_text}]},
        "contents": [{"role": "user", "parts": [{"text": user_text}]}],
        "generationConfig": {"responseMimeType": "application/json", "temperature": 0.1,
                             "maxOutputTokens": max_tokens, "thinkingConfig": {"thinkingBudget": 0}},
    }


def _reply_text(resp: dict) -> str:
    return resp["candidates"][0]["content"]["parts"][0]["text"]


def _parse_candidate(data) -> Candidate | None:
    """Turn one extracted JSON object into a Candidate, or None if it isn't an event."""
    if not isinstance(data, dict) or not data.get("isEvent"):
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


def extract_event(text, api_key, model, http_post=_default_post) -> Candidate | None:
    """Single-post extraction. Transient HTTP failures propagate; an unparseable or
    not-an-event response returns None."""
    resp = http_post(API_URL.format(model=model), {"x-goog-api-key": api_key}, _body(_single_system(), text))
    try:
        data = json.loads(_reply_text(resp))
    except Exception:
        return None
    return _parse_candidate(data)


def extract_events_batch(texts, api_key, model, http_post=_default_post) -> list[Candidate | None]:
    """Extract from several posts in ONE Gemini call (≈10x fewer calls than per-post →
    the free-tier quota lasts far longer). Returns a list aligned with `texts`
    (None where the post isn't an event). A transient HTTP failure propagates so the
    whole batch is retried on a later sweep."""
    if not texts:
        return []
    joined = "\n\n".join(f"### ПОСТ {i}\n{t}" for i, t in enumerate(texts))
    body = _body(_batch_system(), joined, max_tokens=350 * len(texts) + 400)
    resp = http_post(API_URL.format(model=model), {"x-goog-api-key": api_key}, body)
    try:
        data = json.loads(_reply_text(resp))
    except Exception:
        return [None] * len(texts)
    items = data if isinstance(data, list) else (data.get("events") or data.get("results") or [])
    by_index = {obj["index"]: obj for obj in items
                if isinstance(obj, dict) and isinstance(obj.get("index"), int)}
    if by_index:  # robust to reordering / omissions
        return [_parse_candidate(by_index.get(i)) for i in range(len(texts))]
    # fallback: model didn't include index → align positionally
    return [_parse_candidate(items[i]) if i < len(items) else None for i in range(len(texts))]
