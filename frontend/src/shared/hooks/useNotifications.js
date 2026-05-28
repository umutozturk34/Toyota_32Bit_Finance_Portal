import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { notificationService } from '../services/notificationService';
import { useAuth } from '../../features/auth/useAuth';
import { STALE } from '../constants/query';

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
    staleTime: STALE.SHORT,
    refetchOnWindowFocus: false,
  });
}

export function useUnreadNotificationCount() {
  const { isAuthenticated, loading, user } = useAuth();
  const userSub = user?.id ?? null;
  return useQuery({
    queryKey: COUNT_KEY(userSub),
    queryFn: notificationService.unreadCount,
    enabled: isAuthenticated && !loading && Boolean(userSub),
    staleTime: STALE.MEDIUM,
    refetchOnWindowFocus: false,
  });
}

export function useMarkNotificationRead() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: notificationService.markRead,
    // Mark the row read in place across cached pages instead of invalidating the list. Auto-read
    // fires per visible row, so refetching the whole infinite query each time churned it — it
    // dropped in-flight fetchNextPage calls (pagination stalled) and emptied the unread view.
    onMutate: (id) => {
      const now = new Date().toISOString();
      queryClient.setQueriesData({ queryKey: ['notifications'] }, (old) => {
        if (!old?.pages) return old;
        return {
          ...old,
          pages: old.pages.map((p) => (p?.content
            ? { ...p, content: p.content.map((n) => (n.id === id && n.readAt == null ? { ...n, readAt: now } : n)) }
            : p)),
        };
      });
    },
    onSettled: () => {
      queryClient.invalidateQueries({
        predicate: (q) => q.queryKey[0] === 'notifications'
          && q.queryKey[q.queryKey.length - 1] === 'unread-count',
      });
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
