import { useEffect } from 'react';
import i18n from './config';
import { useUserPreferences } from '../hooks/useUserPreferences';

export function useLanguageSync() {
  const { preferences, hasResolvedPreferences } = useUserPreferences();
  const remote = preferences?.language;

  useEffect(() => {
    if (!hasResolvedPreferences || !remote) return;
    if (i18n.language === remote) return;
    i18n.changeLanguage(remote);
  }, [hasResolvedPreferences, remote]);
}

export default function LanguageSyncBridge() {
  useLanguageSync();
  return null;
}
