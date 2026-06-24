import { useTranslation } from 'react-i18next';
import '../css/LanguageSwitcher.css';

const LANGS = [
  { code: 'ru', label: 'RU' },
  { code: 'kk', label: 'KK' },
];

export default function LanguageSwitcher() {
  const { i18n } = useTranslation();
  const change = (code) => {
    i18n.changeLanguage(code);
    localStorage.setItem('lang', code);
  };
  return (
    <div className="lang-switch">
      {LANGS.map((l) => (
        <button
          key={l.code}
          type="button"
          className={`lang-switch__btn${i18n.resolvedLanguage === l.code ? ' lang-switch__btn--on' : ''}`}
          onClick={() => change(l.code)}
        >
          {l.label}
        </button>
      ))}
    </div>
  );
}
