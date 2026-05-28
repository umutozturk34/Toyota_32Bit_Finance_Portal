// EVDS deposit maturity buckets (must match backend macro.yaml): MT06 is the all-maturity TOTAL.
const MATURITY_TO_KEY = {
  MT01: '1m',
  MT02: '3m',
  MT03: '6m',
  MT04: '1y',
  MT05: '1yPlus',
  MT06: 'Total',
};

function depositI18nLabelKey(code) {
  if (!code) return null;
  const parts = code.split('.');
  if (parts.length < 3 || parts[0] !== 'TP') return null;
  const currSeg = parts[1];
  const maturity = parts[parts.length - 1];
  if (!currSeg.endsWith('TAS')) return null;
  const curr = currSeg.slice(0, -3).toLowerCase();
  if (!['try', 'usd', 'eur'].includes(curr)) return null;
  const matKey = MATURITY_TO_KEY[maturity];
  if (!matKey) return null;
  const currCapital = curr.charAt(0).toUpperCase() + curr.slice(1);
  return `marketOverview.macro.deposit${currCapital}${matKey}`;
}

export function instrumentDisplayName(t, type, code, fallbackName) {
  if (!code) return fallbackName || '';
  if (type === 'DEPOSIT' || type === 'MACRO_DEPOSIT') {
    const key = depositI18nLabelKey(code);
    if (key) return t(key, { defaultValue: fallbackName || code });
  }
  return fallbackName || code;
}
