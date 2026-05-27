import React from 'react';
import '../css/Skeleton.css';

const Skeleton = ({ variant = 'text', width, height, style }) => {
  const className = `skeleton skeleton--${variant}`;
  return (
    <div
      className={className}
      style={{ width, height, ...style }}
    />
  );
};

export const SkeletonCard = () => (
  <div className="skeleton-card">
    <div className="skeleton-card__image" />
    <div className="skeleton-card__body">
      <Skeleton variant="title" />
      <Skeleton variant="text" />
      <Skeleton variant="text-short" />
      <div className="skeleton-card__tags">
        <Skeleton variant="tag" />
        <Skeleton variant="tag" />
        <Skeleton variant="tag" />
      </div>
      <div className="skeleton-card__footer">
        <Skeleton variant="text-short" width="100px" />
        <Skeleton variant="button" />
      </div>
    </div>
  </div>
);

export const SkeletonProfile = () => (
  <div className="skeleton-profile">
    <div className="skeleton-profile__header">
      <Skeleton variant="avatar" />
      <div className="skeleton-profile__info">
        <Skeleton variant="title" width="200px" />
        <Skeleton variant="text-short" width="150px" />
      </div>
    </div>
    <div className="skeleton-profile__details">
      <Skeleton variant="text" />
      <Skeleton variant="text" />
      <Skeleton variant="text-short" />
      <Skeleton variant="text" />
      <Skeleton variant="text-short" />
    </div>
  </div>
);

export default Skeleton;
