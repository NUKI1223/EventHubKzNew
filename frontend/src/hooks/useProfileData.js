import { useEffect, useState } from 'react';
import { fetchUserByRoute } from '../api/users';

export function useProfileData(routeUsername, locationKey) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    const load = async () => {
      try {
        setLoading(true);
        const data = await fetchUserByRoute(routeUsername);
        setUser(data);
        document.title = `${data.username} — EventHub.kz`;
      } catch {
        setError('Ошибка при загрузке данных пользователя');
        document.title = 'Профиль — EventHub.kz';
      } finally {
        setLoading(false);
      }
    };
    load();
  }, [routeUsername, locationKey]);

  return { user, setUser, loading, error };
}
