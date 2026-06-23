import React from "react";
import { Link } from "react-router-dom";
import { useTranslation } from "react-i18next";
import "../css/EventLikes.css";

function EventRegistrations({ eventId, count }) {
  const { t } = useTranslation();
  return (
    <Link to={`/events/${eventId}/registrants`} className="toggle-likes-btn">
      {t('eventDetail.whoGoing', { count: count || 0 })}
    </Link>
  );
}

export default EventRegistrations;
