// Категории событий: общие для лендинга (счётчики/плитки) и списка (фильтр по ?type=).
export const CATEGORIES = [
  { label: 'Хакатоны',    color: 'peach',    icon: '⚡', match: (e) => /hack|хакатон/i.test(e.title) || (e.tags || []).includes('hackathon') },
  { label: 'Митапы',      color: 'sage',     icon: '●',  match: (e) => /meetup|митап/i.test(e.title) || (e.tags || []).includes('meetup') },
  { label: 'Конференции', color: 'butter',   icon: '◆',  match: (e) => /\bconf|summit|forum|конференц/i.test(e.title) || (e.tags || []).includes('conference') },
  { label: 'Воркшопы',    color: 'lavender', icon: '✦',  match: (e) => /workshop|воркшоп|weekend|days?\b/i.test(e.title) || (e.tags || []).includes('workshop') },
  { label: 'Онлайн',      color: 'sky',      icon: '◯',  match: (e) => e.online },
  { label: 'Студентам',   color: 'rose',     icon: '✿',  match: (e) => (e.tags || []).some(t => ['career', 'student'].includes(t)) || /student|студент/i.test(e.title) },
];

export const findCategory = (label) => CATEGORIES.find(c => c.label === label) || null;
