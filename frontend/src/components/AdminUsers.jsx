import React, { useState, useEffect, useCallback } from 'react';
import api from '../api';
import toast from 'react-hot-toast';
import Skeleton from './Skeleton';
import EmptyState from './EmptyState';
import PageError from './PageError';
import { useTranslation } from 'react-i18next';
import { useAuthUser } from '../hooks/useAuthUser';

const AdminUsers = () => {
  const { t } = useTranslation();
  const [users, setUsers] = useState([]);
  const [q, setQ] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [target, setTarget] = useState(null);   // пользователь в модалке
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const authUser = useAuthUser();
  const currentUserId = authUser?.userId;

  const load = useCallback(async (query = '') => {
    setLoading(true);
    setError(null);
    try {
      const res = await api.get(`/api/users/admin/list${query ? `?q=${encodeURIComponent(query)}` : ''}`);
      setUsers(res.data);
    } catch (e) {
      setError(t('admin.usersLoadError'));
    } finally {
      setLoading(false);
    }
  }, [t]);

  useEffect(() => { load(); }, [load]);

  const confirmDelete = async () => {
    if (!target || busy) return;
    setBusy(true);
    try {
      const params = reason.trim() ? `?reason=${encodeURIComponent(reason.trim())}` : '';
      await api.delete(`/api/users/${target.id}${params}`);
      toast.success(t('admin.usersDeleteSuccess'));
      setTarget(null);
      setReason('');
      load(q);
    } catch (e) {
      toast.error(t('admin.usersDeleteError'));
    } finally {
      setBusy(false);
    }
  };

  if (error) return <PageError message={error} onRetry={() => load(q)} />;

  return (
    <div className="adm-users">
      <form className="adm-users__search" onSubmit={e => { e.preventDefault(); load(q); }}>
        <input value={q} onChange={e => setQ(e.target.value)}
               placeholder={t('admin.usersSearch')} />
      </form>

      {loading ? <Skeleton /> : users.length === 0 ? (
        <EmptyState icon="search" title={t('admin.usersEmpty')} />
      ) : (
        <table className="adm-users__table">
          <thead>
            <tr>
              <th>{t('admin.usersColId')}</th>
              <th>{t('admin.usersColName')}</th>
              <th>{t('admin.usersColEmail')}</th>
              <th>{t('admin.usersColRole')}</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {users.map(u => (
              <tr key={u.id}>
                <td>{u.id}</td>
                <td>{u.username}</td>
                <td>{u.email}</td>
                <td>{u.role}</td>
                <td>
                  {u.role !== 'ADMIN' && u.id !== currentUserId && (
                    <button className="adm-users__delete" onClick={() => setTarget(u)}>
                      {t('admin.usersDelete')}
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {target && (
        <div className="adm-users__modal-backdrop" onClick={() => !busy && setTarget(null)}>
          <div className="adm-users__modal" onClick={e => e.stopPropagation()}>
            <h3>{t('admin.usersDeleteTitle', { name: target.username })}</h3>
            <p>{t('admin.usersDeleteWarning')}</p>
            <input value={reason} onChange={e => setReason(e.target.value)}
                   placeholder={t('admin.usersDeleteReason')} />
            <div className="adm-users__modal-actions">
              <button onClick={() => setTarget(null)} disabled={busy}>
                {t('admin.usersDeleteCancel')}
              </button>
              <button className="adm-users__delete" onClick={confirmDelete} disabled={busy}>
                {t('admin.usersDeleteConfirm')}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default AdminUsers;
