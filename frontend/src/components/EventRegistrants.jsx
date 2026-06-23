import React, { useState, useEffect, useMemo, useCallback } from 'react';
import { Link, useParams } from 'react-router-dom';
import toast from 'react-hot-toast';
import api from '../api';
import { useAuthUser } from '../hooks/useAuthUser';
import { SkeletonCard } from './Skeleton';
import EmptyState from './EmptyState';
import PageError from './PageError';
import TagSelector from './TagSelector';
import { useTranslation } from 'react-i18next';
import '../css/EventLikers.css';

const EventRegistrants = () => {
  const { t } = useTranslation();
  const { id: eventId } = useParams();
  const authUser = useAuthUser();
  const role = authUser?.role;
  const myId = authUser?.userId;
  const [event, setEvent] = useState(null);
  const [users, setUsers] = useState([]);
  const [attendees, setAttendees] = useState([]);
  const [isManager, setIsManager] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [search, setSearch] = useState('');
  const [filterTags, setFilterTags] = useState([]);
  const [isOwner, setIsOwner] = useState(false);
  const [staff, setStaff] = useState([]);
  const [staffInput, setStaffInput] = useState('');
  const [staffBusy, setStaffBusy] = useState(false);
  const [checkinCode, setCheckinCode] = useState('');
  const [checkinBusy, setCheckinBusy] = useState(false);
  const [expanded, setExpanded] = useState(() => new Set());
  const toggleRow = (id) => setExpanded(prev => {
    const n = new Set(prev); n.has(id) ? n.delete(id) : n.add(id); return n;
  });

  const fetchAttendees = useCallback(async () => {
    try {
      const att = await api.get(`/api/events/${eventId}/attendees`);
      setAttendees(Array.isArray(att.data) ? att.data : []);
    } catch {

    }
  }, [eventId]);

  const loadStaff = useCallback(async (ev) => {
    const ids = Array.isArray(ev?.staffIds) ? ev.staffIds : [];
    if (ids.length === 0) { setStaff([]); return; }
    try {
      const res = await api.get('/api/users/batch', { params: { ids: ids.join(',') } });
      setStaff(Array.isArray(res.data) ? res.data : []);
    } catch { setStaff([]); }
  }, []);

  const addStaff = async (e) => {
    e.preventDefault();
    const name = staffInput.trim();
    if (!name) return;
    setStaffBusy(true);
    try {
      const u = await api.get(`/api/users/username/${encodeURIComponent(name)}`);
      const userId = u.data?.id;
      if (!userId) { toast.error(t('organizer.staffNotFound')); return; }
      await api.post(`/api/events/${eventId}/staff`, { userId });
      setStaffInput('');
      setStaff(prev => prev.some(s => s.id === userId) ? prev : [...prev, u.data]);
      toast.success(t('organizer.staffAdded', { username: u.data.username }));
    } catch (err) {
      toast.error(err?.response?.status === 404 ? t('organizer.staffNotFound') : t('organizer.staffAddFailed'));
    } finally {
      setStaffBusy(false);
    }
  };

  const removeStaff = async (userId) => {
    try {
      await api.delete(`/api/events/${eventId}/staff/${userId}`);
      setStaff(prev => prev.filter(s => s.id !== userId));
    } catch { toast.error(t('organizer.staffRemoveFailed')); }
  };

  useEffect(() => {
    const load = async () => {
      try {
        setLoading(true);
        const [evRes, regRes] = await Promise.all([
          api.get(`/api/events/${eventId}`),
          api.get(`/api/registrations/event/${eventId}`),
        ]);
        setEvent(evRes.data);
        document.title = `Идут на «${evRes.data.title}» — EventHub.kz`;

        // Организатор события или администратор видит таблицу с email, отметку и выгрузку.
        const staffIds = Array.isArray(evRes.data.staffIds) ? evRes.data.staffIds : [];
        const isOrganizer = myId != null && String(evRes.data.organizerId) === String(myId);
        const isStaff = myId != null && staffIds.map(String).includes(String(myId));
        const manager = role === 'ADMIN' || isOrganizer || isStaff;
        setIsManager(manager);
        setIsOwner(role === 'ADMIN' || isOrganizer);
        if (manager) {
          await fetchAttendees();
        }
        if (role === 'ADMIN' || isOrganizer) {
          await loadStaff(evRes.data);
        }

        const regs = Array.isArray(regRes.data) ? regRes.data : [];
        const userIds = [...new Set(regs.map(r => r.userId))];
        if (userIds.length === 0) { setUsers([]); return; }

        const usersRes = await api.get('/api/users/batch', { params: { ids: userIds.join(',') } });
        setUsers(Array.isArray(usersRes.data) ? usersRes.data : []);
      } catch {
        setError(t('organizer.loadListError'));
      } finally {
        setLoading(false);
      }
    };
    load();
  }, [eventId, role, myId, fetchAttendees, loadStaff]);

  const handleCheckIn = async (e) => {
    if (e) e.preventDefault();
    const code = checkinCode.trim();
    if (!code) return;
    setCheckinBusy(true);
    try {
      const res = await api.post('/api/registrations/checkin', { code });
      const uid = res.data?.userId;
      const who = attendees.find(a => a.userId === uid);
      toast.success(t('organizer.checkinSuccess', { name: who?.username || who?.email || uid }));
      setCheckinCode('');
      await fetchAttendees();
    } catch (err) {
      const status = err?.response?.status;
      const serverMsg = err?.response?.data?.message;
      let msg;
      if (status === 404) msg = t('organizer.checkinNotFound');
      else if (status === 403) msg = t('organizer.checkinForbidden');
      else msg = (serverMsg && serverMsg.trim()) || t('organizer.checkinFailed');
      toast.error(msg);
    } finally {
      setCheckinBusy(false);
    }
  };

  const statusLabel = (s) => (s === 'ATTENDED' ? t('organizer.statusAttended') : t('organizer.statusRegistered'));

  const exportExcel = async () => {
    if (!attendees.length) {
      toast.error(t('organizer.exportEmpty'));
      return;
    }
    const XLSX = await import('xlsx');
    const rows = attendees.map((a, i) => {
      const base = {
        [t('organizer.colNum')]: i + 1,
        'ID': a.userId,
        [t('organizer.colName')]: a.username || '',
        'Email': a.email || '',
        [t('organizer.colStatus')]: statusLabel(a.status),
      };
      questions.forEach(q => { base[q.label] = a.answers?.[q.id] || ''; });
      return base;
    });
    const ws = XLSX.utils.json_to_sheet(rows);
    ws['!cols'] = [{ wch: 5 }, { wch: 8 }, { wch: 24 }, { wch: 30 }, { wch: 12 }];
    const wb = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(wb, ws, 'Участники');
    const safe = (event?.title || `event-${eventId}`).replace(/[^\wа-яА-Я0-9-]+/gi, '_').slice(0, 40);
    XLSX.writeFile(wb, `participants-${safe}.xlsx`);
  };

  const questions = Array.isArray(event?.questions) ? event.questions : [];
  const labelById = useMemo(() => Object.fromEntries(questions.map(q => [q.id, q.label])), [questions]);

  const filtered = useMemo(() => {
    return users.filter(u => {
      if (search) {
        const q = search.toLowerCase();
        if (!u.username?.toLowerCase().includes(q)
            && !u.description?.toLowerCase().includes(q)) return false;
      }
      if (filterTags.length > 0) {
        const userTags = Array.isArray(u.tags) ? u.tags : [];
        if (!filterTags.every(t => userTags.includes(t))) return false;
      }
      return true;
    });
  }, [users, search, filterTags]);

  if (loading) return (
    <div className="liker-page">
      <div className="liker-page__hdr"><span className="liker-page__title">{t('common.loading')}</span></div>
      <div className="liker-page__grid">
        {[...Array(4)].map((_, i) => <SkeletonCard key={i} />)}
      </div>
    </div>
  );

  if (error) return <div className="liker-page"><PageError message={error} /></div>;

  return (
    <div className="liker-page">
      <div className="liker-page__hdr">
        <Link to={`/events/${eventId}`} className="liker-page__back">{t('organizer.backToEvent')}</Link>
        <h1 className="liker-page__title">
          {t('organizer.registrantsTitle', { title: event?.title })}
        </h1>
        <p className="liker-page__sub">{users.length === 1 ? t('organizer.participantOne', { count: users.length }) : t('organizer.participantMany', { count: users.length })}</p>
      </div>

      {isManager && (
        <div className="att-panel">
          <div className="att-panel__hdr">
            <div>
              <div className="att-panel__title">{t('organizer.attendeesTitle')}</div>
              <div className="att-panel__sub">{t('organizer.attendeesSub')}</div>
            </div>
            <button type="button" className="att-panel__export" onClick={exportExcel} disabled={!attendees.length}>
              {t('organizer.exportExcel')}
            </button>
          </div>

          <form className="att-checkin" onSubmit={handleCheckIn}>
            <input
              className="att-checkin__input"
              type="text"
              placeholder={t('organizer.checkinPlaceholder')}
              value={checkinCode}
              onChange={e => setCheckinCode(e.target.value.toUpperCase())}
              maxLength={16}
            />
            <button type="submit" className="att-checkin__btn" disabled={checkinBusy || !checkinCode.trim()}>
              {t('organizer.checkinBtn')}
            </button>
          </form>

          {isOwner && (
            <div className="att-staff">
              <div className="att-staff__title">{t('organizer.staffTitle')}</div>
              <div className="att-staff__sub">{t('organizer.staffSub')}</div>
              <form className="att-staff__add" onSubmit={addStaff}>
                <input
                  className="att-staff__input"
                  type="text"
                  placeholder={t('organizer.staffPlaceholder')}
                  value={staffInput}
                  onChange={e => setStaffInput(e.target.value)}
                />
                <button type="submit" className="att-staff__btn" disabled={staffBusy || !staffInput.trim()}>
                  {t('organizer.staffAddBtn')}
                </button>
              </form>
              {staff.length > 0 && (
                <ul className="att-staff__list">
                  {staff.map(s => (
                    <li key={s.id} className="att-staff__item">
                      <span>{s.username}</span>
                      <button type="button" className="att-staff__remove" onClick={() => removeStaff(s.id)}>×</button>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          )}

          {attendees.length === 0 ? (
            <p className="att-panel__empty">{t('organizer.attendeesEmpty')}</p>
          ) : (
            <div className="att-table-wrap">
              <table className="att-table">
                <thead>
                  <tr><th>{t('organizer.colNum')}</th><th>ID</th><th>{t('organizer.colName')}</th><th>Email</th><th>{t('organizer.colStatus')}</th><th></th></tr>
                </thead>
                <tbody>
                  {attendees.map((a, i) => (
                    <React.Fragment key={a.userId}>
                      <tr>
                        <td>{i + 1}</td>
                        <td>{a.userId}</td>
                        <td>{a.username || '—'}</td>
                        <td>{a.email || '—'}</td>
                        <td>
                          <span className={`att-status ${a.status === 'ATTENDED' ? 'att-status--in' : ''}`}>
                            {statusLabel(a.status)}
                          </span>
                        </td>
                        <td>
                          {a.answers && Object.keys(a.answers).length > 0 && (
                            <button type="button" className="att-answers__toggle" onClick={() => toggleRow(a.userId)}>
                              {expanded.has(a.userId) ? t('organizer.answersHide') : t('organizer.answersShow')}
                            </button>
                          )}
                        </td>
                      </tr>
                      {expanded.has(a.userId) && a.answers && (
                        <tr className="att-answers__row">
                          <td colSpan={6}>
                            <dl className="att-answers">
                              {Object.entries(a.answers).map(([qid, val]) => (
                                <div key={qid} className="att-answers__item">
                                  <dt>{labelById[qid] || qid}</dt>
                                  <dd>{val}</dd>
                                </div>
                              ))}
                            </dl>
                          </td>
                        </tr>
                      )}
                    </React.Fragment>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      <div className="liker-page__filters">
        <input
          className="liker-page__search"
          type="text"
          placeholder={t('organizer.searchPlaceholder')}
          value={search}
          onChange={e => setSearch(e.target.value)}
        />
        <div className="liker-page__tag-filter">
          <TagSelector selectedTags={filterTags} onChange={setFilterTags} type="USER" />
        </div>
      </div>

      {filtered.length === 0 ? (
        <EmptyState
          icon="search"
          title={t('organizer.registrantsEmptyTitle')}
          subtitle={t('organizer.registrantsEmptySubtitle')}
        />
      ) : (
        <div className="liker-page__grid">
          {filtered.map(u => (
            <Link to={`/profile/${u.username}`} key={u.id} className="liker-card">
              {u.avatarUrl ? (
                <img src={u.avatarUrl} alt={u.username} className="liker-card__avatar" />
              ) : (
                <div className="liker-card__avatar-ph">{u.username?.[0]?.toUpperCase() || '?'}</div>
              )}
              <div className="liker-card__body">
                <div className="liker-card__name">{u.username || t('organizer.noName')}</div>
                {u.description && (
                  <div className="liker-card__desc">{u.description}</div>
                )}
                {Array.isArray(u.tags) && u.tags.length > 0 && (
                  <div className="liker-card__tags">
                    {u.tags.slice(0, 4).map(t => (
                      <span key={t} className="liker-card__tag">{t}</span>
                    ))}
                    {u.tags.length > 4 && (
                      <span className="liker-card__tag liker-card__tag--more">+{u.tags.length - 4}</span>
                    )}
                  </div>
                )}
              </div>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
};

export default EventRegistrants;
