import { useCallback, useEffect, useRef, useState } from 'react';
import { useUserChartPreferences, useUpdateUserChartPreferences } from '../../../shared/hooks/useUserChartPreferences';

const SAVE_DEBOUNCE_MS = 600;

let nextId = 1;
const genId = () => `ind-${nextId++}`;

const TYPE_DEFAULTS = {
  SMA: { period: 20, color: '#c084fc' },
  EMA: { period: 50, color: '#fb923c' },
  RSI: { period: 14, color: '#e91e63' },
  MACD: { period: 12, color: '#06b6d4' },
};

const FALLBACK_INDICATORS = [
  { id: genId(), type: 'SMA', period: 20, color: '#c084fc', visible: false },
  { id: genId(), type: 'EMA', period: 50, color: '#fb923c', visible: false },
];

function rehydrate(remote) {
  if (!Array.isArray(remote)) return null;
  return remote.map((ind) => ({
    id: ind.id || genId(),
    type: ind.type,
    period: ind.period,
    color: ind.color,
    visible: !!ind.visible,
  }));
}

export default function useIndicators(assetType, assetCode) {
  const enabled = !!assetType && !!assetCode;
  const { data, isSuccess } = useUserChartPreferences(assetType, assetCode);
  const updateMutation = useUpdateUserChartPreferences(assetType, assetCode);

  const [indicators, setIndicators] = useState(FALLBACK_INDICATORS);
  const hydratedRef = useRef(false);
  const saveTimerRef = useRef(null);

  useEffect(() => {
    if (!enabled || !isSuccess) return;
    const remote = rehydrate(data?.config?.indicators);
    setIndicators(remote && remote.length > 0 ? remote : FALLBACK_INDICATORS);
    hydratedRef.current = true;
    return undefined;
  }, [enabled, isSuccess, data]);

  useEffect(() => {
    if (!enabled || !hydratedRef.current) return undefined;
    if (saveTimerRef.current) clearTimeout(saveTimerRef.current);
    saveTimerRef.current = setTimeout(() => {
      updateMutation.mutate({ indicators });
    }, SAVE_DEBOUNCE_MS);
    return () => {
      if (saveTimerRef.current) clearTimeout(saveTimerRef.current);
    };
  }, [enabled, indicators, updateMutation]);

  const addIndicator = useCallback((type, period, color) => {
    const d = TYPE_DEFAULTS[type] || {};
    setIndicators((prev) => [
      ...prev,
      {
        id: genId(),
        type,
        period: period || d.period,
        color: color || d.color,
        visible: true,
      },
    ]);
  }, []);

  const removeIndicator = useCallback((id) => {
    setIndicators((prev) => prev.filter((ind) => ind.id !== id));
  }, []);

  const updateIndicator = useCallback((id, updates) => {
    setIndicators((prev) => prev.map((ind) => (ind.id === id ? { ...ind, ...updates } : ind)));
  }, []);

  const toggleIndicator = useCallback((id) => {
    setIndicators((prev) => prev.map((ind) => (ind.id === id ? { ...ind, visible: !ind.visible } : ind)));
  }, []);

  return {
    indicators,
    addIndicator,
    removeIndicator,
    updateIndicator,
    toggleIndicator,
  };
}
