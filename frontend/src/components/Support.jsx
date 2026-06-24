import React, { useState, useEffect, useRef } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import toast from 'react-hot-toast';
import api from '../api';
import { useAuthUser } from '../hooks/useAuthUser';
import '../css/Support.css';

const CHANNEL_ICONS = {
  chat: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="20" height="20">
      <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
    </svg>
  ),
  email: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="20" height="20">
      <rect x="2" y="4" width="20" height="16" rx="2"/>
      <path d="m22 7-8.97 5.7a1.94 1.94 0 0 1-2.06 0L2 7"/>
    </svg>
  ),
  telegram: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="20" height="20">
      <path d="m22 2-7 20-4-9-9-4 20-7z"/><path d="M22 2 11 13"/>
    </svg>
  ),
  faq: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="20" height="20">
      <circle cx="12" cy="12" r="10"/>
      <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/>
      <line x1="12" y1="17" x2="12.01" y2="17"/>
    </svg>
  ),
};

const Support = () => {
  const { t } = useTranslation();
  const currentUser = useAuthUser();
  const isLoggedIn = !!currentUser;

  const faqs = [
    { q: t('support.faq1q'), a: t('support.faq1a') },
    { q: t('support.faq2q'), a: t('support.faq2a') },
    { q: t('support.faq3q'), a: t('support.faq3a') },
    { q: t('support.faq4q'), a: t('support.faq4a') },
  ];

  const SUGGESTIONS = [
    t('support.suggest1'),
    t('support.suggest2'),
    t('support.suggest3'),
    t('support.suggest4'),
  ];

  const channels = [
    { key: 'chat',     icon: CHANNEL_ICONS.chat,     title: t('support.chanChatTitle'), desc: t('support.chanChatDesc') },
    { key: 'email',    icon: CHANNEL_ICONS.email,    title: 'Email',                    desc: 'support@eventhub.kz' },
    { key: 'telegram', icon: CHANNEL_ICONS.telegram, title: 'Telegram',                 desc: '@eventhubkz' },
    { key: 'faq',      icon: CHANNEL_ICONS.faq,      title: 'FAQ',                      desc: t('support.chanFaqDesc') },
  ];

  const WELCOME = t('support.welcome');

  const [messages, setMessages] = useState([
    { role: 'assistant', content: WELCOME },
  ]);
  const [input, setInput] = useState('');
  const [sending, setSending] = useState(false);
  const [escalating, setEscalating] = useState(false);
  const listRef = useRef(null);

  useEffect(() => {
    document.title = t('support.pageTitle');
  }, []);

  useEffect(() => {
    if (listRef.current) {
      listRef.current.scrollTop = listRef.current.scrollHeight;
    }
  }, [messages, sending]);

  const send = async (text) => {
    const trimmed = (text ?? input).trim();
    if (!trimmed || sending) return;
    if (!isLoggedIn) {
      toast.error(t('support.toastLoginChat'));
      return;
    }
    const next = [...messages, { role: 'user', content: trimmed }];
    setMessages(next);
    setInput('');
    setSending(true);
    try {
      const res = await api.post('/api/support/chat', { messages: next });
      const reply = res.data?.reply || t('support.replyFallback');
      setMessages(prev => [...prev, { role: 'assistant', content: reply }]);
    } catch (err) {
      console.error('Ошибка AI-чата', err);
      setMessages(prev => [...prev, {
        role: 'assistant',
        content: t('support.replyError'),
      }]);
    } finally {
      setSending(false);
    }
  };

  const escalate = async () => {
    if (!isLoggedIn) {
      toast.error(t('support.toastLoginSend'));
      return;
    }
    const conversation = messages
      .filter(m => m.content && m.content.trim())
      .map(m => `${m.role === 'user' ? 'Я' : 'ИИ'}: ${m.content}`)
      .join('\n\n');
    if (!conversation || messages.length <= 1) {
      toast.error(t('support.toastWriteFirst'));
      return;
    }
    setEscalating(true);
    try {
      await api.post('/api/support', {
        name: currentUser.sub || 'Пользователь',
        email: currentUser.email || '',
        message: `Чат с ИИ:\n\n${conversation}`,
      });
      toast.success(t('support.toastEscalated'));
      setMessages(prev => [...prev, {
        role: 'assistant',
        content: t('support.escalatedMsg'),
      }]);
    } catch (err) {
      console.error('Ошибка эскалации', err);
      toast.error(t('support.toastEscalateFailed'));
    } finally {
      setEscalating(false);
    }
  };

  const onKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      send();
    }
  };

  const showSuggestions = messages.length === 1;

  return (
    <div className="sp">
      <div className="sp__hdr">
        <div className="sp__eyebrow">{t('support.eyebrow')}</div>
        <h1 className="sp__title">{t('support.title')}</h1>
        <p className="sp__sub">
          {t('support.subtitle')}
        </p>
      </div>

      {/* Channel cards */}
      <div className="sp__channels">
        {channels.map(c => (
          <div key={c.key} className="sp__channel">
            <div className={`sp__channel-icon sp__channel-icon--${c.key}`}>{c.icon}</div>
            <div className="sp__channel-title">{c.title}</div>
            <div className="sp__channel-desc">{c.desc}</div>
          </div>
        ))}
      </div>

      <div className="sp__body">
        {/* Chat */}
        <div className="sp__chat-card">
          <div className="sp__chat-head">
            <div className="sp__chat-head-dot" />
            <div>
              <div className="sp__chat-head-title">{t('support.chatHeadTitle')}</div>
              <div className="sp__chat-head-sub">{t('support.chatHeadSub')}</div>
            </div>
          </div>

          <div className="sp__chat-list" ref={listRef}>
            {messages.map((m, i) => (
              <div key={i} className={`sp__msg sp__msg--${m.role}`}>
                {m.role === 'assistant' && <div className="sp__msg-av">AI</div>}
                <div className="sp__msg-bubble">{m.content}</div>
              </div>
            ))}
            {sending && (
              <div className="sp__msg sp__msg--assistant">
                <div className="sp__msg-av">AI</div>
                <div className="sp__msg-bubble sp__msg-bubble--typing">
                  <span /><span /><span />
                </div>
              </div>
            )}
          </div>

          {showSuggestions && (
            <div className="sp__chat-suggest">
              {SUGGESTIONS.map(s => (
                <button
                  key={s}
                  type="button"
                  className="sp__chat-chip"
                  onClick={() => send(s)}
                  disabled={sending || !isLoggedIn}
                >
                  {s}
                </button>
              ))}
            </div>
          )}

          <div className="sp__chat-input-row">
            <textarea
              className="sp__chat-input"
              rows={1}
              placeholder={isLoggedIn ? t('support.inputPlaceholder') : t('support.inputPlaceholderGuest')}
              value={input}
              onChange={e => setInput(e.target.value)}
              onKeyDown={onKeyDown}
              disabled={!isLoggedIn || sending}
            />
            <button
              type="button"
              className="sp__chat-send"
              onClick={() => send()}
              disabled={!input.trim() || sending || !isLoggedIn}
              aria-label={t('support.sendLabel')}
            >
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" width="18" height="18">
                <path d="M5 12h14M13 5l7 7-7 7"/>
              </svg>
            </button>
          </div>

          <div className="sp__chat-foot">
            {isLoggedIn ? (
              <button
                type="button"
                className="sp__chat-escalate"
                onClick={escalate}
                disabled={escalating || messages.length <= 1}
              >
                {escalating ? t('support.escalating') : t('support.escalate')}
              </button>
            ) : (
              <Link to="/signin" className="sp__chat-escalate">{t('support.signInContinue')}</Link>
            )}
            <div className="sp__chat-hint">{t('support.hint')}</div>
          </div>
        </div>

        {/* FAQ */}
        <div className="sp__faq-card">
          <div className="sp__faq-title">{t('support.faqTitle')}</div>
          <div className="sp__faq-list">
            {faqs.map((item, i) => (
              <div key={i} className="sp__faq-item">
                <div className="sp__faq-q">{item.q}</div>
                <div className="sp__faq-a">{item.a}</div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
};

export default Support;
