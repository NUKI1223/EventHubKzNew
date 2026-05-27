import React, { useState, useEffect } from 'react';
import api from '../api';
import toast from 'react-hot-toast';
import { formatDate } from '../utils/dateUtils';
import Skeleton from './Skeleton';
import EmptyState from './EmptyState';
import PageError from './PageError';

const AdminSupportMessages = () => {
  const [messages, setMessages] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [filter, setFilter] = useState('open');
  const [replyDraft, setReplyDraft] = useState({});

  useEffect(() => {
    const load = async () => {
      try {
        setLoading(true);
        const params = filter === 'all' ? {} : { resolved: filter === 'resolved' };
        const res = await api.get('/api/support', { params });
        setMessages(Array.isArray(res.data) ? res.data : []);
      } catch {
        setError('Не удалось загрузить сообщения');
      } finally {
        setLoading(false);
      }
    };
    load();
  }, [filter]);

  const resolve = async (id) => {
    try {
      await api.put(`/api/support/${id}/resolve`, { adminReply: replyDraft[id] || '' });
      toast.success('Отмечено как решённое');
      setMessages(prev => prev.map(m => m.id === id
        ? { ...m, resolved: true, adminReply: replyDraft[id] || '', resolvedAt: new Date().toISOString() }
        : m));
    } catch {
      toast.error('Не удалось обновить статус');
    }
  };

  const remove = async (id) => {
    if (!window.confirm('Удалить сообщение?')) return;
    try {
      await api.delete(`/api/support/${id}`);
      setMessages(prev => prev.filter(m => m.id !== id));
      toast.success('Сообщение удалено');
    } catch {
      toast.error('Не удалось удалить');
    }
  };

  if (loading) return (
    <div className="adm__list">
      {[...Array(3)].map((_, i) => (
        <div key={i} style={{ background: 'var(--bg-card)', borderRadius: 'var(--r-lg)', padding: 24, marginBottom: 16, border: '1px solid var(--line)' }}>
          <Skeleton variant="title" />
          <Skeleton variant="text" />
          <Skeleton variant="text" />
        </div>
      ))}
    </div>
  );
  if (error) return <PageError message={error} />;

  return (
    <>
      <div className="adm__filter-bar">
        {[
          { key: 'open', label: 'Активные' },
          { key: 'resolved', label: 'Решённые' },
          { key: 'all', label: 'Все' },
        ].map(b => (
          <button
            key={b.key}
            className={`adm__filter-pill ${filter === b.key ? 'adm__filter-pill--active' : ''}`}
            onClick={() => setFilter(b.key)}
          >
            {b.label}
          </button>
        ))}
      </div>

      {messages.length === 0 ? (
        <EmptyState
          icon="inbox"
          title="Сообщений нет"
          subtitle="Когда пользователи напишут в поддержку, сообщения появятся здесь"
        />
      ) : (
        <div className="adm__list">
          {messages.map(m => (
            <div key={m.id} className="adm__card">
              <div className="adm__card-hdr">
                <div className="adm__card-title">{m.name} — {m.email}</div>
                <span className={`adm__status adm__status--${m.resolved ? 'approved' : 'pending'}`}>
                  {m.resolved ? 'Решено' : 'Открыто'}
                </span>
              </div>

              <div className="adm__card-body">
                <p className="adm__desc-short" style={{ whiteSpace: 'pre-wrap' }}>{m.message}</p>

                <div className="adm__meta-grid">
                  <div className="adm__meta-row">
                    <span className="adm__meta-label">Получено</span>
                    <span className="adm__meta-value">{formatDate(m.createdAt)}</span>
                  </div>
                  {m.resolvedAt && (
                    <div className="adm__meta-row">
                      <span className="adm__meta-label">Закрыто</span>
                      <span className="adm__meta-value">{formatDate(m.resolvedAt)}</span>
                    </div>
                  )}
                </div>

                {m.resolved ? (
                  m.adminReply && (
                    <div style={{ marginTop: 12, padding: 12, background: 'var(--bg, #fffbf2)', borderRadius: 8, border: '1px solid var(--ink-200, #e6e1d6)' }}>
                      <div style={{ fontSize: 12, color: 'var(--ink-500)', marginBottom: 6 }}>Ответ администратора</div>
                      <div style={{ whiteSpace: 'pre-wrap', fontSize: 14 }}>{m.adminReply}</div>
                    </div>
                  )
                ) : (
                  <textarea
                    className="adm__comment"
                    placeholder="Ответ администратора (необязательно)..."
                    value={replyDraft[m.id] || ''}
                    onChange={e => setReplyDraft(prev => ({ ...prev, [m.id]: e.target.value }))}
                  />
                )}
              </div>

              <div className="adm__actions">
                {!m.resolved && (
                  <button className="adm__approve" onClick={() => resolve(m.id)}>
                    Отметить как решённое
                  </button>
                )}
                <button className="adm__reject" onClick={() => remove(m.id)}>
                  Удалить
                </button>
              </div>
            </div>
          ))}
        </div>
      )}
    </>
  );
};

export default AdminSupportMessages;
