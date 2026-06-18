import React, { useRef } from 'react';
import { QRCodeCanvas } from 'qrcode.react';
import { formatDate } from '../utils/dateUtils';
import '../css/EventTicket.css';

/**
 * Билет участника на конкретное мероприятие: QR с кодом, eventId, именем, email
 * и датой регистрации. Организатор вводит код, чтобы отметить приход.
 */
const EventTicket = ({ registration, event, username, email }) => {
  const wrapRef = useRef(null);
  if (!registration) return null;

  const attended = registration.status === 'ATTENDED';
  const payload = JSON.stringify({
    type: 'eventhub-ticket',
    code: registration.code,
    eventId: event?.id,
    eventTitle: event?.title,
    name: username || '',
    email: email || '',
    registeredAt: registration.createdAt,
  });

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
        <span className="ticket__label">Ваш билет</span>
        {attended && <span className="ticket__badge">✓ Вы отмечены</span>}
      </div>

      <div className="ticket__code-box" ref={wrapRef}>
        <QRCodeCanvas value={payload} size={172} level="M" />
      </div>

      <div className="ticket__meta">
        <div className="ticket__row">
          <span className="ticket__row-label">Код</span>
          <span className="ticket__code">{registration.code}</span>
        </div>
        <div className="ticket__row">
          <span className="ticket__row-label">Дата регистрации</span>
          <span className="ticket__row-val">{formatDate(registration.createdAt)}</span>
        </div>
      </div>

      <p className="ticket__hint">
        Покажите QR или назовите код организатору на входе — он отметит ваш приход.
      </p>
      <button type="button" className="ticket__btn" onClick={download}>Скачать билет</button>
    </div>
  );
};

export default EventTicket;
