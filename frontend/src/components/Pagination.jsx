import React from 'react';
import { useTranslation } from 'react-i18next';
import { EVENTS_PER_PAGE } from '../constants';

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
      {[...Array(totalPages)].map((_, i) => (
        <button
          key={i}
          className={`pagination__btn ${page === i ? 'pagination__btn--active' : ''}`}
          onClick={() => onChange(i)}
        >
          {i + 1}
        </button>
      ))}
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
