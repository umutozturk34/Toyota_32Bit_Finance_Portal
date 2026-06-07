export const PAGE_SIZE = 12;

// Analytics instrument type → the market type the detail route is keyed by (SPOT is the stock route).
export const ANALYTICS_TO_MARKET_TYPE = { SPOT: 'STOCK', CRYPTO: 'CRYPTO', FOREX: 'FOREX', FUND: 'FUND', COMMODITY: 'COMMODITY' };

export const FIXED_TYPE_ORDER = ['SPOT', 'CRYPTO', 'FOREX', 'FUND', 'COMMODITY'];

export const TYPE_BADGE = {
  SPOT:      { label: 'BIST',      color: '#5E6AD2' },
  CRYPTO:    { label: 'CRYPTO',    color: '#f97316' },
  FOREX:     { label: 'FOREX',     color: '#06b6d4' },
  FUND:      { label: 'FUND',      color: '#8b5cf6' },
  COMMODITY: { label: 'COMMODITY', color: '#f59e0b' },
};

export const RISK_STYLE = {
  LOW:    { key: 'analytics.returns.riskLow',    badge: 'bg-success/10 text-success border-success/30', chip: 'bg-success/15 text-success border-success/40', idle: 'text-success/70 border-success/25 hover:border-success/50' },
  MEDIUM: { key: 'analytics.returns.riskMedium', badge: 'bg-warning/10 text-warning border-warning/30', chip: 'bg-warning/15 text-warning border-warning/40', idle: 'text-warning/70 border-warning/25 hover:border-warning/50' },
  HIGH:   { key: 'analytics.returns.riskHigh',   badge: 'bg-danger/10 text-danger border-danger/30',   chip: 'bg-danger/15 text-danger border-danger/40',   idle: 'text-danger/70 border-danger/25 hover:border-danger/50' },
};

export const SORT_OPTIONS = [
  { id: 'return', labelKey: 'analytics.returns.return' },
  { id: 'riskAdj', labelKey: 'analytics.returns.riskAdjusted' },
  { id: 'price', labelKey: 'analytics.returns.price' },
  { id: 'delta', labelKey: 'analytics.returns.deltaTry' },
  { id: 'vol', labelKey: 'analytics.returns.volatility' },
];

export const CCY_SYMBOL = { TRY: '₺', USD: '$', EUR: '€' };
