import React from 'react';
import { Link } from 'react-router-dom';
import { formatDate } from '../utils/dateUtils';
import LikeButton from './LikeButton';

const EventCard = ({ event, currentUserId }) => {
  const isOnline = event.online === true || event.online === 'true';
  return (
    <Link to={`/events/${event.id}`} className="event-card">
      <div className="event-card__image-wrap">
        {event.mainImageUrl ? (
          <img src={event.mainImageUrl} alt={event.title} className="event-card__image" />
        ) : (
          <div className="event-card__image-placeholder" />
        )}
        <div className="event-card__overlay">
          <span className="event-card__date-badge">{formatDate(event.eventDate)}</span>
        </div>
        {isOnline && (
          <span className="event-card__badge event-card__badge--online">Онлайн</span>
        )}
      </div>
      <div className="event-card__body">
        <h3 className="event-card__title">{event.title || 'Без названия'}</h3>
        <p className="event-card__desc">{event.shortDescription || event.description || ''}</p>
        <div className="event-card__tags">
          {event.tags?.map((tag, i) => (
            <span key={i} className="event-card__tag">{tag}</span>
          ))}
        </div>
        <div className="event-card__footer">
          <span className="event-card__location">{event.location || 'Не указан'}</span>
          <LikeButton eventId={event.id} currentUserId={currentUserId} />
        </div>
      </div>
    </Link>
  );
};

export default EventCard;
