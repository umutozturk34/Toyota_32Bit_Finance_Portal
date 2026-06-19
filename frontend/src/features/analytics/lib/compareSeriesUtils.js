import { bondService } from '../../bond/services/bondService';
import { macroIndicatorService } from '../../macro/services/macroIndicatorService';
import { analyticsService } from '../services/analyticsService';
import { unifiedMarketService } from '../../../shared/services/unifiedMarketService';
import { viopQuoteCurrency } from '../../../shared/utils/priceCurrency';
import { commodityName } from '../../../shared/utils/commodityName';
import { parseLocal, fmtLocal } from './compareSeriesFill';

// Series fill/backfill + compound transforms moved to compareSeriesFill; re-exported here so every existing
// importer of compareSeriesUtils keeps resolving these names from a single module.
export {
  compoundRateSeries,
  forwardFillTo,
  forwardFillToToday,
  backFillToWindowStart,
  deriveRateSeries,
  forwardFillDaily,
  forwardFillGaps,
} from './compareSeriesFill';

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

// Deposit quote currency from the indicator code. TWO code shapes reach Compare and BOTH carry the ISO
// currency right after their prefix: the macro-list / Beater code DEPOSIT<CCY><maturity> (e.g. depositUsd1m,
// depositEurTotal) and the raw EVDS code TP.<CCY>TAS... (the analytics presets). A USD/EUR deposit grows in
// its OWN currency, so mis-resolving this to TRY made a USD deposit get FX-DIVIDED by the (rising) USD/TRY
// rate in a USD/EUR frame and collapse to a spurious ~−95% "loss" — while the TRY frame (no conversion)
// looked right. Mirrors backend DepositNativeCurrencyStrategy.
export function depositCurrencyFor(code) {
  if (!code) return 'TRY';
  const upper = code.toUpperCase();
  const ccyAt = (i) => {
    const ccy = upper.slice(i, i + 3);
    return ['TRY', 'USD', 'EUR'].includes(ccy) ? ccy : 'TRY';
  };
  if (upper.startsWith('DEPOSIT')) return ccyAt(7);
  if (upper.startsWith('TP.')) return ccyAt(3);
  return 'TRY';
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
    // returnIndexByCcy (USD/EUR) is the real per-currency return index (cost@entry-FX); the line uses it so a
    // foreign frame isn't a single-rate conversion of the netted TRY index. pnlByCcy is the money overlay.
    const pnlByDate = new Map((pnl || []).map((p) => [p.date, {
      pnlTry: Number(p.value), pnlByCcy: p.pnlByCcy ?? null, valueByCcy: p.returnIndexByCcy ?? null,
    }]));
    return (twr || [])
      .map((p) => {
        const m = pnlByDate.get(p.date);
        return {
          date: p.date, value: Number(p.value),
          pnlTry: m ? m.pnlTry : null, pnlByCcy: m ? m.pnlByCcy : null, valueByCcy: m ? m.valueByCcy : null,
        };
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
  // A macro series' unit (PERCENT vs INDEX) is round-tripped positionally beside codes/types so a reload or
  // shared link keeps the cumulative classification correct (a PERCENT rate compounds, an INDEX level stays
  // raw) WITHOUT waiting for the async macro list. An empty slot ('') means "unknown" — the page falls back
  // to the macro-list map for that series. Older links carry no `units` param at all → unitList is empty.
  const unitsParam = params.get('units');
  const unitList = unitsParam ? unitsParam.split(',').map((s) => s.trim()) : [];
  const out = [];
  const seen = new Set();
  for (let i = 0; i < codeList.length; i += 1) {
    const code = codeList[i];
    const type = typeList[i] || typeList[0];
    if (!type) continue;
    const key = `${type}|${code}`;
    if (seen.has(key)) continue;
    seen.add(key);
    const unit = unitList[i] || null;
    // For PORTFOLIO, `code` is a numeric DB id with no human meaning. Leave `name` null so the
    // chip falls back to displayLabel's localized "Portföy" placeholder until the backfill effect in
    // ComparePage resolves the real name from the user's portfolio list.
    const entry = { type, code, name: type === 'PORTFOLIO' ? null : code };
    if (unit && isMacro(type)) entry.unit = unit;
    out.push(entry);
  }
  return out;
}
