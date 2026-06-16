// Financial-literacy term catalog. Each term's text lives in i18n under `learn.terms.<key>.{title,short,long}`;
// this file is the single source of truth for WHICH terms exist, their category, and whether the term has an
// illustrative mini-chart. Both the /learn page and the inline <TermInfo> tooltip read from here, so a term is
// defined once and referenced everywhere by its key.
export const LEARN_CATEGORIES = ['macroRates', 'technical', 'returnsRisk', 'bonds', 'deposits', 'viop', 'general'];

// The 'macroRates' category is NOT listed here — it is rendered data-driven from the app's live macro indicators
// (real EVDS series + their existing names/descriptions), so every indicator the app tracks is explained.
export const LEARN_TERMS = [
  { key: 'candlestick', category: 'technical', chart: 'candles' },
  { key: 'ohlc', category: 'technical' },
  { key: 'volume', category: 'technical' },
  { key: 'movingAverage', category: 'technical' },
  { key: 'rsi', category: 'technical', chart: 'rsi' },
  { key: 'macd', category: 'technical', chart: 'macd' },
  { key: 'supportResistance', category: 'technical' },
  { key: 'trend', category: 'technical' },
  { key: 'return', category: 'returnsRisk' },
  { key: 'realVsNominal', category: 'returnsRisk' },
  { key: 'volatility', category: 'returnsRisk' },
  { key: 'drawdown', category: 'returnsRisk' },
  { key: 'sharpe', category: 'returnsRisk' },
  { key: 'benchmark', category: 'returnsRisk' },
  { key: 'diversification', category: 'returnsRisk' },
  { key: 'bondVsBill', category: 'bonds' },
  { key: 'coupon', category: 'bonds' },
  { key: 'yield', category: 'bonds' },
  { key: 'maturity', category: 'bonds' },
  { key: 'nominalValue', category: 'bonds' },
  { key: 'par', category: 'bonds' },
  { key: 'cpiLinked', category: 'bonds' },
  { key: 'floatingRate', category: 'bonds' },
  { key: 'accruedCoupon', category: 'bonds' },
  { key: 'discountBill', category: 'bonds' },
  { key: 'sukuk', category: 'bonds' },
  { key: 'goldBond', category: 'bonds' },
  { key: 'cleanDirtyPrice', category: 'bonds', chart: 'cleanDirty' },
  { key: 'secondaryMarket', category: 'bonds' },
  { key: 'deposit', category: 'deposits' },
  { key: 'simpleInterest', category: 'deposits' },
  { key: 'withholding', category: 'deposits' },
  { key: 'futures', category: 'viop' },
  { key: 'option', category: 'viop' },
  { key: 'callOption', category: 'viop', chart: 'callPayoff' },
  { key: 'putOption', category: 'viop', chart: 'putPayoff' },
  { key: 'strikePrice', category: 'viop' },
  { key: 'leverage', category: 'viop' },
  { key: 'longShort', category: 'viop' },
  { key: 'margin', category: 'viop' },
  { key: 'settlement', category: 'viop' },
  { key: 'allocation', category: 'general' },
  { key: 'spread', category: 'general' },
  { key: 'liquidity', category: 'general' },
  { key: 'marketCap', category: 'general' },
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
