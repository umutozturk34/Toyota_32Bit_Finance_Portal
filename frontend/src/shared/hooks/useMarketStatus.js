import { useQuery } from '@tanstack/react-query';
import { marketStatusService } from '../services/marketStatusService';
import { useAuth } from '../../features/auth/AuthContext';

const STALE_MS = 60_000;
const REFETCH_MS = 60_000;

const KEY = ['market-status', 'list'];

export function useMarketStatus() {
  const { isAuthenticated, loading } = useAuth();
  return useQuery({
    queryKey: KEY,
    queryFn: marketStatusService.list,
    enabled: isAuthenticated && !loading,
    staleTime: STALE_MS,
    refetchInterval: REFETCH_MS,
  });
}

export function useMarketSession(market) {
  const { data, isLoading } = useMarketStatus();
  if (!market || isLoading || !Array.isArray(data)) return { entry: null, isLoading };
  const entry = data.find((m) => m.market === market) ?? null;
  return { entry, isLoading };
}
