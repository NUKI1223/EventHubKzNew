import React from 'react';
import { EVENTS_PER_PAGE } from '../constants';

const Pagination = ({ page, totalPages, onChange, total }) => {
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
        {page * EVENTS_PER_PAGE + 1}–{Math.min((page + 1) * EVENTS_PER_PAGE, total)} из {total}
      </span>
    </div>
  );
};

export default Pagination;
