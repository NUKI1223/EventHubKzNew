import React from 'react';
import { Navigate } from 'react-router-dom';
import { useAuthUser } from '../hooks/useAuthUser';

const AdminRoute = ({ children }) => {
  const user = useAuthUser();
  if (!user) return <Navigate to="/signin" />;
  if (user.role !== 'ADMIN') return <Navigate to="/" />;
  return children;
};

export default AdminRoute;
