import React, { useState, useEffect, useCallback } from 'react';
import api from '../api';
import toast from 'react-hot-toast';
import Skeleton from './Skeleton';
import EmptyState from './EmptyState';
import PageError from './PageError';
import { useTranslation } from 'react-i18next';

const AdminIngestionSources = () => {
  const { t } = useTranslation();
  const [sources, setSources] = useState([]);
  const [status, setStatus] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [running, setRunning] = useState(false);
  const [name, setName] = useState('');
  const [url, setUrl] = useState('');

  const load = useCallback(async () => {
    setLoading(true); setError(null);
    try {
      const [s, st] = await Promise.all([
        api.get('/api/ingestion/sources'),
        api.get('/api/ingestion/status'),
      ]);
      setSources(s.data); setStatus(st.data);
    } catch (e) { setError(t('admin.sourcesLoadError')); }
    finally { setLoading(false); }
  }, [t]);

  useEffect(() => { load(); }, [load]);

  const add = async (e) => {
    e.preventDefault();
    if (!name.trim() || !url.trim()) return;
    try { await api.post('/api/ingestion/sources', { name: name.trim(), tmeUrl: url.trim() });
      setName(''); setUrl(''); load(); }
    catch { toast.error(t('admin.sourcesLoadError')); }
  };

  const toggle = async (s) => {
    try { await api.patch(`/api/ingestion/sources/${s.id}`, { enabled: !s.enabled }); load(); }
    catch { toast.error(t('admin.sourcesLoadError')); }
  };

  const run = async () => {
    setRunning(true);
    try { const r = await api.post('/api/ingestion/run');
      toast.success(`${t('admin.sourcesFound')}: ${r.data.candidates_published ?? 0}`); load(); }
    catch { toast.error(t('admin.sourcesLoadError')); }
    finally { setRunning(false); }
  };

  if (error) return <PageError message={error} onRetry={load} />;

  return (
    <div className="adm-sources">
      <form className="adm-sources__add" onSubmit={add}>
        <input value={name} onChange={e => setName(e.target.value)} placeholder={t('admin.sourcesName')} />
        <input value={url} onChange={e => setUrl(e.target.value)} placeholder={t('admin.sourcesUrl')} />
        <button type="submit">{t('admin.sourcesAdd')}</button>
        <button type="button" onClick={run} disabled={running}>
          {running ? t('admin.sourcesRunning') : t('admin.sourcesRun')}
        </button>
      </form>
      {status && status.trigger && (
        <p className="adm-sources__status">
          {t('admin.sourcesLastRun')}: {status.candidates_published ?? 0} {t('admin.sourcesFound')}
        </p>
      )}
      {loading ? <Skeleton /> : sources.length === 0 ? (
        <EmptyState icon="inbox" title={t('admin.sourcesEmpty')} />
      ) : (
        <table className="adm-sources__table">
          <thead><tr><th>{t('admin.sourcesName')}</th><th>t.me</th><th>{t('admin.sourcesEnabled')}</th></tr></thead>
          <tbody>
            {sources.map(s => (
              <tr key={s.id}>
                <td>{s.name}</td>
                <td><a href={s.tme_url} target="_blank" rel="noreferrer">{s.tme_url}</a></td>
                <td><input type="checkbox" checked={s.enabled} onChange={() => toggle(s)} /></td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
};

export default AdminIngestionSources;
