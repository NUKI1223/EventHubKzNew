import React, { useState, useEffect } from 'react';
import AdminEventRequests from './AdminEventRequests';
import AdminSupportMessages from './AdminSupportMessages';
import '../css/AdminDashboard.css';

const TABS = [
  { key: 'requests', label: 'Заявки на мероприятия' },
  { key: 'support',  label: 'Сообщения от пользователей' },
];

const AdminDashboard = () => {
  const [tab, setTab] = useState('requests');

  useEffect(() => {
    document.title = 'Панель администратора — EventHub.kz';
  }, []);

  return (
    <div className="adm">
      <div className="adm__hdr">
        <h2 className="adm__title">Панель администратора</h2>
      </div>

      <div className="adm__tabs">
        {TABS.map(t => (
          <button
            key={t.key}
            className={`adm__tab ${tab === t.key ? 'adm__tab--active' : ''}`}
            onClick={() => setTab(t.key)}
          >
            {t.label}
          </button>
        ))}
      </div>

      {tab === 'requests' && <AdminEventRequests />}
      {tab === 'support' && <AdminSupportMessages />}
    </div>
  );
};

export default AdminDashboard;
