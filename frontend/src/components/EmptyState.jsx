import React from 'react';
import { Link } from 'react-router-dom';
import '../css/EmptyState.css';

const CalendarIcon = () => (
  <svg className="empty-state__icon" viewBox="0 0 120 120" fill="none" xmlns="http://www.w3.org/2000/svg">
    <rect x="15" y="25" width="90" height="80" rx="12" stroke="currentColor" strokeWidth="3" />
    <path d="M15 50h90" stroke="currentColor" strokeWidth="3" />
    <rect x="35" y="15" width="4" height="20" rx="2" fill="currentColor" />
    <rect x="81" y="15" width="4" height="20" rx="2" fill="currentColor" />
    <circle cx="45" cy="70" r="5" fill="currentColor" opacity="0.5" />
    <circle cx="60" cy="70" r="5" fill="currentColor" opacity="0.5" />
    <circle cx="75" cy="70" r="5" fill="currentColor" opacity="0.5" />
    <circle cx="45" cy="88" r="5" fill="currentColor" opacity="0.3" />
    <circle cx="60" cy="88" r="5" fill="currentColor" opacity="0.3" />
  </svg>
);

const SearchIcon = () => (
  <svg className="empty-state__icon" viewBox="0 0 120 120" fill="none" xmlns="http://www.w3.org/2000/svg">
    <circle cx="52" cy="52" r="30" stroke="currentColor" strokeWidth="3" />
    <path d="M74 74L100 100" stroke="currentColor" strokeWidth="3" strokeLinecap="round" />
    <path d="M40 52h24" stroke="currentColor" strokeWidth="2" strokeLinecap="round" opacity="0.4" />
    <path d="M52 40v24" stroke="currentColor" strokeWidth="2" strokeLinecap="round" opacity="0.4" />
  </svg>
);

const HeartIcon = () => (
  <svg className="empty-state__icon" viewBox="0 0 120 120" fill="none" xmlns="http://www.w3.org/2000/svg">
    <path
      d="M60 100S15 72 15 42c0-16 12-28 28-28 10 0 17 6 17 6s7-6 17-6c16 0 28 12 28 28 0 30-45 58-45 58z"
      stroke="currentColor"
      strokeWidth="3"
      fill="none"
    />
    <path
      d="M43 30c-8 0-16 7-16 16"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      opacity="0.3"
    />
  </svg>
);

const InboxIcon = () => (
  <svg className="empty-state__icon" viewBox="0 0 120 120" fill="none" xmlns="http://www.w3.org/2000/svg">
    <rect x="20" y="30" width="80" height="65" rx="10" stroke="currentColor" strokeWidth="3" />
    <path d="M20 70h25l8 12h14l8-12h25" stroke="currentColor" strokeWidth="3" />
    <path d="M50 50h20" stroke="currentColor" strokeWidth="2" strokeLinecap="round" opacity="0.4" />
    <path d="M45 58h30" stroke="currentColor" strokeWidth="2" strokeLinecap="round" opacity="0.3" />
  </svg>
);

const icons = {
  calendar: CalendarIcon,
  search: SearchIcon,
  heart: HeartIcon,
  inbox: InboxIcon,
};

const EmptyState = ({ icon = 'calendar', title, subtitle, actionText, actionLink, onAction }) => {
  const IconComponent = icons[icon] || CalendarIcon;

  return (
    <div className="empty-state">
      <IconComponent />
      <h3 className="empty-state__title">{title}</h3>
      {subtitle && <p className="empty-state__subtitle">{subtitle}</p>}
      {actionText && actionLink && (
        <Link to={actionLink} className="empty-state__action">
          {actionText}
        </Link>
      )}
      {actionText && onAction && !actionLink && (
        <button className="empty-state__action" onClick={onAction}>
          {actionText}
        </button>
      )}
    </div>
  );
};

export default EmptyState;
