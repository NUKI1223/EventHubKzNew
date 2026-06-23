import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import api from '../api';
import { contactsFromForm, formFromContacts } from '../config/socials';
import TagSelector from './TagSelector';
import '../css/EditProfile.css';

const EditProfile = () => {
  const { t } = useTranslation();
  const [formData, setFormData] = useState({
    fullName: '',
    email: '',
    description: '',
    telegram: '',
    github: '',
    instagram: '',
    facebook: '',
  });
  const [tags, setTags] = useState([]);
  const [userId, setUserId] = useState(null);
  const [originalData, setOriginalData] = useState(null);
  const [originalTags, setOriginalTags] = useState([]);
  const [file, setFile] = useState(null);
  const [fileName, setFileName] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [successMessage, setSuccessMessage] = useState('');
  const navigate = useNavigate();

  useEffect(() => {
    document.title = t('profile.pageTitle');
  }, []);

  useEffect(() => {
    const fetchUserData = async () => {
      try {
        const res = await api.get('/api/users/me');
        const userData = {
          fullName: res.data.username || '',
          email: res.data.email || '',
          description: res.data.description || '',
          ...formFromContacts(res.data.contacts),
        };
        setFormData(userData);
        setOriginalData(userData);
        setUserId(res.data.id);
        if (res.data.id) localStorage.setItem('userId', String(res.data.id));
        const userTags = Array.isArray(res.data.tags) ? res.data.tags : [];
        setTags(userTags);
        setOriginalTags(userTags);
      } catch (err) {
          setError(t('profile.errorLoadData'));
      }
    };
    fetchUserData();
  }, []);

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData(prev => ({ ...prev, [name]: value }));
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
    setSuccessMessage('');

    try {
      const updatedUser = {
        username: formData.fullName,
        email: formData.email,
        description: formData.description,
        contacts: contactsFromForm(formData),
        tags,
      };

      const tagsChanged = JSON.stringify([...tags].sort()) !== JSON.stringify([...originalTags].sort());
      const hasChanges = JSON.stringify({ ...updatedUser, tags: undefined }) !== JSON.stringify({
        username: originalData.fullName,
        email: originalData.email,
        description: originalData.description,
        contacts: contactsFromForm(originalData),
        tags: undefined,
      }) || tagsChanged || file !== null;

      if (!hasChanges) {
        setSuccessMessage(t('profile.noChanges'));
        setLoading(false);
        setTimeout(() => navigate('/profile'), 1000);
        return;
      }

      if (!userId) {
        setError(t('profile.errorNoUser'));
        setLoading(false);
        return;
      }

      if (file) {
        const uploadData = new FormData();
        uploadData.append('file', file);
        const uploadRes = await api.post(`/api/files/users/${userId}/avatar`, uploadData, {
          headers: { 'Content-Type': 'multipart/form-data' },
        });
        updatedUser.avatarUrl = uploadRes.data.fileUrl;
      }

      await api.put(`/api/users/${userId}`, updatedUser);

      setSuccessMessage(t('profile.saveSuccess'));
      setTimeout(() => navigate('/profile'), 1000);
    } catch (err) {
      setError(
        err.response
          ? t('profile.errorPrefix', { message: err.response.data.message || err.response.statusText })
          : t('profile.errorNoResponse')
      );
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="ep">
      <button className="ep__back" onClick={() => navigate('/profile')}>
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="14" height="14">
          <path d="M19 12H5M12 5l-7 7 7 7"/>
        </svg>
        {t('profile.backToProfile')}
      </button>

      <h1 className="ep__title">{t('profile.editTitle')}</h1>

      <form className="ep__form" onSubmit={handleSubmit}>
        {/* Personal info */}
        <div className="ep__section">
          <div className="ep__section-title">{t('profile.sectionPersonal')}</div>

          <div className="ep__field">
            <label className="ep__label">{t('profile.labelUsername')}</label>
            <input
              className="ep__input"
              type="text"
              name="fullName"
              value={formData.fullName}
              onChange={handleChange}
              placeholder={t('profile.placeholderName')}
              required
            />
          </div>

          <div className="ep__field">
            <label className="ep__label">{t('profile.labelEmail')}</label>
            <input
              className="ep__input"
              type="email"
              name="email"
              value={formData.email}
              onChange={handleChange}
              placeholder="you@email.com"
              required
            />
          </div>

          <div className="ep__field">
            <label className="ep__label">{t('profile.labelBio')}</label>
            <textarea
              className="ep__textarea"
              name="description"
              value={formData.description}
              onChange={handleChange}
              placeholder={t('profile.placeholderBio')}
            />
          </div>
        </div>

        {/* Tags */}
        <div className="ep__section">
          <div className="ep__section-title">{t('profile.sectionTags')}</div>
          <div className="ep__field">
            <label className="ep__label">{t('profile.tagsLabel')}</label>
            <TagSelector selectedTags={tags} onChange={setTags} type="USER" />
          </div>
        </div>

        {/* Social links */}
        <div className="ep__section">
          <div className="ep__section-title">{t('profile.sectionSocials')}</div>

          {[
            { name: 'github', label: 'GitHub', prefix: 'github.com/' },
            { name: 'telegram', label: 'Telegram', prefix: 't.me/' },
            { name: 'instagram', label: 'Instagram', prefix: 'instagram.com/' },
            { name: 'facebook', label: 'Facebook', prefix: 'facebook.com/' },
          ].map(({ name, label, prefix }) => (
            <div className="ep__field" key={name}>
              <label className="ep__label">{label}</label>
              <div className="ep__input-row">
                <span className="ep__prefix">{prefix}</span>
                <input
                  className="ep__input"
                  type="text"
                  name={name}
                  value={formData[name]}
                  onChange={handleChange}
                  placeholder="username"
                />
              </div>
            </div>
          ))}
        </div>

        {/* Avatar upload */}
        <div className="ep__section">
          <div className="ep__section-title">{t('profile.sectionAvatar')}</div>
          <div className="ep__field">
            <label className="ep__label">{t('profile.uploadPhoto')}</label>
            <label className="ep__file-label">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="18" height="18">
                <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
                <polyline points="17 8 12 3 7 8"/>
                <line x1="12" y1="3" x2="12" y2="15"/>
              </svg>
              {fileName || t('profile.chooseImage')}
              <input type="file" onChange={handleFileChange} accept="image/*" />
            </label>
          </div>
        </div>

        {error && <div className="ep__error">{error}</div>}
        {successMessage && <div className="ep__success">{successMessage}</div>}

        <div>
          <button type="submit" className="ep__save-btn" disabled={loading}>
            {loading ? t('profile.saving') : t('profile.saveChanges')}
          </button>
        </div>
      </form>
    </div>
  );
};

export default EditProfile;
