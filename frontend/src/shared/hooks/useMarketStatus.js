import { useQuery } from '@tanstack/react-query';
import { marketStatusService } from '../services/marketStatusService';
import { useAuth } from '../../features/auth/AuthContext';
import { STALE } from '../constants/query';

const KEY = ['market-status', 'list'];

export function useMarketStatus() {
  const { isAuthenticated, loading } = useAuth();
  return useQuery({
    queryKey: KEY,
    queryFn: marketStatusService.list,
    enabled: isAuthenticated && !loading,
    staleTime: STALE.MEDIUM,
    refetchInterval: STALE.MEDIUM,
  });
}

export function useMarketSession(market) {
  const { data, isLoading } = useMarketStatus();
  if (!market || isLoading || !Array.isArray(data)) return { entry: null, isLoading };
  const entry = data.find((m) => m.market === market) ?? null;
  return { entry, isLoading };
}
