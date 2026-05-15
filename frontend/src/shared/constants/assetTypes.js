export const ASSET_TYPE_COLORS = {
  CRYPTO: '#f59e0b',
  STOCK: '#10b981',
  FOREX: '#06b6d4',
  FUND: '#8b5cf6',
  COMMODITY: '#f97316',
  VIOP: '#6366f1',
};

export const ASSET_TYPE_CHART_COLORS = {
  CRYPTO: '#fbbf24',
  STOCK: '#34d399',
  FOREX: '#22d3ee',
  FUND: '#c084fc',
  COMMODITY: '#fb923c',
  VIOP: '#818cf8',
};

export const ASSET_TYPE_STYLES = {
  CRYPTO: { bg: 'bg-warning/10', text: 'text-warning' },
  STOCK: { bg: 'bg-success/10', text: 'text-success' },
  FOREX: { bg: 'bg-cyan-400/10', text: 'text-cyan-400' },
  FUND: { bg: 'bg-violet-400/10', text: 'text-violet-400' },
  COMMODITY: { bg: 'bg-orange-400/10', text: 'text-orange-400' },
  VIOP: { bg: 'bg-indigo-400/10', text: 'text-indigo-400' },
};

export const ASSET_TYPE_FILTERS = [
  { id: null },
  { id: 'CRYPTO' },
  { id: 'STOCK' },
  { id: 'FOREX' },
  { id: 'FUND' },
  { id: 'COMMODITY' },
  { id: 'VIOP' },
];

export const ASSET_TYPE_TABS = [
  { id: 'ALL' },
  { id: 'CRYPTO' },
  { id: 'STOCK' },
  { id: 'FOREX' },
  { id: 'FUND' },
  { id: 'COMMODITY' },
  { id: 'VIOP' },
];

export const PORTFOLIO_RANGE_IDS = ['1M', '3M', '6M', '1Y', '5Y', 'ALL'];
