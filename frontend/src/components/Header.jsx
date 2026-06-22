import React, { useState, useEffect, useRef } from "react";
import { Link, useNavigate, useLocation } from "react-router-dom";
import { useAuthUser } from "../hooks/useAuthUser";
import api from "../api";
import NotificationsDropdown from "./NotificationsDropdown";
import "../css/Header.css";

function Header() {
  const navigate = useNavigate();
  const location = useLocation();
  const token = localStorage.getItem("token");
  const [showNotifications, setShowNotifications] = useState(false);
  const [notifications, setNotifications] = useState([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const [user, setUser] = useState(null);
  const [showDropdown, setShowDropdown] = useState(false);
  const [showLogoutConfirm, setShowLogoutConfirm] = useState(false);
  const [q, setQ] = useState("");
  const [mobileOpen, setMobileOpen] = useState(false);

  const notificationRef = useRef(null);
  const dropdownRef = useRef(null);

  const currentUser = useAuthUser();
  const username = currentUser?.sub ?? null;

  useEffect(() => {
    if (!username) return;
    api.get("/api/users/me")
      .then((res) => setUser(res.data))
      .catch((err) => console.error("Ошибка загрузки пользователя:", err));
  }, [username]);

  useEffect(() => {
    if (!username) return;
    api.get("/api/notifications")
      .then((res) => {
        const data = Array.isArray(res.data) ? res.data : res.data.content || [];
        setNotifications(data);
        setUnreadCount(data.filter((n) => !n.read).length);
      })
      .catch((err) => console.error("Ошибка загрузки уведомлений:", err));
  }, [username]);

  const handleUpdateNotifications = () => {
    if (!username) return;
    api.get("/api/notifications")
      .then((res) => {
        const data = Array.isArray(res.data) ? res.data : res.data.content || [];
        setNotifications(data);
        setUnreadCount(data.filter((n) => !n.read).length);
      })
      .catch((err) => console.error("Ошибка обновления уведомлений:", err));
  };

  useEffect(() => {
    function handleClickOutside(event) {
      if (notificationRef.current && !notificationRef.current.contains(event.target))
        setShowNotifications(false);
      if (dropdownRef.current && !dropdownRef.current.contains(event.target))
        setShowDropdown(false);
    }
    if (showNotifications || showDropdown) {
      document.addEventListener("mousedown", handleClickOutside);
    }
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, [showNotifications, showDropdown]);

  const confirmLogout = () => {
    localStorage.removeItem("token");
    setUser(null);
    setShowDropdown(false);
    setShowLogoutConfirm(false);
    navigate("/signin");
  };

  const handleSearch = (e) => {
    e.preventDefault();
    if (q.trim()) {
      navigate(`/search?q=${encodeURIComponent(q.trim())}`);
      setQ("");
    }
  };

  const isActive = (path) => location.pathname === path;

  const navItems = [
    { path: "/", label: "Главная" },
    ...(token ? [
      { path: "/eventlist", label: "Мероприятия" },
      { path: "/request-event", label: "Создать заявку" },
      { path: "/support", label: "Поддержка" },
      ...(currentUser?.role === "ADMIN" ? [{ path: "/admin", label: "Админ" }] : []),
    ] : []),
  ];

  const firstLetter = username ? username[0].toUpperCase() : "?";

  return (
    <>
      <header className="header">
        {/* Logo */}
        <Link to="/" className="header__logo">
          <div className="header__logo-mark">
            <div className="header__logo-shape" />
            <div className="header__logo-dot" />
          </div>
          <span className="header__logo-text">
            eventhub<span className="header__logo-accent">.kz</span>
          </span>
        </Link>

        {/* Nav */}
        <nav className="header__nav">
          {navItems.map((item) => (
            <Link
              key={item.path}
              to={item.path}
              className={`header__nav-item ${isActive(item.path) ? "header__nav-item--active" : ""}`}
            >
              {item.label}
              {item.badge && (
                <span className="header__nav-badge">{item.badge}</span>
              )}
            </Link>
          ))}
        </nav>

        <div className="header__spacer" />

        {/* Search */}
        <form className="header__search" onSubmit={handleSearch}>
          <svg className="header__search-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/>
          </svg>
          <input
            className="header__search-input"
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="Поиск событий, тегов..."
          />
          <kbd className="header__search-kbd">⌘K</kbd>
        </form>

        {/* Notifications */}
        {token && (
          <div className="header__notif-wrap" ref={notificationRef}>
            <button
              className="header__notif-btn"
              aria-label="Уведомления"
              onClick={() => {
                setShowNotifications((v) => !v);
                if (unreadCount > 0) setUnreadCount(0);
              }}
            >
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" width="18" height="18">
                <path d="M6 8a6 6 0 0 1 12 0c0 5 2 6 2 6H4s2-1 2-6"/>
                <path d="M10.3 20a2 2 0 0 0 3.4 0"/>
              </svg>
              {unreadCount > 0 && (
                <span className="header__notif-badge">{unreadCount}</span>
              )}
            </button>
            {showNotifications && (
              <NotificationsDropdown
                notifications={notifications}
                username={username}
                onClose={() => setShowNotifications(false)}
                onUpdate={handleUpdateNotifications}
              />
            )}
          </div>
        )}

        {/* User */}
        {token ? (
          <div className="header__user-wrap" ref={dropdownRef}>
            <button className="header__user-btn" aria-label="Меню профиля" onClick={() => setShowDropdown((v) => !v)}>
              {user?.avatarUrl ? (
                <img src={user.avatarUrl} alt={username} className="header__avatar-img" />
              ) : (
                <div className="header__avatar-placeholder">{firstLetter}</div>
              )}
              <span className="header__user-name">{username}</span>
              {currentUser?.role === 'ADMIN' && (
                <span className="header__admin-badge">ADMIN</span>
              )}
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" width="13" height="13" style={{color:'var(--ink-400)'}}>
                <path d="m6 9 6 6 6-6"/>
              </svg>
            </button>
            {showDropdown && (
              <ul className="header__dropdown">
                <li>
                  <Link to={`/profile/${username}`} className="header__dropdown-item" onClick={() => setShowDropdown(false)}>
                    Профиль
                  </Link>
                </li>
                <li>
                  <button
                    className="header__dropdown-item header__dropdown-item--danger"
                    onClick={() => { setShowDropdown(false); setShowLogoutConfirm(true); }}
                  >
                    Выйти
                  </button>
                </li>
              </ul>
            )}
          </div>
        ) : (
          <div className="header__auth">
            <Link to="/signin" className="header__auth-signin">Войти</Link>
            <Link to="/signupnew" className="header__auth-signup">Регистрация</Link>
          </div>
        )}

        {/* Burger (mobile) */}
        <button
          className="header__burger"
          aria-label="Меню"
          aria-expanded={mobileOpen}
          onClick={() => setMobileOpen((v) => !v)}
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" width="22" height="22">
            {mobileOpen ? <path d="M6 6l12 12M18 6L6 18"/> : <path d="M3 6h18M3 12h18M3 18h18"/>}
          </svg>
        </button>
      </header>

      {/* Mobile drawer */}
      {mobileOpen && (
        <div className="header__mobile">
          <form className="header__mobile-search" onSubmit={(e) => { handleSearch(e); setMobileOpen(false); }}>
            <input
              className="header__mobile-input"
              value={q}
              onChange={(e) => setQ(e.target.value)}
              placeholder="Поиск событий, тегов..."
              aria-label="Поиск"
            />
          </form>
          {navItems.map((item) => (
            <Link
              key={item.path}
              to={item.path}
              className={`header__mobile-item ${isActive(item.path) ? "header__mobile-item--active" : ""}`}
              onClick={() => setMobileOpen(false)}
            >
              {item.label}
              {item.badge && <span className="header__nav-badge">{item.badge}</span>}
            </Link>
          ))}
          {!token && (
            <div className="header__mobile-auth">
              <Link to="/signin" className="header__auth-signin" onClick={() => setMobileOpen(false)}>Войти</Link>
              <Link to="/signupnew" className="header__auth-signup" onClick={() => setMobileOpen(false)}>Регистрация</Link>
            </div>
          )}
        </div>
      )}

      {/* Logout modal */}
      {showLogoutConfirm && (
        <div className="logout-overlay">
          <div className="logout-dialog">
            <h3 className="logout-dialog__title">Выйти из аккаунта?</h3>
            <p className="logout-dialog__text">Вы уверены, что хотите выйти?</p>
            <div className="logout-dialog__actions">
              <button className="logout-dialog__confirm" onClick={confirmLogout}>Да, выйти</button>
              <button className="logout-dialog__cancel" onClick={() => setShowLogoutConfirm(false)}>Отмена</button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}

export default Header;
