export function formatPercent(value, locale = 'tr-TR') {
  if (value == null) return '—';
  const n = Number(value);
  const sign = n > 0 ? '+' : '';
  return `${sign}${n.toLocaleString(locale, { maximumFractionDigits: 2, minimumFractionDigits: 2 })}%`;
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
