import { ArrowUp, ArrowDown, TrendingUp, TrendingDown } from 'lucide-react';

export const WATCHLIST_SORT_OPTIONS = [
  { id: 'CUSTOM', label: 'Sıralamam' },
  { id: 'NAME', label: 'Alfabetik' },
  { id: 'CURRENT_PRICE', label: 'Fiyat' },
  { id: 'CHANGE_PERCENT', label: '% Değişim' },
  { id: 'ADDED_AT', label: 'Eklenme tarihi' },
];

export const DIRECTION_META = {
  ABOVE: { label: 'üstüne', Icon: ArrowUp, tint: 'text-success' },
  BELOW: { label: 'altına', Icon: ArrowDown, tint: 'text-danger' },
  CHANGE_PCT_UP: { label: '% yükseliş', Icon: TrendingUp, tint: 'text-success' },
  CHANGE_PCT_DOWN: { label: '% düşüş', Icon: TrendingDown, tint: 'text-danger' },
};

const ROUTE_BY_TYPE = {
  CRYPTO: (code) => `/crypto/${code}`,
  STOCK: (code) => `/stocks/${code}`,
  FOREX: (code) => `/forex/${code}`,
  FUND: (code) => `/funds/${code}`,
  COMMODITY: (code) => `/commodities/${code}`,
  BOND: () => '/bonds',
};

export function assetRoute(marketType, assetCode) {
  const builder = ROUTE_BY_TYPE[marketType];
  return builder ? builder(assetCode) : null;
}
