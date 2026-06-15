import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { STALE } from '../constants/query';
import { watchlistService } from '../services/watchlistService';
import { useAuth } from '../../features/auth/useAuth';

const LISTS_KEY = ['watchlists'];
const ITEMS_KEY = (id, params) => ['watchlists', id, 'items', params];

export function useWatchlists({ enabled = true } = {}) {
  const { isAuthenticated, loading } = useAuth();
  return useQuery({
    queryKey: LISTS_KEY,
    queryFn: watchlistService.list,
    enabled: enabled && isAuthenticated && !loading,
    staleTime: STALE.MEDIUM,
  });
}

export function useWatchlistItems(id, { sort = 'CUSTOM', direction = 'ASC', enabled = true } = {}) {
  const { isAuthenticated, loading } = useAuth();
  return useQuery({
    queryKey: ITEMS_KEY(id, { sort, direction }),
    queryFn: () => watchlistService.listItems(id, { sort, direction }),
    enabled: enabled && isAuthenticated && !loading && id != null,
    staleTime: STALE.SHORT,
    // Keep the current rows on screen while a sort/direction change refetches under a new query key. Without
    // this the new key has no cached data → isLoading flips true → the list/empty-state unmounts and re-runs its
    // entrance animation, which reads as a jarring "snap" when you only changed the sort.
    placeholderData: (prev) => prev,
  });
}

export function useReorderWatchlistItems(watchlistId) {
  const queryClient = useQueryClient();
  const itemsPrefix = ['watchlists', watchlistId, 'items'];
  const isCustomCache = (key) => {
    const params = key[3];
    return !params || !params.sort || params.sort === 'CUSTOM';
  };
  return useMutation({
    mutationFn: (itemIds) => watchlistService.reorder(watchlistId, itemIds),
    onMutate: async (itemIds) => {
      if (watchlistId == null) return {};
      await queryClient.cancelQueries({ queryKey: itemsPrefix });
      const matches = queryClient.getQueriesData({ queryKey: itemsPrefix });
      const snapshots = matches
        .filter(([key]) => isCustomCache(key))
        .map(([key, data]) => [key, data]);
      matches.forEach(([key, data]) => {
        if (!isCustomCache(key)) return;
        if (!Array.isArray(data)) return;
        const lookup = new Map(data.map((it) => [it.id, it]));
        const reordered = itemIds.map((id) => lookup.get(id)).filter(Boolean);
        if (reordered.length === data.length) {
          queryClient.setQueryData(key, reordered);
        }
      });
      return { snapshots };
    },
    onError: (_err, _vars, ctx) => {
      ctx?.snapshots?.forEach(([key, data]) => queryClient.setQueryData(key, data));
    },
    onSettled: () => queryClient.invalidateQueries({
      predicate: (q) =>
        q.queryKey[0] === 'watchlists' &&
        q.queryKey[1] === watchlistId &&
        q.queryKey[2] === 'items',
    }),
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

export function useUpdateWatchlistItem(watchlistId) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ itemId, payload }) => watchlistService.updateItem(itemId, payload),
    onSuccess: () => {
      if (watchlistId != null) {
        queryClient.invalidateQueries({ queryKey: ['watchlists', watchlistId, 'items'] });
      }
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
      const itemsPrefix = ['watchlists', activeListId, 'items'];
      await queryClient.cancelQueries({ queryKey: itemsPrefix });
      const matches = queryClient.getQueriesData({ queryKey: itemsPrefix });
      const snapshots = matches.map(([key, data]) => [key, data]);
      matches.forEach(([key, data]) => {
        if (Array.isArray(data)) {
          queryClient.setQueryData(key, data.filter((it) => it.id !== itemId));
        }
      });
      const prevLists = queryClient.getQueryData(LISTS_KEY);
      queryClient.setQueryData(LISTS_KEY, (old) =>
        Array.isArray(old)
          ? old.map((w) => w.id === activeListId ? { ...w, itemCount: Math.max(0, (w.itemCount ?? 1) - 1) } : w)
          : old
      );
      return { snapshots, prevLists };
    },
    onError: (_e, _id, ctx) => {
      ctx?.snapshots?.forEach(([key, data]) => queryClient.setQueryData(key, data));
      if (ctx?.prevLists) queryClient.setQueryData(LISTS_KEY, ctx.prevLists);
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: LISTS_KEY }),
  });
}
