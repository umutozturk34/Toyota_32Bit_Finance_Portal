import { useEffect } from 'react';
import i18n from './config';
import { useUserPreferences } from '../hooks/useUserPreferences';

const STORAGE_KEY = 'finance-language';

export function useLanguageSync() {
  const { preferences, hasResolvedPreferences } = useUserPreferences();
  const remote = preferences?.language;

  useEffect(() => {
    if (!hasResolvedPreferences || !remote) return;
    if (i18n.language === remote) return;
    i18n.changeLanguage(remote);
    try {
      localStorage.setItem(STORAGE_KEY, remote);
    } catch {
      /* storage unavailable */
    }
  }, [hasResolvedPreferences, remote]);
}

export default function LanguageSyncBridge() {
  useLanguageSync();
  return null;
}
