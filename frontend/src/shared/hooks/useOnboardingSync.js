import { useEffect, useRef } from 'react';
import { useUserPreferences, useUpdateUserPreferences } from './useUserPreferences';
import { useAuth } from '../../features/auth/useAuth';
import i18n from '../i18n/config';

const SYNC_FLAG_KEY = 'finance-prefs-bootstrap-synced';

function readLocalTheme() {
  try {
    const stored = localStorage.getItem('finance-theme');
    if (stored === 'light') return 'LIGHT';
    if (stored === 'dark') return 'DARK';
  } catch {
    // localStorage unavailable; fall through
  }
  return null;
}

function normalizeLanguage(lang) {
  if (!lang) return null;
  const short = String(lang).slice(0, 2).toLowerCase();
  return short === 'tr' || short === 'en' ? short : null;
}

function alreadySynced() {
  try { return localStorage.getItem(SYNC_FLAG_KEY) === '1'; } catch { return false; }
}

function markSynced() {
  try { localStorage.setItem(SYNC_FLAG_KEY, '1'); } catch { /* noop */ }
}

export default function useOnboardingSync() {
  const { isAuthenticated, loading: authLoading } = useAuth();
  const { preferences, isFetched } = useUserPreferences();
  const updatePreferences = useUpdateUserPreferences();
  const firedRef = useRef(false);

  useEffect(() => {
    if (firedRef.current) return;
    if (authLoading || !isAuthenticated || !isFetched) return;
    if (alreadySynced()) {
      firedRef.current = true;
      return;
    }
    const theme = readLocalTheme();
    const language = normalizeLanguage(i18n.language);
    const themeDiffers = theme && preferences?.theme !== theme;
    const languageDiffers = language && preferences?.language !== language;
    if (!themeDiffers && !languageDiffers) {
      firedRef.current = true;
      markSynced();
      return;
    }
    firedRef.current = true;
    updatePreferences.mutate({
      ...(themeDiffers ? { theme } : {}),
      ...(languageDiffers ? { language } : {}),
    }, {
      onSuccess: markSynced,
    });
  }, [authLoading, isAuthenticated, isFetched, preferences, updatePreferences]);
}
