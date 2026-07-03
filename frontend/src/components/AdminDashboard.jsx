import React, { useState, useEffect } from 'react';
import AdminEventRequests from './AdminEventRequests';
import AdminSupportMessages from './AdminSupportMessages';
import AdminUsers from './AdminUsers';
import AdminAuditLog from './AdminAuditLog';
import { useTranslation } from 'react-i18next';
import '../css/AdminDashboard.css';

const TABS = [
  { key: 'requests', labelKey: 'admin.tabRequests' },
  { key: 'support',  labelKey: 'admin.tabSupport' },
  { key: 'users',    labelKey: 'admin.tabUsers' },
  { key: 'audit',    labelKey: 'admin.tabAudit' },
];

const AdminDashboard = () => {
  const { t } = useTranslation();
  const [tab, setTab] = useState('requests');

  useEffect(() => {
    document.title = t('admin.pageTitle');
  }, [t]);

  return (
    <div className="adm">
      <div className="adm__hdr">
        <h2 className="adm__title">{t('admin.heading')}</h2>
      </div>

      <div className="adm__tabs">
        {TABS.map(item => (
          <button
            key={item.key}
            className={`adm__tab ${tab === item.key ? 'adm__tab--active' : ''}`}
            onClick={() => setTab(item.key)}
          >
            {t(item.labelKey)}
          </button>
        ))}
      </div>

      {tab === 'requests' && <AdminEventRequests />}
      {tab === 'support' && <AdminSupportMessages />}
      {tab === 'users' && <AdminUsers />}
      {tab === 'audit' && <AdminAuditLog />}
    </div>
  );
};

export default AdminDashboard;
