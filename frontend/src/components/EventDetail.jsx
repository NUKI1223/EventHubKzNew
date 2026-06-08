import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import api from '../api';
import toast from 'react-hot-toast';
import { useAuthUser } from '../hooks/useAuthUser';
import { formatDate, isPastEvent } from '../utils/dateUtils';
import '../css/EventDetail.css';
import LikeButton from './LikeButton';
import EventLikes from './EventLikes';
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
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
  const currentUserId = localStorage.getItem('userId');
  const authUser = useAuthUser();
  const isAdmin = authUser?.role === 'ADMIN';

  useEffect(() => {
    const fetchEvent = async () => {
      try {
        const res = await api.get(`/api/events/${id}`);
        setEvent(res.data);
        document.title = `${res.data.title} — EventHub.kz`;
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
        </div>
        <div className="event-detail-description">
          <h3>Описание мероприятия</h3>
          <p>{event.fullDescription}</p>
          {event.externalLink && (
            <p>
              <a href={event.externalLink} target="_blank" rel="noopener noreferrer">
                Перейти на сайт мероприятия
              </a>
            </p>
          )}
          {isPastEvent(event) && (
            <p className="event-detail__past-banner">
              Мероприятие уже прошло — лайкнуть нельзя, но можно посмотреть, кто отметил его раньше.
            </p>
          )}
          <div className="event-actions">
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