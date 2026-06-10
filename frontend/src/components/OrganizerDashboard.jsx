import React, { useState, useEffect, useMemo } from 'react';
import { Link } from 'react-router-dom';
import api from '../api';
import { formatDate, toDate, isPastEvent } from '../utils/dateUtils';
import { SkeletonCard } from './Skeleton';
import EmptyState from './EmptyState';
import PageError from './PageError';
import '../css/OrganizerDashboard.css';

const STATUS_META = {
  PENDING:  { label: 'На рассмотрении', cls: 'pending' },
  APPROVED: { label: 'Одобрено',        cls: 'approved' },
  REJECTED: { label: 'Отклонено',       cls: 'rejected' },
};

// Обратный отсчёт до дедлайна регистрации
function deadlineLabel(event) {
  const d = toDate(event?.registrationDeadline);
  if (!d) return null;
  const diff = d.getTime() - Date.now();
  if (diff <= 0) return { text: 'Регистрация закрыта', closed: true };
  const days = Math.floor(diff / 86400000);
  const hours = Math.floor((diff % 86400000) / 3600000);
  if (days > 0) return { text: `До конца регистрации: ${days} дн.`, closed: false };
  return { text: `До конца регистрации: ${hours} ч.`, closed: false };
}

const OrganizerDashboard = () => {
  const [tab, setTab] = useState('events');
  const [events, setEvents] = useState([]);
  const [requests, setRequests] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const userId = localStorage.getItem('userId');

  useEffect(() => {
    const load = async () => {
      try {
        setLoading(true);
        const [evRes, reqRes] = await Promise.all([
          userId ? api.get(`/api/events/organizer/${userId}`) : Promise.resolve({ data: [] }),
          api.get('/api/event-requests/my'),
        ]);
        const evList = Array.isArray(evRes.data) ? evRes.data : [];
        // Реальный счётчик лайков живёт в like-service (поле Event.likeCount
        // денормализовано и не обновляется фронтом), поэтому берём его оттуда.
        const withLikes = await Promise.all(evList.map(async (ev) => {
          try {
            const { data } = await api.get(`/api/likes/event/${ev.id}/count`);
            return { ...ev, likesCount: typeof data === 'number' ? data : (ev.likesCount || 0) };
          } catch {
            return { ...ev, likesCount: ev.likesCount || 0 };
          }
        }));
        setEvents(withLikes);
        const reqs = Array.isArray(reqRes.data) ? reqRes.data : [];
        // Свежие заявки сверху
        reqs.sort((a, b) => (toDate(b.createdAt)?.getTime() || 0) - (toDate(a.createdAt)?.getTime() || 0));
        setRequests(reqs);
      } catch (err) {
        console.error(err);
        setError('Не удалось загрузить ваши мероприятия');
      } finally {
        setLoading(false);
      }
    };
    load();
  }, [userId]);

  const stats = useMemo(() => ({
    events: events.length,
    likes: events.reduce((s, e) => s + (e.likesCount || 0), 0),
    views: events.reduce((s, e) => s + (e.viewsCount || 0), 0),
    pending: requests.filter(r => r.status === 'PENDING').length,
  }), [events, requests]);

  if (loading) return (
    <div className="orgd">
      <div className="orgd__hdr"><h1 className="orgd__title">Мои мероприятия</h1></div>
      <div className="orgd__grid">
        {[...Array(3)].map((_, i) => <SkeletonCard key={i} />)}
      </div>
    </div>
  );

  if (error) return <div className="orgd"><PageError message={error} /></div>;

  return (
    <div className="orgd">
      <div className="orgd__hdr">
        <h1 className="orgd__title">Мои мероприятия</h1>
        <p className="orgd__sub">Кабинет организатора — публикации, заявки и вовлечённость аудитории</p>
      </div>

      {/* Сводка */}
      <div className="orgd__stats">
        <div className="orgd__stat">
          <span className="orgd__stat-val">{stats.events}</span>
          <span className="orgd__stat-lbl">Опубликовано</span>
        </div>
        <div className="orgd__stat">
          <span className="orgd__stat-val">{stats.likes}</span>
          <span className="orgd__stat-lbl">Лайков всего</span>
        </div>
        <div className="orgd__stat">
          <span className="orgd__stat-val">{stats.views}</span>
          <span className="orgd__stat-lbl">Просмотров всего</span>
        </div>
        <div className="orgd__stat">
          <span className="orgd__stat-val">{stats.pending}</span>
          <span className="orgd__stat-lbl">Заявок на рассмотрении</span>
        </div>
      </div>

      {/* Вкладки */}
      <div className="orgd__tabs">
        <button
          className={`orgd__tab ${tab === 'events' ? 'orgd__tab--active' : ''}`}
          onClick={() => setTab('events')}
        >
          Опубликованные{events.length > 0 && ` (${events.length})`}
        </button>
        <button
          className={`orgd__tab ${tab === 'requests' ? 'orgd__tab--active' : ''}`}
          onClick={() => setTab('requests')}
        >
          Заявки{requests.length > 0 && ` (${requests.length})`}
        </button>
      </div>

      {/* Опубликованные мероприятия */}
      {tab === 'events' && (
        events.length === 0 ? (
          <EmptyState
            icon="search"
            title="У вас пока нет опубликованных мероприятий"
            subtitle="Подайте заявку — после одобрения администратором мероприятие появится здесь"
            actionText="Создать заявку"
            actionLink="/request-event"
          />
        ) : (
          <div className="orgd__grid">
            {events.map(ev => {
              const dl = deadlineLabel(ev);
              const past = isPastEvent(ev);
              return (
                <div key={ev.id} className="orgd-card">
                  <Link to={`/events/${ev.id}`} className="orgd-card__img-wrap">
                    {ev.mainImageUrl ? (
                      <img src={ev.mainImageUrl} alt={ev.title} className="orgd-card__img" />
                    ) : (
                      <div className="orgd-card__ph">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" width="32" height="32">
                          <rect x="3" y="3" width="18" height="18" rx="3"/>
                          <circle cx="8.5" cy="8.5" r="1.5"/>
                          <path d="m21 15-5-5L5 21"/>
                        </svg>
                      </div>
                    )}
                    {past && <span className="orgd-card__badge orgd-card__badge--past">Прошло</span>}
                  </Link>
                  <div className="orgd-card__body">
                    <Link to={`/events/${ev.id}`} className="orgd-card__title">{ev.title}</Link>
                    <div className="orgd-card__date">{formatDate(ev.eventDate)}</div>

                    <div className="orgd-card__metrics">
                      <span className="orgd-metric" title="Лайки">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="15" height="15">
                          <path d="M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1-1.1a5.5 5.5 0 1 0-7.8 7.8L12 21l8.8-8.6a5.5 5.5 0 0 0 0-7.8z"/>
                        </svg>
                        {ev.likesCount || 0}
                      </span>
                      <span className="orgd-metric" title="Просмотры">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="15" height="15">
                          <path d="M1 12s4-7 11-7 11 7 11 7-4 7-11 7-11-7-11-7z"/>
                          <circle cx="12" cy="12" r="3"/>
                        </svg>
                        {ev.viewsCount || 0}
                      </span>
                      <Link to={`/events/${ev.id}/likers`} className="orgd-card__likers">
                        Кто лайкнул →
                      </Link>
                    </div>

                    {dl && (
                      <div className={`orgd-card__deadline ${dl.closed ? 'orgd-card__deadline--closed' : ''}`}>
                        {dl.text}
                      </div>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        )
      )}

      {/* Заявки */}
      {tab === 'requests' && (
        requests.length === 0 ? (
          <EmptyState
            icon="search"
            title="Вы ещё не подавали заявок"
            subtitle="Создайте заявку на мероприятие — она появится здесь со статусом рассмотрения"
            actionText="Создать заявку"
            actionLink="/request-event"
          />
        ) : (
          <div className="orgd__reqs">
            {requests.map(r => {
              const meta = STATUS_META[r.status] || STATUS_META.PENDING;
              return (
                <div key={r.id} className="orgd-req">
                  <div className="orgd-req__main">
                    <div className="orgd-req__title">{r.title}</div>
                    <div className="orgd-req__meta">
                      Подано: {formatDate(r.createdAt)} · {formatDate(r.eventDate)}
                    </div>
                    {r.status === 'REJECTED' && r.adminComment && (
                      <div className="orgd-req__comment">
                        <strong>Комментарий администратора:</strong> {r.adminComment}
                      </div>
                    )}
                  </div>
                  <span className={`orgd-status orgd-status--${meta.cls}`}>{meta.label}</span>
                </div>
              );
            })}
          </div>
        )
      )}
    </div>
  );
};

export default OrganizerDashboard;
