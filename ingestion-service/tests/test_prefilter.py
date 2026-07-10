from app.prefilter import looks_like_event

def test_event_post_passes():
    assert looks_like_event("Митап по Go 15 марта в Алматы, регистрация по ссылке") is True

def test_advertising_dropped():
    assert looks_like_event("Скидка 50% на курсы! Успей купить сегодня") is False

def test_text_without_date_dropped():
    assert looks_like_event("Приходите на наш воркшоп по Kotlin") is False

def test_text_without_event_keyword_dropped():
    assert looks_like_event("Завтра 20 июня будет солнечно") is False
