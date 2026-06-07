import { BarChart3, Activity } from 'lucide-react';

export const MAX_COMPARE = 6;
// Bonds are excluded from Compare (they are rate-level series that never FX-convert and aren't a
// meaningful price-return comparison); excludeTypes={['BOND']} on the search is the belt, this is the
// suspenders — the assets-mode type filter simply never asks for them.
export const MARKET_TYPES_FILTER = 'STOCK,CRYPTO,FOREX,FUND,COMMODITY,VIOP';

export const MODES = [
  { id: 'assets',    labelKey: 'modeAssets',    Icon: BarChart3, filterType: MARKET_TYPES_FILTER },
  { id: 'mixed',     labelKey: 'modeMixed',     Icon: Activity,  filterType: undefined },
];
