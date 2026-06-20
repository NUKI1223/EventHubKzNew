import React, { useState, useEffect } from 'react';
import { Link, useParams } from 'react-router-dom';
import api from '../api';
import { fetchUserByRoute } from '../api/users';
import { formatDate } from '../utils/dateUtils';
import '../css/LikedEvents.css';
import { SkeletonCard } from './Skeleton';
import EmptyState from './EmptyState';
import PageError from './PageError';

const LikedEvents = ({ hideHeader = false, limit }) => {
  const { username: routeUsername } = useParams();
  const [events, setEvents] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [showAll, setShowAll] = useState(false);

  useEffect(() => {
    const fetchLikedEvents = async () => {
      try {
        const { id: userId } = await fetchUserByRoute(routeUsername);
        const idsRes = await api.get(`/api/likes/user/${userId}/events`);
        const ids = Array.isArray(idsRes.data) ? idsRes.data : [];
        if (ids.length === 0) { setEvents([]); return; }
        const eventsRes = await api.get('/api/events/batch', { params: { ids: ids.join(',') } });
        setEvents(Array.isArray(eventsRes.data) ? eventsRes.data : []);
      } catch (err) {
        console.error(err);
        setError('Ошибка при загрузке сохранённых мероприятий');
      } finally {
        setLoading(false);
      }
    };
    fetchLikedEvents();
  }, [routeUsername]);

  if (loading) return (
    <div className="lev">
      {!hideHeader && (
        <div className="lev__hdr">
          <span className="lev__title">Сохранённые события</span>
        </div>
      )}
      <div className="lev__grid">
        {[...Array(3)].map((_, i) => <SkeletonCard key={i} />)}
      </div>
    </div>
  );

  if (error) return <div className="lev"><PageError message={error} /></div>;

  if (events.length === 0) return (
    <div className="lev">
      {!hideHeader && (
        <div className="lev__hdr">
          <span className="lev__title">Сохранённые события</span>
          <span className="lev__count">0</span>
        </div>
      )}
      <EmptyState
        icon="heart"
        title="Нет сохранённых событий"
        subtitle="Ставьте лайки мероприятиям, которые вам нравятся, и они появятся здесь"
        actionText="Смотреть события"
        actionLink="/eventlist"
      />
    </div>
  );

  return (
    <div className="lev">
      {!hideHeader && (
        <div className="lev__hdr">
          <span className="lev__title">
            {routeUsername ? `Сохранённые — ${routeUsername}` : 'Сохранённые события'}
          </span>
          <span className="lev__count">{events.length}</span>
        </div>
      )}

      <div className="lev__grid">
        {(limit && !showAll ? events.slice(0, limit) : events).map(event => (
          <Link to={`/events/${event.id}`} key={event.id} className="lev__card">
            <div className="lev__card-img-wrap">
              {event.mainImageUrl ? (
                <img src={event.mainImageUrl} alt={event.title} className="lev__card-img" />
              ) : (
                <div className="lev__card-ph">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" width="32" height="32">
                    <rect x="3" y="3" width="18" height="18" rx="3"/>
                    <circle cx="8.5" cy="8.5" r="1.5"/>
                    <path d="m21 15-5-5L5 21"/>
                  </svg>
                </div>
              )}
            </div>
            <div className="lev__card-body">
              <div className="lev__card-title">{event.title}</div>
              {event.shortDescription && (
                <div className="lev__card-desc">{event.shortDescription}</div>
              )}
              <div className="lev__card-date">{formatDate(event.eventDate)}</div>
            </div>
          </Link>
        ))}
      </div>
      {limit && !showAll && events.length > limit && (
        <button className="orgd__showmore" onClick={() => setShowAll(true)}>
          Показать ещё
        </button>
      )}
    </div>
  );
};

export default LikedEvents;
