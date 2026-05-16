import { useCallback, useMemo } from 'react';
import useAppStore from '../stores/useAppStore';
import { useExchangeRates } from './useExchangeRates';
import { formatPrice, currentLocaleTag } from '../utils/formatters';

const SUPPORTED = ['TRY', 'USD', 'EUR'];

/**
 * Currency-aware money helper. Reads the user's chosen display currency and live FX rates,
 * converts a value from its base currency to that target and formats it.
 *
 * When the user picks 'ORIGINAL', the per-call target is the asset's natural currency
 * (passed via opts.natural), falling back to the value's own base. Portfolio call sites
 * that omit `natural` therefore stay TRY-canonical under ORIGINAL — matching the rule that
 * a multi-asset portfolio is always reported in TRY first.
 */
export function useMoney() {
  const displayCurrency = useAppStore((s) => s.displayCurrency) || 'TRY';
  const rates = useExchangeRates();

  const resolveTarget = useCallback((base, natural) => {
    if (displayCurrency !== 'ORIGINAL') return displayCurrency;
    const candidate = natural ?? base ?? 'TRY';
    return SUPPORTED.includes(candidate) ? candidate : 'TRY';
  }, [displayCurrency]);

  const convert = useCallback((value, base = 'TRY', natural) => {
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
  }, [resolveTarget, rates]);

  const format = useCallback((value, base = 'TRY', opts = {}) => {
    const natural = opts.natural;
    const converted = convert(value, base, natural);
    if (converted == null) return 'N/A';
    const target = resolveTarget(base, natural);
    const abs = Math.abs(converted);
    const maxDecimals = opts.maxDecimals ?? (abs < 10 ? 4 : abs < 1000 ? 3 : 2);
    return formatPrice(converted, {
      currency: target,
      minDecimals: opts.minDecimals ?? 2,
      maxDecimals,
    });
  }, [convert, resolveTarget]);

  const formatCompact = useCallback((value, base = 'TRY', threshold = 100_000, natural) => {
    const converted = convert(value, base, natural);
    if (converted == null) return 'N/A';
    if (Math.abs(converted) < threshold) return format(value, base, { natural });
    const target = resolveTarget(base, natural);
    return new Intl.NumberFormat(currentLocaleTag(), {
      notation: 'compact',
      style: 'currency',
      currency: target,
      maximumFractionDigits: 1,
    }).format(converted);
  }, [convert, format, resolveTarget]);

  return useMemo(
    () => ({ currency: displayCurrency, convert, format, formatCompact, rates, resolveTarget }),
    [displayCurrency, convert, format, formatCompact, rates, resolveTarget],
  );
}
