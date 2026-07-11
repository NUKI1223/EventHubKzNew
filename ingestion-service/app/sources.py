import re

# Telegram public username: 5–32 chars, letters/digits/underscore, must start with a letter.
_HANDLE = re.compile(r"^[A-Za-z][A-Za-z0-9_]{4,31}$")

# t.me path segments that are NOT channels (private invites, service pages).
_RESERVED = {"joinchat", "s", "proxy", "addstickers", "share", "iv", "socks", "bg", "login"}


def normalize_tme_url(raw: str) -> str | None:
    """Normalize a user-entered Telegram reference to the canonical web-mirror URL
    `https://t.me/s/<channel>` the parser fetches, or None if it isn't a public channel.

    Accepts: `channel`, `@channel`, `t.me/channel`, `https://t.me/channel`,
    `t.me/s/channel`, `https://t.me/s/channel`, `telegram.me/channel` (± trailing slash).
    Rejects: private invite links (`t.me/+…`, `t.me/joinchat/…`), non-Telegram URLs, junk.
    """
    if not raw:
        return None
    s = raw.strip()
    s = re.sub(r"^https?://", "", s, flags=re.IGNORECASE)          # drop scheme
    s = re.sub(r"^(www\.)?(t\.me|telegram\.me)/", "", s, flags=re.IGNORECASE)  # drop host
    s = s.lstrip("@")
    s = re.sub(r"^s/", "", s, flags=re.IGNORECASE)                 # drop web-mirror prefix
    channel = s.split("/")[0].split("?")[0].strip()
    if channel.lower() in _RESERVED or channel.startswith("+"):
        return None
    if not _HANDLE.match(channel):
        return None
    return f"https://t.me/s/{channel}"
