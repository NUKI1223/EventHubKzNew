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
