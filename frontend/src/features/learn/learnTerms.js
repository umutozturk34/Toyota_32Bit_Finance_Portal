// Financial-literacy term catalog. Each term's text lives in i18n under `learn.terms.<key>.{title,short,long}`;
// this file is the single source of truth for WHICH terms exist, their category, and whether the term has an
// illustrative mini-chart. Both the /learn page and the inline <TermInfo> tooltip read from here, so a term is
// defined once and referenced everywhere by its key.
export const LEARN_CATEGORIES = ['macroRates', 'technical', 'returnsRisk', 'bonds', 'viop', 'general'];

// The 'macroRates' category is NOT listed here — it is rendered data-driven from the app's live macro indicators
// (real EVDS series + their existing names/descriptions), so every indicator the app tracks is explained.
export const LEARN_TERMS = [
  { key: 'movingAverage', category: 'technical' },
  { key: 'rsi', category: 'technical', chart: 'rsi' },
  { key: 'macd', category: 'technical', chart: 'macd' },
  { key: 'volume', category: 'technical' },
  { key: 'volatility', category: 'returnsRisk' },
  { key: 'return', category: 'returnsRisk' },
  { key: 'drawdown', category: 'returnsRisk' },
  { key: 'benchmark', category: 'returnsRisk' },
  { key: 'coupon', category: 'bonds' },
  { key: 'maturity', category: 'bonds' },
  { key: 'cpiLinked', category: 'bonds' },
  { key: 'accruedCoupon', category: 'bonds' },
  { key: 'par', category: 'bonds' },
  { key: 'discountBill', category: 'bonds' },
  { key: 'futures', category: 'viop' },
  { key: 'leverage', category: 'viop' },
  { key: 'longShort', category: 'viop' },
  { key: 'allocation', category: 'general' },
  { key: 'perDateFx', category: 'general' },
];

const TERM_KEYS = new Set(LEARN_TERMS.map((t) => t.key));

// True when a term key has catalog content — guards the inline <TermInfo> against a typo silently rendering an
// empty tooltip.
export function isKnownTerm(key) {
  return TERM_KEYS.has(key);
}

export function termsByCategory(category) {
  return LEARN_TERMS.filter((t) => t.category === category);
}
