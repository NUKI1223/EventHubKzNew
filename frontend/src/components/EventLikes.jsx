import React from "react";
import { Link } from "react-router-dom";
import { useTranslation } from "react-i18next";
import "../css/EventLikes.css";

function EventLikes({ eventId, likeCount }) {
  const { t } = useTranslation();
  return (
    <Link to={`/events/${eventId}/likers`} className="toggle-likes-btn">
      {t('events.whoLiked', { count: likeCount || 0 })}
    </Link>
  );
}

export default EventLikes;
