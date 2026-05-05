import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { notificationService } from '../services/notificationService';
import { useAuth } from '../../features/auth/AuthContext';

const LIST_KEY = (params) => ['notifications', params];
const COUNT_KEY = ['notifications', 'unread-count'];

export function useNotifications({ unreadOnly = false, page = 0, size = 20 } = {}) {
  const { isAuthenticated, loading } = useAuth();
  return useQuery({
    queryKey: LIST_KEY({ unreadOnly, page, size }),
    queryFn: () => notificationService.list({ unreadOnly, page, size }),
    enabled: isAuthenticated && !loading,
    staleTime: 30_000,
  });
}

export function useUnreadNotificationCount() {
  const { isAuthenticated, loading } = useAuth();
  return useQuery({
    queryKey: COUNT_KEY,
    queryFn: notificationService.unreadCount,
    enabled: isAuthenticated && !loading,
    refetchInterval: 60_000,
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
