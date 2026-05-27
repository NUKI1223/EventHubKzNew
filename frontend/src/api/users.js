import api from '../api';

export async function fetchUserByRoute(routeUsername) {
  const res = routeUsername
    ? await api.get(`/api/users/username/${routeUsername}`)
    : await api.get('/api/users/me');
  return res.data;
}
