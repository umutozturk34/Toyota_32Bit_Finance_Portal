export function formatPercent(value, locale = 'tr-TR') {
  if (value == null) return '—';
  const n = Number(value);
  const sign = n > 0 ? '+' : '';
  return `${sign}${n.toLocaleString(locale, { maximumFractionDigits: 2, minimumFractionDigits: 2 })}%`;
}

// Decimals scaled to magnitude: big figures (crypto can be millions per unit) drop decimals for
// readability, while sub-cent unit prices (small funds, minor currencies) keep enough digits not to
// collapse to "0 ₺".
function moneyDigits(n) {
  const a = Math.abs(n);
  if (a >= 1000) return 0;
  if (a >= 1) return 2;
  if (a >= 0.01) return 4;
  return 6;
}

export function formatMoney(value, locale = 'tr-TR') {
  if (value == null) return '—';
  const n = Number(value);
  return `${n.toLocaleString(locale, { maximumFractionDigits: moneyDigits(n) })} ₺`;
}

// Signed TRY change (the per-unit price delta over the window).
export function formatMoneyDelta(value, locale = 'tr-TR') {
  if (value == null) return '—';
  const n = Number(value);
  const sign = n > 0 ? '+' : '';
  return `${sign}${n.toLocaleString(locale, { maximumFractionDigits: moneyDigits(n) })} ₺`;
}

function isoDate(date) {
  const d = date instanceof Date ? date : new Date(date);
  // Local-zone sv-SE (never UTC toISOString, which shifts the day in non-Istanbul / pre-03:00 zones),
  // matching compareSeriesUtils.toIso so the scenario default start/end and picker maxDate stay correct.
  return d.toLocaleDateString('sv-SE');
}

export function todayIso() {
  return isoDate(new Date());
}

export function dateOffsetIso(months) {
  const d = new Date();
  d.setMonth(d.getMonth() - months);
  return isoDate(d);
}
