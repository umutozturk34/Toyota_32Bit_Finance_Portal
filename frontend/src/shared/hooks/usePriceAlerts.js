import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { priceAlertService } from '../services/priceAlertService';
import { useAuth } from '../../features/auth/AuthContext';

const KEY = (params) => ['priceAlerts', params];

export function usePriceAlerts({ page = 0, size = 50 } = {}) {
  const { isAuthenticated, loading } = useAuth();
  return useQuery({
    queryKey: KEY({ page, size }),
    queryFn: () => priceAlertService.list({ page, size }),
    enabled: isAuthenticated && !loading,
    staleTime: 30_000,
  });
}

export function useCreatePriceAlert() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: priceAlertService.create,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['priceAlerts'] }),
  });
}

export function useDeletePriceAlert() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: priceAlertService.remove,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['priceAlerts'] }),
  });
}
