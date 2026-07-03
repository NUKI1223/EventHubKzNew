import React, { useState, useEffect, useCallback } from 'react';
import api from '../api';
import { formatDate } from '../utils/dateUtils';
import Skeleton from './Skeleton';
import EmptyState from './EmptyState';
import PageError from './PageError';
import Pagination from './Pagination';
import { useTranslation } from 'react-i18next';

const ACTIONS = [
  'USER_REGISTERED', 'USER_DELETED',
  'EVENT_CREATED', 'EVENT_UPDATED', 'EVENT_DELETED',
  'EVENT_LIKED', 'EVENT_RSVP',
  'REQUEST_CREATED', 'REQUEST_APPROVED', 'REQUEST_REJECTED',
];

const AdminAuditLog = () => {
  const { t } = useTranslation();
  const [rows, setRows] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [action, setAction] = useState('');
  const [actorId, setActorId] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');

  const load = useCallback(async (p = 0) => {
    setLoading(true);
    setError(null);
    try {
      const params = new URLSearchParams({ page: p, size: 20 });
      if (action) params.set('action', action);
      if (actorId) params.set('actorId', actorId);
      if (from) params.set('from', `${from}T00:00:00`);
      if (to) params.set('to', `${to}T23:59:59`);
      const res = await api.get(`/api/admin/audit?${params}`);
      setRows(res.data.content);
      setTotalPages(res.data.totalPages);
      setTotal(res.data.totalElements);
      setPage(p);
    } catch (e) {
      setError(t('admin.auditLoadError'));
    } finally {
      setLoading(false);
    }
  }, [action, actorId, from, to, t]);

  useEffect(() => { load(0); }, []); // первичная загрузка

  const resetFilters = () => { setAction(''); setActorId(''); setFrom(''); setTo(''); };

  if (error) return <PageError message={error} onRetry={() => load(page)} />;

  return (
    <div className="adm-audit">
      <div className="adm-audit__filters">
        <select value={action} onChange={e => setAction(e.target.value)} aria-label={t('admin.auditColAction')}>
          <option value="">{t('admin.auditFilterAction')}</option>
          {ACTIONS.map(a => <option key={a} value={a}>{t(`admin.auditAction_${a}`)}</option>)}
        </select>
        <input type="number" value={actorId} onChange={e => setActorId(e.target.value)}
               placeholder={t('admin.auditFilterActor')} />
        <input type="date" value={from} onChange={e => setFrom(e.target.value)}
               aria-label={t('admin.auditFilterFrom')} />
        <input type="date" value={to} onChange={e => setTo(e.target.value)}
               aria-label={t('admin.auditFilterTo')} />
        <button onClick={() => load(0)}>{t('admin.auditApply')}</button>
        <button onClick={resetFilters}>{t('admin.auditReset')}</button>
      </div>

      {loading ? <Skeleton /> : rows.length === 0 ? (
        <EmptyState icon="inbox" title={t('admin.auditEmpty')} />
      ) : (
        <>
          <table className="adm-audit__table">
            <thead>
              <tr>
                <th>{t('admin.auditColTime')}</th>
                <th>{t('admin.auditColActor')}</th>
                <th>{t('admin.auditColAction')}</th>
                <th>{t('admin.auditColTarget')}</th>
                <th>{t('admin.auditColDetails')}</th>
              </tr>
            </thead>
            <tbody>
              {rows.map(r => (
                <tr key={r.id}>
                  <td>{formatDate(r.occurredAt)}</td>
                  <td>{r.actorName || (r.actorId ? `#${r.actorId}` : '—')}</td>
                  <td><span className={`adm-audit__chip adm-audit__chip--${r.action.toLowerCase()}`}>
                    {t(`admin.auditAction_${r.action}`)}
                  </span></td>
                  <td>{r.targetLabel || '—'}</td>
                  <td>{r.details || '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <Pagination page={page} totalPages={totalPages} onChange={load} total={total} />
        </>
      )}
    </div>
  );
};

export default AdminAuditLog;
