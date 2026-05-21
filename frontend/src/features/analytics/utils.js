export function formatCurrency(value, currency = 'TRY', locale = 'tr-TR') {
  if (value == null) return '—';
  return Number(value).toLocaleString(locale, {
    style: 'currency', currency, maximumFractionDigits: 0,
  });
}

export function formatPercent(value, locale = 'tr-TR') {
  if (value == null) return '—';
  const n = Number(value);
  const sign = n > 0 ? '+' : '';
  return `${sign}${n.toLocaleString(locale, { maximumFractionDigits: 2, minimumFractionDigits: 2 })}%`;
}

export function formatDate(iso, locale = 'tr-TR') {
  if (!iso) return null;
  return new Date(iso).toLocaleDateString(locale, { day: '2-digit', month: 'short', year: 'numeric' });
}

export function isoDate(date) {
  const d = date instanceof Date ? date : new Date(date);
  return d.toISOString().slice(0, 10);
}

export function todayIso() {
  return isoDate(new Date());
}

export function dateOffsetIso(months) {
  const d = new Date();
  d.setMonth(d.getMonth() - months);
  return isoDate(d);
}
