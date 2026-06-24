import { useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import toast from 'react-hot-toast';

const AUTH_PAGES = ['/signin', '/signupnew', '/verify', '/verification'];

function readExpiryMs(token) {
  try {
    const payload = JSON.parse(atob(token.split('.')[1]));
    return typeof payload.exp === 'number' ? payload.exp * 1000 : null;
  } catch {
    return null;
  }
}

export function useAuthExpiryWatcher() {
  const navigate = useNavigate();
  const location = useLocation();
  const { t } = useTranslation();

  useEffect(() => {
    if (AUTH_PAGES.some(p => location.pathname.startsWith(p))) return;

    const token = localStorage.getItem('token');
    if (!token) return;

    const expiresAt = readExpiryMs(token);
    const logout = () => {
      localStorage.removeItem('token');
      localStorage.removeItem('userId');
      toast.error(t('common.sessionExpired'));
      navigate('/signin', { replace: true });
    };

    if (expiresAt === null || expiresAt <= Date.now()) {
      logout();
      return;
    }

    const id = setTimeout(logout, expiresAt - Date.now());
    return () => clearTimeout(id);
  }, [location.pathname, navigate]);
}
