import React, { useState, useEffect, useRef } from "react";
import { useNavigate, useLocation, Link } from "react-router-dom";
import api from "../api";
import "../css/SignIn.css";

const CODE_LENGTH = 6;

function Verify() {
  const navigate = useNavigate();
  const location = useLocation();
  const email = location.state?.email || "";

  const [digits, setDigits] = useState(Array(CODE_LENGTH).fill(""));
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const [resending, setResending] = useState(false);
  const [resendCooldown, setResendCooldown] = useState(0);
  const inputsRef = useRef([]);

  useEffect(() => {
    document.title = "Подтверждение email — EventHub.kz";
    if (!email) {
      navigate("/signupnew", { replace: true });
      return;
    }
    inputsRef.current[0]?.focus();
  }, [email, navigate]);

  useEffect(() => {
    if (resendCooldown <= 0) return;
    const t = setTimeout(() => setResendCooldown(c => c - 1), 1000);
    return () => clearTimeout(t);
  }, [resendCooldown]);

  const code = digits.join("");
  const isComplete = code.length === CODE_LENGTH && /^\d+$/.test(code);

  const handleChange = (idx, raw) => {
    const ch = raw.replace(/\D/g, "").slice(-1);
    const next = [...digits];
    next[idx] = ch;
    setDigits(next);
    setError("");
    if (ch && idx < CODE_LENGTH - 1) {
      inputsRef.current[idx + 1]?.focus();
    }
  };

  const handleKeyDown = (idx, e) => {
    if (e.key === "Backspace" && !digits[idx] && idx > 0) {
      inputsRef.current[idx - 1]?.focus();
    }
  };

  const handlePaste = (e) => {
    const pasted = e.clipboardData.getData("text").replace(/\D/g, "").slice(0, CODE_LENGTH);
    if (!pasted) return;
    e.preventDefault();
    const next = Array(CODE_LENGTH).fill("");
    for (let i = 0; i < pasted.length; i++) next[i] = pasted[i];
    setDigits(next);
    const focusIdx = Math.min(pasted.length, CODE_LENGTH - 1);
    inputsRef.current[focusIdx]?.focus();
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!isComplete) {
      setError("Введите 6-значный код из письма.");
      return;
    }
    setLoading(true);
    setError("");
    try {
      await api.post("/auth/verify", { email, verificationCode: code });
      navigate("/signin", {
        state: { notice: "Аккаунт подтверждён. Теперь войдите по email и паролю." },
      });
    } catch (err) {
      const msg = err.response?.data?.error || err.response?.data?.message || "Неверный или просроченный код.";
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  const handleResend = async () => {
    if (resendCooldown > 0 || resending) return;
    setResending(true);
    setError("");
    try {
      await api.post("/auth/resend", { email });
      setResendCooldown(60);
      setDigits(Array(CODE_LENGTH).fill(""));
      inputsRef.current[0]?.focus();
    } catch (err) {
      const msg = err.response?.data?.error || err.response?.data?.message || "Не удалось отправить код заново.";
      setError(msg);
    } finally {
      setResending(false);
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
            Ещё один шаг —<br />и ты в&nbsp;<em>EventHub.</em>
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
            <div className="auth-page__step auth-page__step--active" />
            <span className="auth-page__step-label">ШАГ 2 ИЗ 2</span>
          </div>

          <h1 className="auth-page__title">Подтверди email</h1>
          <p className="auth-page__sub">
            Мы отправили 6-значный код на <strong>{email}</strong>
          </p>

          <form className="auth-form" onSubmit={handleSubmit}>
            <div className="auth-form__field">
              <label className="auth-form__label">Код из письма</label>
              <div className="auth-form__code-row">
                {digits.map((d, i) => (
                  <input
                    key={i}
                    ref={el => (inputsRef.current[i] = el)}
                    className="auth-form__code-input"
                    type="text"
                    inputMode="numeric"
                    autoComplete="one-time-code"
                    maxLength={1}
                    value={d}
                    onChange={(e) => handleChange(i, e.target.value)}
                    onKeyDown={(e) => handleKeyDown(i, e)}
                    onPaste={i === 0 ? handlePaste : undefined}
                  />
                ))}
              </div>
            </div>

            {error && <div className="auth-form__error">{error}</div>}

            <button className="auth-form__submit" type="submit" disabled={loading || !isComplete}>
              {loading ? "Проверяем..." : "Подтвердить →"}
            </button>

            <div className="auth-form__resend">
              {resendCooldown > 0 ? (
                <span className="auth-form__resend-hint">
                  Отправить код заново можно через {resendCooldown}с
                </span>
              ) : (
                <button
                  type="button"
                  className="auth-form__link auth-form__link--btn"
                  onClick={handleResend}
                  disabled={resending}
                >
                  {resending ? "Отправляем..." : "Отправить код заново"}
                </button>
              )}
            </div>

            <p className="auth-form__footer">
              Не тот email? <Link to="/signupnew" className="auth-form__link">Назад к регистрации</Link>
            </p>
          </form>
        </div>
      </div>
    </div>
  );
}

export default Verify;
