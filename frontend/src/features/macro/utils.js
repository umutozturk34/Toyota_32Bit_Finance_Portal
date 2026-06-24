import { CATEGORY_THEME, FALLBACK_THEME } from './constants';
import { visibleDecimals } from '../../shared/utils/formatters';

export function themeFor(category) {
  return CATEGORY_THEME[category] || FALLBACK_THEME;
}

const UNIT_FORMATTERS = {
  PERCENT: (v, locale) => `%${Number(v).toLocaleString(locale, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`,
  INDEX:   (v, locale) => Number(v).toLocaleString(locale, { maximumFractionDigits: 2 }),
  NUMBER:  (v, locale) => Number(v).toLocaleString(locale, { maximumFractionDigits: 2 }),
};

export function formatValue(value, unit, locale = 'tr-TR') {
  if (value == null) return '—';
  const formatter = UNIT_FORMATTERS[unit] || UNIT_FORMATTERS.NUMBER;
  return formatter(value, locale);
}

export function formatDate(dateIso, locale = 'tr-TR', opts = { day: '2-digit', month: 'short', year: 'numeric' }) {
  if (!dateIso) return null;
  return new Date(dateIso).toLocaleDateString(locale, opts);
}

export function computeChange(points) {
  if (!points || points.length < 2) return null;
  const last = Number(points[points.length - 1].value);
  const first = Number(points[0].value);
  if (!isFinite(last) || !isFinite(first)) return null;
  const delta = last - first;
  const percent = first !== 0 ? (delta / Math.abs(first)) * 100 : null;
  return { delta, percent, direction: delta > 0 ? 'up' : delta < 0 ? 'down' : 'flat' };
}

export function computeStats(points) {
  if (!points || points.length === 0) return null;
  const values = points.map((p) => Number(p.value)).filter((v) => isFinite(v));
  if (values.length === 0) return null;
  const min = Math.min(...values);
  const max = Math.max(...values);
  const avg = values.reduce((s, v) => s + v, 0) / values.length;
  return { min, max, avg, count: values.length };
}

export function changeBadgeText(change, unit) {
  if (!change || !isFinite(change.percent)) return null;
  const sign = change.direction === 'up' ? '+' : change.direction === 'down' ? '−' : '';
  const absPct = Math.abs(change.percent);
  if (unit === 'PERCENT') {
    const absPts = Math.abs(change.delta);
    return `${sign}${absPts.toFixed(2)}pt`;
  }
  return `${sign}${absPct.toFixed(visibleDecimals(absPct, 2))}%`;
}
