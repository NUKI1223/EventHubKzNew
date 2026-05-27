import React, { useRef, useEffect } from 'react';
import { formatDate } from '../utils/dateUtils';
import '../css/NotificationsDropdown.css';
import api from '../api';

const NotificationsDropdown = ({ notifications, onClose, onUpdate }) => {
  const dropdownRef = useRef(null);

  useEffect(() => {
    const handleClickOutside = (e) => {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target)) {
        onClose();
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, [onClose]);

  const markAsRead = async (id) => {
    try {
      await api.post(`/api/notifications/${id}/read`);
      onUpdate();
    } catch (err) {
      console.error('Ошибка при обновлении статуса уведомления:', err);
    }
  };

  const deleteOne = async (id) => {
    try {
      await api.delete(`/api/notifications/${id}`);
      onUpdate();
    } catch (err) {
      console.error('Ошибка удаления уведомления:', err);
    }
  };

  const deleteAll = async () => {
    try {
      await api.delete('/api/notifications/all');
      onUpdate();
    } catch (err) {
      console.error('Ошибка удаления всех уведомлений:', err);
    }
  };

  const markAllAsRead = async () => {
    try {
      await api.post('/api/notifications/read-all');
      onUpdate();
    } catch (err) {
      console.error('Ошибка при отметке всех как прочитанных:', err);
    }
  };

  const hasUnread = notifications.some(n => !n.read);

  return (
    <div className="notif-dd" ref={dropdownRef}>
      <div className="notif-dd__hdr">
        <span className="notif-dd__title">Уведомления</span>
        {notifications.length > 0 && (
          <div className="notif-dd__hdr-actions">
            {hasUnread && (
              <button className="notif-dd__read-all" onClick={markAllAsRead}>
                Прочитать всё
              </button>
            )}
            <button className="notif-dd__clear" onClick={deleteAll}>
              Очистить
            </button>
          </div>
        )}
      </div>

      <div className="notif-dd__list">
        {notifications.length === 0 ? (
          <div className="notif-dd__empty">Нет новых уведомлений</div>
        ) : (
          notifications.map((n) => (
            <div
              key={n.id}
              className={`notif-dd__item ${n.read ? 'notif-dd__item--read' : 'notif-dd__item--unread'}`}
            >
              <div className="notif-dd__dot" />

              <div className="notif-dd__content">
                <div className="notif-dd__msg">{n.message}</div>
                {n.link && (
                  <a
                    href={n.link}
                    className="notif-dd__link"
                    onClick={() => markAsRead(n.id)}
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    Подробнее →
                  </a>
                )}
                <div className="notif-dd__meta">
                  <span>{formatDate(n.createdAt)}</span>
                  <span className={`notif-dd__status notif-dd__status--${n.read ? 'read' : 'unread'}`}>
                    {n.read ? 'Прочитано' : 'Новое'}
                  </span>
                </div>
              </div>

              <button className="notif-dd__delete" onClick={() => deleteOne(n.id)} title="Удалить">
                ×
              </button>
            </div>
          ))
        )}
      </div>
    </div>
  );
};

export default NotificationsDropdown;
