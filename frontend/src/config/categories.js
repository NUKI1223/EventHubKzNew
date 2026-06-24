// Категории событий: общие для лендинга (счётчики/плитки) и списка (фильтр по ?type=).
// `label` — стабильный идентификатор (значение ?type= и ключ match), его НЕ переводим.
// `labelKey` — ключ i18n для отображаемой подписи (namespace `categories`).
export const CATEGORIES = [
  { label: 'Хакатоны',    labelKey: 'categories.hackathons',  color: 'peach',    icon: '⚡', match: (e) => /hack|хакатон/i.test(e.title) || (e.tags || []).includes('hackathon') },
  { label: 'Митапы',      labelKey: 'categories.meetups',     color: 'sage',     icon: '●',  match: (e) => /meetup|митап/i.test(e.title) || (e.tags || []).includes('meetup') },
  { label: 'Конференции', labelKey: 'categories.conferences', color: 'butter',   icon: '◆',  match: (e) => /\bconf|summit|forum|конференц/i.test(e.title) || (e.tags || []).includes('conference') },
  { label: 'Воркшопы',    labelKey: 'categories.workshops',   color: 'lavender', icon: '✦',  match: (e) => /workshop|воркшоп|weekend|days?\b/i.test(e.title) || (e.tags || []).includes('workshop') },
  { label: 'Онлайн',      labelKey: 'categories.online',      color: 'sky',      icon: '◯',  match: (e) => e.online },
  { label: 'Студентам',   labelKey: 'categories.students',    color: 'rose',     icon: '✿',  match: (e) => (e.tags || []).some(t => ['career', 'student'].includes(t)) || /student|студент/i.test(e.title) },
];

export const findCategory = (label) => CATEGORIES.find(c => c.label === label) || null;
