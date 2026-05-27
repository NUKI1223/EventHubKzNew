import React, { useState, useEffect, useRef } from 'react';
import api from '../api';
import toast from 'react-hot-toast';
import '../css/LikeButton.css';

const LikeButton = ({ eventId, currentUserId, onLikeChange, onLikedChange }) => {
  const [liked, setLiked] = useState(false);
  const [likeCount, setLikeCount] = useState(0);
  const onLikedChangeRef = useRef(onLikedChange);
  const onLikeChangeRef = useRef(onLikeChange);
  onLikedChangeRef.current = onLikedChange;
  onLikeChangeRef.current = onLikeChange;

  useEffect(() => {
    if (currentUserId && eventId) {
      const fetchUserLikes = async () => {
        try {
          const res = await api.get(`/api/likes/user/${currentUserId}/events`);
          const likedEventIds = Array.isArray(res.data) ? res.data : [];
          const isLiked = likedEventIds.includes(eventId);
          setLiked(isLiked);
          if (onLikedChangeRef.current) onLikedChangeRef.current(isLiked);
        } catch (err) {
          console.error('Ошибка получения лайков пользователя', err);
        }
      };
      fetchUserLikes();
    }
  }, [currentUserId, eventId]);

  useEffect(() => {
    if (!eventId) return;
    const fetchLikeCount = async () => {
      try {
        const res = await api.get(`/api/likes/event/${eventId}/count`);
        setLikeCount(res.data);
        if (onLikeChangeRef.current) onLikeChangeRef.current(res.data);
      } catch (err) {
        console.error(err);
      }
    };
    fetchLikeCount();
  }, [eventId]);

  const toggleLike = async () => {
    if (!currentUserId) {
      toast.error('Вы должны быть авторизованы для лайка');
      return;
    }
    try {
      if (liked) {
        await api.delete(`/api/likes/event/${eventId}`);
        setLikeCount(prev => {
          const newCount = prev - 1;
          if (onLikeChange) onLikeChange(newCount);
          return newCount;
        });
        setLiked(false);
        if (onLikedChange) onLikedChange(false);
      } else {
        await api.post(`/api/likes/event/${eventId}`);
        setLikeCount(prev => {
          const newCount = prev + 1;
          if (onLikeChange) onLikeChange(newCount);
          return newCount;
        });
        setLiked(true);
        if (onLikedChange) onLikedChange(true);
      }
    } catch (err) {
      console.error(err);
    }
  };

  return (
    <button
      className={`like-button ${liked ? 'like-button--liked' : ''}`}
      onClick={(e) => { e.preventDefault(); e.stopPropagation(); toggleLike(); }}
    >
      {liked ? '❤ Нравится' : '♡ Нравится'} ({likeCount})
    </button>
  );
};

export default LikeButton;