import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import ru from './locales/ru.json';
import kk from './locales/kk.json';

const saved = (typeof localStorage !== 'undefined' && localStorage.getItem('lang')) || 'ru';

i18n.use(initReactI18next).init({
  resources: { ru: { translation: ru }, kk: { translation: kk } },
  lng: saved,
  fallbackLng: 'ru',
  interpolation: { escapeValue: false },
});

export default i18n;
