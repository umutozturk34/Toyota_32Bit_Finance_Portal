import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { notificationService } from '../services/notificationService';
import { useAuth } from '../../features/auth/AuthContext';

const LIST_KEY = (userSub, params) => ['notifications', userSub, params];
const COUNT_KEY = (userSub) => ['notifications', userSub, 'unread-count'];

export function useNotifications({ unreadOnly = false, size = 20, search = '' } = {}) {
  const { isAuthenticated, loading, user } = useAuth();
  const userSub = user?.id ?? null;
  return useInfiniteQuery({
    queryKey: LIST_KEY(userSub, { unreadOnly, size, search }),
    queryFn: ({ pageParam = 0 }) => notificationService.list({ unreadOnly, page: pageParam, size, search }),
    initialPageParam: 0,
    getNextPageParam: (last) => {
      const next = (last?.page ?? 0) + 1;
      return next < (last?.totalPages ?? 0) ? next : undefined;
    },
    enabled: isAuthenticated && !loading && Boolean(userSub),
    staleTime: 30_000,
  });
}

export function useUnreadNotificationCount() {
  const { isAuthenticated, loading, user } = useAuth();
  const userSub = user?.id ?? null;
  return useQuery({
    queryKey: COUNT_KEY(userSub),
    queryFn: notificationService.unreadCount,
    enabled: isAuthenticated && !loading && Boolean(userSub),
    staleTime: 30_000,
  });
}

export function useMarkNotificationRead() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: notificationService.markRead,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['notifications'] });
    },
  });
}

export function useMarkAllNotificationsRead() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: notificationService.markAllRead,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['notifications'] });
    },
  });
}

export function useDeleteNotification() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: notificationService.remove,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['notifications'] });
    },
  });
}

export function useDeleteAllNotifications() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: notificationService.removeAll,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['notifications'] });
    },
  });
}
