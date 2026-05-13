import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { STALE } from '../constants/query';
import { priceAlertService } from '../services/priceAlertService';
import { useAuth } from '../../features/auth/AuthContext';

const KEY = (params) => ['priceAlerts', params];

export function usePriceAlerts({ page = 0, size = 50, enabled = true } = {}) {
  const { isAuthenticated, loading } = useAuth();
  return useQuery({
    queryKey: KEY({ page, size }),
    queryFn: () => priceAlertService.list({ page, size }),
    enabled: enabled && isAuthenticated && !loading,
    staleTime: STALE.SHORT,
  });
}

export function useCreatePriceAlert() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: priceAlertService.create,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['priceAlerts'] }),
  });
}

export function useUpdatePriceAlert() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, payload }) => priceAlertService.update(id, payload),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['priceAlerts'] }),
  });
}

export function useReactivatePriceAlert() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: priceAlertService.reactivate,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['priceAlerts'] }),
  });
}

export function useDeletePriceAlert() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: priceAlertService.remove,
    onMutate: async (id) => {
      const matches = queryClient.getQueriesData({ queryKey: ['priceAlerts'] });
      const snapshots = matches.map(([key, data]) => [key, data]);
      matches.forEach(([key, data]) => {
        if (!data) return;
        if (Array.isArray(data?.content)) {
          queryClient.setQueryData(key, {
            ...data,
            content: data.content.filter((a) => a.id !== id),
            totalElements: Math.max(0, (data.totalElements ?? 1) - 1),
          });
        } else if (Array.isArray(data?.items)) {
          queryClient.setQueryData(key, {
            ...data,
            items: data.items.filter((a) => a.id !== id),
          });
        }
      });
      return { snapshots };
    },
    onError: (_e, _id, ctx) => {
      ctx?.snapshots?.forEach(([key, data]) => queryClient.setQueryData(key, data));
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: ['priceAlerts'] }),
  });
}
