import { useCallback, useMemo } from 'react';
import useAppStore from '../stores/useAppStore';
import { useExchangeRates } from './useExchangeRates';
import { useRateHistory } from './useRateHistory';
import { formatPrice, currentLocaleTag } from '../utils/formatters';

const SUPPORTED = ['TRY', 'USD', 'EUR'];

export function useMoney() {
  const displayCurrency = useAppStore((s) => s.displayCurrency) || 'TRY';
  const rates = useExchangeRates();
  const { convertAt, rateAt } = useRateHistory();

  const resolveTarget = useCallback((base, natural) => {
    if (displayCurrency !== 'ORIGINAL') return displayCurrency;
    const candidate = natural ?? base ?? 'TRY';
    return SUPPORTED.includes(candidate) ? candidate : 'TRY';
  }, [displayCurrency]);

  // convertAt returns the UNCONVERTED base value when its rate-history lookup misses for either side
  // (cold-load, before the rate-history query resolves). In that case the value is still in `base`, so
  // the readiness gate must NOT relabel it as `target`. This is true only when rateAt resolves a rate
  // for both base and target at the date — mirroring convertAt's own null-guard.
  const dateRatesReady = useCallback((base, target, dateAt) => (
    rateAt(base, dateAt) != null && rateAt(target, dateAt) != null
  ), [rateAt]);

  const convert = useCallback((value, base = 'TRY', natural, dateAt) => {
    if (dateAt) return convertAt(value, base, dateAt, natural);
    if (value == null) return null;
    const num = Number(value);
    if (!Number.isFinite(num)) return null;
    const from = SUPPORTED.includes(base) ? base : 'TRY';
    const target = resolveTarget(from, natural);
    if (from === target) return num;
    const fromRate = rates[from];
    const toRate = rates[target];
    if (fromRate == null || toRate == null) return num;
    const inTry = from === 'TRY' ? num : num * fromRate;
    return target === 'TRY' ? inTry : inTry / toRate;
  }, [resolveTarget, rates, convertAt]);

  const format = useCallback((value, base = 'TRY', opts = {}) => {
    const natural = opts.natural;
    const dateAt = opts.dateAt;
    const converted = convert(value, base, natural, dateAt);
    if (converted == null) return 'N/A';
    const target = resolveTarget(base, natural);
    const normalizedBase = SUPPORTED.includes(base) ? base : 'TRY';
    // The convertAt (dateAt) path uses its own date-series rates: only treat its value as being in
    // `target` when those rates actually resolved (dateRatesReady) — on a cold-load miss convertAt
    // returns the value still in `base`, so fall through to the spot-rates gate / base label instead.
    const ratesReady = (dateAt != null && dateRatesReady(normalizedBase, target, dateAt))
      || normalizedBase === target
      || (rates[normalizedBase] != null && rates[target] != null);
    const effectiveCurrency = ratesReady ? target : normalizedBase;
    const abs = Math.abs(converted);
    const maxDecimals = opts.maxDecimals ?? (abs < 10 ? 4 : abs < 1000 ? 3 : 2);
    return formatPrice(converted, {
      currency: effectiveCurrency,
      minDecimals: opts.minDecimals ?? 2,
      maxDecimals,
    });
  }, [convert, resolveTarget, rates, dateRatesReady]);

  const formatCompact = useCallback((value, base = 'TRY', threshold = 100_000, natural, dateAt) => {
    const converted = convert(value, base, natural, dateAt);
    if (converted == null) return 'N/A';
    if (Math.abs(converted) < threshold) return format(value, base, { natural, dateAt });
    const target = resolveTarget(base, natural);
    const normalizedBase = SUPPORTED.includes(base) ? base : 'TRY';
    // dateAt uses convertAt's own date-series rates; only keep the `target` symbol when those rates
    // resolved (dateRatesReady) — a cold-load miss leaves the value in `base`, so gate accordingly.
    const ratesReady = (dateAt != null && dateRatesReady(normalizedBase, target, dateAt))
      || normalizedBase === target
      || (rates[normalizedBase] != null && rates[target] != null);
    const effectiveCurrency = ratesReady ? target : normalizedBase;
    return new Intl.NumberFormat(currentLocaleTag(), {
      notation: 'compact',
      style: 'currency',
      currency: effectiveCurrency,
      maximumFractionDigits: 2,
    }).format(converted);
  }, [convert, format, resolveTarget, rates, dateRatesReady]);

  return useMemo(
    () => ({ currency: displayCurrency, convert, format, formatCompact, rates, resolveTarget }),
    [displayCurrency, convert, format, formatCompact, rates, resolveTarget],
  );
}
