import React, { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import api from '../api';
import { useToggleResource } from '../hooks/useToggleResource';
import RegistrationModal from './RegistrationModal';
import '../css/RegisterButton.css';

const RegisterButton = ({
  eventId, currentUserId, onCountChange, onRegisteredChange, disabled = false,
  variant = 'full', initialRegistered, initialCount, selfFetch, questions,
}) => {
  const doSelfFetch = selfFetch ?? (initialRegistered === undefined);
  const { t } = useTranslation();

  const [modalOpen, setModalOpen] = useState(false);
  const resolverRef = useRef(null);
  const hasQuestions = Array.isArray(questions) && questions.length > 0;

  const onBeforeActivate = hasQuestions
    ? () => new Promise((resolve) => { resolverRef.current = resolve; setModalOpen(true); })
    : undefined;

  const { active: registered, count, busy, toggle, setActive, setCount } = useToggleResource({
    initialActive: initialRegistered || false,
    initialCount: initialCount || 0,
    currentUserId,
    postUrl: `/api/registrations/event/${eventId}`,
    deleteUrl: `/api/registrations/event/${eventId}`,
    msgOn: t('eventDetail.registerSuccess'),
    msgOff: t('eventDetail.registerCancelled'),
    msgError: t('eventDetail.registerError'),
    onActiveChange: (v) => onRegisteredChange?.(v),
    onBeforeActivate,
  });

  const modal = modalOpen ? (
    <RegistrationModal
      questions={questions}
      onSubmit={(answers) => { setModalOpen(false); resolverRef.current?.({ answers }); }}
      onClose={() => { setModalOpen(false); resolverRef.current?.(null); }}
    />
  ) : null;

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
    disabled: disabled || busy || modalOpen,
    'aria-pressed': registered,
    'aria-busy': busy,
    'aria-label': registered ? t('eventDetail.ariaUnregister') : t('eventDetail.ariaRegister'),
    onClick: handleClick,
  };

  if (variant === 'compact') {
    return (
      <>
        <button className={`register-button register-button--compact ${registered ? 'register-button--on' : ''} ${disabled ? 'register-button--disabled' : ''}`} {...common}>
          {busy ? '…' : (registered ? `✓ ${t('eventDetail.compactGoing')}` : (disabled ? t('eventDetail.compactClosed') : t('eventDetail.compactRegister')))}
        </button>
        {modal}
      </>
    );
  }

  const label = registered ? `✓ ${t('eventDetail.labelGoing')}` : (disabled ? t('eventDetail.labelClosed') : t('eventDetail.labelRegister'));
  return (
    <>
      <button
        className={`register-button ${registered ? 'register-button--on' : ''} ${disabled ? 'register-button--disabled' : ''}`}
        {...common}
        title={disabled ? t('eventDetail.titleClosed') : undefined}
      >
        {busy ? '…' : label}
        {!disabled && <span className="register-button__count">{count}</span>}
      </button>
      {modal}
    </>
  );
};

export default RegisterButton;
