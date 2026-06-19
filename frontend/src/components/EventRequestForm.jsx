import React, { useState, useEffect } from 'react';
import api from '../api';
import toast from 'react-hot-toast';
import TagSelector from './TagSelector';
import QuestionEditor from './QuestionEditor';
import '../css/EventRequestForm.css';

const EventRequestForm = () => {
  useEffect(() => {
    document.title = 'Создать событие — EventHub.kz';
  }, []);

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
      toast.error('Заполни название и описание — ИИ подберёт теги');
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
        toast.error('ИИ не смог подобрать теги. Заполни описание подробнее или проверь подключение.');
        return;
      }
      setSelectedTags(prev => {
        const set = new Set(prev);
        suggested.forEach(t => set.add(t));
        return Array.from(set);
      });
      toast.success(`Добавлено: ${suggested.join(', ')}`);
    } catch (err) {
      console.error('Ошибка подбора тегов', err);
      toast.error('Не удалось получить подсказку');
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
          setError('Укажите ссылку на сайт мероприятия');
          setLoading(false);
          return;
        }
        if (!/^https?:\/\//i.test(externalLink)) {
          externalLink = 'https://' + externalLink;
        }
        const URL_RE = /^https?:\/\/[A-Za-z0-9.\-]+\.[A-Za-z]{2,}(?:[:/?#][^\s]*)?$/;
        if (!URL_RE.test(externalLink)) {
          setError('Введите корректный URL вида https://example.com');
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

      toast.success('Заявка успешно отправлена!');

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
      toast.error('Ошибка при отправке заявки');
      setError('Ошибка при отправке. Проверьте данные и попробуйте снова.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="erf">
      <div className="erf__hdr">
        <div className="erf__eyebrow">Для организаторов</div>
        <h1 className="erf__title">Создать событие</h1>
        <p className="erf__sub">Заполните форму — мы проверим и опубликуем ваше мероприятие в течение 1–2 дней.</p>
      </div>

      <form className="erf__form" onSubmit={handleSubmit}>
        {/* Основное */}
        <div className="erf__section">
          <div className="erf__section-title">Основное</div>

          <div className="erf__field">
            <label className="erf__label">Название события</label>
            <input
              className="erf__input"
              type="text"
              name="title"
              value={formData.title}
              onChange={handleChange}
              placeholder="Например: Almaty Spring Hackathon 2026"
              required
            />
          </div>

          <div className="erf__field">
            <label className="erf__label">Краткое описание</label>
            <textarea
              className="erf__textarea"
              name="shortDescription"
              value={formData.shortDescription}
              onChange={handleChange}
              placeholder="1–2 предложения для карточки события"
              required
            />
          </div>

          <div className="erf__field">
            <label className="erf__label">Полное описание</label>
            <textarea
              className="erf__textarea"
              style={{ minHeight: 140 }}
              name="fullDescription"
              value={formData.fullDescription}
              onChange={handleChange}
              placeholder="Подробная программа, условия участия, призы..."
              required
            />
          </div>
        </div>

        {/* Место и формат */}
        <div className="erf__section">
          <div className="erf__section-title">Место и формат</div>

          <div className="erf__field">
            <label className="erf__label">Место проведения</label>
            <input
              className="erf__input"
              type="text"
              name="location"
              value={formData.location}
              onChange={handleChange}
              placeholder="Город, адрес или площадка"
              required
            />
          </div>

          <div className="erf__field">
            <label className="erf__label">Формат</label>
            <label className="erf__toggle-wrap">
              <input
                className="erf__toggle-input"
                type="checkbox"
                name="online"
                checked={formData.online}
                onChange={handleChange}
              />
              <span className="erf__toggle-label">
                {formData.online ? 'Онлайн' : 'Офлайн'} — нажмите для переключения
              </span>
            </label>
          </div>
        </div>

        {/* Даты */}
        <div className="erf__section">
          <div className="erf__section-title">Даты</div>

          <div className="erf__two-col">
            <div className="erf__field">
              <label className="erf__label">Дата проведения</label>
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
              <label className="erf__label">Дедлайн регистрации</label>
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
            <div className="erf__section-title">Теги</div>
            <button
              type="button"
              className="erf__ai-btn"
              onClick={handleSuggestTags}
              disabled={suggesting}
            >
              {suggesting ? 'Подбираю…' : '✨ Подобрать тегами ИИ'}
            </button>
          </div>
          <TagSelector selectedTags={selectedTags} onChange={setSelectedTags} />
        </div>

        {/* Регистрация */}
        <div className="erf__section">
          <div className="erf__section-title">Регистрация</div>

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
                <span className="erf__regtype-title">На нашем сайте</span>
                <span className="erf__regtype-desc">Участники записываются здесь — вы получаете список, QR-билеты и ответы на вопросы.</span>
              </label>
              <label className={`erf__regtype-opt ${formData.registrationType === 'EXTERNAL' ? 'erf__regtype-opt--on' : ''}`}>
                <input
                  type="radio"
                  name="registrationType"
                  value="EXTERNAL"
                  checked={formData.registrationType === 'EXTERNAL'}
                  onChange={handleChange}
                />
                <span className="erf__regtype-title">По внешней ссылке</span>
                <span className="erf__regtype-desc">Регистрация на вашем сайте — мы просто покажем кнопку-ссылку.</span>
              </label>
            </div>
          </div>

          {formData.registrationType === 'EXTERNAL' && (
            <div className="erf__field">
              <label className="erf__label">
                Ссылка на сайт мероприятия <span className="erf__required">*</span>
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
                title="Введите корректный URL вида https://example.com"
              />
            </div>
          )}
        </div>

        {/* Дополнительно */}
        <div className="erf__section">
          <div className="erf__section-title">Дополнительно</div>

          <div className="erf__field">
            <label className="erf__label">
              Email для связи <span className="erf__required">*</span>
              <span className="erf__hint"> — будет показан как контакт организатора на странице события</span>
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
            <label className="erf__label">Обложка события</label>
            <label className="erf__file-label">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="20" height="20">
                <rect x="3" y="3" width="18" height="18" rx="3"/>
                <circle cx="8.5" cy="8.5" r="1.5"/>
                <path d="m21 15-5-5L5 21"/>
              </svg>
              <span>{fileName || 'Загрузить изображение'}</span>
              <span className="erf__file-hint">PNG, JPG до 5 МБ</span>
              <input type="file" onChange={handleFileChange} accept="image/*" />
            </label>
          </div>
        </div>

        {formData.registrationType === 'NATIVE' && (
          <div className="erf__section">
            <div className="erf__section-title">Вопросы к участникам (необязательно)</div>
            <p className="erf__sub" style={{ margin: '0 0 10px' }}>
              Участник ответит на них при регистрации на нашем сайте.
            </p>
            <QuestionEditor questions={questions} onChange={setQuestions} />
          </div>
        )}

        {error && <div className="erf__error">{error}</div>}

        <div className="erf__footer">
          <button type="submit" className="erf__submit" disabled={loading}>
            {loading ? 'Отправка...' : 'Отправить заявку →'}
          </button>
        </div>
      </form>
    </div>
  );
};

export default EventRequestForm;
