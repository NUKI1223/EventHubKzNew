import React from 'react';
import { BrowserRouter as Router, Routes, Route, useLocation } from 'react-router-dom';
import { Toaster } from 'react-hot-toast';
import EventList from './components/EventList';
import EventDetail from './components/EventDetail';
import EventRequestForm from './components/EventRequestForm';
import SearchResults from './components/SearchResults';
import UserProfile from './components/Profile';
import LikedEvents from './components/LikedEvents';
import EventLikers from './components/EventLikers';
import EventRegistrants from './components/EventRegistrants';
import EditProfile from './components/EditProfile';
import AdminRoute from './components/AdminRoute';
import AdminDashboard from './components/AdminDashboard';
import SignUpNew from './components/SignUpNew';
import SignIn from './components/SignIn';
import Verify from './components/Verify';
import Header from './components/Header';
import MainPage from './components/MainPage';
import Support from './components/Support';
import { useAuthExpiryWatcher } from './hooks/useAuthExpiryWatcher';

function AppContent() {
  const location = useLocation();
  useAuthExpiryWatcher();

  const hideNavbarPaths = ['/signupnew', '/signin', '/verify'];

  const shouldShowNavbar = !hideNavbarPaths.some(p => location.pathname.startsWith(p));

  return (
    <>
      {shouldShowNavbar && <Header />}
      <Routes>
        <Route path="/" element={<MainPage />} />
        <Route path="/eventlist" element={<EventList />} />
        <Route path="/events/:id" element={<EventDetail />} />
        <Route path="/events/:id/likers" element={<EventLikers />} />
        <Route path="/events/:id/registrants" element={<EventRegistrants />} />
        <Route path="/request-event" element={<EventRequestForm />} />
        <Route path="/search" element={<SearchResults />} />
        <Route path="/profile/:username?" element={<UserProfile />} />
        <Route path="/profile/:username/liked-events" element={<LikedEvents />} />
        <Route path="/liked-events" element={<LikedEvents />} />
        <Route path="/edit-profile" element={<EditProfile />} />
        <Route path="/admin" element={<AdminRoute><AdminDashboard /></AdminRoute>} />
        <Route path="/support" element={<Support />} />
        <Route path="/signupnew" element={<SignUpNew />} />
        <Route path="/signin" element={<SignIn />} />
        <Route path="/verify" element={<Verify />} />
      </Routes>
    </>
  );
}

function App() {
  return (
    <Router>
      <Toaster
        position="top-right"
        toastOptions={{
          duration: 4000,
          style: {
            borderRadius: '12px',
            background: '#1e293b',
            color: '#fff',
            fontFamily: 'Inter, sans-serif',
            fontSize: '14px',
            padding: '12px 20px',
            boxShadow: '0 8px 24px rgba(0,0,0,0.15)',
          },
          success: {
            iconTheme: { primary: '#10b981', secondary: '#fff' },
          },
          error: {
            iconTheme: { primary: '#ef4444', secondary: '#fff' },
          },
        }}
      />
      <AppContent />
    </Router>
  );
}

export default App;
