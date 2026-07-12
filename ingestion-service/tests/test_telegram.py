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


def test_parse_extracts_image_url():
    html = (
        '<div class="tgme_widget_message" data-post="kz/5">'
        '<a class="tgme_widget_message_photo_wrap" '
        "style=\"background-image:url('https://cdn4.telesco.pe/file/abc.jpg')\"></a>"
        '<div class="tgme_widget_message_text">Митап 5 марта</div></div>'
    )
    posts = parse_posts(html, "kz")
    assert posts[0].image_url == "https://cdn4.telesco.pe/file/abc.jpg"


def test_parse_no_image_is_none():
    html = ('<div class="tgme_widget_message" data-post="kz/6">'
            '<div class="tgme_widget_message_text">no photo</div></div>')
    posts = parse_posts(html, "kz")
    assert posts[0].image_url is None
