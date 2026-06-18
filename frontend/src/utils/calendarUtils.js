import { toDate } from './dateUtils';

// Локальное «плавающее» время в формате YYYYMMDDTHHMMSS (без таймзоны).
function fmt(d) {
  const p = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}${p(d.getMonth() + 1)}${p(d.getDate())}T${p(d.getHours())}${p(d.getMinutes())}00`;
}

function bounds(event) {
  const start = toDate(event?.eventDate);
  if (!start) return null;
  const end = new Date(start.getTime() + 2 * 60 * 60 * 1000); // +2 часа по умолчанию
  return { start, end };
}

function place(event) {
  if (event?.online) return 'Онлайн';
  return event?.location || '';
}

/** Ссылка «Добавить в Google Календарь». */
export function googleCalendarUrl(event) {
  const b = bounds(event);
  if (!b) return null;
  const params = new URLSearchParams({
    action: 'TEMPLATE',
    text: event.title || 'Мероприятие',
    dates: `${fmt(b.start)}/${fmt(b.end)}`,
    details: (event.shortDescription || event.fullDescription || '') + '\n\nEventHub.kz',
    location: place(event),
  });
  return `https://calendar.google.com/calendar/render?${params.toString()}`;
}

/** Скачивает .ics-файл события (универсальный календарный формат). */
export function downloadIcs(event) {
  const b = bounds(event);
  if (!b) return;
  const esc = (s) => String(s || '').replace(/([,;\\])/g, '\\$1').replace(/\n/g, '\\n');
  const ics = [
    'BEGIN:VCALENDAR',
    'VERSION:2.0',
    'PRODID:-//EventHub.kz//RU',
    'BEGIN:VEVENT',
    `UID:eventhub-${event.id}@eventhub.kz`,
    `DTSTAMP:${fmt(new Date())}`,
    `DTSTART:${fmt(b.start)}`,
    `DTEND:${fmt(b.end)}`,
    `SUMMARY:${esc(event.title)}`,
    `DESCRIPTION:${esc(event.shortDescription || event.fullDescription || '')}`,
    `LOCATION:${esc(place(event))}`,
    'END:VEVENT',
    'END:VCALENDAR',
  ].join('\r\n');

  const blob = new Blob([ics], { type: 'text/calendar;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `${(event.title || 'event').replace(/[^\wа-яА-Я0-9-]+/gi, '_').slice(0, 40)}.ics`;
  a.click();
  URL.revokeObjectURL(url);
}
