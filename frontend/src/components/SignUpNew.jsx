import React, { useState, useEffect } from "react";
import { useNavigate, Link } from "react-router-dom";
import api from "../api";
import "../css/SignIn.css";

function SignUpNew() {
  const [showPassword, setShowPassword] = useState(false);

  useEffect(() => {
    document.title = 'Регистрация — EventHub.kz';
  }, []);
  const [formData, setFormData] = useState({ username: "", email: "", password: "", confirmPassword: "" });
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData((prev) => ({ ...prev, [name]: value }));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    setError("");

    const passwordRegex = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,}$/;
    if (!passwordRegex.test(formData.password)) {
      setError("Пароль: мин 8 символов, заглавная и строчная буква, цифра.");
      setLoading(false);
      return;
    }
    if (formData.password !== formData.confirmPassword) {
      setError("Пароли не совпадают.");
      setLoading(false);
      return;
    }

    try {
      await api.post("/auth/signup", {
        username: formData.username,
        email: formData.email,
        password: formData.password,
      });
      navigate("/verify", { state: { email: formData.email } });
    } catch (err) {
      const msg = err.response?.data?.message || err.response?.data?.error || "Ошибка регистрации.";
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  const p = formData.password;
  const strength = [p.length >= 8, /[A-Z]/.test(p), /\d/.test(p)].filter(Boolean).length;

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
            Присоединяйся<br />к IT-сообществу<br />
            <em>Казахстана.</em>
          </h2>
          <div className="auth-page__illus-stats">
            {[["1200+","событий"],["60 тыс.","участников"],["480","организаторов"]].map(([n,l]) => (
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
          <div className="auth-page__steps">
            <div className="auth-page__step auth-page__step--active" />
            <div className="auth-page__step" />
            <span className="auth-page__step-label">ШАГ 1 ИЗ 2</span>
          </div>

          <h1 className="auth-page__title">Создай аккаунт</h1>
          <p className="auth-page__sub">Заполни основные данные</p>

          <form className="auth-form" onSubmit={handleSubmit}>
            <div className="auth-form__field">
              <label className="auth-form__label">Имя пользователя</label>
              <input className="auth-form__input" type="text" name="username"
                placeholder="username" value={formData.username}
                onChange={handleChange} required />
            </div>

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
              {formData.password && (
                <div className="auth-form__strength">
                  <div className={`auth-form__strength-bar auth-form__strength-bar--${strength >= 1 ? "fill" : ""}`} />
                  <div className={`auth-form__strength-bar auth-form__strength-bar--${strength >= 2 ? "fill" : ""}`} />
                  <div className={`auth-form__strength-bar auth-form__strength-bar--${strength >= 3 ? "fill" : ""}`} />
                  <span className="auth-form__strength-hint">
                    {strength < 2 ? "слабый" : strength < 3 ? "средний" : "надёжный"}
                  </span>
                </div>
              )}
            </div>

            <div className="auth-form__field">
              <label className="auth-form__label">Повтори пароль</label>
              <input className="auth-form__input" type="password" name="confirmPassword"
                placeholder="••••••••" value={formData.confirmPassword}
                onChange={handleChange} required />
            </div>

            {error && <div className="auth-form__error">{error}</div>}

            <button className="auth-form__submit" type="submit" disabled={loading}>
              {loading ? "Загрузка..." : "Продолжить →"}
            </button>

            <p className="auth-form__footer">
              Уже есть аккаунт? <Link to="/signin" className="auth-form__link">Войти</Link>
            </p>
          </form>
        </div>
      </div>
    </div>
  );
}

export default SignUpNew;
