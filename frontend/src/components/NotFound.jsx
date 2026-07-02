import React from 'react';
import { useTranslation } from 'react-i18next';
import EmptyState from './EmptyState';

const NotFound = () => {
  const { t } = useTranslation();
  return (
    <div style={{ padding: '64px 24px' }}>
      <EmptyState
        icon="search"
        title={t('notFound.title')}
        subtitle={t('notFound.subtitle')}
        actionText={t('notFound.action')}
        actionLink="/"
      />
    </div>
  );
};

export default NotFound;
