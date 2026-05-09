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

function rehydrate(remote) {
  if (!Array.isArray(remote)) return [];
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
  const mutateRef = useRef(updateMutation.mutate);
  mutateRef.current = updateMutation.mutate;

  const [indicators, setIndicators] = useState([]);
  const [config, setConfig] = useState(null);
  const hydratedRef = useRef(false);
  const saveTimerRef = useRef(null);

  useEffect(() => {
    if (!enabled || !isSuccess || hydratedRef.current) return;
    setIndicators(rehydrate(data?.config?.indicators));
    setConfig(data?.config ?? null);
    hydratedRef.current = true;
  }, [enabled, isSuccess, data]);

  useEffect(() => {
    if (!enabled || !hydratedRef.current) return undefined;
    if (saveTimerRef.current) clearTimeout(saveTimerRef.current);
    const next = { ...(config || {}), indicators };
    saveTimerRef.current = setTimeout(() => {
      mutateRef.current(next);
    }, SAVE_DEBOUNCE_MS);
    return () => {
      if (saveTimerRef.current) clearTimeout(saveTimerRef.current);
    };
  }, [enabled, indicators, config]);

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
