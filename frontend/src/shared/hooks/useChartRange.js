import { useCallback, useState } from 'react';
import { useUserPreferences } from './useUserPreferences';

const FALLBACK_RANGE = '1M';

export default function useChartRange(key) {
  const prefs = useUserPreferences();
  const userDefault = prefs.data?.defaultChartRange ?? FALLBACK_RANGE;
  const storageKey = `ss:${key}`;

  const [override, setOverride] = useState(() => {
    try {
      const stored = sessionStorage.getItem(storageKey);
      return stored !== null ? JSON.parse(stored) : null;
    } catch {
      return null;
    }
  });

  const set = useCallback((v) => {
    setOverride(v);
    try {
      sessionStorage.setItem(storageKey, JSON.stringify(v));
    } catch {
      /* sessionStorage unavailable (e.g., private mode) — fall back to in-memory state */
    }
  }, [storageKey]);

  return [override ?? userDefault, set];
}
