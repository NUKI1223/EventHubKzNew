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
export const relativeEventLabel = (event) => {
  const d = toDate(event?.eventDate);
  if (!d) return null;
  const diff = d.getTime() - Date.now();
  if (diff < 0) return 'Прошло';
  const days = Math.floor(diff / 86400000);
  if (days === 0) return 'Сегодня';
  if (days === 1) return 'Завтра';
  if (days < 7) return `через ${days} дн.`;
  if (days < 30) return `через ${Math.floor(days / 7)} нед.`;
  return `через ${Math.floor(days / 30)} мес.`;
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

export const BUCKET_LABELS = {
  today: 'Сегодня',
  week: 'На этой неделе',
  month: 'В этом месяце',
  later: 'Позже',
  past: 'Прошедшие',
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
