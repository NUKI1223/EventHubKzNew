import React, { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import api from "../api";
import { CATEGORIES } from "../config/categories";
import "../css/MainPage.css";

const MONTHS_ABBR = ["ЯНВ", "ФЕВ", "МАР", "АПР", "МАЙ", "ИЮН", "ИЮЛ", "АВГ", "СЕН", "ОКТ", "НОЯ", "ДЕК"];
const MONTHS_IN = ["январе", "феврале", "марте", "апреле", "мае", "июне", "июле", "августе", "сентябре", "октябре", "ноябре", "декабре"];
const SEASONS = ["Зимний", "Зимний", "Весенний", "Весенний", "Весенний", "Летний", "Летний", "Летний", "Осенний", "Осенний", "Осенний", "Зимний"];

const FEATURES = [
  "Поиск по тегам и городам",
  "Уведомления об одобрении заявок",
  "Личный кабинет с подписками",
  "Поддержка через встроенный чат",
];

function plural(n, [one, few, many]) {
  const mod10 = n % 10;
  const mod100 = n % 100;
  if (mod10 === 1 && mod100 !== 11) return one;
  if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) return few;
  return many;
}

function MainPage() {
  const [events, setEvents] = useState(null);

  useEffect(() => {
    document.title = 'EventHub.kz — IT-события Казахстана';
  }, []);

  useEffect(() => {
    api.get('/api/events')
      .then(res => setEvents(Array.isArray(res.data) ? res.data : []))
      .catch(err => {
        console.error('Не удалось загрузить события для лендинга', err);
        setEvents([]);
      });
  }, []);

  const derived = useMemo(() => {
    if (!events) return null;
    const now = new Date();
    const month = now.getMonth();

    const upcoming = events
      .filter(e => e.eventDate && new Date(e.eventDate) > now)
      .sort((a, b) => new Date(a.eventDate) - new Date(b.eventDate));

    const inThisMonth = events.filter(e => {
      if (!e.eventDate) return false;
      const d = new Date(e.eventDate);
      return d.getFullYear() === now.getFullYear() && d.getMonth() === month;
    }).length;

    const openNow = events.filter(e => {
      if (!e.registrationDeadline) return new Date(e.eventDate) > now;
      return new Date(e.registrationDeadline) > now;
    }).length;

    const categoryCounts = CATEGORIES.map(c => ({
      ...c,
      count: events.filter(c.match).length,
    }));

    const cities = new Set();
    const organizers = new Set();
    let totalLikes = 0;
    for (const e of events) {
      if (e.location) {
        const city = e.location.split(',')[0].trim();
        if (city && city.toLowerCase() !== 'online') cities.add(city);
      }
      if (e.organizerId) organizers.add(e.organizerId);
      totalLikes += e.likesCount || 0;
    }

    return {
      next: upcoming[0] || null,
      inThisMonth,
      openNow,
      categoryCounts,
      stats: {
        total: events.length,
        cities: cities.size,
        organizers: organizers.size,
        likes: totalLikes,
      },
      monthLabel: MONTHS_IN[month],
      season: SEASONS[month],
    };
  }, [events]);

  const next = derived?.next;
  const nextDate = next?.eventDate ? new Date(next.eventDate) : null;
  const daysUntilNext = nextDate ? Math.max(0, Math.ceil((nextDate - Date.now()) / 86400000)) : null;

  let nextVenue = '';
  let nextCity = '';
  if (next?.location) {
    const parts = next.location.split(',').map(s => s.trim()).filter(Boolean);
    if (parts.length >= 2) { nextCity = parts[0]; nextVenue = parts.slice(1).join(', '); }
    else { nextCity = parts[0] || ''; }
  }
  if (next?.online) { nextVenue = nextVenue || 'Онлайн'; nextCity = nextCity || ''; }

  return (
    <main className="mp">

      {/* ————— HERO ————— */}
      <div className="mp__container">
        <div className="mp-hero">
          {/* Left */}
          <div className="mp-hero__left">
            <div className="mp-hero__season-badge">
              <span className="mp-hero__season-dot" />
              {derived
                ? `${derived.season} сезон · ${derived.inThisMonth} ${plural(derived.inThisMonth, ['событие', 'события', 'событий'])} в ${derived.monthLabel}`
                : 'Загружаем актуальные события…'}
            </div>

            <h1 className="mp-hero__title">
              Все IT‑события<br />
              Казахстана —<br />
              <em className="mp-hero__title-em">в одном месте.</em>
            </h1>

            <p className="mp-hero__sub">
              Хакатоны, митапы, конференции и воркшопы от сообщества.
              Находи, сохраняй и приходи — или создавай свои.
            </p>

            <div className="mp-hero__actions">
              <Link to="/eventlist" className="mp-btn mp-btn--primary">
                Смотреть события
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" width="16" height="16"><path d="M5 12h14M13 5l7 7-7 7"/></svg>
              </Link>
              <Link to="/request-event" className="mp-btn mp-btn--ghost">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" width="16" height="16"><path d="M12 5v14M5 12h14"/></svg>
                Создать событие
              </Link>
            </div>

            {/* Trust row */}
            <div className="mp-hero__trust">
              <div className="mp-hero__trust-avatars">
                {["A","N","D","F","G"].map((l, i) => (
                  <div key={i} className={`mp-hero__trust-av mp-hero__trust-av--${["peach","sage","lavender","sky","butter"][i]}`}
                    style={{ marginLeft: i ? -10 : 0 }}>
                    {l}
                  </div>
                ))}
              </div>
              <div className="mp-hero__trust-text">
                <div className="mp-hero__trust-orgs">Astana Hub · nFactorial · GDG · MOST · Kaspi</div>
                {derived
                  ? `${derived.stats.organizers} ${plural(derived.stats.organizers, ['организатор уже', 'организатора уже', 'организаторов уже'])} с нами`
                  : 'организаторы доверяют платформе'}
              </div>
            </div>
          </div>

          {/* Right — floating card collage */}
          <div className="mp-hero__right">
            {/* Cover image placeholder */}
            <div className="mp-hero__cover">
              {next?.mainImageUrl ? (
                <img src={next.mainImageUrl} alt="" className="mp-hero__cover-img" />
              ) : (
                <>
                  <div className="mp-hero__cover-stripes" />
                  <div className="mp-hero__cover-blob" />
                </>
              )}
              <div className="mp-hero__cover-live">
                <span className="mp-hero__cover-live-dot" />
                {next ? (daysUntilNext === 0 ? 'Сегодня' : `Через ${daysUntilNext} ${plural(daysUntilNext, ['день','дня','дней'])}`) : 'Сейчас идёт'}
              </div>
            </div>

            {/* Floating card */}
            <Link
              to={next ? `/events/${next.id}` : '/eventlist'}
              className="mp-hero__card"
              style={{ textDecoration: 'none', color: 'inherit' }}
            >
              <div className="mp-hero__card-date">
                <div className="mp-hero__card-month">
                  {nextDate ? MONTHS_ABBR[nextDate.getMonth()] : 'СКОРО'}
                </div>
                <div className="mp-hero__card-day">
                  {nextDate ? nextDate.getDate() : '—'}
                </div>
              </div>
              <div>
                <div className="mp-hero__card-title">
                  {next ? next.title : 'Загружаем ближайшее событие…'}
                </div>
                <div className="mp-hero__card-meta">
                  {next
                    ? [nextVenue, nextCity].filter(Boolean).join(' · ') || 'IT-сообщество'
                    : ''}
                </div>
              </div>
              <div className="mp-hero__card-bar-wrap">
                <div className="mp-hero__card-bar-meta">
                  <span>
                    {next?.likesCount
                      ? `${next.likesCount} ${plural(next.likesCount, ['лайк','лайка','лайков'])}`
                      : (next ? 'Пока без лайков' : '')}
                  </span>
                  <span className="mp-mono">
                    {daysUntilNext != null
                      ? (daysUntilNext === 0 ? 'сегодня' : `до события ${daysUntilNext} ${plural(daysUntilNext, ['день','дня','дней'])}`)
                      : ''}
                  </span>
                </div>
              </div>
            </Link>

            {/* Open badge */}
            <div className="mp-hero__open-badge">
              <span className="mp-hero__open-dot" />
              {derived
                ? `${derived.openNow} ${plural(derived.openNow, ['событие открыто', 'события открыты', 'событий открыто'])} к регистрации`
                : 'регистрация открыта'}
            </div>
          </div>
        </div>
      </div>

      {/* ————— CATEGORIES ————— */}
      <div className="mp__container mp-categories">
        {(derived?.categoryCounts || CATEGORIES.map(c => ({ ...c, count: 0 }))).map((c) => (
          <Link
            key={c.label}
            to={`/eventlist?type=${encodeURIComponent(c.label)}`}
            className={`mp-cat mp-cat--${c.color}`}
          >
            <span className="mp-cat__icon">{c.icon}</span>
            <span className="mp-cat__label">{c.label}</span>
            <span className="mp-cat__count mp-mono">
              {derived
                ? `${c.count} ${plural(c.count, ['событие','события','событий'])}`
                : '…'}
            </span>
          </Link>
        ))}
      </div>

      {/* ————— SIGNATURE ORNAMENT ————— */}
      <div className="mp__container">
        <div className="mp-ornament" aria-hidden="true">
          <span className="mp-ornament__line" />
          <span className="mp-ornament__mark" />
          <span className="mp-ornament__line" />
        </div>
      </div>

      {/* ————— STATS ————— */}
      <div className="mp__container">
        <div className="mp-stats">
          {(derived ? [
            { n: String(derived.stats.total), l: plural(derived.stats.total, ['событие на платформе', 'события на платформе', 'событий на платформе']) },
            { n: String(derived.stats.cities), l: plural(derived.stats.cities, ['город', 'города', 'городов']) },
            { n: String(derived.stats.organizers), l: plural(derived.stats.organizers, ['организатор', 'организатора', 'организаторов']) },
            { n: String(derived.stats.likes), l: plural(derived.stats.likes, ['лайк', 'лайка', 'лайков']) },
          ] : [
            { n: '—', l: 'событий' },
            { n: '—', l: 'городов' },
            { n: '—', l: 'организаторов' },
            { n: '—', l: 'лайков' },
          ]).map((s, i, arr) => (
            <div key={i} className={`mp-stats__item ${i < arr.length - 1 ? "mp-stats__item--border" : ""}`}>
              <div className="mp-stats__n">{s.n}</div>
              <div className="mp-stats__l">{s.l}</div>
            </div>
          ))}
        </div>
      </div>

      {/* ————— ORGANIZER CTA ————— */}
      <div className="mp__container">
        <div className="mp-cta">
          <div className="mp-cta__blob-1" />
          <div className="mp-cta__blob-2" />
          <div className="mp-cta__left">
            <div className="mp-cta__eyebrow mp-mono">Для организаторов</div>
            <h2 className="mp-cta__title">
              Опубликуй событие за{" "}
              <em className="mp-cta__title-em">5 минут</em>
            </h2>
            <p className="mp-cta__sub">
              Заполни форму — модерация подтверждает заявку и публикует мероприятие.
            </p>
            <Link to="/request-event" className="mp-btn mp-btn--secondary mp-cta__btn">
              Создать событие
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" width="16" height="16"><path d="M5 12h14M13 5l7 7-7 7"/></svg>
            </Link>
          </div>
          <div className="mp-cta__right">
            {FEATURES.map((t, i) => (
              <div key={i} className="mp-cta__feature">
                <div className="mp-cta__feature-icon">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" width="13" height="13"><path d="M4 12l5 5L20 6"/></svg>
                </div>
                <span>{t}</span>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* ————— FOOTER ————— */}
      <footer className="mp-footer">
        <div className="mp__container mp-footer__inner">
          <div className="mp-footer__brand">
            <div className="mp-footer__logo">
              <div className="mp-footer__logo-mark">
                <div className="mp-footer__logo-shape" />
              </div>
              <span className="mp-footer__logo-text">eventhub.kz</span>
            </div>
            <p className="mp-footer__tagline">
              Платформа IT-событий Казахстана. Хакатоны, митапы, конференции и воркшопы — в одном месте.
            </p>
            <div className="mp-footer__social">
              {[
                { s: "TG", label: "Telegram",  href: "https://t.me/eventhubkz" },
                { s: "IG", label: "Instagram", href: "https://instagram.com/eventhubkz" },
                { s: "YT", label: "YouTube",   href: "https://youtube.com/@eventhubkz" },
                { s: "LI", label: "LinkedIn",  href: "https://linkedin.com/company/eventhubkz" },
              ].map(({ s, label, href }) => (
                <a
                  key={s}
                  href={href}
                  target="_blank"
                  rel="noopener noreferrer"
                  aria-label={label}
                  className="mp-footer__social-btn mp-mono"
                >
                  {s}
                </a>
              ))}
            </div>
          </div>
          {[
            { h: "Продукт",  items: [
              { label: "События", to: "/eventlist" },
              { label: "Создать событие", to: "/request-event" },
              { label: "Профиль", to: "/profile" },
              { label: "Поиск", to: "/search" },
            ]},
            { h: "Помощь",   items: [
              { label: "Поддержка", to: "/support" },
              { label: "О платформе", to: "/eventlist" },
              { label: "Регистрация", to: "/signupnew" },
              { label: "Вход", to: "/signin" },
            ]},
          ].map((col) => (
            <div key={col.h} className="mp-footer__col">
              <div className="mp-footer__col-head">{col.h}</div>
              {col.items.map((it) => (
                <Link key={it.label} to={it.to} className="mp-footer__col-link">{it.label}</Link>
              ))}
            </div>
          ))}
        </div>
        <div className="mp__container mp-footer__bottom">
          <span>© 2026 EventHub Kazakhstan · Астана</span>
        </div>
      </footer>

    </main>
  );
}

export default MainPage;
