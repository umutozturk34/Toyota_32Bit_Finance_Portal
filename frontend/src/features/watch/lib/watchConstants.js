import { ArrowUp, ArrowDown, TrendingUp, TrendingDown } from 'lucide-react';

export const WATCHLIST_SORT_OPTION_IDS = ['CUSTOM', 'NAME', 'CURRENT_PRICE', 'CHANGE_PERCENT', 'ADDED_AT'];

export const DIRECTION_META = {
  ABOVE: { labelKey: 'alertRow.direction.ABOVE', Icon: ArrowUp, tint: 'text-success' },
  BELOW: { labelKey: 'alertRow.direction.BELOW', Icon: ArrowDown, tint: 'text-danger' },
  CHANGE_PCT_UP: { labelKey: 'alertRow.direction.CHANGE_PCT_UP', Icon: TrendingUp, tint: 'text-success' },
  CHANGE_PCT_DOWN: { labelKey: 'alertRow.direction.CHANGE_PCT_DOWN', Icon: TrendingDown, tint: 'text-danger' },
};

const ROUTE_BY_TYPE = {
  CRYPTO: (code) => `/crypto/${encodeURIComponent(code)}`,
  STOCK: (code) => `/stocks/${encodeURIComponent(code)}`,
  FOREX: (code) => `/forex/${encodeURIComponent(code)}`,
  FUND: (code) => `/funds/${encodeURIComponent(code)}`,
  COMMODITY: (code) => `/commodities/${encodeURIComponent(code)}`,
  VIOP: (code) => `/viop/${encodeURIComponent(code)}`,
  BOND: (code) => `/bonds/${encodeURIComponent(code)}`,
};

export function assetRoute(marketType, assetCode) {
  const builder = ROUTE_BY_TYPE[marketType];
  return builder ? builder(assetCode) : null;
}
