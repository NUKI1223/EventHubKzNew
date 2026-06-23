import React, { useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { QRCodeCanvas } from 'qrcode.react';
import { formatDate } from '../utils/dateUtils';
import '../css/EventTicket.css';

/**
 * Билет участника на конкретное мероприятие: QR с кодом, eventId, именем, email
 * и датой регистрации. Организатор вводит код, чтобы отметить приход.
 */
const EventTicket = ({ registration, event, username, email }) => {
  const { t } = useTranslation();
  const wrapRef = useRef(null);
  if (!registration) return null;

  const attended = registration.status === 'ATTENDED';
  // QR кодирует ссылку отметки прихода: организатор/сотрудник наводит обычную
  // камеру телефона → открывается /checkin/<код> → приход отмечается.
  const payload = `${window.location.origin}/checkin/${registration.code}`;

  const download = () => {
    const canvas = wrapRef.current?.querySelector('canvas');
    if (!canvas) return;
    const a = document.createElement('a');
    a.href = canvas.toDataURL('image/png');
    a.download = `ticket-${event?.id || 'event'}-${registration.code}.png`;
    a.click();
  };

  return (
    <div className="ticket">
      <div className="ticket__head">
        <span className="ticket__label">{t('eventDetail.ticketLabel')}</span>
        {attended && <span className="ticket__badge">✓ {t('eventDetail.ticketAttended')}</span>}
      </div>

      <div className="ticket__code-box" ref={wrapRef}>
        <QRCodeCanvas value={payload} size={172} level="M" />
      </div>

      <div className="ticket__meta">
        <div className="ticket__row">
          <span className="ticket__row-label">{t('eventDetail.ticketCode')}</span>
          <span className="ticket__code">{registration.code}</span>
        </div>
        <div className="ticket__row">
          <span className="ticket__row-label">{t('eventDetail.ticketRegDate')}</span>
          <span className="ticket__row-val">{formatDate(registration.createdAt)}</span>
        </div>
      </div>

      <p className="ticket__hint">
        {t('eventDetail.ticketHint')}
      </p>
      <button type="button" className="ticket__btn" onClick={download}>{t('eventDetail.ticketDownload')}</button>
    </div>
  );
};

export default EventTicket;
