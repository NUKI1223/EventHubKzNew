import React, { useState, useEffect } from 'react';
import { Link, useParams } from 'react-router-dom';
import api from '../api';
import { fetchUserByRoute } from '../api/users';
import { formatDate } from '../utils/dateUtils';
import '../css/LikedEvents.css';
import { SkeletonCard } from './Skeleton';
import EmptyState from './EmptyState';
import PageError from './PageError';

const RegisteredEvents = ({ hideHeader = false, limit }) => {
  const { username: routeUsername } = useParams();
  const [events, setEvents] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [showAll, setShowAll] = useState(false);

  useEffect(() => {
    const fetchRegistered = async () => {
      try {
        const { id: userId } = await fetchUserByRoute(routeUsername);
        // Берём сами записи (с датой регистрации), а не только id — чтобы показать
        // мероприятия в порядке «на что записался последним» (свежие сверху).
        const regRes = await api.get(`/api/registrations/user/${userId}`);
        const regs = Array.isArray(regRes.data) ? regRes.data : [];
        if (regs.length === 0) { setEvents([]); return; }
        regs.sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
        const orderedIds = regs.map(r => r.eventId);
        const eventsRes = await api.get('/api/events/batch', { params: { ids: orderedIds.join(',') } });
        const byId = new Map((Array.isArray(eventsRes.data) ? eventsRes.data : []).map(e => [e.id, e]));
        setEvents(orderedIds.map(id => byId.get(id)).filter(Boolean));
      } catch (err) {
        console.error(err);
        setError('Ошибка при загрузке ваших регистраций');
      } finally {
        setLoading(false);
      }
    };
    fetchRegistered();
  }, [routeUsername]);

  if (loading) return (
    <div className="lev">
      {!hideHeader && (
        <div className="lev__hdr">
          <span className="lev__title">Мои регистрации</span>
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
          <span className="lev__title">Мои регистрации</span>
          <span className="lev__count">0</span>
        </div>
      )}
      <EmptyState
        icon="search"
        title="Вы ещё никуда не записались"
        subtitle="Запишитесь на мероприятие — оно появится здесь вместе с вашим билетом"
        actionText="Смотреть события"
        actionLink="/eventlist"
      />
    </div>
  );

  return (
    <div className="lev">
      {!hideHeader && (
        <div className="lev__hdr">
          <span className="lev__title">Мои регистрации</span>
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

export default RegisteredEvents;
