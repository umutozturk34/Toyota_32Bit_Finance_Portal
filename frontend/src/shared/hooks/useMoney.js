import { useCallback, useMemo } from 'react';
import useAppStore from '../stores/useAppStore';
import { useExchangeRates } from './useExchangeRates';
import { useRateHistory } from './useRateHistory';
import { formatPrice, currentLocaleTag, priceDecimals, fitMoney } from '../utils/formatters';

const SUPPORTED = ['TRY', 'USD', 'EUR'];

// `lockBase` pins every format to its own `base` currency and bypasses all FX conversion — the value is
// rendered exactly as supplied (e.g. a TRY total stays TRY whatever the global selector is). The
// fixed-income surface uses this because bonds/deposits are reported in TRY by the backend and must NEVER
// be FX-converted (bonds-stay-TRY); converting an already-TRY figure to the display currency would
// double-display it in the wrong unit.
export function useMoney({ lockBase = false } = {}) {
  const displayCurrency = useAppStore((s) => s.displayCurrency) || 'TRY';
  const rates = useExchangeRates();
  const { convertAt, rateAt, convertBetween } = useRateHistory();

  const resolveTarget = useCallback((base, natural) => {
    if (lockBase) return SUPPORTED.includes(base) ? base : 'TRY';
    if (displayCurrency !== 'ORIGINAL') return displayCurrency;
    const candidate = natural ?? base ?? 'TRY';
    return SUPPORTED.includes(candidate) ? candidate : 'TRY';
  }, [displayCurrency, lockBase]);

  // convertAt returns the UNCONVERTED base value when its rate-history lookup misses for either side
  // (cold-load, before the rate-history query resolves). In that case the value is still in `base`, so
  // the readiness gate must NOT relabel it as `target`. This is true only when rateAt resolves a rate
  // for both base and target at the date — mirroring convertAt's own null-guard.
  const dateRatesReady = useCallback((base, target, dateAt) => (
    rateAt(base, dateAt) != null && rateAt(target, dateAt) != null
  ), [rateAt]);

  const convert = useCallback((value, base = 'TRY', natural, dateAt) => {
    if (dateAt && !lockBase) return convertAt(value, base, dateAt, natural);
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
  }, [resolveTarget, rates, convertAt, lockBase]);

  // TRY-equivalent magnitude of a value, independent of the display currency. The compact-vs-full
  // threshold is calibrated in TRY (100k), so gating on the CONVERTED display value would let a
  // USD/EUR figure (≈30x smaller numerically) slip under the threshold and never compact.
  const toTry = useCallback((value, base = 'TRY', dateAt) => {
    if (value == null) return null;
    const num = Number(value);
    if (!Number.isFinite(num)) return null;
    // lockBase renders the value as supplied (no FX), so its display magnitude IS the gate magnitude —
    // returning null lets the caller fall back to the converted (== as-supplied) value.
    if (lockBase) return null;
    // Use convertBetween (explicit to='TRY'), NOT convertAt(...,'TRY'): convertAt's resolveTarget ignores the
    // 'TRY' natural hint whenever a display currency is set, so in a USD/EUR frame it would return the
    // DISPLAY-currency value (~30x smaller) and let a 1–30B TRY figure slip under the TRY-calibrated compact
    // threshold and never compact. convertBetween converts base→TRY at the date regardless of displayCurrency.
    if (dateAt) return convertBetween(value, base, 'TRY', dateAt);
    const from = SUPPORTED.includes(base) ? base : 'TRY';
    if (from === 'TRY') return num;
    const fromRate = rates[from];
    return fromRate == null ? num : num * fromRate;
  }, [rates, convertBetween, lockBase]);

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
    const maxDecimals = opts.maxDecimals ?? Math.max(priceDecimals(abs), abs < 10 ? 4 : abs < 1000 ? 3 : 2);
    return formatPrice(converted, {
      currency: effectiveCurrency,
      minDecimals: opts.minDecimals ?? 2,
      maxDecimals,
    });
  }, [convert, resolveTarget, rates, dateRatesReady]);

  const formatCompact = useCallback((value, base = 'TRY', threshold = 100_000, natural, dateAt) => {
    const converted = convert(value, base, natural, dateAt);
    if (converted == null) return 'N/A';
    // Gate on the TRY-equivalent magnitude (threshold is in TRY), but format the CONVERTED value so the
    // displayed unit stays correct. Fall back to the converted magnitude if the TRY conversion misses.
    const tryEquivalent = toTry(value, base, dateAt);
    const gateMagnitude = tryEquivalent == null ? Math.abs(converted) : Math.abs(tryEquivalent);
    if (gateMagnitude < threshold) return format(value, base, { natural, dateAt });
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
  }, [convert, format, resolveTarget, rates, dateRatesReady, toTry]);

  // Width-aware money: full when it fits `maxChars`, compact (never digit-clipped) when it would overflow.
  // Same conversion + currency-readiness gate as format(); pair with useFitChars + a title= carrying format().
  const formatFit = useCallback((value, base = 'TRY', opts = {}) => {
    const converted = convert(value, base, opts.natural, opts.dateAt);
    if (converted == null) return 'N/A';
    const target = resolveTarget(base, opts.natural);
    const normalizedBase = SUPPORTED.includes(base) ? base : 'TRY';
    const ratesReady = (opts.dateAt != null && dateRatesReady(normalizedBase, target, opts.dateAt))
      || normalizedBase === target
      || (rates[normalizedBase] != null && rates[target] != null);
    const effectiveCurrency = ratesReady ? target : normalizedBase;
    const abs = Math.abs(converted);
    const maxDecimals = opts.maxDecimals ?? Math.max(priceDecimals(abs), abs < 10 ? 4 : abs < 1000 ? 3 : 2);
    return fitMoney(converted, { currency: effectiveCurrency, maxChars: opts.maxChars, maxDecimals });
  }, [convert, resolveTarget, rates, dateRatesReady]);

  return useMemo(
    () => ({ currency: displayCurrency, convert, format, formatCompact, formatFit, rates, resolveTarget }),
    [displayCurrency, convert, format, formatCompact, formatFit, rates, resolveTarget],
  );
}
