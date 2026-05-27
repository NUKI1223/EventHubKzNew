export const formatDate = (dateInput) => {
  let dateObj;
  if (Array.isArray(dateInput)) {
    const [year, month, day, hour, minute, second = 0, nanosecond = 0] = dateInput;
    dateObj = new Date(year, month - 1, day, hour, minute, second, Math.floor(nanosecond / 1000000));
  } else {
    dateObj = new Date(dateInput);
  }
  if (!isNaN(dateObj.getTime())) {
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
