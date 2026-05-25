import { useCallback, useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { unifiedMarketService } from '../services/unifiedMarketService';
import useAppStore from '../stores/useAppStore';
import { useExchangeRates } from './useExchangeRates';
import { STALE, GC } from '../constants/query';

const SUPPORTED = ['TRY', 'USD', 'EUR'];

function buildSeries(points) {
  return (points || [])
    .map((p) => {
      const date = String(p.candleDate || p.date || '').slice(0, 10);
      const rate = Number(p.sellingPrice ?? p.close ?? p.price);
      return date && Number.isFinite(rate) && rate > 0 ? [date, rate] : null;
    })
    .filter(Boolean)
    .sort((a, b) => (a[0] < b[0] ? -1 : 1));
}

function rateOn(series, dateStr) {
  if (!series || series.length === 0) return null;
  const target = String(dateStr).slice(0, 10);
  let lo = 0;
  let hi = series.length - 1;
  let answer = null;
  while (lo <= hi) {
    const mid = (lo + hi) >> 1;
    if (series[mid][0] <= target) {
      answer = series[mid][1];
      lo = mid + 1;
    } else {
      hi = mid - 1;
    }
  }
  return answer;
}

export function useRateHistory() {
  const displayCurrency = useAppStore((s) => s.displayCurrency) || 'TRY';
  const currentRates = useExchangeRates();

  const { data } = useQuery({
    queryKey: ['rateHistory'],
    queryFn: async () => {
      const [usd, eur] = await Promise.all([
        unifiedMarketService.getHistory('FOREX', 'USD', 'ALL'),
        unifiedMarketService.getHistory('FOREX', 'EUR', 'ALL'),
      ]);
      return { USD: buildSeries(usd), EUR: buildSeries(eur) };
    },
    staleTime: STALE.LONG,
    gcTime: GC.LONG,
  });

  const rateAt = useCallback((currency, dateStr) => {
    if (currency === 'TRY') return 1;
    const historical = rateOn(data?.[currency], dateStr);
    return historical ?? currentRates[currency] ?? null;
  }, [data, currentRates]);

  const resolveTarget = useCallback((base, natural) => {
    if (displayCurrency !== 'ORIGINAL') return displayCurrency;
    const candidate = natural ?? base ?? 'TRY';
    return SUPPORTED.includes(candidate) ? candidate : 'TRY';
  }, [displayCurrency]);

  const convertAt = useCallback((value, base = 'TRY', dateStr, natural) => {
    if (value == null) return null;
    const num = Number(value);
    if (!Number.isFinite(num)) return null;
    const from = SUPPORTED.includes(base) ? base : 'TRY';
    const target = resolveTarget(from, natural);
    if (from === target) return num;
    const baseRate = rateAt(from, dateStr);
    const displayRate = rateAt(target, dateStr);
    if (baseRate == null || displayRate == null) return num;
    const inTry = from === 'TRY' ? num : num * baseRate;
    return target === 'TRY' ? inTry : inTry / displayRate;
  }, [resolveTarget, rateAt]);

  return useMemo(
    () => ({ currency: displayCurrency, convertAt, rateAt, resolveTarget }),
    [displayCurrency, convertAt, rateAt, resolveTarget],
  );
}
