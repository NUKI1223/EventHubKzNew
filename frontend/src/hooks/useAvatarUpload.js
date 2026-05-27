import { useEffect, useState } from 'react';
import api from '../api';
import { SOCIALS } from '../config/socials';

const EMPTY_CONTACTS = SOCIALS.reduce((acc, s) => ({ ...acc, [s.contactKey]: '' }), {});

export function useAvatarUpload(user, setUser) {
  const [avatarFile, setAvatarFile] = useState(null);
  const [avatarLoading, setAvatarLoading] = useState(false);
  const [avatarError, setAvatarError] = useState(null);

  useEffect(() => {
    if (!avatarFile || !user) return;
    const upload = async () => {
      setAvatarLoading(true);
      setAvatarError(null);
      try {
        const userId = localStorage.getItem('userId');

        const uploadData = new FormData();
        uploadData.append('file', avatarFile);
        const uploadRes = await api.post(`/api/files/users/${userId}/avatar`, uploadData, {
          headers: { 'Content-Type': 'multipart/form-data' },
        });

        await api.put(`/api/users/${userId}`, {
          username: user.username || '',
          email: user.email || '',
          description: user.description || '',
          avatarUrl: uploadRes.data.fileUrl,
          contacts: user.contacts || EMPTY_CONTACTS,
        });

        const updated = await api.get('/api/users/me');
        setUser({
          ...updated.data,
          avatarUrl: updated.data.avatarUrl
            ? `${updated.data.avatarUrl}?t=${Date.now()}`
            : updated.data.avatarUrl,
        });
        setAvatarFile(null);
      } catch (err) {
        setAvatarError(
          err.response
            ? `Ошибка: ${err.response.data.message || err.response.statusText}`
            : 'Нет ответа от сервера'
        );
      } finally {
        setAvatarLoading(false);
      }
    };
    upload();
  }, [avatarFile]);

  return { setAvatarFile, avatarLoading, avatarError };
}
