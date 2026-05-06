import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { adminMessageService, messageService } from '../services/messageService';
import { useAuth } from '../../features/auth/AuthContext';

const USER_INBOX_KEY = (params) => ['messages', 'inbox', params];
const USER_SENT_KEY = (params) => ['messages', 'sent', params];
const USER_UNREAD_KEY = ['messages', 'unread-count'];
const ADMIN_INBOX_KEY = (params) => ['messages', 'admin-inbox', params];
const ADMIN_INBOX_COUNT_KEY = ['messages', 'admin-inbox-count'];

export function useUserInbox({ page = 0, size = 20 } = {}) {
  const { isAuthenticated, loading } = useAuth();
  return useQuery({
    queryKey: USER_INBOX_KEY({ page, size }),
    queryFn: () => messageService.inbox({ page, size }),
    enabled: isAuthenticated && !loading,
    staleTime: 30_000,
  });
}

export function useUserSent({ page = 0, size = 20 } = {}) {
  const { isAuthenticated, loading } = useAuth();
  return useQuery({
    queryKey: USER_SENT_KEY({ page, size }),
    queryFn: () => messageService.sent({ page, size }),
    enabled: isAuthenticated && !loading,
    staleTime: 30_000,
  });
}

export function useUnreadMessageCount() {
  const { isAuthenticated, loading } = useAuth();
  return useQuery({
    queryKey: USER_UNREAD_KEY,
    queryFn: messageService.unreadCount,
    enabled: isAuthenticated && !loading,
    refetchInterval: 60_000,
    staleTime: 30_000,
  });
}

export function useSendMessage() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: messageService.send,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['messages'] }),
  });
}

export function useMarkMessageRead() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: messageService.markRead,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['messages'] }),
  });
}

export function useAdminInbox({ page = 0, size = 20 } = {}) {
  const { isAuthenticated, loading } = useAuth();
  return useQuery({
    queryKey: ADMIN_INBOX_KEY({ page, size }),
    queryFn: () => adminMessageService.inbox({ page, size }),
    enabled: isAuthenticated && !loading,
    staleTime: 30_000,
  });
}

export function useAdminInboxCount() {
  const { isAuthenticated, loading } = useAuth();
  return useQuery({
    queryKey: ADMIN_INBOX_COUNT_KEY,
    queryFn: adminMessageService.inboxCount,
    enabled: isAuthenticated && !loading,
    refetchInterval: 60_000,
    staleTime: 30_000,
  });
}

export function useSendAdminMessage() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: adminMessageService.send,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['messages'] }),
  });
}
