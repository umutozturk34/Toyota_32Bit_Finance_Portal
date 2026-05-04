import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { watchlistService } from '../services/watchlistService';
import { useAuth } from '../../features/auth/AuthContext';

const KEY = ['watchlist'];

export function useWatchlist() {
  const { isAuthenticated, loading } = useAuth();
  return useQuery({
    queryKey: KEY,
    queryFn: watchlistService.list,
    enabled: isAuthenticated && !loading,
    staleTime: 60_000,
  });
}

export function useAddWatchlistItem() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: watchlistService.add,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: KEY }),
  });
}

export function useRemoveWatchlistItem() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: watchlistService.remove,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: KEY }),
  });
}
