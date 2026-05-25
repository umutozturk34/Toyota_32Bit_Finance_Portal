import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import LanguageDetector from 'i18next-browser-languagedetector';
import tr from './tr.json';
import en from './en.json';

const STORAGE_KEY = 'finance-language';

function persistLanguage(lang) {
  try {
    localStorage.setItem(STORAGE_KEY, lang);
    document.cookie = `${STORAGE_KEY}=${lang};path=/;max-age=31536000;SameSite=Lax`;
    document.cookie = `KEYCLOAK_LOCALE=${lang};path=/;max-age=31536000;SameSite=Lax`;
  } catch { void 0; }
}

i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources: {
      tr: { translation: tr },
      en: { translation: en },
    },
    fallbackLng: 'en',
    supportedLngs: ['tr', 'en'],
    interpolation: { escapeValue: false },
    detection: {
      order: ['cookie', 'localStorage'],
      lookupCookie: STORAGE_KEY,
      lookupLocalStorage: STORAGE_KEY,
      caches: ['localStorage'],
    },
    returnNull: false,
  });

i18n.on('languageChanged', persistLanguage);

export default i18n;
