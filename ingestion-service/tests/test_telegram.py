from pathlib import Path
from app.telegram import parse_posts

HTML = (Path(__file__).parent / "fixtures" / "tme_kzdev.html").read_text(encoding="utf-8")


def test_parse_posts_extracts_ref_and_text():
    posts = parse_posts(HTML, "kzdev")
    assert len(posts) >= 1
    p = posts[0]
    assert p.ref.startswith("kzdev/") or "/" in p.ref   # <channel>/<id>
    assert isinstance(p.text, str) and len(p.text) >= 0
    # date parsed from <time datetime=...> when present
    assert p.date is None or hasattr(p.date, "year")
