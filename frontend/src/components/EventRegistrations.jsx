import React from "react";
import { Link } from "react-router-dom";
import "../css/EventLikes.css";

function EventRegistrations({ eventId, count }) {
  return (
    <Link to={`/events/${eventId}/registrants`} className="toggle-likes-btn">
      Кто идёт ({count || 0})
    </Link>
  );
}

export default EventRegistrations;
