import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { watchlistService } from '../services/watchlistService';
import { useAuth } from '../../features/auth/AuthContext';

const LISTS_KEY = ['watchlists'];
const ITEMS_KEY = (id) => ['watchlists', id, 'items'];

export function useWatchlists() {
  const { isAuthenticated, loading } = useAuth();
  return useQuery({
    queryKey: LISTS_KEY,
    queryFn: watchlistService.list,
    enabled: isAuthenticated && !loading,
    staleTime: 60_000,
  });
}

export function useWatchlistItems(id) {
  const { isAuthenticated, loading } = useAuth();
  return useQuery({
    queryKey: ITEMS_KEY(id),
    queryFn: () => watchlistService.listItems(id),
    enabled: isAuthenticated && !loading && id != null,
    staleTime: 30_000,
  });
}

export function useCreateWatchlist() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: watchlistService.create,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: LISTS_KEY }),
  });
}

export function useRenameWatchlist() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, name }) => watchlistService.rename(id, name),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: LISTS_KEY }),
  });
}

export function useDeleteWatchlist() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: watchlistService.remove,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: LISTS_KEY }),
  });
}

export function useAddWatchlistItem() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ watchlistId, ...payload }) => watchlistService.addItem(watchlistId, payload),
    onSuccess: (_data, { watchlistId }) => {
      queryClient.invalidateQueries({ queryKey: ITEMS_KEY(watchlistId) });
      queryClient.invalidateQueries({ queryKey: LISTS_KEY });
    },
  });
}

export function useAddToFavorites() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: watchlistService.addToFavorites,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: LISTS_KEY });
    },
  });
}

export function useRemoveWatchlistItem() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: watchlistService.removeItem,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: LISTS_KEY }),
  });
}
