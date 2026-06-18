import React, { useState, useEffect } from "react";
import { useNavigate, Link, useLocation } from "react-router-dom";
import api from "../api";
import "../css/SignIn.css";

function SignIn() {
  const navigate = useNavigate();
  const location = useLocation();
  const [showPassword, setShowPassword] = useState(false);
  const [notice, setNotice] = useState(location.state?.notice || "");

  useEffect(() => {
    document.title = 'Войти — EventHub.kz';
    if (location.state?.notice) {
      window.history.replaceState({}, document.title);
    }
  }, [location.state]);
  const [formData, setFormData] = useState({ email: "", password: "" });
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData((prev) => ({ ...prev, [name]: value }));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError("");
    setLoading(true);
    try {
      const res = await api.post("/auth/login", formData);
      localStorage.setItem("token", res.data.token);
      if (res.data.userId) {
        localStorage.setItem("userId", String(res.data.userId));
      }
      // Возврат на страницу, с которой гостя отправили на вход (?redirect=...).
      const redirect = new URLSearchParams(location.search).get("redirect");
      navigate(redirect || "/");
    } catch (err) {
      const msg = err.response?.data?.message || "Неверный email или пароль";
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-page">
      <div className="auth-page__illus">
        <div className="auth-page__illus-inner">
          <div className="auth-page__illus-logo">
            <div className="auth-page__illus-mark">
              <div className="auth-page__illus-shape" />
              <div className="auth-page__illus-dot" />
            </div>
            <span className="auth-page__illus-name">eventhub.kz</span>
          </div>
          <h2 className="auth-page__illus-tagline">
            Все IT-события<br />Казахстана —<br />
            <em>в одном месте.</em>
          </h2>
          <div className="auth-page__illus-stats">
            {[["1200+","событий"],["60 тыс.","участников"],["38","городов"]].map(([n,l]) => (
              <div key={l} className="auth-page__illus-stat">
                <div className="auth-page__illus-stat-n">{n}</div>
                <div className="auth-page__illus-stat-l">{l}</div>
              </div>
            ))}
          </div>
        </div>
        <div className="auth-page__illus-blob-1" />
        <div className="auth-page__illus-blob-2" />
      </div>

      <div className="auth-page__right">
        <div className="auth-page__form-wrap">
          <h1 className="auth-page__title">С возвращением!</h1>
          <p className="auth-page__sub">Войди по email и паролю</p>

          {notice && (
            <div className="auth-form__notice" role="status">
              {notice}
            </div>
          )}

          <form className="auth-form" onSubmit={handleSubmit}>
            <div className="auth-form__field">
              <label className="auth-form__label">Email</label>
              <input className="auth-form__input" type="email" name="email"
                placeholder="you@email.com" value={formData.email}
                onChange={handleChange} required />
            </div>

            <div className="auth-form__field">
              <label className="auth-form__label">Пароль</label>
              <div className="auth-form__input-wrap">
                <input className="auth-form__input" type={showPassword ? "text" : "password"}
                  name="password" placeholder="••••••••" value={formData.password}
                  onChange={handleChange} required />
                <button type="button" className="auth-form__eye" onClick={() => setShowPassword(v => !v)}>
                  {showPassword
                    ? <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="16" height="16"><path d="M17.94 17.94A10 10 0 0 1 12 20c-7 0-11-8-11-8a18 18 0 0 1 5.06-5.94M9.9 4.24A9 9 0 0 1 12 4c7 0 11 8 11 8a18 18 0 0 1-2.16 3.19"/><line x1="1" y1="1" x2="23" y2="23"/></svg>
                    : <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="16" height="16"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
                  }
                </button>
              </div>
            </div>

            {error && <div className="auth-form__error">{error}</div>}

            <button className="auth-form__submit" type="submit" disabled={loading}>
              {loading ? "Вход..." : "Войти"}
            </button>

            <div className="auth-form__divider"><span>или</span></div>

            <p className="auth-form__footer">
              Нет аккаунта? <Link to="/signupnew" className="auth-form__link">Зарегистрируйся</Link>
            </p>
          </form>
        </div>
      </div>
    </div>
  );
}

export default SignIn;
