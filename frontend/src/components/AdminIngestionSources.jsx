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
    catch (err) { toast.error(err?.response?.data?.detail || t('admin.sourcesInvalidUrl')); }
  };

  const toggle = async (s) => {
    try { await api.patch(`/api/ingestion/sources/${s.id}`, { enabled: !s.enabled }); load(); }
    catch { toast.error(t('admin.sourcesLoadError')); }
  };

  const run = async () => {
    setRunning(true);
    const prevId = status?.id;
    try {
      await api.post('/api/ingestion/run');
      toast.success(t('admin.sourcesRunStarted'));
      // The sweep runs in the background (throttled Gemini calls take minutes).
      // Poll the last-run status until a new, finished run appears.
      for (let i = 0; i < 90; i++) {
        await new Promise(r => setTimeout(r, 4000));
        const st = await api.get('/api/ingestion/status');
        setStatus(st.data);
        if (st.data.id && st.data.id !== prevId && st.data.finished_at) break;
      }
      load();
    } catch { toast.error(t('admin.sourcesLoadError')); }
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
        <div className="adm-sources__status">
          <div className="adm-sources__status-line">
            <strong>{t('admin.sourcesLastRun')}:</strong>{' '}
            {status.finished_at
              ? t('admin.sourcesRunStats', {
                  fetched: status.posts_fetched ?? 0,
                  filtered: status.passed_prefilter ?? 0,
                  extracted: status.extracted ?? 0,
                  published: status.candidates_published ?? 0,
                })
              : t('admin.sourcesRunning')}
          </div>
          {status.finished_at && (
            <ul className="adm-sources__reasons">
              {status.gemini_rate_limited > 0 && (
                <li className="adm-sources__reason adm-sources__reason--warn">
                  {t('admin.sourcesReasonRateLimited', { n: status.gemini_rate_limited })}
                </li>
              )}
              {status.gemini_errors > 0 && (
                <li className="adm-sources__reason">{t('admin.sourcesReasonAiError', { n: status.gemini_errors })}</li>
              )}
              {status.dropped_past > 0 && (
                <li className="adm-sources__reason">{t('admin.sourcesReasonPast', { n: status.dropped_past })}</li>
              )}
              {status.dropped_invalid > 0 && (
                <li className="adm-sources__reason">{t('admin.sourcesReasonInvalid', { n: status.dropped_invalid })}</li>
              )}
              {status.error && (
                <li className="adm-sources__reason adm-sources__reason--warn">
                  {t('admin.sourcesReasonSource', { err: status.error })}
                </li>
              )}
              {status.candidates_published > 0 && (
                <li className="adm-sources__reason adm-sources__reason--ok">
                  {t('admin.sourcesReasonPublished', { n: status.candidates_published })}
                </li>
              )}
            </ul>
          )}
        </div>
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
