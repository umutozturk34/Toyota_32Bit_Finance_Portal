export const ASSET_TYPE_LABELS = {
  CRYPTO: 'Kripto',
  STOCK: 'Hisse',
  FOREX: 'Döviz',
  FUND: 'Fon',
  CASH: 'Nakit',
};

export const ASSET_TYPE_COLORS = {
  CRYPTO: '#f59e0b',
  STOCK: '#10b981',
  FOREX: '#06b6d4',
  FUND: '#8b5cf6',
};

export const ASSET_TYPE_CHART_COLORS = {
  CRYPTO: '#fbbf24',
  STOCK: '#34d399',
  FOREX: '#22d3ee',
  FUND: '#c084fc',
};

export const ASSET_TYPE_STYLES = {
  CRYPTO: { bg: 'bg-warning/10', text: 'text-warning' },
  STOCK: { bg: 'bg-success/10', text: 'text-success' },
  FOREX: { bg: 'bg-cyan-400/10', text: 'text-cyan-400' },
  FUND: { bg: 'bg-violet-400/10', text: 'text-violet-400' },
};

export const ASSET_TYPE_FILTERS = [
  { id: null, label: 'Tümü' },
  { id: 'CRYPTO', label: 'Kripto' },
  { id: 'STOCK', label: 'Hisse' },
  { id: 'FOREX', label: 'Döviz' },
  { id: 'FUND', label: 'Fon' },
];

export const ASSET_TYPE_TABS = [
  { id: 'ALL', label: 'Tümü' },
  { id: 'CRYPTO', label: 'Kripto' },
  { id: 'STOCK', label: 'Hisse' },
  { id: 'FOREX', label: 'Döviz' },
  { id: 'FUND', label: 'Fon' },
];

export const PORTFOLIO_RANGES = [
  { id: '1M', label: '1A' },
  { id: '3M', label: '3A' },
  { id: '6M', label: '6A' },
  { id: '1Y', label: '1Y' },
  { id: 'ALL', label: 'Maks' },
];
