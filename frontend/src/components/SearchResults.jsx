import React, { useState, useEffect } from 'react';
import { useLocation, Link } from 'react-router-dom';
import api from '../api';
import '../css/SearchResult.css';
import '../css/EventList.css';
import { SkeletonCard } from './Skeleton';
import EmptyState from './EmptyState';
import PageError from './PageError';
import EventCard from './EventCard';
import Pagination from './Pagination';
import { EVENTS_PER_PAGE } from '../constants';
import { useEventFiltering } from '../hooks/useEventFiltering';

function useQuery() {
  return new URLSearchParams(useLocation().search);
}

const SearchResults = () => {
  const query = useQuery().get('query') || useQuery().get('q') || '';
  const [tab, setTab] = useState('events');
  const [eventResults, setEventResults] = useState([]);
  const [userResults, setUserResults] = useState([]);
  const [availableTags, setAvailableTags] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const currentUserId = localStorage.getItem('userId');

  const [selectedTags, setSelectedTags] = useState([]);
  const [selectedCity, setSelectedCity] = useState('');
  const [onlineOnly, setOnlineOnly] = useState(false);
  const [sortOption, setSortOption] = useState('');
  const [page, setPage] = useState(0);

  const filteredEvents = useEventFiltering(eventResults, {
    selectedTags, selectedCity, onlineOnly, sortOption,
  });

  useEffect(() => {
    document.title = query
      ? `Поиск: ${query} — EventHub.kz`
      : 'Поиск — EventHub.kz';
  }, [query]);

  useEffect(() => {
    api.get('/api/tags')
      .then(res => setAvailableTags(Array.isArray(res.data) ? res.data.map(t => t.name) : []))
      .catch(() => {});
  }, []);

  useEffect(() => {
    const fetchResults = async () => {
      if (!query) {
        setError('Введите запрос для поиска');
        setLoading(false);
        return;
      }
      try {
        setLoading(true);
        const eventRes = await api.get(`/api/search/events?query=${encodeURIComponent(query)}`);
        const evData = eventRes.data;
        setEventResults(Array.isArray(evData) ? evData : evData?.content ?? []);

        const userRes = await api.get('/api/users');
        const users = Array.isArray(userRes.data) ? userRes.data : [];
        setUserResults(
          users.filter(u => u.username?.toLowerCase().includes(query.toLowerCase()))
        );
      } catch (err) {
        console.error('Ошибка поиска:', err);
        setError(err.response?.data?.message || 'Ошибка при поиске');
      } finally {
        setLoading(false);
      }
    };
    fetchResults();
  }, [query]);

  useEffect(() => { setPage(0); }, [filteredEvents]);

  const toggleTag = (tag) =>
    setSelectedTags(prev => prev.includes(tag) ? prev.filter(t => t !== tag) : [...prev, tag]);

  if (loading) return (
    <div className="sr">
      <div className="sr__hdr">
        <h1 className="sr__query">Поиск: <em>{query}</em></h1>
      </div>
      <div className="events-grid">
        {[...Array(4)].map((_, i) => <SkeletonCard key={i} />)}
      </div>
    </div>
  );

  if (error) return <div className="sr"><PageError message={error} /></div>;

  return (
    <div className="sr">
      <div className="sr__hdr">
        <h1 className="sr__query">
          Результаты: <em>«{query}»</em>
        </h1>
      </div>

      {/* Tabs */}
      <div className="sr__tabs">
        <button
          className={`sr__tab ${tab === 'events' ? 'sr__tab--active' : ''}`}
          onClick={() => setTab('events')}
        >
          Мероприятия{eventResults.length > 0 && ` (${eventResults.length})`}
        </button>
        <button
          className={`sr__tab ${tab === 'users' ? 'sr__tab--active' : ''}`}
          onClick={() => setTab('users')}
        >
          Пользователи{userResults.length > 0 && ` (${userResults.length})`}
        </button>
      </div>

      {/* Filters for events */}
      {tab === 'events' && (
        <>
          <div className="sr__filters">
            <input
              className="sr__filter-input"
              type="text"
              placeholder="Город..."
              value={selectedCity}
              onChange={e => setSelectedCity(e.target.value)}
            />
            <label className="sr__filter-toggle">
              <input
                type="checkbox"
                checked={onlineOnly}
                onChange={() => setOnlineOnly(!onlineOnly)}
              />
              Онлайн
            </label>
            <select
              className="sr__filter-select"
              value={sortOption}
              onChange={e => setSortOption(e.target.value)}
            >
              <option value="">Сортировка</option>
              <option value="nameAsc">Название (А-Я)</option>
              <option value="date">По дате</option>
              <option value="likes">По популярности</option>
            </select>
          </div>

          <div className="sr__tags">
            {availableTags.map(tag => (
              <button
                key={tag}
                className={`tag-pill ${selectedTags.includes(tag) ? 'tag-pill--active' : ''}`}
                onClick={() => toggleTag(tag)}
              >
                {tag}
              </button>
            ))}
            {selectedTags.length > 0 && (
              <button className="sr__tag-reset" onClick={() => setSelectedTags([])}>
                Сбросить
              </button>
            )}
          </div>
        </>
      )}

      {/* Events */}
      {tab === 'events' && (
        <>
          <div className="events-grid">
            {filteredEvents.length > 0 ? (
              filteredEvents
                .slice(page * EVENTS_PER_PAGE, (page + 1) * EVENTS_PER_PAGE)
                .map(event => (
                  <EventCard key={event.id} event={event} currentUserId={currentUserId} />
                ))
            ) : (
              <EmptyState
                icon="search"
                title="Ничего не найдено"
                subtitle="Попробуйте изменить поисковый запрос или фильтры"
                actionText="Все мероприятия"
                actionLink="/eventlist"
              />
            )}
          </div>
          <Pagination
            page={page}
            totalPages={Math.ceil(filteredEvents.length / EVENTS_PER_PAGE)}
            total={filteredEvents.length}
            onChange={setPage}
          />
        </>
      )}

      {/* Users */}
      {tab === 'users' && (
        <div className="sr__users-grid">
          {userResults.length > 0 ? (
            userResults.map(user => (
              <Link to={`/profile/${user.username}`} key={user.id} className="sr__user-card">
                {user.avatarUrl ? (
                  <img src={user.avatarUrl} alt={user.username} className="sr__user-avatar" />
                ) : (
                  <div className="sr__user-avatar-ph">
                    {user.username?.[0]?.toUpperCase() || '?'}
                  </div>
                )}
                <div className="sr__user-name">{user.username || 'Без имени'}</div>
                {user.description && (
                  <div className="sr__user-desc">{user.description}</div>
                )}
              </Link>
            ))
          ) : (
            <EmptyState
              icon="search"
              title="Пользователи не найдены"
              subtitle="Попробуйте изменить поисковый запрос"
            />
          )}
        </div>
      )}
    </div>
  );
};

export default SearchResults;
