import React, { useEffect } from 'react';
import api from '../api';
import { useToggleResource } from '../hooks/useToggleResource';
import '../css/RegisterButton.css';

const RegisterButton = ({
  eventId, currentUserId, onCountChange, onRegisteredChange, disabled = false,
  variant = 'full', initialRegistered, initialCount, selfFetch,
}) => {
  const doSelfFetch = selfFetch ?? (initialRegistered === undefined);

  const { active: registered, count, busy, toggle, setActive, setCount } = useToggleResource({
    initialActive: initialRegistered || false,
    initialCount: initialCount || 0,
    currentUserId,
    postUrl: `/api/registrations/event/${eventId}`,
    deleteUrl: `/api/registrations/event/${eventId}`,
    msgOn: 'Вы записаны! Билет отправлен на почту',
    msgOff: 'Запись отменена',
    msgError: 'Не удалось изменить запись',
    onActiveChange: (v) => onRegisteredChange?.(v),
  });

  useEffect(() => {
    if (!doSelfFetch || !eventId) return;
    api.get(`/api/registrations/event/${eventId}/count`)
      .then(res => setCount(typeof res.data === 'number' ? res.data : 0))
      .catch(() => {});
    if (currentUserId) {
      api.get(`/api/registrations/user/${currentUserId}/events`)
        .then(res => {
          const ids = Array.isArray(res.data) ? res.data : [];
          setActive(ids.includes(Number(eventId)) || ids.includes(eventId));
        })
        .catch(() => {});
    }
  }, [doSelfFetch, eventId, currentUserId, setCount, setActive]);

  useEffect(() => { onCountChange?.(count); }, [count]); // eslint-disable-line react-hooks/exhaustive-deps

  const handleClick = (e) => { e.preventDefault(); e.stopPropagation(); if (!disabled) toggle(); };

  const common = {
    disabled: disabled || busy,
    'aria-pressed': registered,
    'aria-busy': busy,
    'aria-label': registered ? 'Отменить запись на мероприятие' : 'Записаться на мероприятие',
    onClick: handleClick,
  };

  if (variant === 'compact') {
    return (
      <button className={`register-button register-button--compact ${registered ? 'register-button--on' : ''} ${disabled ? 'register-button--disabled' : ''}`} {...common}>
        {busy ? '…' : (registered ? '✓ Иду' : (disabled ? 'Закрыта' : 'Записаться'))}
      </button>
    );
  }

  const label = registered ? '✓ Вы идёте' : (disabled ? 'Регистрация закрыта' : 'Записаться');
  return (
    <button
      className={`register-button ${registered ? 'register-button--on' : ''} ${disabled ? 'register-button--disabled' : ''}`}
      {...common}
      title={disabled ? 'Регистрация на это мероприятие закрыта' : undefined}
    >
      {busy ? '…' : label}
      {!disabled && <span className="register-button__count">{count}</span>}
    </button>
  );
};

export default RegisterButton;
