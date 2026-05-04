import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { adminUserService } from './adminUserService';

const KEY = (params) => ['adminUsers', params];

export function useAdminUsers(params) {
  return useQuery({
    queryKey: KEY(params),
    queryFn: () => adminUserService.list(params),
    staleTime: 30_000,
  });
}

export function useAdminUserCount(search) {
  return useQuery({
    queryKey: ['adminUsers', 'count', search],
    queryFn: () => adminUserService.count({ search }),
    staleTime: 30_000,
  });
}

export function useBanUser() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: adminUserService.ban,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['adminUsers'] }),
  });
}

export function useUnbanUser() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: adminUserService.unban,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['adminUsers'] }),
  });
}
