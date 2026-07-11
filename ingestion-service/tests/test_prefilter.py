from app.prefilter import looks_like_event

def test_event_post_passes():
    assert looks_like_event("Митап по Go 15 марта в Алматы, регистрация по ссылке") is True

def test_advertising_dropped():
    assert looks_like_event("Скидка 50% на курсы! Успей купить сегодня") is False

def test_text_without_date_dropped():
    assert looks_like_event("Приходите на наш воркшоп по Kotlin") is False

def test_text_without_event_keyword_dropped():
    assert looks_like_event("Завтра 20 июня будет солнечно") is False


def test_kazakh_event_passes():
    # was wrongly dropped before: Kazakh month "шілде" (July) + Kazakh/EN keywords
    assert looks_like_event("3 шілдеде Astana Hub-та Glovo Startup Competition финалы, тіркелу ашық") is True


def test_kazakh_month_with_kazakh_keyword():
    assert looks_like_event("15 қыркүйекте кездесу болады, тіркелу") is True


def test_forum_keyword_passes():
    assert looks_like_event("Форум Digital Qazaqstan 20 октября в Астане") is True
