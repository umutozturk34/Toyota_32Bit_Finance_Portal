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
      const selling = Number(p.sellingPrice ?? p.close ?? p.price);
      if (!date || !Number.isFinite(selling) || selling <= 0) return null;
      const buying = Number(p.buyingPrice);
      const effBuying = Number(p.effectiveBuyingPrice);
      const effSelling = Number(p.effectiveSellingPrice);
      return {
        date,
        sellingPrice: selling,
        buyingPrice: Number.isFinite(buying) && buying > 0 ? buying : selling,
        effectiveBuyingPrice: Number.isFinite(effBuying) && effBuying > 0 ? effBuying : selling,
        effectiveSellingPrice: Number.isFinite(effSelling) && effSelling > 0 ? effSelling : selling,
      };
    })
    .filter(Boolean)
    .sort((a, b) => (a.date < b.date ? -1 : 1));
}

function rateOn(series, dateStr, field = 'sellingPrice') {
  if (!series || series.length === 0) return null;
  const target = String(dateStr).slice(0, 10);
  let lo = 0;
  let hi = series.length - 1;
  let answer = null;
  while (lo <= hi) {
    const mid = (lo + hi) >> 1;
    if (series[mid].date <= target) {
      answer = series[mid][field] ?? series[mid].sellingPrice;
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

  const rateAt = useCallback((currency, dateStr, field = 'sellingPrice') => {
    if (currency === 'TRY') return 1;
    const series = data?.[currency];
    const historical = rateOn(series, dateStr, field);
    if (historical != null) return historical;
    // Date precedes loaded history → use the earliest historical rate, never today's spot.
    if (series && series.length > 0) return series[0][field] ?? series[0].sellingPrice;
    return currentRates[currency] ?? null;
  }, [data, currentRates]);

  const resolveTarget = useCallback((base, natural) => {
    if (displayCurrency !== 'ORIGINAL') return displayCurrency;
    const candidate = natural ?? base ?? 'TRY';
    return SUPPORTED.includes(candidate) ? candidate : 'TRY';
  }, [displayCurrency]);

  const convertAt = useCallback((value, base = 'TRY', dateStr, natural, rateField) => {
    if (value == null) return null;
    const num = Number(value);
    if (!Number.isFinite(num)) return null;
    const from = SUPPORTED.includes(base) ? base : 'TRY';
    const target = resolveTarget(from, natural);
    if (from === target) return num;
    const baseRate = rateAt(from, dateStr, rateField);
    const displayRate = rateAt(target, dateStr, rateField);
    if (baseRate == null || displayRate == null) return num;
    const inTry = from === 'TRY' ? num : num * baseRate;
    return target === 'TRY' ? inTry : inTry / displayRate;
  }, [resolveTarget, rateAt]);

  return useMemo(
    () => ({ currency: displayCurrency, convertAt, rateAt, resolveTarget }),
    [displayCurrency, convertAt, rateAt, resolveTarget],
  );
}
