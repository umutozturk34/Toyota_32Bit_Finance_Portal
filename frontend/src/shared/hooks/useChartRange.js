import { useCallback, useState } from 'react';
import { useUserPreferences } from './useUserPreferences';

const FALLBACK_RANGE = '1M';

export default function useChartRange() {
  const prefs = useUserPreferences();
  const userDefault = prefs.data?.defaultChartRange ?? FALLBACK_RANGE;
  const [override, setOverride] = useState(null);

  const set = useCallback((v) => setOverride(v), []);

  return [override ?? userDefault, set];
}
