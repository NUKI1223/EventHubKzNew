export const toDate = (dateInput) => {
  if (dateInput == null) return null;
  if (Array.isArray(dateInput)) {
    const [year, month, day, hour = 0, minute = 0, second = 0, nanosecond = 0] = dateInput;
    return new Date(year, month - 1, day, hour, minute, second, Math.floor(nanosecond / 1000000));
  }
  const d = new Date(dateInput);
  return isNaN(d.getTime()) ? null : d;
};

export const isPastEvent = (event) => {
  const d = toDate(event?.eventDate);
  return d != null && d.getTime() < Date.now();
};

// Регистрация закрыта, если событие уже прошло либо истёк дедлайн регистрации.
export const isRegistrationClosed = (event) => {
  if (isPastEvent(event)) return true;
  const d = toDate(event?.registrationDeadline);
  return d != null && d.getTime() < Date.now();
};

// Короткая относительная метка времени до события: «Сегодня», «Завтра», «через N дн.», «Прошло».
// Принимает функцию перевода t (из useTranslation) — ключи в namespace `relative`.
export const relativeEventLabel = (event, t) => {
  const d = toDate(event?.eventDate);
  if (!d) return null;
  const diff = d.getTime() - Date.now();
  if (diff < 0) return t('relative.past');
  const days = Math.floor(diff / 86400000);
  if (days === 0) return t('relative.today');
  if (days === 1) return t('relative.tomorrow');
  if (days < 7) return t('relative.inDays', { count: days });
  if (days < 30) return t('relative.inWeeks', { count: Math.floor(days / 7) });
  return t('relative.inMonths', { count: Math.floor(days / 30) });
};

// Временной «ведро»-ключ для группировки списка.
export const timeBucket = (event) => {
  const d = toDate(event?.eventDate);
  if (!d) return 'later';
  const diff = d.getTime() - Date.now();
  if (diff < 0) return 'past';
  const days = Math.floor(diff / 86400000);
  if (days === 0) return 'today';
  if (days < 7) return 'week';
  if (days < 31) return 'month';
  return 'later';
};

export const formatDate = (dateInput) => {
  const dateObj = toDate(dateInput);
  if (dateObj) {
    return dateObj.toLocaleString([], {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit'
    });
  }
  return 'Неверная дата';
};
