from dataclasses import dataclass, field
from datetime import datetime
import httpx
from bs4 import BeautifulSoup


@dataclass
class Post:
    ref: str
    text: str
    date: datetime | None
    links: list[str] = field(default_factory=list)


async def fetch_channel(url: str, timeout: float) -> str:
    async with httpx.AsyncClient(timeout=timeout, headers={"User-Agent": "Mozilla/5.0 EventHubBot"}) as c:
        r = await c.get(url)
        r.raise_for_status()
        return r.text


def parse_posts(html: str, channel: str) -> list[Post]:
    soup = BeautifulSoup(html, "html.parser")
    posts: list[Post] = []
    for block in soup.select("div.tgme_widget_message"):
        ref = block.get("data-post") or ""
        if not ref:
            continue
        text_el = block.select_one("div.tgme_widget_message_text")
        text = text_el.get_text("\n", strip=True) if text_el else ""
        time_el = block.select_one("time[datetime]")
        date = None
        if time_el and time_el.get("datetime"):
            try:
                date = datetime.fromisoformat(time_el["datetime"].replace("Z", "+00:00"))
            except ValueError:
                date = None
        links = [a["href"] for a in (text_el.select("a[href]") if text_el else []) if a.get("href")]
        posts.append(Post(ref=ref, text=text, date=date, links=links))
    return posts
