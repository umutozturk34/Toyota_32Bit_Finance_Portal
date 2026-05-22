import { bondService } from '../../bond/services/bondService';
import { macroIndicatorService } from '../../macro/services/macroIndicatorService';
import { unifiedMarketService } from '../../../shared/services/unifiedMarketService';

const PALETTE = ['#5E6AD2', '#10b981', '#f59e0b', '#06b6d4', '#ef4444', '#8b5cf6'];

export function isMacro(type) {
  return type && type.startsWith('MACRO');
}

export function isRateLike(type) {
  return isMacro(type) || type === 'BOND';
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

export function widenBounds(bounds, months) {
  const from = new Date(bounds.from);
  from.setMonth(from.getMonth() - months);
  return { from: from.toISOString().slice(0, 10), to: bounds.to };
}

export async function fetchSeries(item, bounds) {
  if (isMacro(item.type)) {
    const wide = widenBounds(bounds, 3);
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
      .filter((p) => p.date && Number.isFinite(p.value)
        && p.date >= bounds.from && p.date <= bounds.to);
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
  const beforeWindow = sorted.filter((p) => p.date < windowStart);
  const inWindow = sorted.filter((p) => p.date >= windowStart);
  if (beforeWindow.length === 0) return inWindow;
  const anchor = beforeWindow[beforeWindow.length - 1];
  const firstInWindow = inWindow[0];
  if (firstInWindow && firstInWindow.date === windowStart) return inWindow;
  return [{ date: windowStart, value: anchor.value, _backfilled: true }, ...inWindow];
}
