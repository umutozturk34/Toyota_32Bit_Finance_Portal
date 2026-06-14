import { bondService } from '../../bond/services/bondService';
import { macroIndicatorService } from '../../macro/services/macroIndicatorService';
import { analyticsService } from '../services/analyticsService';
import { unifiedMarketService } from '../../../shared/services/unifiedMarketService';
import { viopQuoteCurrency } from '../../../shared/utils/priceCurrency';
import { commodityName } from '../../../shared/utils/commodityName';

const PALETTE = ['#5E6AD2', '#10b981', '#f59e0b', '#06b6d4', '#ef4444', '#8b5cf6'];

export function isMacro(type) {
  return type && type.startsWith('MACRO');
}

// Localized type badge for a compared series. Compare carries every market type plus the PORTFOLIO id, so a
// single namespace never covers them: MACRO_* labels live under marketOverview.macro.enum, every spot/asset
// type under assets.labels, and the portfolio under nav.portfolio. Falls back to the raw type for safety.
export function compareTypeLabel(t, type) {
  if (type === 'PORTFOLIO') return t('nav.portfolio', { defaultValue: 'Portföy' });
  if (isMacro(type)) return t(`marketOverview.macro.enum.${type}`, { defaultValue: type });
  return t(`assets.labels.${type}`, { defaultValue: type });
}

