import React, { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { useTranslation } from "react-i18next";
import api from "../api";
import { CATEGORIES } from "../config/categories";
import "../css/MainPage.css";

function plural(n, [one, few, many]) {
  const mod10 = n % 10;
  const mod100 = n % 100;
  if (mod10 === 1 && mod100 !== 11) return one;
  if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) return few;
  return many;
}

function MainPage() {
  const { t } = useTranslation();

  const MONTHS_ABBR = t('landing.monthsAbbr', { returnObjects: true });
  const MONTHS_IN   = t('landing.monthsIn',   { returnObjects: true });
  const SEASONS     = t('landing.seasons',     { returnObjects: true });
  const FEATURES    = t('landing.features',    { returnObjects: true });

  const [events, setEvents] = useState(null);

  useEffect(() => {
    document.title = t('landing.pageTitle');
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
      monthIndex: month,
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
  if (next?.online) { nextVenue = nextVenue || t('landing.venueOnline'); nextCity = nextCity || ''; }

  const monthLabel = derived != null ? MONTHS_IN[derived.monthIndex] : '';
  const season     = derived != null ? SEASONS[derived.monthIndex]   : '';

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
                ? t('landing.seasonBadge', {
                    season,
                    count: derived.inThisMonth,
                    eventWord: plural(derived.inThisMonth, t('landing.pluralEvent', { returnObjects: true })),
                    month: monthLabel,
                  })
                : t('landing.seasonBadgeLoading')}
            </div>

            <h1 className="mp-hero__title">
              {t('landing.heroTitle1')}<br />
              {t('landing.heroTitle2') && <>{t('landing.heroTitle2')}<br /></>}
              <em className="mp-hero__title-em">{t('landing.heroTitleEm')}</em>
            </h1>

            <p className="mp-hero__sub">
              {t('landing.heroSub')}
            </p>

            <div className="mp-hero__actions">
              <Link to="/eventlist" className="mp-btn mp-btn--primary">
                {t('landing.btnBrowse')}
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" width="16" height="16"><path d="M5 12h14M13 5l7 7-7 7"/></svg>
              </Link>
              <Link to="/request-event" className="mp-btn mp-btn--ghost">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" width="16" height="16"><path d="M12 5v14M5 12h14"/></svg>
                {t('landing.btnCreate')}
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
                <div className="mp-hero__trust-orgs">{t('landing.trustOrgs')}</div>
                {derived
                  ? t('landing.trustCountLine', {
                      orgCount: derived.stats.organizers,
                      orgWord: plural(derived.stats.organizers, t('landing.pluralOrgTrust', { returnObjects: true })),
                    })
                  : t('landing.trustFallback')}
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
                {next
                  ? (daysUntilNext === 0
                      ? t('landing.liveToday')
                      : t('landing.liveDays', {
                          count: daysUntilNext,
                          dayWord: plural(daysUntilNext, t('landing.pluralDay', { returnObjects: true })),
                        }))
                  : t('landing.liveSoon')}
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
                  {nextDate ? MONTHS_ABBR[nextDate.getMonth()] : t('landing.cardSoon')}
                </div>
                <div className="mp-hero__card-day">
                  {nextDate ? nextDate.getDate() : '—'}
                </div>
              </div>
              <div>
                <div className="mp-hero__card-title">
                  {next ? next.title : t('landing.cardLoading')}
                </div>
                <div className="mp-hero__card-meta">
                  {next
                    ? [nextVenue, nextCity].filter(Boolean).join(' · ') || t('landing.cardMetaFallback')
                    : ''}
                </div>
              </div>
              <div className="mp-hero__card-bar-wrap">
                <div className="mp-hero__card-bar-meta">
                  <span>
                    {next?.likesCount
                      ? t('landing.cardLikes', {
                          count: next.likesCount,
                          likeWord: plural(next.likesCount, t('landing.pluralLike', { returnObjects: true })),
                        })
                      : (next ? t('landing.cardNoLikes') : '')}
                  </span>
                  <span className="mp-mono">
                    {daysUntilNext != null
                      ? (daysUntilNext === 0
                          ? t('landing.cardDaysToday')
                          : t('landing.cardDaysUntil', {
                              count: daysUntilNext,
                              dayWord: plural(daysUntilNext, t('landing.pluralDay', { returnObjects: true })),
                            }))
                      : ''}
                  </span>
                </div>
              </div>
            </Link>

            {/* Open badge */}
            <div className="mp-hero__open-badge">
              <span className="mp-hero__open-dot" />
              {derived
                ? t('landing.openBadge', {
                    count: derived.openNow,
                    eventWord: plural(derived.openNow, t('landing.pluralEventOpen', { returnObjects: true })),
                  })
                : t('landing.openBadgeFallback')}
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
            <span className="mp-cat__label">{t(c.labelKey)}</span>
            <span className="mp-cat__count mp-mono">
              {derived
                ? `${c.count} ${plural(c.count, t('landing.pluralEvent', { returnObjects: true }))}`
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
            { n: String(derived.stats.total),      l: plural(derived.stats.total,      t('landing.pluralEventOnPlatform', { returnObjects: true })) },
            { n: String(derived.stats.cities),     l: plural(derived.stats.cities,     t('landing.pluralCity',            { returnObjects: true })) },
            { n: String(derived.stats.organizers), l: plural(derived.stats.organizers, t('landing.pluralOrg',             { returnObjects: true })) },
            { n: String(derived.stats.likes),      l: plural(derived.stats.likes,      t('landing.pluralLike',            { returnObjects: true })) },
          ] : [
            { n: '—', l: t('landing.statsEventsFallback') },
            { n: '—', l: t('landing.statsCitiesFallback') },
            { n: '—', l: t('landing.statsOrgsFallback') },
            { n: '—', l: t('landing.statsLikesFallback') },
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
            <div className="mp-cta__eyebrow mp-mono">{t('landing.ctaEyebrow')}</div>
            <h2 className="mp-cta__title">
              {t('landing.ctaTitle1')}{" "}
              <em className="mp-cta__title-em">{t('landing.ctaTitleEm')}</em>
            </h2>
            <p className="mp-cta__sub">
              {t('landing.ctaSub')}
            </p>
            <Link to="/request-event" className="mp-btn mp-btn--secondary mp-cta__btn">
              {t('landing.btnCreate')}
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" width="16" height="16"><path d="M5 12h14M13 5l7 7-7 7"/></svg>
            </Link>
          </div>
          <div className="mp-cta__right">
            {FEATURES.map((feat, i) => (
              <div key={i} className="mp-cta__feature">
                <div className="mp-cta__feature-icon">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" width="13" height="13"><path d="M4 12l5 5L20 6"/></svg>
                </div>
                <span>{feat}</span>
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
              {t('landing.footerTagline')}
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
            { h: t('landing.footerColProduct'), items: [
              { label: t('landing.footerLinkEvents'),  to: "/eventlist" },
              { label: t('landing.footerLinkCreate'),  to: "/request-event" },
              { label: t('landing.footerLinkProfile'), to: "/profile" },
              { label: t('landing.footerLinkSearch'),  to: "/search" },
            ]},
            { h: t('landing.footerColHelp'), items: [
              { label: t('landing.footerLinkSupport'), to: "/support" },
              { label: t('landing.footerLinkAbout'),   to: "/eventlist" },
              { label: t('landing.footerLinkSignup'),  to: "/signupnew" },
              { label: t('landing.footerLinkSignin'),  to: "/signin" },
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
          <span>{t('landing.footerCopyright')}</span>
        </div>
      </footer>

    </main>
  );
}

export default MainPage;
