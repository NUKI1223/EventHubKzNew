import React from 'react';

const PageError = ({ message, onRetry }) => (
  <div style={{
    display: 'flex', flexDirection: 'column', alignItems: 'center',
    gap: 12, padding: '64px 24px', textAlign: 'center',
  }}>
    <div style={{
      width: 48, height: 48, borderRadius: '50%',
      background: 'var(--color-error-light)', display: 'grid',
      placeItems: 'center', color: 'var(--color-error)', fontSize: 22,
    }}>✕</div>
    <div style={{
      fontFamily: 'var(--font-display)', fontSize: 16,
      fontWeight: 700, color: 'var(--ink-900)',
    }}>Не удалось загрузить</div>
    <div style={{ fontSize: 14, color: 'var(--ink-500)', maxWidth: 360 }}>{message}</div>
    {onRetry && (
      <button
        onClick={onRetry}
        style={{
          padding: '9px 22px', borderRadius: 'var(--r-sm)',
          background: 'var(--ink-900)', color: 'var(--bg)',
          fontSize: 13, fontWeight: 600, border: 'none',
          cursor: 'pointer', fontFamily: 'var(--font-sans)',
        }}
      >
        Попробовать снова
      </button>
    )}
  </div>
);

export default PageError;
