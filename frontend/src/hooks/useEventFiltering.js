import { useMemo } from 'react';

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

    if (sortOption === 'nameAsc') {
      filtered.sort((a, b) => (a.title || '').localeCompare(b.title || ''));
    } else if (sortOption === 'date') {
      filtered.sort((a, b) => Date.parse(a.eventDate) - Date.parse(b.eventDate));
    } else if (sortOption === 'likes') {
      filtered.sort((a, b) => (Number(b.likeCount) || 0) - (Number(a.likeCount) || 0));
    }

    return filtered;
  }, [events, selectedTags, selectedCity, onlineOnly, sortOption]);
}
