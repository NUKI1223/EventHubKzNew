import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { fetchUserByRoute } from '../api/users';

export function useProfileData(routeUsername, locationKey) {
  const { t } = useTranslation();
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
        setError(t('profile.loadUserError'));
        document.title = t('profile.viewPageTitle');
      } finally {
        setLoading(false);
      }
    };
    load();
  }, [routeUsername, locationKey]);

  return { user, setUser, loading, error };
}
