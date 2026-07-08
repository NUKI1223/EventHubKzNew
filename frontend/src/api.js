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
});

api.interceptors.request.use(
  (config) => {
    const isAuthEndpoint = config.url?.startsWith('/auth/');
    if (isAuthEndpoint) return config;

    const token = localStorage.getItem('token');
    if (token) {
      if (isTokenExpired(token)) {
        localStorage.removeItem('token');
        localStorage.removeItem('userId');
        window.location.href = '/signin';
        return Promise.reject(new Error('Token expired'));
      }
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response && error.response.status === 401) {
      localStorage.removeItem('token');
      localStorage.removeItem('userId');
      if (window.location.pathname !== '/signin' && window.location.pathname !== '/signupnew') {
        window.location.href = '/signin';
      }
    }
    if (error.response && error.response.status === 429) {
      toast.error(i18n.t('common.rateLimited'));
    }
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
