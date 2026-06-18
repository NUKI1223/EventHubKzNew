import { useMemo } from 'react';
import { isPastEvent } from '../utils/dateUtils';

export function useEventFiltering(events, { selectedTags, selectedCity, onlineOnly, sortOption }) {
  return useMemo(() => {
    let filtered = [...events];

    if (selectedTags?.length > 0) {
      filtered = filtered.filter(e => selectedTags.every(t => e.tags?.includes(t)));
    }
    if (selectedCity) {
      filtered = filtered.filter(e => e.location?.toLowerCase().includes(selectedCity.toLowerCase()));
    }
    if (onlineOnly) {
      filtered = filtered.filter(e => e.online === true || e.online === 'true');
    }

    const byOption = (a, b) => {
      if (sortOption === 'nameAsc') return (a.title || '').localeCompare(b.title || '');
      if (sortOption === 'date') return Date.parse(a.eventDate) - Date.parse(b.eventDate);
      if (sortOption === 'likes') return (Number(b.likeCount) || 0) - (Number(a.likeCount) || 0);
      return 0;
    };

    // Прошедшие всегда уходят вниз, независимо от выбранной сортировки.
    filtered.sort((a, b) => {
      const pa = isPastEvent(a), pb = isPastEvent(b);
      if (pa !== pb) return pa ? 1 : -1;
      return byOption(a, b);
    });

    return filtered;
  }, [events, selectedTags, selectedCity, onlineOnly, sortOption]);
}
