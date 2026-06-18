import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../api';
import toast from 'react-hot-toast';

/**
 * Общая логика кнопок-тоглов (лайк / запись): состояние active + счётчик,
 * защита от двойного клика (busy), оптимистичное обновление с откатом при ошибке,
 * редирект гостя на вход. active/count можно задать снаружи (для списка — без само-запросов).
 */
export function useToggleResource({
  initialActive = false,
  initialCount = 0,
  currentUserId,
  postUrl,
  deleteUrl,
  msgOn,
  msgOff,
  msgError = 'Не удалось выполнить действие',
  onActiveChange,
}) {
  const [active, setActive] = useState(initialActive);
  const [count, setCount] = useState(initialCount);
  const [busy, setBusy] = useState(false);
  const navigate = useNavigate();

  // Синхронизация со значениями, которые мог передать родитель (список).
  useEffect(() => { setActive(initialActive); }, [initialActive]);
  useEffect(() => { setCount(initialCount); }, [initialCount]);

  const toggle = useCallback(async () => {
    if (busy) return;
    if (!currentUserId) {
      const here = window.location.pathname + window.location.search;
      navigate(`/signin?redirect=${encodeURIComponent(here)}`);
      return;
    }
    const wasActive = active;
    setBusy(true);
    // оптимистично
    setActive(!wasActive);
    setCount((c) => Math.max(0, c + (wasActive ? -1 : 1)));
    try {
      if (wasActive) await api.delete(deleteUrl);
      else await api.post(postUrl);
      onActiveChange?.(!wasActive);
      const m = wasActive ? msgOff : msgOn;
      if (m) toast.success(m);
    } catch (err) {
      // откат
      setActive(wasActive);
      setCount((c) => Math.max(0, c + (wasActive ? 1 : -1)));
      const status = err?.response?.status;
      const serverMsg = err?.response?.data?.message;
      toast.error((serverMsg && serverMsg.trim()) || (status === 409 ? 'Действие недоступно' : msgError));
    } finally {
      setBusy(false);
    }
  }, [busy, currentUserId, active, postUrl, deleteUrl, msgOn, msgOff, msgError, onActiveChange, navigate]);

  return { active, count, busy, toggle, setActive, setCount };
}
