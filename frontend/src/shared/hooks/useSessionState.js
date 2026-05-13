import { useState, useCallback } from 'react';

export default function useSessionState(key, defaultValue) {
  const k = `ss:${key}`;

  const [value, setValue] = useState(() => {
    try {
      const s = sessionStorage.getItem(k);
      return s !== null ? JSON.parse(s) : defaultValue;
    } catch {
      return defaultValue;
    }
  });

  const set = useCallback((v) => {
    setValue(v);
    try { sessionStorage.setItem(k, JSON.stringify(v)); } catch { /* sessionStorage unavailable */ }
  }, [k]);

  return [value, set];
}
