import React, { useState, useEffect, useMemo } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import api from '../api';
import '../css/EventList.css';
import { SkeletonCard } from './Skeleton';
import EmptyState from './EmptyState';
import PageError from './PageError';
import EventCard from './EventCard';
import Pagination from './Pagination';
import { EVENTS_PER_PAGE } from '../constants';
import { useEventFiltering } from '../hooks/useEventFiltering';
import { isPastEvent, timeBucket, BUCKET_LABELS } from '../utils/dateUtils';
import { findCategory } from '../config/categories';

const EventList = () => {
  const { t } = useTranslation();
  const [searchParams] = useSearchParams();
  const [events, setEvents] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [page, setPage] = useState(0);
  const currentUserId = localStorage.getItem('userId');

  // Начальные фильтры из query-параметров (переходы с лендинга: ?type / ?tag / ?city / ?online).
  const [selectedTags, setSelectedTags] = useState(
    () => (searchParams.get('tag') ? [searchParams.get('tag')] : []));
  const [selectedCity, setSelectedCity] = useState(() => searchParams.get('city') || '');
  const [onlineOnly, setOnlineOnly] = useState(() => searchParams.get('online') === 'true');
  const [sortOption, setSortOption] = useState('nameAsc');
  const [showPast, setShowPast] = useState(false);
  const [category, setCategory] = useState(() => findCategory(searchParams.get('type')));

  const [availableTags, setAvailableTags] = useState([]);
  const [showAllTags, setShowAllTags] = useState(false);

  // Один батч-запрос на страницу вместо N запросов с карточек (лайки/записи).
  const [likeCounts, setLikeCounts] = useState({});
  const [regCounts, setRegCounts] = useState({});
  const [likedIds, setLikedIds] = useState(() => new Set());
  const [registeredIds, setRegisteredIds] = useState(() => new Set());

  const { activeEvents, pastCount } = useMemo(() => {
    const past = events.filter(isPastEvent);
    const active = events.filter(e => !isPastEvent(e));
    const base = showPast ? events : active;
    // Категория с лендинга (Хакатоны/Митапы/Онлайн/…) фильтрует по своему матчеру.
    const byCategory = category ? base.filter(category.match) : base;
    return { activeEvents: byCategory, pastCount: past.length };
  }, [events, showPast, category]);

  const filteredEvents = useEventFiltering(activeEvents, {
    selectedTags, selectedCity, onlineOnly, sortOption,
  });

  useEffect(() => {
    document.title = t('events.pageTitle');
  }, []);

  const fetchEvents = async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await api.get('/api/events');
      const list = Array.isArray(res.data) ? res.data : [];
      setEvents(list);
      loadEngagement(list.map(e => e.id));
    } catch (err) {
      setError(t('events.loadError'));
    } finally {
      setLoading(false);
    }
  };

  // Батч-загрузка вовлечённости: счётчики лайков/записей + что лайкнул/куда записан текущий юзер.
  const loadEngagement = (ids) => {
    if (ids.length) {
      const params = { eventIds: ids.join(',') };
      api.get('/api/likes/counts', { params }).then(r => setLikeCounts(r.data || {})).catch(() => {});
      api.get('/api/registrations/counts', { params }).then(r => setRegCounts(r.data || {})).catch(() => {});
    }
    if (currentUserId) {
      api.get(`/api/likes/user/${currentUserId}/events`)
        .then(r => setLikedIds(new Set(Array.isArray(r.data) ? r.data : []))).catch(() => {});
      api.get(`/api/registrations/user/${currentUserId}/events`)
        .then(r => setRegisteredIds(new Set(Array.isArray(r.data) ? r.data : []))).catch(() => {});
    }
  };

  useEffect(() => { fetchEvents(); }, []);

  useEffect(() => {
    api.get('/api/tags')
      .then(res => setAvailableTags(res.data.map(t => t.name)))
      .catch(() => {});
  }, []);

  useEffect(() => { setPage(0); }, [filteredEvents]);

  if (loading) return (
    <div className="events-container">
      <h2 className="events-heading">{t('events.heading')}</h2>
      <div className="events-grid">
        {[...Array(EVENTS_PER_PAGE)].map((_, i) => <SkeletonCard key={i} />)}
      </div>
    </div>
  );

  if (error) return (
    <div className="events-container">
      <h2 className="events-heading">{t('events.heading')}</h2>
      <PageError message={error} onRetry={fetchEvents} />
    </div>
  );

  const totalPages = Math.ceil(filteredEvents.length / EVENTS_PER_PAGE);
  const paginated = filteredEvents.slice(page * EVENTS_PER_PAGE, (page + 1) * EVENTS_PER_PAGE);
  const tagsToShow = showAllTags ? availableTags : availableTags.slice(0, 5);

  return (
    <div className="events-container">
      <h2 className="events-heading">{t('events.heading')}</h2>

      <div className="filters">
        <div className="filters__row">
          <div className="filter-input-wrap">
            <svg className="filter-icon" viewBox="0 0 20 20" fill="currentColor" width="16" height="16">
              <path fillRule="evenodd" d="M5.05 4.05a7 7 0 119.9 9.9 7 7 0 01-9.9-9.9zM10 18a8 8 0 100-16 8 8 0 000 16z" clipRule="evenodd"/>
            </svg>
            <input
              type="text"
              placeholder={t('events.cityPlaceholder')}
              value={selectedCity}
              onChange={(e) => setSelectedCity(e.target.value)}
              className="filter-input"
            />
          </div>
          <label className="filter-toggle">
            <input type="checkbox" checked={onlineOnly} onChange={() => setOnlineOnly(!onlineOnly)} />
            <span className="filter-toggle__label">{t('events.online')}</span>
          </label>
          <select value={sortOption} onChange={(e) => setSortOption(e.target.value)} className="filter-select">
            <option value="">{t('events.sortDefault')}</option>
            <option value="nameAsc">{t('events.sortNameAsc')}</option>
            <option value="date">{t('events.sortDate')}</option>
            <option value="likes">{t('events.sortLikes')}</option>
          </select>
          {pastCount > 0 && (
            <button
              type="button"
              className={`filter-toggle__btn ${showPast ? 'filter-toggle__btn--active' : ''}`}
              onClick={() => setShowPast(p => !p)}
            >
              {showPast ? t('events.hidePast', { count: pastCount }) : t('events.showPast', { count: pastCount })}
            </button>
          )}
        </div>

        {category && (
          <div className="active-category">
            <span className="active-category__chip">
              {category.label}
              <button
                type="button"
                className="active-category__x"
                onClick={() => setCategory(null)}
                aria-label={t('events.resetCategory')}
              >×</button>
            </span>
          </div>
        )}

        <div className="tag-filter">
          <div className="tag-list">
            {tagsToShow.map(tag => (
              <button
                key={tag}
                className={`tag-pill ${selectedTags.includes(tag) ? 'tag-pill--active' : ''}`}
                onClick={() => setSelectedTags(prev =>
                  prev.includes(tag) ? prev.filter(t => t !== tag) : [...prev, tag]
                )}
              >
                {tag}
              </button>
            ))}
            {availableTags.length > 5 && (
              <button className="tag-pill tag-pill--more" onClick={() => setShowAllTags(!showAllTags)}>
                {showAllTags ? t('events.tagsCollapse') : t('events.tagsMore', { count: availableTags.length - 5 })}
              </button>
            )}
            {selectedTags.length > 0 && (
              <button className="tag-pill tag-pill--reset" onClick={() => setSelectedTags([])}>
                {t('events.tagsReset')}
              </button>
            )}
          </div>
        </div>
      </div>

      <div className="events-grid">
        {paginated.length > 0 ? (
          (() => {
            // При сортировке по дате показываем заголовки-«вёдра»: Сегодня / На этой неделе / …
            if (sortOption !== 'date') {
              return paginated.map(event => (
                <EventCard
                key={event.id}
                event={event}
                currentUserId={currentUserId}
                liked={likedIds.has(event.id)}
                likeCount={likeCounts[event.id] ?? event.likesCount ?? 0}
                registered={registeredIds.has(event.id)}
                regCount={regCounts[event.id] ?? 0}
              />
              ));
            }
            const nodes = [];
            let last = null;
            for (const event of paginated) {
              const b = timeBucket(event);
              if (b !== last) {
                nodes.push(
                  <div key={`grp-${b}`} className="events-group-header">{BUCKET_LABELS[b] || ''}</div>
                );
                last = b;
              }
              nodes.push(<EventCard
                key={event.id}
                event={event}
                currentUserId={currentUserId}
                liked={likedIds.has(event.id)}
                likeCount={likeCounts[event.id] ?? event.likesCount ?? 0}
                registered={registeredIds.has(event.id)}
                regCount={regCounts[event.id] ?? 0}
              />);
            }
            return nodes;
          })()
        ) : (
          <EmptyState
            icon="search"
            title={t('events.emptyTitle')}
            subtitle={t('events.emptySubtitle')}
            actionText={t('events.emptyAction')}
            onAction={() => { setSelectedTags([]); setSelectedCity(''); setOnlineOnly(false); setSortOption('nameAsc'); setCategory(null); }}
          />
        )}
      </div>

      <Pagination
        page={page}
        totalPages={totalPages}
        total={filteredEvents.length}
        onChange={setPage}
      />
    </div>
  );
};

export default EventList;
