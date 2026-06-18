import React, { useEffect } from 'react';
import api from '../api';
import { useToggleResource } from '../hooks/useToggleResource';
import '../css/LikeButton.css';

const Heart = ({ filled }) => (
  <svg
    viewBox="0 0 24 24" width="16" height="16"
    fill={filled ? 'currentColor' : 'none'}
    stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"
    className="like-button__icon" aria-hidden="true"
  >
    <path d="M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1-1.1a5.5 5.5 0 1 0-7.8 7.8L12 21l8.8-8.6a5.5 5.5 0 0 0 0-7.8z"/>
  </svg>
);

const LikeButton = ({
  eventId, currentUserId, onLikeChange, onLikedChange, disabled = false,
  variant = 'full', initialLiked, initialCount, selfFetch,
}) => {
  // Если родитель (список) уже передал состояние — собственных запросов не делаем.
  const doSelfFetch = selfFetch ?? (initialLiked === undefined);

  const { active: liked, count, busy, toggle, setActive, setCount } = useToggleResource({
    initialActive: initialLiked || false,
    initialCount: initialCount || 0,
    currentUserId,
    postUrl: `/api/likes/event/${eventId}`,
    deleteUrl: `/api/likes/event/${eventId}`,
    msgError: 'Не удалось обновить лайк',
    onActiveChange: (v) => onLikedChange?.(v),
  });

  useEffect(() => {
    if (!doSelfFetch || !eventId) return;
    api.get(`/api/likes/event/${eventId}/count`)
      .then(res => setCount(typeof res.data === 'number' ? res.data : 0))
      .catch(() => {});
    if (currentUserId) {
      api.get(`/api/likes/user/${currentUserId}/events`)
        .then(res => {
          const ids = Array.isArray(res.data) ? res.data : [];
          setActive(ids.includes(Number(eventId)) || ids.includes(eventId));
        })
        .catch(() => {});
    }
  }, [doSelfFetch, eventId, currentUserId, setCount, setActive]);

  useEffect(() => { onLikeChange?.(count); }, [count]); // eslint-disable-line react-hooks/exhaustive-deps

  const handleClick = (e) => { e.preventDefault(); e.stopPropagation(); if (!disabled) toggle(); };

  const common = {
    disabled: disabled || busy,
    'aria-pressed': liked,
    'aria-busy': busy,
    'aria-label': liked ? 'Убрать лайк' : 'Поставить лайк',
    title: disabled ? 'Мероприятие уже прошло' : undefined,
    onClick: handleClick,
  };

  if (variant === 'icon') {
    return (
      <button className={`like-icon ${liked ? 'like-icon--on' : ''}`} {...common}>
        <Heart filled={liked} />
        <span className="like-icon__count">{count}</span>
      </button>
    );
  }

  return (
    <button className={`like-button ${liked ? 'like-button--liked' : ''} ${disabled ? 'like-button--disabled' : ''}`} {...common}>
      <Heart filled={liked} />
      Нравится ({count})
    </button>
  );
};

export default LikeButton;
