import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import api from '../api';
import toast from 'react-hot-toast';
import { useAuthUser } from '../hooks/useAuthUser';
import { formatDate, isPastEvent, isRegistrationClosed } from '../utils/dateUtils';
import { googleCalendarUrl, downloadIcs } from '../utils/calendarUtils';
import '../css/EventDetail.css';
import LikeButton from './LikeButton';
import EventLikes from './EventLikes';
import RegisterButton from './RegisterButton';
import EventRegistrations from './EventRegistrations';
import EventTicket from './EventTicket';
import Skeleton from './Skeleton';
import PageError from './PageError';

const EventDetail = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const [event, setEvent] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [likeCount, setLikeCount] = useState(0);
  const [liked, setLiked] = useState(false);
  const [registrationCount, setRegistrationCount] = useState(0);
  const [myRegistration, setMyRegistration] = useState(null);
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
  const currentUserId = localStorage.getItem('userId');
  const authUser = useAuthUser();
  const isAdmin = authUser?.role === 'ADMIN';

  const refreshMyRegistration = () => {
    if (!currentUserId) { setMyRegistration(null); return; }
    api.get(`/api/registrations/event/${id}/mine`)
      .then(res => setMyRegistration(res.data))
      .catch(() => setMyRegistration(null));
  };

  useEffect(() => {
    const fetchEvent = async () => {
      try {
        const res = await api.get(`/api/events/${id}`);
        setEvent(res.data);
        document.title = `${res.data.title} — EventHub.kz`;
        if (res.data.registrationType === 'NATIVE' && currentUserId) {
          refreshMyRegistration();
        }
      } catch (err) {
        console.error(err);
        setError('Ошибка при загрузке мероприятия');
        document.title = 'Мероприятие — EventHub.kz';
      } finally {
        setLoading(false);
      }
    };
    fetchEvent();
    // Регистрируем просмотр (best-effort, не блокирует загрузку)
    api.post(`/api/events/${id}/view`).catch(() => {});
    return () => { document.title = 'EventHub.kz'; };
  }, [id]);

  const handleShare = async () => {
    const url = window.location.href;
    if (navigator.share) {
      try { await navigator.share({ title: event?.title, url }); } catch { /* отменено пользователем */ }
    } else {
      try {
        await navigator.clipboard.writeText(url);
        toast.success('Ссылка скопирована');
      } catch {
        toast.error('Не удалось скопировать ссылку');
      }
    }
  };

  const handleDelete = async () => {
    try {
      await api.delete(`/api/events/${id}`);
      toast.success('Мероприятие удалено');
      navigate('/eventlist');
    } catch (err) {
      toast.error('Не удалось удалить мероприятие');
    } finally {
      setShowDeleteConfirm(false);
    }
  };

  if (loading) return (
    <div className="event-detail-container">
      <div className="event-detail-header">
        <Skeleton variant="image" />
        <Skeleton variant="title" width="60%" style={{ marginTop: '20px' }} />
      </div>
      <div className="event-detail-content">
        <div className="event-detail-info">
          <Skeleton variant="text" />
          <Skeleton variant="text" />
          <Skeleton variant="text-short" />
          <Skeleton variant="text" />
        </div>
        <div className="event-detail-description">
          <Skeleton variant="title" width="40%" />
          <Skeleton variant="text" />
          <Skeleton variant="text" />
          <Skeleton variant="text" />
          <Skeleton variant="text-short" />
        </div>
      </div>
    </div>
  );
  if (error) return <div className="event-detail-container"><PageError message={error} /></div>;
  if (!event) return <div className="event-detail-container"><PageError message="Мероприятие не найдено" /></div>;

  return (
    <div className="event-detail-container">
      <div className="event-detail-header">
        {event.mainImageUrl && (
          <img src={event.mainImageUrl} alt={event.title} className="event-detail-image" />
        )}
        <h2 className="event-detail-title">{event.title}</h2>
      </div>
      <div className="event-detail-content">
        <div className="event-detail-info">
          <p><strong>Дата мероприятия:</strong> {formatDate(event.eventDate)}</p>
          <p><strong>Дедлайн регистрации:</strong> {formatDate(event.registrationDeadline)}</p>
          <p><strong>Формат:</strong> {event.online ? 'Онлайн' : 'Оффлайн'}</p>
          {!event.online && event.location && (
            <p><strong>Место проведения:</strong> {event.location}</p>
          )}
          {event.tags && event.tags.length > 0 && (
            <p><strong>Тэги:</strong> {event.tags.join(', ')}</p>
          )}
          <p><strong>Организатор:</strong> {event.organizerEmail}</p>
          <p><strong>Просмотров:</strong> {event.viewsCount ?? 0}</p>

          <div className="event-detail__quick">
            <a
              className="event-detail__quick-btn"
              href={googleCalendarUrl(event) || '#'}
              target="_blank"
              rel="noopener noreferrer"
            >
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="15" height="15"><rect x="3" y="4" width="18" height="18" rx="2"/><path d="M16 2v4M8 2v4M3 10h18"/></svg>
              В Google Календарь
            </a>
            <button type="button" className="event-detail__quick-btn" onClick={() => downloadIcs(event)}>
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="15" height="15"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M7 10l5 5 5-5M12 15V3"/></svg>
              Скачать .ics
            </button>
            <button type="button" className="event-detail__quick-btn" onClick={handleShare}>
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="15" height="15"><circle cx="18" cy="5" r="3"/><circle cx="6" cy="12" r="3"/><circle cx="18" cy="19" r="3"/><path d="M8.6 13.5l6.8 4M15.4 6.5l-6.8 4"/></svg>
              Поделиться
            </button>
          </div>
        </div>
        <div className="event-detail-description">
          <h3>Описание мероприятия</h3>
          <p>{event.fullDescription}</p>
          {event.registrationType === 'EXTERNAL' && event.externalLink && (
            <p>
              <a href={event.externalLink} target="_blank" rel="noopener noreferrer">
                Перейти на сайт мероприятия
              </a>
            </p>
          )}
          {isPastEvent(event) && (
            <p className="event-detail__past-banner">
              Мероприятие уже прошло — записаться нельзя, но можно посмотреть, кто отметил его раньше.
            </p>
          )}
          <div className="event-actions">
            {event.registrationType === 'NATIVE' && (
              <>
                <RegisterButton
                  eventId={event.id}
                  currentUserId={currentUserId}
                  onCountChange={setRegistrationCount}
                  onRegisteredChange={refreshMyRegistration}
                  disabled={isRegistrationClosed(event)}
                />
                <EventRegistrations eventId={event.id} count={registrationCount} />
              </>
            )}
            <LikeButton
              eventId={event.id}
              currentUserId={currentUserId}
              onLikeChange={setLikeCount}
              onLikedChange={setLiked}
              disabled={isPastEvent(event)}
            />
            <EventLikes eventId={event.id} likeCount={likeCount} liked={liked} />
            {isAdmin && (
              <button className="delete-event-btn" onClick={() => setShowDeleteConfirm(true)}>
                Удалить мероприятие
              </button>
            )}
          </div>

          {event.registrationType === 'NATIVE' && myRegistration && (
            <EventTicket
              registration={myRegistration}
              event={event}
              username={authUser?.sub}
              email={authUser?.email}
            />
          )}
        </div>
      </div>

      {showDeleteConfirm && (
        <div className="logout-overlay" onClick={() => setShowDeleteConfirm(false)}>
          <div className="logout-dialog" onClick={e => e.stopPropagation()}>
            <div className="logout-dialog__title">Удалить мероприятие?</div>
            <p className="logout-dialog__text">
              «{event.title}» будет удалено безвозвратно.
            </p>
            <div className="logout-dialog__actions">
              <button className="logout-dialog__confirm" onClick={handleDelete}>
                Удалить
              </button>
              <button className="logout-dialog__cancel" onClick={() => setShowDeleteConfirm(false)}>
                Отмена
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default EventDetail;