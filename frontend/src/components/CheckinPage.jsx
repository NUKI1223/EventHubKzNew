import React, { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import api from '../api';
import '../css/CheckinPage.css';

const CheckinPage = () => {
  const { code } = useParams();
  const { t } = useTranslation();
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
        const message = s === 403 ? 'forbidden'
          : s === 404 ? 'notFound'
          : 'failed';
        setState({ status: 'error', message, reg: null });
      });
    return () => { cancelled = true; };
  }, [code]);

  return (
    <div className="checkin">
      <div className={`checkin__card checkin__card--${state.status}`}>
        {state.status === 'loading' && <p className="checkin__msg">{t('eventDetail.checkinLoading')}</p>}
        {state.status === 'ok' && (
          <>
            <div className="checkin__icon">✓</div>
            <p className="checkin__msg">
              {state.message === 'already' ? t('eventDetail.checkinAlready') : t('eventDetail.checkinDone')}
            </p>
            <p className="checkin__sub">{t('eventDetail.checkinSub', { userId: state.reg?.userId, code })}</p>
          </>
        )}
        {state.status === 'error' && (
          <>
            <div className="checkin__icon checkin__icon--err">✕</div>
            <p className="checkin__msg">
              {state.message === 'forbidden' ? t('eventDetail.checkinForbidden')
                : state.message === 'notFound' ? t('eventDetail.checkinNotFound')
                : t('eventDetail.checkinFailed')}
            </p>
          </>
        )}
        <Link to="/" className="checkin__home">{t('eventDetail.checkinHome')}</Link>
        <p className="checkin__hint">{t('eventDetail.checkinHint')}</p>
      </div>
    </div>
  );
};

export default CheckinPage;
