import { useQuery } from '@tanstack/react-query';
import { STALE } from '../constants/query';
import { unifiedMarketService } from '../services/unifiedMarketService';
import { useAuth } from '../../features/auth/useAuth';

const PREFIX_BY_TYPE = {
  CRYPTO: 'crypto',
  STOCK: 'stock',
  FOREX: 'forex',
  FUND: 'fund',
  COMMODITY: 'commodity',
  BOND: 'bond',
};

export function useAssetMeta(marketType, assetCode) {
  const { isAuthenticated, loading } = useAuth();
  const prefix = PREFIX_BY_TYPE[marketType] ?? 'asset';
  return useQuery({
    queryKey: [prefix, assetCode],
    queryFn: () => unifiedMarketService.getByCode(marketType, assetCode),
    enabled: isAuthenticated && !loading && !!marketType && !!assetCode,
    staleTime: STALE.MEDIUM,
  });
}