// Deposits AND PERCENT-unit reference rates (raw TLREF rate, CBRT policy rate) are compounded into a
// cumulative growth index (see compoundRateSeries); a MACRO_RATE already published as an INDEX (e.g. the
// BIST TLREF Index, ~6022) is left as-is because it is ALREADY cumulative — ComparePage compounds only
// when the indicator unit is PERCENT, otherwise double-compounding explodes the value. Either way these
// are value series, not rate lines, and FX-convert like any other instrument (TRY-native, so convert to
// USD/EUR per-date in a foreign-ccy compare). CPI/TÜFE (MACRO_INFLATION) is already a cumulative
// price-level index used as a deflator, and BOND yields are rate levels: those stay rate-like — never
// compounded, never FX-converted.
export function isRateLike(type) {
  return (isMacro(type) && type !== 'MACRO_DEPOSIT' && type !== 'MACRO_RATE') || type === 'BOND';
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

// Parse a YYYY-MM-DD key as a LOCAL-midnight date so a cursor advanced via local setDate and the key
// emitted via local toLocaleDateString stay in the same zone. Building the cursor from `new Date(iso)`
// (UTC midnight) and reading it back via toISOString in a DST-observing zone drops or duplicates the
// spring-forward day. Istanbul is DST-free, but non-Turkey browsers corrupt the fill.
const parseLocal = (iso) => {
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(y, m - 1, d);
};

// Fast local YYYY-MM-DD (same shape as toLocaleDateString('sv-SE')) WITHOUT Intl. The day-by-day fill and
// compound loops below run tens of thousands of times on the ALL range × several mixed series, and Intl
// formatting was a measurable chunk of that recompute; manual zero-padding is identical output, far cheaper.
const fmtLocal = (d) => {
  const y = d.getFullYear();
  const m = d.getMonth() + 1;
  const day = d.getDate();
  return `${y}-${m < 10 ? '0' : ''}${m}-${day < 10 ? '0' : ''}${day}`;
};

// Compound an annual-rate-% series into a cumulative growth index (starts at 1.0). Mirrors backend
// ScenarioService.applyCompound: daily compounding over a 365-day year, with the rate in effect
// during each interval applied over that interval's day count. When `endIso` is given, the last
// published rate is carried forward past the final observation (see the tail block below).
export function compoundRateSeries(points, endIso) {
  const sorted = [...(points || [])]
    .filter((p) => p && p.date && Number.isFinite(Number(p.value)))
    .sort((a, b) => String(a.date).localeCompare(String(b.date)));
  if (sorted.length === 0) return [];
  const out = [{ ...sorted[0], value: 1 }];
  let factor = 1;
  // Emit the compounding DAY-BY-DAY across each inter-observation gap, not one cumulative lump at the next
  // observation. Deposit/rate quotes are published ~monthly, so crediting a whole month's interest onto the
  // observation date — then letting forwardFillDaily carry the value flat in between — rendered the index as
  // a staircase (flat for a month, then a jump up) even though interest accrues continuously. Walking it
  // daily makes the line rise smoothly within a fixed-rate month, matching the tail-carry below. The end
  // value is mathematically identical: (1 + dailyRate)^days == applying (1 + dailyRate) once per day.
  for (let i = 1; i < sorted.length; i += 1) {
    const annualRatePct = Number(sorted[i - 1].value);
    const dailyRate = Number.isFinite(annualRatePct) ? annualRatePct / 100 / 365 : 0;
    const cursor = parseLocal(sorted[i - 1].date);
    const stepEnd = parseLocal(sorted[i].date);
    const days = Math.round((stepEnd - cursor) / 86400000);
    if (days <= 0) {
      out.push({ ...sorted[i], value: factor });
      continue;
    }
    for (let d = 1; d < days; d += 1) {
      cursor.setDate(cursor.getDate() + 1);
      factor *= (1 + dailyRate);
      out.push({ date: fmtLocal(cursor), value: factor, _filled: true });
    }
    // The final day of the gap IS the next observation date: compound it and attach the real point's
    // fields (raw rate, etc.) so the observation stays a non-synthetic anchor.
    factor *= (1 + dailyRate);
    out.push({ ...sorted[i], value: factor });
  }
  // Tail carry-forward: deposit/rate interest keeps accruing daily at the last published rate until a
  // newer one is published, so the index must continue to the window edge instead of flat-lining at the
  // final observation. Without this the post-publication days (e.g. ~13 for a weekly-published deposit)
  // silently drop their interest and Compare understates the deposit vs the backend ScenarioService
  // (which already carries the last rate to endDate in simulateRate). Emitted day-by-day so the tail
  // renders as a smooth continuation rather than a flat segment with a jump at the edge.
  const lastRate = Number(sorted[sorted.length - 1].value);
  if (endIso && Number.isFinite(lastRate)) {
    const dailyRate = lastRate / 100 / 365;
    let cursor = parseLocal(sorted[sorted.length - 1].date);
    const end = parseLocal(endIso);
    cursor.setDate(cursor.getDate() + 1);
    while (cursor <= end) {
      factor *= (1 + dailyRate);
      out.push({ date: fmtLocal(cursor), value: factor, _filled: true });
      cursor = new Date(cursor);
      cursor.setDate(cursor.getDate() + 1);
    }
  }
  return out;
}

// Resolves the human label for a compare series across every instrument type: COMMODITY and MACRO get
// their localized i18n name (commodity.name.* / marketOverview.macro.<label>), every other asset uses its
// backend long name. The macro label is not on the selection object (only {type,code,name}), so it is
// looked up from the code->label map built off the macro indicator list. Needs `t`; callers without it
// (rare) fall back to the backend name / stripped code.
export function displayLabel(t, indicator, macroLabelByCode) {
  if (!indicator) return '';
  const { type, code, name } = indicator;
  // PORTFOLIO's `code` is a numeric DB id with no human meaning ("3") — it must NEVER surface. Until the
  // backfill effect resolves the real name from `usePortfolioList`, show a plain "Portföy" placeholder.
  if (type === 'PORTFOLIO') return (name && name !== code) ? name : (t ? t('nav.portfolio', { defaultValue: 'Portföy' }) : 'Portföy');
  if (isMacro(type)) {
    const label = indicator.label || (macroLabelByCode && macroLabelByCode[code]);
    const stripped = code.replace(/^TP\./i, '').replace(/\.D$/i, '');
    if (label && t) return t(`marketOverview.macro.${label}`, { defaultValue: (name && name !== code) ? name : stripped });
    if (name && name !== code) return name;
    return stripped;
  }
  if (type === 'COMMODITY' && t) return commodityName(t, code, name || code);
  return name || code;
}

// Local-zone date key (sv-SE → YYYY-MM-DD) to match every other key in this file and the backend's
// local-zone candle/observation dates. toISOString would shift the boundary a day in non-Istanbul or
// pre-03:00 zones, filtering out a same-day edge candle or landing the window start a day off.
export function toIso(d) {
  return fmtLocal(d);
}

export function rangeBounds(days) {
  const to = new Date();
  const from = new Date(to);
  from.setDate(from.getDate() - days);
  return { from: toIso(from), to: toIso(to) };
}

// Subtract a calendar span, clamping the day-of-month exactly like java.time.LocalDate.minusMonths/minusYears
// (e.g. Mar 31 − 1 month = Feb 28, not Mar 3 as naive setMonth would roll to). Shifting via day=1 first avoids
// the JS month-overflow before re-clamping to the target month's last valid day.
function minusCalendar(base, { years = 0, months = 0, days = 0 }) {
  const d = new Date(base);
  if (years || months) {
    const day = d.getDate();
    d.setDate(1);
    d.setMonth(d.getMonth() - months - years * 12);
    const lastDay = new Date(d.getFullYear(), d.getMonth() + 1, 0).getDate();
    d.setDate(Math.min(day, lastDay));
  }
  if (days) d.setDate(d.getDate() - days);
  return d;
}

// Calendar-correct window bounds for a range token, mirroring the backend's LocalDate.minusYears/minusMonths
// (5Y = today.minusYears(5)) instead of a naive day count. The day count drifts one day per leap year — e.g.
// 5Y as 1825 days lands a day late — and when an asset happens to jump on that exact boundary day Compare's %
// diverged from the inflation-beater (which anchors at the calendar date). ALL keeps the wide day sweep, which
// has no calendar meaning.
export function rangeBoundsCalendar(rangeId) {
  const to = new Date();
  const spans = {
    '1W': { days: 7 },
    '1M': { months: 1 },
    '3M': { months: 3 },
    '6M': { months: 6 },
    '1Y': { years: 1 },
    '3Y': { years: 3 },
    '5Y': { years: 5 },
    ALL: { days: 11000 },
  };
  const from = minusCalendar(to, spans[rangeId] ?? { years: 1 });
  return { from: toIso(from), to: toIso(to) };
}

function widenBounds(bounds, months) {
  const from = parseLocal(bounds.from);
  from.setMonth(from.getMonth() - months);
  return { from: toIso(from), to: bounds.to };
}

export async function fetchSeries(item, bounds) {
  if (item.type === 'PORTFOLIO') {
    // Two series: the cumulative-return index drives the normalized % line (comparable to inflation); the
    // cumulative TL P&L rides along per-point (pnlTry) so the tooltip / info-bar can show money beside the %.
    const [twr, pnl] = await Promise.all([
      analyticsService.portfolioSeries(item.code, bounds, 'twr'),
      analyticsService.portfolioSeries(item.code, bounds, 'pnl'),
    ]);
    // Each pnl point also carries pnlByCcy (USD/EUR, entry-FX cost-based) so the money overlay shows the true
    // foreign-currency P&L, not a single-rate conversion of the TRY P&L. TRY frame falls back to pnlTry.
    const pnlByDate = new Map((pnl || []).map((p) => [p.date, { pnlTry: Number(p.value), pnlByCcy: p.pnlByCcy ?? null }]));
    return (twr || [])
      .map((p) => {
        const m = pnlByDate.get(p.date);
        return { date: p.date, value: Number(p.value), pnlTry: m ? m.pnlTry : null, pnlByCcy: m ? m.pnlByCcy : null };
      })
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
    // For PORTFOLIO, `code` is a numeric DB id with no human meaning. Leave `name` null so the
    // chip falls back to displayLabel's localized "Portföy" placeholder until the backfill effect in
    // ComparePage resolves the real name from the user's portfolio list.
    out.push({ type, code, name: type === 'PORTFOLIO' ? null : code });
  }
  return out;
}

export function forwardFillTo(points, endIso) {
  if (!points || points.length === 0) return points;
  const sorted = [...points].sort((a, b) => String(a.date).localeCompare(String(b.date)));
  const last = sorted[sorted.length - 1];
  if (last.date >= endIso) return sorted;
  return [...sorted, { ...last, date: endIso, value: last.value, _filled: true }];
}

export function forwardFillToToday(points) {
  return forwardFillTo(points, new Date().toISOString().slice(0, 10));
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

// Derive a YoY ('yoy') or MoM ('mom') RATE series (%) from a cumulative INDEX series:
// rate(t) = value(t) / value(t − span) − 1. Inflation/PPI/index-rates are stored as an ever-rising
// cumulative index, so this is what users read as the actual annual/monthly rate. The base is matched by
// nearest-earlier date within a tolerance (handles month-length drift); points without a usable base are
// skipped, so the caller must include ~12 months of history before the window start for full coverage.
export function deriveRateSeries(points, view) {
  if (!points || points.length === 0) return [];
  const sorted = [...points].sort((a, b) => String(a.date).localeCompare(String(b.date)));
  const months = view === 'mom' ? 1 : 12;
  const tolDays = view === 'mom' ? 16 : 25;
  const out = [];
  for (let i = 0; i < sorted.length; i += 1) {
    const cur = Number(sorted[i].value);
    if (!Number.isFinite(cur)) continue;
    const target = new Date(sorted[i].date);
    target.setMonth(target.getMonth() - months);
    let base = null;
    let bestDiff = Infinity;
    for (let j = i - 1; j >= 0; j -= 1) {
      const diff = Math.abs((new Date(sorted[j].date).getTime() - target.getTime()) / 86_400_000);
      if (diff < bestDiff) { bestDiff = diff; base = sorted[j]; }
    }
    if (!base || bestDiff > tolDays) continue;
    const baseV = Number(base.value);
    if (!Number.isFinite(baseV) || baseV <= 0) continue;
    out.push({ date: sorted[i].date, value: (cur / baseV - 1) * 100 });
  }
  return out;
}

export function forwardFillDaily(points, fromIso, toIso) {
  if (!points || points.length === 0) return points;
  const sorted = [...points].sort((a, b) => String(a.date).localeCompare(String(b.date)));
  if (sorted.length > 1) {
    let maxGap = 0;
    for (let i = 1; i < sorted.length; i += 1) {
      const prev = parseLocal(sorted[i - 1].date);
      const curr = parseLocal(sorted[i].date);
      const gap = Math.round((curr - prev) / 86400000);
      if (gap > maxGap) maxGap = gap;
    }
    if (maxGap < 4) return sorted;
  }
  const result = [];
  let cursor = parseLocal(fromIso);
  const end = parseLocal(toIso);
  let idx = 0;
  let currentPoint = null;
  while (cursor <= end) {
    const cursorIso = fmtLocal(cursor);
    while (idx < sorted.length && sorted[idx].date <= cursorIso) {
      currentPoint = sorted[idx];
      idx += 1;
    }
    // currentPoint is the latest real point with date <= cursorIso (the idx-walk above advances it),
    // so when its date equals the cursor it IS that day's real point — no need to rescan `sorted` for
    // an exact match. The old per-day .find() made this O(days × points): on the ALL range a 1995→now
    // series ran ~11k days × ~11k points ≈ 120M scans PER series, the multi-second freeze.
    if (currentPoint !== null && currentPoint.date === cursorIso) {
      result.push(currentPoint);
    } else if (currentPoint !== null) {
      // Spread the carried point so extra fields (e.g. the portfolio's pnlTry) survive the fill.
      result.push({ ...currentPoint, date: cursorIso, value: currentPoint.value, _filled: true });
    }
    cursor = new Date(cursor);
    cursor.setDate(cursor.getDate() + 1);
  }
  return result;
}

// Carry the previous reading forward to every intervening day WITHOUT inflating the whole series to
// daily cadence the way forwardFillDaily does. For sparse macro lines (CPI, rates) this gives every
// in-between day the last published value, so the line and its tooltips are continuous across gaps.
export function forwardFillGaps(points) {
  if (!points || points.length < 2) return points;
  const sorted = [...points].sort((a, b) => String(a.date).localeCompare(String(b.date)));
  const result = [sorted[0]];
  for (let i = 1; i < sorted.length; i += 1) {
    const prev = sorted[i - 1];
    const curr = sorted[i];
    let cursor = parseLocal(prev.date);
    cursor.setDate(cursor.getDate() + 1);
    const currDate = parseLocal(curr.date);
    while (cursor < currDate) {
      result.push({ date: fmtLocal(cursor), value: prev.value, _filled: true });
      cursor = new Date(cursor);
      cursor.setDate(cursor.getDate() + 1);
    }
    result.push(curr);
  }
  return result;
}
