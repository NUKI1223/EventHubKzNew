import React, { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import api from '../api';
import '../css/CheckinPage.css';

const CheckinPage = () => {
  const { code } = useParams();
  const [state, setState] = useState({ status: 'loading', message: '', reg: null });

  useEffect(() => {
    let cancelled = false;
    api.post('/api/registrations/checkin', { code })
      .then(res => {
        if (cancelled) return;
        const already = res.data?.status === 'ATTENDED';
        setState({ status: 'ok', reg: res.data, message: already ? 'already' : 'done' });
      })
      .catch(err => {
        if (cancelled) return;
        const s = err?.response?.status;
        const message = s === 403 ? 'У вас нет прав отмечать участников этого мероприятия'
          : s === 404 ? 'Код не найден'
          : 'Не удалось отметить участника. Попробуйте ещё раз.';
        setState({ status: 'error', message, reg: null });
      });
    return () => { cancelled = true; };
  }, [code]);

  return (
    <div className="checkin">
      <div className={`checkin__card checkin__card--${state.status}`}>
        {state.status === 'loading' && <p className="checkin__msg">Отмечаем…</p>}
        {state.status === 'ok' && (
          <>
            <div className="checkin__icon">✓</div>
            <p className="checkin__msg">
              {state.message === 'already' ? 'Участник уже был отмечен ранее' : 'Приход отмечен'}
            </p>
            <p className="checkin__sub">ID участника: {state.reg?.userId} · код {code}</p>
          </>
        )}
        {state.status === 'error' && (
          <>
            <div className="checkin__icon checkin__icon--err">✕</div>
            <p className="checkin__msg">{state.message}</p>
          </>
        )}
        <Link to="/" className="checkin__home">На главную</Link>
        <p className="checkin__hint">Чтобы отметить следующего — наведите камеру на его QR.</p>
      </div>
    </div>
  );
};

export default CheckinPage;
