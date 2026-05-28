import { bondService } from '../../bond/services/bondService';
import { macroIndicatorService } from '../../macro/services/macroIndicatorService';
import { analyticsService } from '../services/analyticsService';
import { unifiedMarketService } from '../../../shared/services/unifiedMarketService';
import { viopQuoteCurrency } from '../../../shared/utils/priceCurrency';

const PALETTE = ['#5E6AD2', '#10b981', '#f59e0b', '#06b6d4', '#ef4444', '#8b5cf6'];

export function isMacro(type) {
  return type && type.startsWith('MACRO');
}

// Deposits are compounded into a growth curve (see compoundRateSeries), so they are a value
// series — not a rate line — and must be FX-converted like any other instrument.
export function isRateLike(type) {
  return (isMacro(type) && type !== 'MACRO_DEPOSIT') || type === 'BOND';
}

// Deposit quote currency from the EVDS code: TP.<CCY>... (mirrors DepositNativeCurrencyStrategy).
export function depositCurrencyFor(code) {
  if (!code || !code.startsWith('TP.')) return 'TRY';
  const payload = code.slice(3);
  if (payload.length < 3) return 'TRY';
  const prefix = payload.slice(0, 3).toUpperCase();
  return ['TRY', 'USD', 'EUR'].includes(prefix) ? prefix : 'TRY';
}

export function nativeCurrencyFor(type, code) {
  if (type === 'CRYPTO') {
    if ((code || '').toLowerCase() === 'tether') return 'TRY';
    return 'USD';
  }
  // Commodities are cross-converted to TRY at ingest, so candle series are always TRY.
  if (type === 'COMMODITY') return 'TRY';
  // VIOP quote currency comes from the symbol suffix (date-stripped), not the code's raw tail.
  if (type === 'VIOP') return viopQuoteCurrency(code);
  // A deposit grows in its own currency; converted to the target via that day's FX.
  if (type === 'MACRO_DEPOSIT') return depositCurrencyFor(code);
  return 'TRY';
}

// Compound an annual-rate-% series into a cumulative growth index (starts at 1.0). Mirrors backend
// ScenarioService.applyCompound: daily compounding over a 365-day year, with the rate in effect
// during each interval applied over that interval's day count.
export function compoundRateSeries(points) {
  const sorted = [...(points || [])]
    .filter((p) => p && p.date && Number.isFinite(Number(p.value)))
    .sort((a, b) => String(a.date).localeCompare(String(b.date)));
  if (sorted.length === 0) return [];
  const out = [{ ...sorted[0], value: 1 }];
  let factor = 1;
  for (let i = 1; i < sorted.length; i += 1) {
    const annualRatePct = Number(sorted[i - 1].value);
    const days = Math.round((new Date(sorted[i].date) - new Date(sorted[i - 1].date)) / 86400000);
    if (days > 0) {
      const dailyRate = annualRatePct / 100 / 365;
      factor *= (1 + dailyRate) ** days;
    }
    out.push({ ...sorted[i], value: factor });
  }
  return out;
}

export function displayLabel(indicator) {
  if (!indicator) return '';
  const { type, code, name } = indicator;
  if (isMacro(type)) {
    if (name && name !== code) return name;
    return code.replace(/^TP\./i, '').replace(/\.D$/i, '');
  }
  return name || code;
}

export function toIso(d) {
  return d.toISOString().slice(0, 10);
}

export function rangeBounds(days) {
  const to = new Date();
  const from = new Date(to);
  from.setDate(from.getDate() - days);
  return { from: toIso(from), to: toIso(to) };
}

function widenBounds(bounds, months) {
  const from = new Date(bounds.from);
  from.setMonth(from.getMonth() - months);
  return { from: from.toISOString().slice(0, 10), to: bounds.to };
}

