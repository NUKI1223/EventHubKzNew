from app.sources import normalize_tme_url


def test_bare_channel_gets_web_mirror_url():
    assert normalize_tme_url("thedecentrathon") == "https://t.me/s/thedecentrathon"


def test_tme_without_scheme():
    assert normalize_tme_url("t.me/gdsc_aitu") == "https://t.me/s/gdsc_aitu"


def test_full_channel_url_rewritten_to_mirror():
    assert normalize_tme_url("https://t.me/AstanaHub_Events") == "https://t.me/s/AstanaHub_Events"


def test_already_mirror_url_is_idempotent():
    assert normalize_tme_url("https://t.me/s/kzdev") == "https://t.me/s/kzdev"
    assert normalize_tme_url("t.me/s/kzdev") == "https://t.me/s/kzdev"


def test_at_handle():
    assert normalize_tme_url("@AstanaHub_Events") == "https://t.me/s/AstanaHub_Events"


def test_trailing_slash_and_whitespace():
    assert normalize_tme_url("  https://t.me/gdsc_aitu/  ") == "https://t.me/s/gdsc_aitu"


def test_private_invite_links_rejected():
    assert normalize_tme_url("https://t.me/+AbCdEf123") is None
    assert normalize_tme_url("t.me/joinchat/AAAAAE123") is None


def test_non_telegram_or_junk_rejected():
    assert normalize_tme_url("https://example.com/foo") is None
    assert normalize_tme_url("hello world") is None
    assert normalize_tme_url("") is None
    assert normalize_tme_url("ab") is None  # too short for a channel handle
