import React, { useState, useEffect, useRef } from "react";
import { useNavigate, useLocation, Link } from "react-router-dom";
import { useTranslation, Trans } from 'react-i18next';
import api from "../api";
import "../css/SignIn.css";

const CODE_LENGTH = 6;

function Verify() {
  const navigate = useNavigate();
  const location = useLocation();
  const { t } = useTranslation();
  const email = location.state?.email || "";

  const [digits, setDigits] = useState(Array(CODE_LENGTH).fill(""));
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const [resending, setResending] = useState(false);
  const [resendCooldown, setResendCooldown] = useState(0);
  const inputsRef = useRef([]);

  useEffect(() => {
    document.title = t('auth.verifyTitle');
    if (!email) {
      navigate("/signupnew", { replace: true });
      return;
    }
    inputsRef.current[0]?.focus();
  }, [email, navigate, t]);

  useEffect(() => {
    if (resendCooldown <= 0) return;
    const timer = setTimeout(() => setResendCooldown(c => c - 1), 1000);
    return () => clearTimeout(timer);
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
      setError(t('auth.errorInvalidCode'));
      return;
    }
    setLoading(true);
    setError("");
    try {
      await api.post("/auth/verify", { email, verificationCode: code });
      navigate("/signin", {
        state: { notice: t('auth.accountVerified') },
      });
    } catch (err) {
      const msg = err.response?.data?.error || err.response?.data?.message || t('auth.errorExpiredCode');
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
      const msg = err.response?.data?.error || err.response?.data?.message || t('auth.errorResendFailed');
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
            {t('auth.illustVerifyTagline').split('\n').map((line, i, arr) =>
              i === arr.length - 1 ? <em key={i}>{line}</em> : <React.Fragment key={i}>{line}<br /></React.Fragment>
            )}
          </h2>
          <div className="auth-page__illus-stats">
            {[["1200+", t('auth.statEvents')],["60 тыс.", t('auth.statParticipants')],["480", t('auth.statOrganizers')]].map(([n,l]) => (
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
            <span className="auth-page__step-label">{t('auth.step2of2')}</span>
          </div>

          <h1 className="auth-page__title">{t('auth.verifyHeading')}</h1>
          <p className="auth-page__sub">
            <Trans i18nKey="auth.verifySub" values={{ email }} components={{ bold: <strong /> }} />
          </p>

          <form className="auth-form" onSubmit={handleSubmit}>
            <div className="auth-form__field">
              <label className="auth-form__label">{t('auth.labelCodeFromEmail')}</label>
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
              {loading ? t('auth.btnConfirmLoading') : t('auth.btnConfirm')}
            </button>

            <div className="auth-form__resend">
              {resendCooldown > 0 ? (
                <span className="auth-form__resend-hint">
                  {t('auth.resendCooldown', { count: resendCooldown })}
                </span>
              ) : (
                <button
                  type="button"
                  className="auth-form__link auth-form__link--btn"
                  onClick={handleResend}
                  disabled={resending}
                >
                  {resending ? t('auth.btnResendLoading') : t('auth.btnResend')}
                </button>
              )}
            </div>

            <p className="auth-form__footer">
              {t('auth.wrongEmail')} <Link to="/signupnew" className="auth-form__link">{t('auth.wrongEmailLink')}</Link>
            </p>
          </form>
        </div>
      </div>
    </div>
  );
}

export default Verify;
