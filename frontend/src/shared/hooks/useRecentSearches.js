import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { recentSearchService } from '../services/recentSearchService';
import { useAuth } from '../../features/auth/useAuth';
import { STALE } from '../constants/query';

const KEY = ['recentSearches'];

/**
 * The current user's recent search selections (newest first). Disabled (and resolves to an empty
 * list) when unauthenticated so dropdowns never break for anonymous visitors.
 */
export function useRecentSearches() {
  const { isAuthenticated, loading, user } = useAuth();
  const userSub = user?.id ?? null;
  return useQuery({
    queryKey: KEY,
    queryFn: recentSearchService.list,
    enabled: isAuthenticated && !loading && Boolean(userSub),
    staleTime: STALE.SHORT,
    refetchOnWindowFocus: false,
    placeholderData: [],
  });
}

export function useRecordRecentSearch() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: recentSearchService.record,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: KEY });
    },
  });
}

export function useClearRecentSearches() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: recentSearchService.clear,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: KEY });
    },
  });
}

export function useRemoveRecentSearch() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: recentSearchService.remove,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: KEY });
    },
  });
}
