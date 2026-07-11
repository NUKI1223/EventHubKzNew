import React from 'react';
import { useTranslation } from 'react-i18next';
import { EVENTS_PER_PAGE } from '../constants';

// Windowed page list: first, last, and a range around the current page, with
// ellipses. Keeps the control usable when there are hundreds of pages (audit log).
const pageItems = (page, totalPages, window = 2) => {
  const items = [];
  const start = Math.max(0, page - window);
  const end = Math.min(totalPages - 1, page + window);
  if (start > 0) {
    items.push(0);
    if (start > 1) items.push('…');
  }
  for (let i = start; i <= end; i++) items.push(i);
  if (end < totalPages - 1) {
    if (end < totalPages - 2) items.push('…');
    items.push(totalPages - 1);
  }
  return items;
};

const Pagination = ({ page, totalPages, onChange, total, pageSize = EVENTS_PER_PAGE }) => {
  const { t } = useTranslation();
  if (totalPages <= 1) return null;
  return (
    <div className="pagination">
      <button
        className="pagination__btn"
        onClick={() => onChange(page - 1)}
        disabled={page === 0}
      >
        ←
      </button>
      {pageItems(page, totalPages).map((it, idx) =>
        it === '…' ? (
          <span key={`gap-${idx}`} className="pagination__ellipsis">…</span>
        ) : (
          <button
            key={it}
            className={`pagination__btn ${page === it ? 'pagination__btn--active' : ''}`}
            onClick={() => onChange(it)}
          >
            {it + 1}
          </button>
        )
      )}
      <button
        className="pagination__btn"
        onClick={() => onChange(page + 1)}
        disabled={page === totalPages - 1}
      >
        →
      </button>
      <span className="pagination__info">
        {t('common.pageRange', {
          from: page * pageSize + 1,
          to: Math.min((page + 1) * pageSize, total),
          total,
        })}
      </span>
    </div>
  );
};

export default Pagination;
