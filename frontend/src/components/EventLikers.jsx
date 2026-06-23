import React, { useState, useEffect, useMemo } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import api from '../api';
import { SkeletonCard } from './Skeleton';
import EmptyState from './EmptyState';
import PageError from './PageError';
import TagSelector from './TagSelector';
import '../css/EventLikers.css';

const EventLikers = () => {
  const { t } = useTranslation();
  const { id: eventId } = useParams();
  const [event, setEvent] = useState(null);
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [search, setSearch] = useState('');
  const [filterTags, setFilterTags] = useState([]);

  useEffect(() => {
    const load = async () => {
      try {
        setLoading(true);
        const [evRes, likesRes] = await Promise.all([
          api.get(`/api/events/${eventId}`),
          api.get(`/api/likes/event/${eventId}`),
        ]);
        setEvent(evRes.data);
        document.title = `Лайкнули «${evRes.data.title}» — EventHub.kz`;

        const likes = Array.isArray(likesRes.data) ? likesRes.data : [];
        const userIds = [...new Set(likes.map(l => l.userId))];
        if (userIds.length === 0) { setUsers([]); return; }

        const usersRes = await api.get('/api/users/batch', { params: { ids: userIds.join(',') } });
        setUsers(Array.isArray(usersRes.data) ? usersRes.data : []);
      } catch {
        setError(t('events.likersLoadError'));
      } finally {
        setLoading(false);
      }
    };
    load();
  }, [eventId]);

  const filtered = useMemo(() => {
    return users.filter(u => {
      if (search) {
        const q = search.toLowerCase();
        if (!u.username?.toLowerCase().includes(q)
            && !u.description?.toLowerCase().includes(q)) return false;
      }
      if (filterTags.length > 0) {
        const userTags = Array.isArray(u.tags) ? u.tags : [];
        if (!filterTags.every(t => userTags.includes(t))) return false;
      }
      return true;
    });
  }, [users, search, filterTags]);

  if (loading) return (
    <div className="liker-page">
      <div className="liker-page__hdr"><span className="liker-page__title">{t('common.loading')}</span></div>
      <div className="liker-page__grid">
        {[...Array(4)].map((_, i) => <SkeletonCard key={i} />)}
      </div>
    </div>
  );

  if (error) return <div className="liker-page"><PageError message={error} /></div>;

  return (
    <div className="liker-page">
      <div className="liker-page__hdr">
        <Link to={`/events/${eventId}`} className="liker-page__back">{t('events.backToEvent')}</Link>
        <h1 className="liker-page__title">
          {t('events.likersTitle', { title: event?.title })}
        </h1>
        <p className="liker-page__sub">{users.length === 1 ? t('events.participantsOne', { count: users.length }) : t('events.participantsMany', { count: users.length })}</p>
      </div>

      <div className="liker-page__filters">
        <input
          className="liker-page__search"
          type="text"
          placeholder={t('events.likersSearchPlaceholder')}
          value={search}
          onChange={e => setSearch(e.target.value)}
        />
        <div className="liker-page__tag-filter">
          <TagSelector selectedTags={filterTags} onChange={setFilterTags} type="USER" />
        </div>
      </div>

      {filtered.length === 0 ? (
        <EmptyState
          icon="search"
          title={t('events.likersEmptyTitle')}
          subtitle={t('events.likersEmptySubtitle')}
        />
      ) : (
        <div className="liker-page__grid">
          {filtered.map(u => (
            <Link to={`/profile/${u.username}`} key={u.id} className="liker-card">
              {u.avatarUrl ? (
                <img src={u.avatarUrl} alt={u.username} className="liker-card__avatar" />
              ) : (
                <div className="liker-card__avatar-ph">{u.username?.[0]?.toUpperCase() || '?'}</div>
              )}
              <div className="liker-card__body">
                <div className="liker-card__name">{u.username || t('events.noName')}</div>
                {u.description && (
                  <div className="liker-card__desc">{u.description}</div>
                )}
                {Array.isArray(u.tags) && u.tags.length > 0 && (
                  <div className="liker-card__tags">
                    {u.tags.slice(0, 4).map(t => (
                      <span key={t} className="liker-card__tag">{t}</span>
                    ))}
                    {u.tags.length > 4 && (
                      <span className="liker-card__tag liker-card__tag--more">+{u.tags.length - 4}</span>
                    )}
                  </div>
                )}
              </div>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
};

export default EventLikers;
