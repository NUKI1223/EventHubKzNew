import React, { useState, useEffect } from 'react';
import { useParams, useNavigate, useLocation, Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import api from '../api';
import { useAuthUser } from '../hooks/useAuthUser';
import { useProfileData } from '../hooks/useProfileData';
import { useAvatarUpload } from '../hooks/useAvatarUpload';
import { SOCIALS } from '../config/socials';
import '../css/Profile.css';
import LikedEvents from './LikedEvents';
import OrganizerDashboard from './OrganizerDashboard';
import { SkeletonProfile } from './Skeleton';
import PageError from './PageError';

const UserProfile = () => {
  const { username: routeUsername } = useParams();
  const navigate = useNavigate();
  const location = useLocation();
  const currentUser = useAuthUser();
  const { t } = useTranslation();

  const { user, setUser, loading, error } = useProfileData(routeUsername, location.pathname);
  const { setAvatarFile, avatarLoading, avatarError } = useAvatarUpload(user, setUser);

  const [staffedEvents, setStaffedEvents] = useState([]);
  useEffect(() => {
    if (!user?.id) { setStaffedEvents([]); return; }
    api.get(`/api/events/staffed-by/${user.id}`)
      .then(res => setStaffedEvents(Array.isArray(res.data) ? res.data : []))
      .catch(() => setStaffedEvents([]));
  }, [user?.id]);

  const isOwnProfile =
    currentUser?.sub &&
    user?.username &&
    currentUser.sub.toLowerCase() === user.username.toLowerCase();

  const getUsernameFromLink = (link, prefix) => {
    if (!link) return '';
    return link.startsWith(prefix) ? link.replace(prefix, '') : link;
  };

  const handleAvatarChange = (e) => {
    const file = e.target.files[0];
    if (file) setAvatarFile(file);
  };

  if (loading) return <SkeletonProfile />;
  if (error) return <div className="pf"><PageError message={error} /></div>;
  if (!user) return <div className="pf"><PageError message={t('profile.userNotFound')} /></div>;

  const initials = user.username?.[0]?.toUpperCase() || '?';

  const socials = SOCIALS
    .map(s => ({ ...s, href: user.contacts?.[s.contactKey] }))
    .filter(s => s.href);

  return (
    <div className="pf">
      <div className="pf__container">
        {/* Sidebar */}
        <div className="pf__sidebar">
          <div className="pf__avatar-wrap">
            {user.avatarUrl ? (
              <img src={user.avatarUrl} alt={user.username} className="pf__avatar" />
            ) : (
              <div className="pf__avatar-ph">{initials}</div>
            )}
            {isOwnProfile && (
              <>
                <div className="pf__avatar-overlay">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="22" height="22">
                    <path d="M23 19a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4l2-3h6l2 3h4a2 2 0 0 1 2 2z"/>
                    <circle cx="12" cy="13" r="4"/>
                  </svg>
                </div>
                <input
                  type="file"
                  accept="image/*"
                  onChange={handleAvatarChange}
                  className="pf__avatar-input"
                  disabled={avatarLoading}
                />
              </>
            )}
          </div>

          {avatarError && <div className="pf__error">{avatarError}</div>}

          <h2 className="pf__name">{user.username || t('profile.nameNotSet')}</h2>

          {user.description && (
            <p className="pf__bio">{user.description}</p>
          )}

          {Array.isArray(user.tags) && user.tags.length > 0 && (
            <div className="pf__tags">
              {user.tags.map(t => (
                <span key={t} className="pf__tag">{t}</span>
              ))}
            </div>
          )}

          {socials.length > 0 && (
            <div className="pf__socials">
              {socials.map(s => (
                <a
                  key={s.contactKey}
                  href={s.href}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="pf__social-item"
                >
                  <img src={s.icon} alt={s.label} className="pf__social-icon" />
                  {getUsernameFromLink(s.href, s.prefix)}
                </a>
              ))}
            </div>
          )}

          {staffedEvents.length > 0 && (
            <div className="pf__staffed">
              <div className="pf__staffed-title">{t('profile.staffedTitle')}</div>
              <ul className="pf__staffed-list">
                {staffedEvents.map(ev => (
                  <li key={ev.id}>
                    <Link to={`/events/${ev.id}`} className="pf__staffed-link">{ev.title}</Link>
                  </li>
                ))}
              </ul>
            </div>
          )}

          {isOwnProfile && (
            <button className="pf__edit-btn" onClick={() => navigate('/edit-profile')}>
              {t('profile.editBtn')}
            </button>
          )}
        </div>

        {/* Main info */}
        <div className="pf__main">
          <div className="pf__card">
            <div className="pf__card-title">{t('profile.infoTitle')}</div>

            <div className="pf__row">
              <span className="pf__row-label">{t('profile.fieldUsername')}</span>
              <span className="pf__row-value">{user.username || t('profile.notSet')}</span>
            </div>

            {isOwnProfile && (
              <div className="pf__row">
                <span className="pf__row-label">{t('profile.fieldEmail')}</span>
                <span className="pf__row-value">{currentUser?.email || t('profile.notSet')}</span>
              </div>
            )}

            <div className="pf__row">
              <span className="pf__row-label">{t('profile.fieldBio')}</span>
              <span className="pf__row-value">{user.description || t('profile.notSet')}</span>
            </div>
          </div>
        </div>
      </div>

      {isOwnProfile && <OrganizerDashboard />}

      {/* Чужой профиль: показываем только лайкнутые (публичны). Регистрации
          приватны — их список доступен только самому пользователю/админу. */}
      {!isOwnProfile && (
        <div className="pf-events">
          <div className="pf-events__head">{t('profile.likedEvents')}</div>
          <LikedEvents hideHeader />
        </div>
      )}
    </div>
  );
};

export default UserProfile;
