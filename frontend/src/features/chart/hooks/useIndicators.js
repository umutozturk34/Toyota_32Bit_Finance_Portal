import { useCallback, useMemo } from 'react';
import useChartConfig from './useChartConfig';

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
  const { config, setField } = useChartConfig(assetType, assetCode);
  const indicators = useMemo(() => rehydrate(config?.indicators), [config?.indicators]);

  const addIndicator = useCallback((type, period, color) => {
    const d = TYPE_DEFAULTS[type] || {};
    setField('indicators', (prev) => [
      ...(Array.isArray(prev) ? prev : []),
      {
        id: genId(),
        type,
        period: period || d.period,
        color: color || d.color,
        visible: true,
      },
    ]);
  }, [setField]);

  const removeIndicator = useCallback((id) => {
    setField('indicators', (prev) => (Array.isArray(prev) ? prev : []).filter((ind) => ind.id !== id));
  }, [setField]);

  const updateIndicator = useCallback((id, updates) => {
    setField('indicators', (prev) =>
      (Array.isArray(prev) ? prev : []).map((ind) => (ind.id === id ? { ...ind, ...updates } : ind)),
    );
  }, [setField]);

  const toggleIndicator = useCallback((id) => {
    setField('indicators', (prev) =>
      (Array.isArray(prev) ? prev : []).map((ind) => (ind.id === id ? { ...ind, visible: !ind.visible } : ind)),
    );
  }, [setField]);

  return {
    indicators,
    addIndicator,
    removeIndicator,
    updateIndicator,
    toggleIndicator,
  };
}