export async function fetchSeries(item, bounds) {
  if (item.type === 'PORTFOLIO') {
    const points = await analyticsService.portfolioSeries(item.code, bounds);
    return (points || [])
      .map((p) => ({ date: p.date, value: Number(p.value) }))
      .filter((p) => p.date && Number.isFinite(p.value));
  }
  if (isMacro(item.type) || item.unit) {
    const wide = widenBounds(bounds, 18);
    const points = await macroIndicatorService.history(item.code, wide);
    return points.map((p) => ({ date: p.observedAt, value: Number(p.value) }));
  }
  if (item.type === 'BOND') {
    const rows = await bondService.getRateHistory(item.code);
    return (rows || [])
      .map((r) => ({
        date: (r.date || r.rateDate || '').slice(0, 10),
        value: Number(r.rate ?? r.couponRate ?? r.value),
      }))
      .filter((p) => p.date && Number.isFinite(p.value) && p.date <= bounds.to);
  }
  const candles = await unifiedMarketService.getHistory(item.type, item.code, 'ALL');
  return (candles || [])
    .map((c) => {
      const rawDate = c.candleDate || c.date || c.observedAt;
      const date = typeof rawDate === 'string' ? rawDate.slice(0, 10) : null;
      const value = Number(c.close ?? c.price ?? c.sellingPrice ?? c.value ?? c.rate);
      return { date, value };
    })
    .filter((p) => p.date && Number.isFinite(p.value)
      && p.date >= bounds.from && p.date <= bounds.to);
}

export function colorFor(item, idx) {
  return PALETTE[idx % PALETTE.length];
}

export function parseInitialSelection(params) {
  const codes = params.get('codes');
  const types = params.get('types');
  if (!codes || !types) return [];
  const codeList = codes.split(',').map((s) => s.trim()).filter(Boolean);
  const typeList = types.split(',').map((s) => s.trim()).filter(Boolean);
  const out = [];
  const seen = new Set();
  for (let i = 0; i < codeList.length; i += 1) {
    const code = codeList[i];
    const type = typeList[i] || typeList[0];
    if (!type) continue;
    const key = `${type}|${code}`;
    if (seen.has(key)) continue;
    seen.add(key);
    out.push({ type, code, name: code });
  }
  return out;
}

export function forwardFillToToday(points) {
  if (!points || points.length === 0) return points;
  const sorted = [...points].sort((a, b) => String(a.date).localeCompare(String(b.date)));
  const last = sorted[sorted.length - 1];
  const todayIso = new Date().toISOString().slice(0, 10);
  if (last.date >= todayIso) return sorted;
  return [...sorted, { date: todayIso, value: last.value, _filled: true }];
}

export function backFillToWindowStart(points, windowStart) {
  if (!points || points.length === 0) return points;
  const sorted = [...points].sort((a, b) => String(a.date).localeCompare(String(b.date)));
  const beforeOrAt = sorted.filter((p) => p.date <= windowStart);
  const inWindow = sorted.filter((p) => p.date > windowStart);
  if (beforeOrAt.length === 0) return inWindow;
  const anchor = beforeOrAt[beforeOrAt.length - 1];
  if (anchor.date === windowStart) return [anchor, ...inWindow];
  return [{ date: windowStart, value: anchor.value, _backfilled: true }, ...inWindow];
}

export function forwardFillDaily(points, fromIso, toIso) {
  if (!points || points.length === 0) return points;
  const sorted = [...points].sort((a, b) => String(a.date).localeCompare(String(b.date)));
  if (sorted.length > 1) {
    let maxGap = 0;
    for (let i = 1; i < sorted.length; i += 1) {
      const prev = new Date(sorted[i - 1].date);
      const curr = new Date(sorted[i].date);
      const gap = Math.round((curr - prev) / 86400000);
      if (gap > maxGap) maxGap = gap;
    }
    if (maxGap < 4) return sorted;
  }
  const result = [];
  let cursor = new Date(fromIso);
  const end = new Date(toIso);
  let idx = 0;
  let currentValue = null;
  while (cursor <= end) {
    const cursorIso = cursor.toISOString().slice(0, 10);
    while (idx < sorted.length && sorted[idx].date <= cursorIso) {
      currentValue = sorted[idx].value;
      idx += 1;
    }
    const exact = sorted.find((p) => p.date === cursorIso);
    if (exact) {
      result.push(exact);
    } else if (currentValue !== null) {
      result.push({ date: cursorIso, value: currentValue, _filled: true });
    }
    cursor.setDate(cursor.getDate() + 1);
  }
  return result;
}
