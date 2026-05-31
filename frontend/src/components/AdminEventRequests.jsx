import React, { useState, useEffect } from 'react';
import api from '../api';
import toast from 'react-hot-toast';
import { formatDate } from '../utils/dateUtils';
import Skeleton from './Skeleton';
import EmptyState from './EmptyState';
import PageError from './PageError';

const statusLabel = { APPROVED: 'Одобрено', REJECTED: 'Отклонено', PENDING: 'На рассмотрении' };
const statusMod = { APPROVED: 'approved', REJECTED: 'rejected', PENDING: 'pending' };

const AdminEventRequests = () => {
  const [eventRequests, setEventRequests] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [showModal, setShowModal] = useState(false);
  const [currentAction, setCurrentAction] = useState({ id: null, status: '', adminComment: '' });
  const [reindexing, setReindexing] = useState(false);
  const [submittingId, setSubmittingId] = useState(null);
  const [expandedIds, setExpandedIds] = useState(() => new Set());

  const toggleExpanded = (id) => {
    setExpandedIds(prev => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
  };

  useEffect(() => {
    const load = async () => {
      try {
        const res = await api.get('/api/admin/all');
        setEventRequests(res.data);
      } catch {
        setError('Ошибка при загрузке заявок');
      } finally {
        setLoading(false);
      }
    };
    load();
  }, []);

  const handleReindex = async () => {
    setReindexing(true);
    try {
      await api.post('/api/events/reindex');
      toast.success('Переиндексация запущена');
    } catch {
      toast.error('Ошибка при переиндексации');
    } finally {
      setReindexing(false);
    }
  };

  const updateRequest = async () => {
    const { id, status, adminComment } = currentAction;
    if (submittingId != null) return;
    setSubmittingId(id);
    try {
      await api.put(`/api/admin/${id}/update`, { status, adminComment });
      setEventRequests(prev => prev.map(req => req.id === id ? { ...req, status, adminComment } : req));
      setShowModal(false);
      toast.success(status === 'APPROVED' ? 'Заявка одобрена' : 'Заявка отклонена');
    } catch (err) {
      const msg = err?.response?.data?.error
        || (err?.response?.status === 409 ? 'Заявка уже обработана' : 'Ошибка при обновлении заявки');
      toast.error(msg);
    } finally {
      setSubmittingId(null);
    }
  };

  const openModal = (id, status, adminComment) => {
    setCurrentAction({ id, status, adminComment });
    setShowModal(true);
  };

  const closeModal = () => {
    setShowModal(false);
    setCurrentAction({ id: null, status: '', adminComment: '' });
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
      <div className="adm__toolbar">
        <span className="adm__count">{eventRequests.length}</span>
        <button
          className="adm__approve"
          onClick={handleReindex}
          disabled={reindexing}
          style={{ fontSize: 12, padding: '6px 14px' }}
        >
          {reindexing ? 'Индексация...' : 'Переиндексировать поиск'}
        </button>
      </div>

      {eventRequests.length > 0 ? (
        <div className="adm__list">
          {eventRequests.map(request => (
            <div key={request.id} className="adm__card">
              <div className="adm__card-hdr">
                <div className="adm__card-title">{request.title}</div>
                <span className={`adm__status adm__status--${statusMod[request.status] || 'pending'}`}>
                  {statusLabel[request.status] || request.status}
                </span>
              </div>

              <div className="adm__card-body">
                {request.shortDescription && (
                  <p className="adm__desc-short">{request.shortDescription}</p>
                )}

                {request.fullDescription && (
                  <div className="adm__desc-full-wrap">
                    {expandedIds.has(request.id) && (
                      <p className="adm__desc-full">{request.fullDescription}</p>
                    )}
                    <button
                      type="button"
                      className="adm__expand-btn"
                      onClick={() => toggleExpanded(request.id)}
                    >
                      {expandedIds.has(request.id) ? 'Скрыть' : 'Открыть полностью'}
                    </button>
                  </div>
                )}

                <div className="adm__meta-grid">
                  <div className="adm__meta-row">
                    <span className="adm__meta-label">Дата события</span>
                    <span className="adm__meta-value">{formatDate(request.eventDate)}</span>
                  </div>
                  <div className="adm__meta-row">
                    <span className="adm__meta-label">Дедлайн регистрации</span>
                    <span className="adm__meta-value">{formatDate(request.registrationDeadline)}</span>
                  </div>
                  <div className="adm__meta-row">
                    <span className="adm__meta-label">Место</span>
                    <span className="adm__meta-value">{request.location || '—'}</span>
                  </div>
                  <div className="adm__meta-row">
                    <span className="adm__meta-label">Формат</span>
                    <span className="adm__meta-value">
                      <span className={`adm__type adm__type--${request.online ? 'online' : 'offline'}`}>
                        {request.online ? 'Онлайн' : 'Офлайн'}
                      </span>
                    </span>
                  </div>
                  <div className="adm__meta-row">
                    <span className="adm__meta-label">Email заявителя</span>
                    <span className="adm__meta-value">{request.requesterEmail}</span>
                  </div>
                  {request.externalLink && (
                    <div className="adm__meta-row">
                      <span className="adm__meta-label">Ссылка</span>
                      <span className="adm__meta-value">
                        <a href={request.externalLink} target="_blank" rel="noopener noreferrer">
                          Открыть →
                        </a>
                      </span>
                    </div>
                  )}
                </div>

                {request.tags?.length > 0 && (
                  <div className="adm__tags">
                    {request.tags.map((tag, i) => (
                      <span key={i} className="adm__tag">{tag}</span>
                    ))}
                  </div>
                )}

                {request.mainImageUrl && (
                  <img className="adm__img" src={request.mainImageUrl} alt="Обложка" />
                )}

                <textarea
                  className="adm__comment"
                  placeholder="Комментарий администратора..."
                  value={request.adminComment || ''}
                  onChange={e => {
                    const newComment = e.target.value;
                    setEventRequests(prev =>
                      prev.map(req => req.id === request.id ? { ...req, adminComment: newComment } : req)
                    );
                  }}
                />
              </div>

              <div className="adm__actions">
                {request.status === 'PENDING' ? (
                  <>
                    <button
                      className="adm__approve"
                      disabled={submittingId === request.id}
                      onClick={() => openModal(request.id, 'APPROVED', request.adminComment)}
                    >
                      {submittingId === request.id ? 'Сохраняем…' : 'Одобрить'}
                    </button>
                    <button
                      className="adm__reject"
                      disabled={submittingId === request.id}
                      onClick={() => openModal(request.id, 'REJECTED', request.adminComment)}
                    >
                      {submittingId === request.id ? 'Сохраняем…' : 'Отклонить'}
                    </button>
                  </>
                ) : (
                  <span className="adm__final-state">
                    {request.status === 'APPROVED'
                      ? 'Заявка одобрена — изменить нельзя'
                      : 'Заявка отклонена — изменить нельзя'}
                  </span>
                )}
              </div>
            </div>
          ))}
        </div>
      ) : (
        <EmptyState
          icon="inbox"
          title="Нет заявок"
          subtitle="Новые заявки на мероприятия появятся здесь"
        />
      )}

      {showModal && (
        <div className="adm__modal">
          <div className="adm__modal-inner">
            <p className="adm__modal-msg">
              Вы уверены, что хотите{' '}
              <strong>{currentAction.status === 'APPROVED' ? 'одобрить' : 'отклонить'}</strong> эту заявку?
            </p>
            <div className="adm__modal-btns">
              <button className="adm__cancel" onClick={closeModal} disabled={submittingId != null}>
                Отмена
              </button>
              <button
                className="adm__confirm"
                onClick={updateRequest}
                disabled={submittingId != null}
              >
                {submittingId != null ? 'Сохраняем…' : 'Подтвердить'}
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
};

export default AdminEventRequests;
