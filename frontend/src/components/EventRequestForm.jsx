import React, { useState, useEffect } from 'react';
import api from '../api';
import toast from 'react-hot-toast';
import TagSelector from './TagSelector';
import QuestionEditor from './QuestionEditor';
import { useTranslation } from 'react-i18next';
import '../css/EventRequestForm.css';

const EventRequestForm = () => {
  const { t } = useTranslation();

  useEffect(() => {
    document.title = t('requestForm.pageTitle');
  }, [t]);

  const [formData, setFormData] = useState({
    title: '',
    shortDescription: '',
    fullDescription: '',
    location: '',
    online: false,
    eventDate: '',
    registrationDeadline: '',
    registrationType: 'NATIVE',
    externalLink: '',
    contactEmail: '',
  });
  const [selectedTags, setSelectedTags] = useState([]);
  const [questions, setQuestions] = useState([]);
  const [file, setFile] = useState(null);
  const [fileName, setFileName] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [suggesting, setSuggesting] = useState(false);

  const handleChange = (e) => {
    const { name, value, type, checked } = e.target;
    setFormData(prev => ({
      ...prev,
      [name]: type === 'checkbox' ? checked : value,
    }));
  };

  const handleSuggestTags = async () => {
    if (!formData.title.trim() && !formData.shortDescription.trim() && !formData.fullDescription.trim()) {
      toast.error(t('requestForm.suggestTagsEmpty'));
      return;
    }
    setSuggesting(true);
    try {
      const res = await api.post('/api/events/suggest-tags', {
        title: formData.title,
        shortDescription: formData.shortDescription,
        fullDescription: formData.fullDescription,
      });
      const suggested = Array.isArray(res.data?.tags) ? res.data.tags : [];
      if (suggested.length === 0) {
        toast.error(t('requestForm.suggestTagsFailed'));
        return;
      }
      setSelectedTags(prev => {
        const set = new Set(prev);
        suggested.forEach(tag => set.add(tag));
        return Array.from(set);
      });
      toast.success(t('requestForm.suggestTagsAdded', { tags: suggested.join(', ') }));
    } catch (err) {
      console.error('Ошибка подбора тегов', err);
      toast.error(t('requestForm.suggestTagsError'));
    } finally {
      setSuggesting(false);
    }
  };

  const handleFileChange = (e) => {
    const f = e.target.files[0];
    setFile(f);
    setFileName(f ? f.name : '');
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    setError(null);
    try {
      const isExternal = formData.registrationType === 'EXTERNAL';
      let externalLink = formData.externalLink ? formData.externalLink.trim() : '';
      if (isExternal) {
        if (!externalLink) {
          setError(t('requestForm.errorExternalLinkRequired'));
          setLoading(false);
          return;
        }
        if (!/^https?:\/\//i.test(externalLink)) {
          externalLink = 'https://' + externalLink;
        }
        const URL_RE = /^https?:\/\/[A-Za-z0-9.\-]+\.[A-Za-z]{2,}(?:[:/?#][^\s]*)?$/;
        if (!URL_RE.test(externalLink)) {
          setError(t('requestForm.errorExternalLinkInvalid'));
          setLoading(false);
          return;
        }
      } else {
        externalLink = '';
      }

      let mainImageUrl = null;
      if (file) {
        const uploadData = new FormData();
        uploadData.append('file', file);
        const uploadRes = await api.post('/api/files/upload?folder=events', uploadData, {
          headers: { 'Content-Type': 'multipart/form-data' },
        });
        mainImageUrl = uploadRes.data.fileUrl;
      }

      const cleanQuestions = isExternal ? [] : questions
        .map(q => ({ ...q, label: q.label.trim(), options: (q.options || []).map(o => o.trim()).filter(Boolean) }))
        .filter(q => q.label && (q.type !== 'SINGLE' || q.options.length >= 2));

      await api.post('/api/event-requests', {
        title: formData.title,
        shortDescription: formData.shortDescription,
        fullDescription: formData.fullDescription,
        tags: selectedTags,
        location: formData.location,
        online: formData.online,
        eventDate: formData.eventDate,
        registrationDeadline: formData.registrationDeadline,
        registrationType: formData.registrationType,
        externalLink,
        contactEmail: formData.contactEmail,
        mainImageUrl,
        questions: cleanQuestions,
      });

      toast.success(t('requestForm.submitSuccess'));

      setFormData({
        title: '',
        shortDescription: '',
        fullDescription: '',
        location: '',
        online: false,
        eventDate: '',
        registrationDeadline: '',
        registrationType: 'NATIVE',
        externalLink: '',
        contactEmail: '',
      });
      setSelectedTags([]);
      setQuestions([]);
      setFile(null);
      setFileName('');
    } catch (err) {
      console.error(err);
      toast.error(t('requestForm.submitError'));
      setError(t('requestForm.submitErrorDetail'));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="erf">
      <div className="erf__hdr">
        <div className="erf__eyebrow">{t('requestForm.eyebrow')}</div>
        <h1 className="erf__title">{t('requestForm.title')}</h1>
        <p className="erf__sub">{t('requestForm.subtitle')}</p>
      </div>

      <form className="erf__form" onSubmit={handleSubmit}>
        {/* Основное */}
        <div className="erf__section">
          <div className="erf__section-title">{t('requestForm.sectionBasic')}</div>

          <div className="erf__field">
            <label className="erf__label">{t('requestForm.labelTitle')}</label>
            <input
              className="erf__input"
              type="text"
              name="title"
              value={formData.title}
              onChange={handleChange}
              placeholder={t('requestForm.placeholderTitle')}
              required
            />
          </div>

          <div className="erf__field">
            <label className="erf__label">{t('requestForm.labelShortDesc')}</label>
            <textarea
              className="erf__textarea"
              name="shortDescription"
              value={formData.shortDescription}
              onChange={handleChange}
              placeholder={t('requestForm.placeholderShortDesc')}
              required
            />
          </div>

          <div className="erf__field">
            <label className="erf__label">{t('requestForm.labelFullDesc')}</label>
            <textarea
              className="erf__textarea"
              style={{ minHeight: 140 }}
              name="fullDescription"
              value={formData.fullDescription}
              onChange={handleChange}
              placeholder={t('requestForm.placeholderFullDesc')}
              required
            />
          </div>
        </div>

        {/* Место и формат */}
        <div className="erf__section">
          <div className="erf__section-title">{t('requestForm.sectionVenue')}</div>

          <div className="erf__field">
            <label className="erf__label">{t('requestForm.labelLocation')}</label>
            <input
              className="erf__input"
              type="text"
              name="location"
              value={formData.location}
              onChange={handleChange}
              placeholder={t('requestForm.placeholderLocation')}
              required
            />
          </div>

          <div className="erf__field">
            <label className="erf__label">{t('requestForm.labelFormat')}</label>
            <label className="erf__toggle-wrap">
              <input
                className="erf__toggle-input"
                type="checkbox"
                name="online"
                checked={formData.online}
                onChange={handleChange}
              />
              <span className="erf__toggle-label">
                {formData.online ? t('requestForm.formatOnline') : t('requestForm.formatOffline')} — {t('requestForm.formatToggleHint')}
              </span>
            </label>
          </div>
        </div>

        {/* Даты */}
        <div className="erf__section">
          <div className="erf__section-title">{t('requestForm.sectionDates')}</div>

          <div className="erf__two-col">
            <div className="erf__field">
              <label className="erf__label">{t('requestForm.labelEventDate')}</label>
              <input
                className="erf__input"
                type="datetime-local"
                name="eventDate"
                value={formData.eventDate}
                onChange={handleChange}
                required
              />
            </div>
            <div className="erf__field">
              <label className="erf__label">{t('requestForm.labelDeadline')}</label>
              <input
                className="erf__input"
                type="datetime-local"
                name="registrationDeadline"
                value={formData.registrationDeadline}
                onChange={handleChange}
                required
              />
            </div>
          </div>
        </div>

        {/* Теги */}
        <div className="erf__section">
          <div className="erf__section-title-row">
            <div className="erf__section-title">{t('requestForm.sectionTags')}</div>
            <button
              type="button"
              className="erf__ai-btn"
              onClick={handleSuggestTags}
              disabled={suggesting}
            >
              {suggesting ? t('requestForm.suggestTagsBusy') : t('requestForm.suggestTagsBtn')}
            </button>
          </div>
          <TagSelector selectedTags={selectedTags} onChange={setSelectedTags} />
        </div>

        {/* Регистрация */}
        <div className="erf__section">
          <div className="erf__section-title">{t('requestForm.sectionRegistration')}</div>

          <div className="erf__field">
            <div className="erf__regtype">
              <label className={`erf__regtype-opt ${formData.registrationType === 'NATIVE' ? 'erf__regtype-opt--on' : ''}`}>
                <input
                  type="radio"
                  name="registrationType"
                  value="NATIVE"
                  checked={formData.registrationType === 'NATIVE'}
                  onChange={handleChange}
                />
                <span className="erf__regtype-title">{t('requestForm.regTypeNativeTitle')}</span>
                <span className="erf__regtype-desc">{t('requestForm.regTypeNativeDesc')}</span>
              </label>
              <label className={`erf__regtype-opt ${formData.registrationType === 'EXTERNAL' ? 'erf__regtype-opt--on' : ''}`}>
                <input
                  type="radio"
                  name="registrationType"
                  value="EXTERNAL"
                  checked={formData.registrationType === 'EXTERNAL'}
                  onChange={handleChange}
                />
                <span className="erf__regtype-title">{t('requestForm.regTypeExternalTitle')}</span>
                <span className="erf__regtype-desc">{t('requestForm.regTypeExternalDesc')}</span>
              </label>
            </div>
          </div>

          {formData.registrationType === 'EXTERNAL' && (
            <div className="erf__field">
              <label className="erf__label">
                {t('requestForm.labelExternalLink')} <span className="erf__required">*</span>
              </label>
              <input
                className="erf__input"
                type="url"
                name="externalLink"
                value={formData.externalLink}
                onChange={handleChange}
                placeholder="https://example.com/event"
                required
                pattern="^https?://[A-Za-z0-9.\-]+\.[A-Za-z]{2,}(?:[:/?#].*)?$"
                title={t('requestForm.errorExternalLinkInvalid')}
              />
            </div>
          )}
        </div>

        {/* Дополнительно */}
        <div className="erf__section">
          <div className="erf__section-title">{t('requestForm.sectionExtra')}</div>

          <div className="erf__field">
            <label className="erf__label">
              {t('requestForm.labelContactEmail')} <span className="erf__required">*</span>
              <span className="erf__hint"> — {t('requestForm.contactEmailHint')}</span>
            </label>
            <input
              className="erf__input"
              type="email"
              name="contactEmail"
              value={formData.contactEmail}
              onChange={handleChange}
              placeholder="organizer@email.com"
              required
            />
          </div>

          <div className="erf__field">
            <label className="erf__label">{t('requestForm.labelCover')}</label>
            <label className="erf__file-label">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="20" height="20">
                <rect x="3" y="3" width="18" height="18" rx="3"/>
                <circle cx="8.5" cy="8.5" r="1.5"/>
                <path d="m21 15-5-5L5 21"/>
              </svg>
              <span>{fileName || t('requestForm.uploadImage')}</span>
              <span className="erf__file-hint">{t('requestForm.uploadHint')}</span>
              <input type="file" onChange={handleFileChange} accept="image/*" />
            </label>
          </div>
        </div>

        {formData.registrationType === 'NATIVE' && (
          <div className="erf__section">
            <div className="erf__section-title">{t('requestForm.sectionQuestions')}</div>
            <p className="erf__sub" style={{ margin: '0 0 10px' }}>
              {t('requestForm.questionsHint')}
            </p>
            <QuestionEditor questions={questions} onChange={setQuestions} />
          </div>
        )}

        {error && <div className="erf__error">{error}</div>}

        <div className="erf__footer">
          <button type="submit" className="erf__submit" disabled={loading}>
            {loading ? t('requestForm.submitting') : t('requestForm.submitBtn')}
          </button>
        </div>
      </form>
    </div>
  );
};

export default EventRequestForm;
