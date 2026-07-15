import axios from 'axios';
import toast from 'react-hot-toast';
import i18n from './i18n';

function isTokenExpired(token) {
  try {
    const payload = JSON.parse(atob(token.split('.')[1]));
    const now = Math.floor(Date.now() / 1000);
    return payload.exp < now;
  } catch (error) {
    return true;
  }
}

const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL || 'http://localhost:8180',
  withCredentials: true,   // send/receive the httpOnly refresh_token cookie
});

// Single in-flight refresh shared by all callers (avoids a stampede of /auth/refresh).
let refreshPromise = null;
function refreshAccessToken() {
  if (!refreshPromise) {
    const base = import.meta.env.VITE_API_URL || 'http://localhost:8180';
    refreshPromise = axios
      .post(`${base}/auth/refresh`, {}, { withCredentials: true })
      .then((res) => {
        const token = res.data.token;
        localStorage.setItem('token', token);
        if (res.data.userId != null) localStorage.setItem('userId', res.data.userId);
        return token;
      })
      .finally(() => { refreshPromise = null; });
  }
  return refreshPromise;
}

function forceSignout() {
  localStorage.removeItem('token');
  localStorage.removeItem('userId');
  if (window.location.pathname !== '/signin' && window.location.pathname !== '/signupnew') {
    window.location.href = '/signin';
  }
}

api.interceptors.request.use(
  async (config) => {
    const isAuthEndpoint = config.url?.startsWith('/auth/');
    if (isAuthEndpoint) return config;

    let token = localStorage.getItem('token');
    if (token && isTokenExpired(token)) {
      // access token expired → get a new one from the refresh cookie before sending
      try { token = await refreshAccessToken(); }
      catch { forceSignout(); return Promise.reject(new Error('Session expired')); }
    }
    if (token) config.headers.Authorization = `Bearer ${token}`;
    return config;
  },
  (error) => Promise.reject(error)
);

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const original = error.config;
    const status = error.response?.status;
    const isAuthEndpoint = original?.url?.startsWith('/auth/');

    // 401 on a normal call → try one transparent refresh + retry
    if (status === 401 && original && !original._retried && !isAuthEndpoint) {
      original._retried = true;
      try {
        const token = await refreshAccessToken();
        original.headers = original.headers || {};
        original.headers.Authorization = `Bearer ${token}`;
        return api(original);
      } catch {
        forceSignout();
        return Promise.reject(error);
      }
    }
    if (status === 401 && !isAuthEndpoint) forceSignout();
    if (status === 429) toast.error(i18n.t('common.rateLimited'));
    return Promise.reject(error);
  }
);

const fetchCurrentUser = () => {
  const authPages = ['/signin', '/signupnew', '/verify', '/verification'];
  if (authPages.some(p => window.location.pathname.startsWith(p))) return;

  const token = localStorage.getItem('token');
  if (token && !isTokenExpired(token)) {
    api.get('/api/users/me')
      .then(res => {
        const user = res.data;
        localStorage.setItem('userId', user.id);
      })
      .catch(err => console.error('Ошибка получения данных пользователя', err));
  }
};
fetchCurrentUser();

export default api;
