import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { adminMessageService, messageService } from '../services/messageService';
import { useAuth } from '../../features/auth/AuthContext';

const USER_INBOX_KEY = (params) => ['messages', 'inbox', params];
const USER_SENT_KEY = (params) => ['messages', 'sent', params];
const USER_UNREAD_KEY = ['messages', 'unread-count'];
export function useUserInbox({ page = 0, size = 20 } = {}) {
  const { isAuthenticated, loading } = useAuth();
  return useQuery({
    queryKey: USER_INBOX_KEY({ page, size }),
    queryFn: () => messageService.inbox({ page, size }),
    enabled: isAuthenticated && !loading,
    staleTime: 15_000,
    refetchInterval: 30_000,
  });
}

export function useUserSent({ page = 0, size = 20 } = {}) {
  const { isAuthenticated, loading } = useAuth();
  return useQuery({
    queryKey: USER_SENT_KEY({ page, size }),
    queryFn: () => messageService.sent({ page, size }),
    enabled: isAuthenticated && !loading,
    staleTime: 15_000,
    refetchInterval: 30_000,
  });
}

export function useUnreadMessageCount() {
  const { isAuthenticated, loading } = useAuth();
  return useQuery({
    queryKey: USER_UNREAD_KEY,
    queryFn: messageService.unreadCount,
    enabled: isAuthenticated && !loading,
    refetchInterval: 30_000,
    staleTime: 15_000,
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

export function useSendAdminMessage() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: adminMessageService.send,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['messages'] }),
  });
}

const ADMIN_CONVERSATIONS_KEY = (params) => ['messages', 'admin-conversations', params];
const ADMIN_CONVERSATION_KEY = (userSub) => ['messages', 'admin-conversation', userSub];

export function useAdminConversations({ page = 0, size = 20, search = '' } = {}) {
  const { isAuthenticated, loading } = useAuth();
  return useQuery({
    queryKey: ADMIN_CONVERSATIONS_KEY({ page, size, search }),
    queryFn: () => adminMessageService.conversations({ page, size, search }),
    enabled: isAuthenticated && !loading,
    staleTime: 15_000,
    refetchInterval: 30_000,
  });
}

export function useAdminConversation(userSub) {
  const { isAuthenticated, loading } = useAuth();
  return useQuery({
    queryKey: ADMIN_CONVERSATION_KEY(userSub),
    queryFn: () => adminMessageService.conversation(userSub),
    enabled: isAuthenticated && !loading && !!userSub,
    staleTime: 5_000,
    refetchInterval: 15_000,
  });
}

export function useCloseConversation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: adminMessageService.closeConversation,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['messages'] }),
  });
}

export function useReopenConversation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: adminMessageService.reopenConversation,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['messages'] }),
  });
}

export function useMarkAdminConversationRead() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: adminMessageService.markConversationRead,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['messages'] }),
  });
}

export function useDeleteConversation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: adminMessageService.deleteConversation,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['messages'] }),
  });
}

export function useBroadcast() {
  return useMutation({
    mutationFn: adminMessageService.broadcast,
  });
}
