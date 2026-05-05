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
    onSuccess: (created) => {
      queryClient.setQueryData(LISTS_KEY, (existing) => {
        if (!Array.isArray(existing)) return [created];
        if (existing.some((w) => w.id === created.id)) return existing;
        return [...existing, created];
      });
      queryClient.invalidateQueries({ queryKey: LISTS_KEY });
    },
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
    onMutate: async (id) => {
      await queryClient.cancelQueries({ queryKey: LISTS_KEY });
      const prev = queryClient.getQueryData(LISTS_KEY);
      queryClient.setQueryData(LISTS_KEY, (old) =>
        Array.isArray(old) ? old.filter((w) => w.id !== id) : old
      );
      return { prev };
    },
    onError: (_e, _id, ctx) => {
      if (ctx?.prev) queryClient.setQueryData(LISTS_KEY, ctx.prev);
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: LISTS_KEY }),
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

export function useRemoveWatchlistItem(activeListId) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: watchlistService.removeItem,
    onMutate: async (itemId) => {
      if (activeListId == null) return {};
      const key = ITEMS_KEY(activeListId);
      await queryClient.cancelQueries({ queryKey: key });
      const prevItems = queryClient.getQueryData(key);
      const prevLists = queryClient.getQueryData(LISTS_KEY);
      queryClient.setQueryData(key, (old) =>
        Array.isArray(old) ? old.filter((it) => it.id !== itemId) : old
      );
      queryClient.setQueryData(LISTS_KEY, (old) =>
        Array.isArray(old)
          ? old.map((w) => w.id === activeListId ? { ...w, itemCount: Math.max(0, (w.itemCount ?? 1) - 1) } : w)
          : old
      );
      return { prevItems, prevLists, key };
    },
    onError: (_e, _id, ctx) => {
      if (ctx?.key && ctx?.prevItems) queryClient.setQueryData(ctx.key, ctx.prevItems);
      if (ctx?.prevLists) queryClient.setQueryData(LISTS_KEY, ctx.prevLists);
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: LISTS_KEY }),
  });
}
