import React from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { formatDate, isPastEvent, isRegistrationClosed, relativeEventLabel } from '../utils/dateUtils';
import LikeButton from './LikeButton';
import RegisterButton from './RegisterButton';

const EventCard = ({ event, currentUserId, liked, likeCount, registered, regCount }) => {
  const { t } = useTranslation();
  const isOnline = event.online === true || event.online === 'true';
  const isPast = isPastEvent(event);
  const isNative = event.registrationType === 'NATIVE';
  const regClosed = isRegistrationClosed(event);
  const when = relativeEventLabel(event, t);
  const likes = likeCount ?? event.likesCount ?? 0;
  const going = regCount ?? 0;

  return (
    <Link to={`/events/${event.id}`} className={`event-card ${isPast ? 'event-card--past' : ''}`}>
      <div className="event-card__image-wrap">
        {event.mainImageUrl ? (
          <img src={event.mainImageUrl} alt={event.title} className="event-card__image" loading="lazy" />
        ) : (
          <div className="event-card__image-placeholder" />
        )}
        <div className="event-card__overlay">
          <span className="event-card__date-badge">{formatDate(event.eventDate)}</span>
        </div>
        <div className="event-card__badges">
          {isOnline && (
            <span className="event-card__badge event-card__badge--online">{t('events.online')}</span>
          )}
          {isPast ? (
            <span className="event-card__badge event-card__badge--past">{t('events.badgePast')}</span>
          ) : when && (
            <span className="event-card__badge event-card__badge--when">{when}</span>
          )}
        </div>
        <div className="event-card__like-corner">
          <LikeButton
            eventId={event.id}
            currentUserId={currentUserId}
            variant="icon"
            initialLiked={!!liked}
            initialCount={likes}
            selfFetch={false}
            disabled={isPast}
          />
        </div>
      </div>
      <div className="event-card__body">
        <h3 className="event-card__title">{event.title || t('events.noTitle')}</h3>
        <p className="event-card__desc">{event.shortDescription || event.description || ''}</p>
        <div className="event-card__tags">
          {event.tags?.map((tag, i) => (
            <span key={i} className="event-card__tag">{tag}</span>
          ))}
        </div>

        {isNative && !isPast && (
          <div className="event-card__rsvp">
            <RegisterButton
              eventId={event.id}
              currentUserId={currentUserId}
              variant="compact"
              initialRegistered={!!registered}
              initialCount={going}
              selfFetch={false}
              disabled={regClosed}
              questions={event.registrationType === 'NATIVE' ? event.questions : null}
            />
            <span className="event-card__going">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="13" height="13">
                <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/>
                <path d="M22 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/>
              </svg>
              {t('events.going', { count: going })}
            </span>
          </div>
        )}

        <div className="event-card__footer">
          <span className="event-card__location">{event.location || t('events.noLocation')}</span>
        </div>
      </div>
    </Link>
  );
};

export default EventCard;
