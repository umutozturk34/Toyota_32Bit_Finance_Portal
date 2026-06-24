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

  // Convert between two explicit currencies at a date, ignoring displayCurrency. Use when the caller
  // already decided the target (e.g. Compare framing in a URL/deposit currency) rather than the
  // per-series ORIGINAL/displayCurrency resolution that convertAt applies.
  const convertBetween = useCallback((value, from, to, dateStr, rateField) => {
    if (value == null) return null;
    const num = Number(value);
    if (!Number.isFinite(num)) return null;
    const src = SUPPORTED.includes(from) ? from : 'TRY';
    const dst = SUPPORTED.includes(to) ? to : 'TRY';
    if (src === dst) return num;
    const srcRate = rateAt(src, dateStr, rateField);
    const dstRate = rateAt(dst, dateStr, rateField);
    if (srcRate == null || dstRate == null) return num;
    const inTry = src === 'TRY' ? num : num * srcRate;
    return dst === 'TRY' ? inTry : inTry / dstRate;
  }, [rateAt]);

  // THE universal currency-aware P&L primitive: express a (costTry @ costDate, valueTry @ valueDate) pair in
  // the display currency, each leg converted at its OWN date's FX, % computed within that single frame. So
  // every surface (lot rows, daily card, ...) shows a holding's own-currency return — a EUR holding reads
  // ~0% in EUR, not the lira's +2837% — instead of each surface re-deriving FX ad hoc. Mirrors the backend
  // MultiCurrencyPnlCalculator.pointFrame for surfaces its frame map does not reach. TRY display (or missing
  // FX) returns the supplied TRY scalars unchanged, so the four cards keep reconciling. directionSign (+1
  // LONG / −1 SHORT) makes the frame DIRECTION-AWARE: pnl = directionSign × (value − cost). A VIOP SHORT's
  // converted notional FALLS as it profits, so a direction-blind value − cost reads its USD/EUR K/Z backwards;
  // the sign flips it. Non-VIOP callers leave directionSign at its +1 default → identical to before.
  const frame = useCallback((costTry, valueTry, costDate, valueDate, fallbackPnl, fallbackPct, directionSign = 1) => {
    const fallback = { pnl: Number(fallbackPnl) || 0, pnlPercent: fallbackPct, base: 'TRY' };
    if (displayCurrency !== 'USD' && displayCurrency !== 'EUR') return fallback;
    // Rate-readiness gate (mirrors useMoney.dateRatesReady): on cold load the FX history query is unresolved,
    // so rateAt misses and convertAt returns the raw TRY scalar UNCHANGED (not null) — which would render a
    // TRY-magnitude K/Z under a $/€ symbol for the few hundred ms until the query resolves. Hold the TRY
    // fallback until BOTH legs' display-currency rates exist (TRY base is always rate 1), so the K/Z matches
    // its sibling cells (correct ₺ magnitude/symbol) instead of flashing a ~30× wrong figure.
    if (rateAt(displayCurrency, costDate) == null || rateAt(displayCurrency, valueDate) == null) return fallback;
    const cost = convertAt(costTry, 'TRY', costDate);
    const value = convertAt(valueTry, 'TRY', valueDate);
    if (cost == null || value == null) return fallback;
    const pnl = directionSign * (Number(value) - Number(cost));
    const pnlPercent = Math.abs(cost) > 1e-9 ? (pnl / Math.abs(cost)) * 100 : fallbackPct;
    return { pnl, pnlPercent, base: displayCurrency };
  }, [displayCurrency, convertAt, rateAt]);

  return useMemo(
    () => ({ currency: displayCurrency, convertAt, convertBetween, rateAt, resolveTarget, frame }),
    [displayCurrency, convertAt, convertBetween, rateAt, resolveTarget, frame],
  );
}
