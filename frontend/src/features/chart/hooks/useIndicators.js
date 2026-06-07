import { useCallback, useMemo } from 'react';
import useChartConfig from './useChartConfig';

const genId = () => `ind-${crypto.randomUUID()}`;

// Cap on simultaneously configured indicators — keeps the overlay/sub-panel legible and the toolbar readouts
// from overflowing. Exported so the panel can disable "add" and surface the same limit in its message.
export const MAX_INDICATORS = 4;

const TYPE_DEFAULTS = {
  SMA: { period: 20, color: '#c084fc' },
  EMA: { period: 50, color: '#fb923c' },
  RSI: { period: 14, color: '#e91e63' },
  MACD: { period: 12, color: '#06b6d4' },
};

// Rehydrate persisted indicators, repairing any missing OR DUPLICATE id. The old id generator was a
// resettable per-session counter (`ind-1`, `ind-2`, ...), so adding an indicator after a reload could mint
// an id already held by another persisted indicator. Two indicators sharing an id made updateIndicator hit
// BOTH (e.g. changing RSI's color also recolored SMA). Repair ids are deterministic per position so they
// stay stable across renders; the mutators persist them, permanently fixing the collision on the next edit.
function rehydrate(remote) {
  if (!Array.isArray(remote)) return [];
  const seen = new Set();
  return remote.map((ind, i) => {
    let id = ind.id || `ind-pos-${i}`;
    while (seen.has(id)) id = `${id}-${i}`;
    seen.add(id);
    return {
      id,
      type: ind.type,
      period: ind.period,
      color: ind.color,
      visible: !!ind.visible,
    };
  });
}

export default function useIndicators(assetType, assetCode, range, persistEnabled = true) {
  const { config, setField } = useChartConfig(assetType, assetCode, range, persistEnabled);
  const indicators = useMemo(() => rehydrate(config?.indicators), [config?.indicators]);

  // All mutators operate on the rehydrated (deduped-id) list rather than the raw persisted prev, so every
  // edit writes back collision-free ids AND targets exactly one indicator by its current id.
  // Returns { ok } or { ok:false, error } so the panel can show an i18n message. Rejects when the cap is
  // reached, or when an indicator of the SAME type AND period already exists (a same-type-same-period copy is
  // visually indistinguishable, so it is meaningless — color alone doesn't make it a new indicator).
  const addIndicator = useCallback((type, period, color) => {
    const d = TYPE_DEFAULTS[type] || {};
    const resolvedPeriod = period || d.period;
    if (indicators.length >= MAX_INDICATORS) {
      return { ok: false, error: 'max' };
    }
    if (indicators.some((ind) => ind.type === type && Number(ind.period) === Number(resolvedPeriod))) {
      return { ok: false, error: 'duplicate' };
    }
    setField('indicators', () => [
      ...indicators,
      {
        id: genId(),
        type,
        period: resolvedPeriod,
        color: color || d.color,
        visible: true,
      },
    ]);
    return { ok: true };
  }, [setField, indicators]);

  const removeIndicator = useCallback((id) => {
    setField('indicators', () => indicators.filter((ind) => ind.id !== id));
  }, [setField, indicators]);

  const updateIndicator = useCallback((id, updates) => {
    setField('indicators', () => indicators.map((ind) => (ind.id === id ? { ...ind, ...updates } : ind)));
  }, [setField, indicators]);

  const toggleIndicator = useCallback((id) => {
    setField('indicators', () => indicators.map((ind) => (ind.id === id ? { ...ind, visible: !ind.visible } : ind)));
  }, [setField, indicators]);

  return {
    indicators,
    addIndicator,
    removeIndicator,
    updateIndicator,
    toggleIndicator,
  };
}
