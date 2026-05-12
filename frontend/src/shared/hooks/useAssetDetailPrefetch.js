import { useQueryClient } from '@tanstack/react-query';
import { STALE } from '../constants/query';
import { unifiedMarketService } from '../services/unifiedMarketService';

const PREFIX_BY_TYPE = {
  CRYPTO: 'crypto',
  STOCK: 'stock',
  FOREX: 'forex',
  FUND: 'fund',
  COMMODITY: 'commodity',
  BOND: 'bond',
};

const DEFAULT_RANGE = '6M';

export function useAssetDetailPrefetch() {
  const queryClient = useQueryClient();
  return (marketType, assetCode) => {
    const prefix = PREFIX_BY_TYPE[marketType];
    if (!prefix || !assetCode) return;
    queryClient.prefetchQuery({
      queryKey: [prefix, assetCode],
      queryFn: () => unifiedMarketService.getByCode(marketType, assetCode),
      staleTime: STALE.MEDIUM,
    });
    queryClient.prefetchQuery({
      queryKey: [`${prefix}History`, assetCode, DEFAULT_RANGE],
      queryFn: () => unifiedMarketService.getHistory(marketType, assetCode, DEFAULT_RANGE),
      staleTime: STALE.MEDIUM,
    });
  };
}
