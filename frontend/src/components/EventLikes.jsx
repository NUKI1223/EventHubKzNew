import React from "react";
import { Link } from "react-router-dom";
import "../css/EventLikes.css";

function EventLikes({ eventId, likeCount }) {
  return (
    <Link to={`/events/${eventId}/likers`} className="toggle-likes-btn">
      Кто лайкнул ({likeCount || 0})
    </Link>
  );
}

export default EventLikes;
