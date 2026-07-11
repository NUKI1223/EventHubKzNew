import re

_EVENT_KEYWORDS = re.compile(
    # RU / EN
    r"(митап|meetup|конференц|conference|хакатон|hackathon|воркшоп|workshop|"
    r"вебинар|webinar|лекци|семинар|встреч|регистрац|register|доклад|спикер|talk|"
    r"форум|forum|фестивал|festival|саммит|summit|competition|pitch|демо[- ]?day|demoday|"
    # KK (Kazakh event vocabulary — the prefilter previously missed these entirely)
    r"кездесу|тіркел|байқау|көрме|жарыс|іс-шара|конференция|семинары)",
    re.IGNORECASE)

# date signals: dd.mm / dd месяца / ISO / "завтра/сегодня" are NOT enough alone —
# require a concrete date token (day+month) to reduce false positives.
_MONTHS = (r"январ|феврал|март|апрел|ма[йя]|июн|июл|август|сентябр|октябр|ноябр|декабр|"
           r"jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec|"
           # KK months
           r"қаңтар|ақпан|наурыз|сәуір|мамыр|маусым|шілде|тамыз|қыркүйек|қазан|қараша|желтоқсан")
_DATE = re.compile(rf"(\b\d{{1,2}}[.\-/]\d{{1,2}}(?:[.\-/]\d{{2,4}})?\b|\b\d{{1,2}}\s*({_MONTHS})\w*)",
                   re.IGNORECASE)

def looks_like_event(text: str) -> bool:
    if not text:
        return False
    return bool(_EVENT_KEYWORDS.search(text)) and bool(_DATE.search(text))
