import React, { useState, useEffect, useCallback } from 'react';
import api from '../api';
import Skeleton from './Skeleton';
import EmptyState from './EmptyState';
import { useTranslation } from 'react-i18next';

const STAGES = ['', 'PUBLISHED', 'DROPPED_PAST', 'DROPPED_INVALID', 'NOT_EVENT',
  'RATE_LIMITED', 'AI_ERROR', 'PREFILTER_REJECTED'];

const AdminIngestionItems = () => {
  const { t } = useTranslation();
  const [items, setItems] = useState([]);
  const [stage, setStage] = useState('');
  const [loading, setLoading] = useState(true);
  const [expanded, setExpanded] = useState(() => new Set());

  const load = useCallback(async (st = stage) => {
    setLoading(true);
    try {
      const res = await api.get(`/api/ingestion/items${st ? `?stage=${st}` : ''}`);
      setItems(res.data);
    } catch { setItems([]); }
    finally { setLoading(false); }
  }, [stage]);

  useEffect(() => { load(''); }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const onStage = (v) => { setStage(v); load(v); };
  const toggle = (ref) => setExpanded(p => {
    const n = new Set(p); n.has(ref) ? n.delete(ref) : n.add(ref); return n;
  });

  return (
    <div className="adm-items">
      <div className="adm-items__hdr">
        <h4 className="adm-items__title-h">{t('admin.itemsTitle')}</h4>
        <div className="adm-items__controls">
          <select value={stage} onChange={e => onStage(e.target.value)}>
            {STAGES.map(s => (
              <option key={s} value={s}>{s ? t(`admin.stage_${s}`) : t('admin.itemsAll')}</option>
            ))}
          </select>
          <button type="button" onClick={() => load()} title={t('admin.itemsRefresh')}>↻</button>
        </div>
      </div>

      {loading ? <Skeleton /> : items.length === 0 ? (
        <EmptyState icon="inbox" title={t('admin.itemsEmpty')} />
      ) : (
        <ul className="adm-items__list">
          {items.map(it => {
            const key = `${it.channel}/${it.post_ref}`;
            const open = expanded.has(key);
            return (
              <li key={key} className="adm-items__row">
                <button type="button" className="adm-items__row-hdr" onClick={() => toggle(key)}>
                  <span className={`adm-items__badge adm-items__badge--${it.stage.toLowerCase()}`}>
                    {t(`admin.stage_${it.stage}`)}
                  </span>
                  <span className="adm-items__row-title">{it.title || t('admin.itemsNoTitle')}</span>
                  <span className="adm-items__row-meta">
                    {it.channel}{it.event_date ? ` · ${it.event_date.slice(0, 10)}` : ''}{it.city ? ` · ${it.city}` : ''}
                  </span>
                </button>
                {open && (
                  <div className="adm-items__detail">
                    <div className="adm-items__extracted">
                      <strong>{t('admin.itemsExtracted')}:</strong>{' '}
                      {t('admin.itemsFieldTitle')}: {it.title || '—'} · {t('admin.itemsFieldDate')}: {it.event_date || '—'} · {t('admin.itemsFieldCity')}: {it.city || '—'} · {t('admin.itemsFieldLoc')}: {it.location || '—'}
                    </div>
                    {it.snippet && (
                      <div className="adm-items__snippet">
                        <strong>{t('admin.itemsSource')}:</strong> {it.snippet}
                      </div>
                    )}
                    {it.post_url && (
                      <a href={it.post_url} target="_blank" rel="noreferrer">{t('admin.reqSourceLink')}</a>
                    )}
                  </div>
                )}
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
};

export default AdminIngestionItems;
