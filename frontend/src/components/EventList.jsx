import React, { useState, useEffect, useMemo } from 'react';
import api from '../api';
import '../css/EventList.css';
import { SkeletonCard } from './Skeleton';
import EmptyState from './EmptyState';
import PageError from './PageError';
import EventCard from './EventCard';
import Pagination from './Pagination';
import { EVENTS_PER_PAGE } from '../constants';
import { useEventFiltering } from '../hooks/useEventFiltering';
import { isPastEvent } from '../utils/dateUtils';

const EventList = () => {
  const [events, setEvents] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [page, setPage] = useState(0);
  const currentUserId = localStorage.getItem('userId');

  const [selectedTags, setSelectedTags] = useState([]);
  const [selectedCity, setSelectedCity] = useState('');
  const [onlineOnly, setOnlineOnly] = useState(false);
  const [sortOption, setSortOption] = useState('');
  const [showPast, setShowPast] = useState(false);

  const [availableTags, setAvailableTags] = useState([]);
  const [showAllTags, setShowAllTags] = useState(false);

  const { activeEvents, pastCount } = useMemo(() => {
    const past = events.filter(isPastEvent);
    const active = events.filter(e => !isPastEvent(e));
    return {
      activeEvents: showPast ? events : active,
      pastCount: past.length,
    };
  }, [events, showPast]);

  const filteredEvents = useEventFiltering(activeEvents, {
    selectedTags, selectedCity, onlineOnly, sortOption,
  });

  useEffect(() => {
    document.title = 'Мероприятия — EventHub.kz';
  }, []);

  const fetchEvents = async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await api.get('/api/events');
      setEvents(res.data);
    } catch (err) {
      setError('Ошибка при загрузке мероприятий');
    } finally {
      setLoading(false);
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
      <h2 className="events-heading">Мероприятия</h2>
      <div className="events-grid">
        {[...Array(EVENTS_PER_PAGE)].map((_, i) => <SkeletonCard key={i} />)}
      </div>
    </div>
  );

  if (error) return (
    <div className="events-container">
      <h2 className="events-heading">Мероприятия</h2>
      <PageError message={error} onRetry={fetchEvents} />
    </div>
  );

  const totalPages = Math.ceil(filteredEvents.length / EVENTS_PER_PAGE);
  const paginated = filteredEvents.slice(page * EVENTS_PER_PAGE, (page + 1) * EVENTS_PER_PAGE);
  const tagsToShow = showAllTags ? availableTags : availableTags.slice(0, 5);

  return (
    <div className="events-container">
      <h2 className="events-heading">Мероприятия</h2>

      <div className="filters">
        <div className="filters__row">
          <div className="filter-input-wrap">
            <svg className="filter-icon" viewBox="0 0 20 20" fill="currentColor" width="16" height="16">
              <path fillRule="evenodd" d="M5.05 4.05a7 7 0 119.9 9.9 7 7 0 01-9.9-9.9zM10 18a8 8 0 100-16 8 8 0 000 16z" clipRule="evenodd"/>
            </svg>
            <input
              type="text"
              placeholder="Поиск по городу..."
              value={selectedCity}
              onChange={(e) => setSelectedCity(e.target.value)}
              className="filter-input"
            />
          </div>
          <label className="filter-toggle">
            <input type="checkbox" checked={onlineOnly} onChange={() => setOnlineOnly(!onlineOnly)} />
            <span className="filter-toggle__label">Онлайн</span>
          </label>
          <select value={sortOption} onChange={(e) => setSortOption(e.target.value)} className="filter-select">
            <option value="">Сортировка</option>
            <option value="nameAsc">Название (А-Я)</option>
            <option value="date">Дата</option>
            <option value="likes">Популярность</option>
          </select>
          {pastCount > 0 && (
            <button
              type="button"
              className={`filter-toggle__btn ${showPast ? 'filter-toggle__btn--active' : ''}`}
              onClick={() => setShowPast(p => !p)}
            >
              {showPast ? `Скрыть прошедшие (${pastCount})` : `Показать скрытые (${pastCount})`}
            </button>
          )}
        </div>

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
                {showAllTags ? 'Свернуть' : `Ещё +${availableTags.length - 5}`}
              </button>
            )}
            {selectedTags.length > 0 && (
              <button className="tag-pill tag-pill--reset" onClick={() => setSelectedTags([])}>
                Сбросить
              </button>
            )}
          </div>
        </div>
      </div>

      <div className="events-grid">
        {paginated.length > 0 ? (
          paginated.map(event => (
            <EventCard key={event.id} event={event} currentUserId={currentUserId} />
          ))
        ) : (
          <EmptyState
            icon="search"
            title="Ничего не найдено"
            subtitle="Попробуйте изменить фильтры или сбросить параметры поиска"
            actionText="Сбросить фильтры"
            onAction={() => { setSelectedTags([]); setSelectedCity(''); setOnlineOnly(false); setSortOption(''); }}
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
