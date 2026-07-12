from dataclasses import dataclass
from datetime import datetime
import json
import httpx

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
            "Верни СТРОГО JSON-объект с полем \"events\" — массив, по одному объекту на КАЖДЫЙ пост, "
            "с обязательными полями index (номер поста из заголовка) и isEvent(bool). " + _FIELDS + _year_rule())


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


def _chat(base_url, api_key, model, system_text, user_text, max_tokens, http_post):
    """One OpenAI-compatible chat/completions call (works with Groq, Cerebras, OpenRouter,
    Mistral, OpenAI). Returns the raw response dict; HTTP errors propagate as transient."""
    url = base_url.rstrip("/") + "/chat/completions"
    headers = {"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"}
    body = {
        "model": model,
        "messages": [
            {"role": "system", "content": system_text},
            {"role": "user", "content": user_text},
        ],
        "temperature": 0.1,
        "max_tokens": max_tokens,
        "response_format": {"type": "json_object"},
    }
    return http_post(url, headers, body)


def _reply_text(resp: dict) -> str:
    return resp["choices"][0]["message"]["content"]


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


def extract_event(text, base_url, api_key, model, http_post=_default_post) -> Candidate | None:
    """Single-post extraction. Transient HTTP failures propagate; an unparseable or
    not-an-event response returns None."""
    resp = _chat(base_url, api_key, model, _single_system(), text, 800, http_post)
    try:
        data = json.loads(_reply_text(resp))
    except Exception:
        return None
    return _parse_candidate(data)


def extract_events_batch(texts, base_url, api_key, model, http_post=_default_post) -> list[Candidate | None]:
    """Extract from several posts in ONE LLM call (≈Nx fewer calls than per-post → the
    free tier lasts far longer). Returns a list aligned with `texts` (None where the post
    isn't an event). A transient HTTP failure propagates so the whole batch is retried."""
    if not texts:
        return []
    joined = "\n\n".join(f"### ПОСТ {i}\n{t}" for i, t in enumerate(texts))
    resp = _chat(base_url, api_key, model, _batch_system(), joined, 350 * len(texts) + 400, http_post)
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
