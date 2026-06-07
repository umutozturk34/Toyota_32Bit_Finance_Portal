export const PAGE_SIZE = 10;
export const FIXED_TYPE_ORDER = ['SPOT', 'CRYPTO', 'FUND', 'FOREX', 'COMMODITY', 'DEPOSIT', 'MACRO'];
export const SORT_OPTIONS = [
  { id: 'rank', labelKey: 'analytics.rankOrder' },
  { id: 'nominal', labelKey: 'analytics.nominalReturn' },
  { id: 'excess', labelKey: 'analytics.excessReturn' },
];
export const BENCHMARK_CATEGORIES = ['INFLATION', 'RATES', 'DEPOSIT'];
export const MACRO_CATEGORY_TO_MARKET_TYPE = {
  DEPOSIT: 'MACRO_DEPOSIT',
  INFLATION: 'MACRO_INFLATION',
  RATES: 'MACRO_RATE',
};
export const ANALYTICS_TO_MARKET_TYPE = {
  SPOT: 'STOCK',
  CRYPTO: 'CRYPTO',
  FOREX: 'FOREX',
  FUND: 'FUND',
  COMMODITY: 'COMMODITY',
  VIOP: 'VIOP',
  BOND: 'BOND',
  DEPOSIT: 'MACRO_DEPOSIT',
};
export const TYPE_BADGE = {
  SPOT:      { label: 'BIST',      color: '#5E6AD2' },
  CRYPTO:    { label: 'CRYPTO',    color: '#f97316' },
  FOREX:     { label: 'FOREX',     color: '#06b6d4' },
  FUND:      { label: 'FUND',      color: '#8b5cf6' },
  COMMODITY: { label: 'COMMODITY', color: '#f59e0b' },
  VIOP:      { label: 'VIOP',      color: '#ef4444' },
  BOND:      { label: 'BOND',      color: '#d946ef' },
  DEPOSIT:   { label: 'DEPOSIT',   color: '#10b981' },
  MACRO:     { label: 'MACRO',     color: '#0ea5e9' },
};
