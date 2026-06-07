export const ASSET_ROUTE = {
  STOCK: 'stocks',
  CRYPTO: 'crypto',
  FOREX: 'forex',
  COMMODITY: 'commodities',
  VIOP: 'viop',
  FUND: 'funds',
};

export function buildBackTarget(from, fromType, fromCode) {
  if (from === 'beaters') return '/analytics?tab=beaters';
  // The beater WIDGET lives on the market-overview home, not the beater page, so its click-through must
  // return there — otherwise "back" dropped the user on /analytics?tab=beaters they never visited.
  if (from === 'overview') return '/market';
  if (from === 'portfolio') return '/portfolio';
  if (from === 'asset' && fromType && fromCode) {
    const segment = ASSET_ROUTE[fromType];
    if (segment) return `/${segment}/${encodeURIComponent(fromCode)}`;
  }
  return null;
}
