import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { userCredentialService } from '../services/userCredentialService';
import { useAuth } from '../../features/auth/AuthContext';

const PENDING_KEY = ['emailChange', 'pending'];

export function usePendingEmailChange() {
  const { isAuthenticated, loading } = useAuth();
  return useQuery({
    queryKey: PENDING_KEY,
    queryFn: userCredentialService.getPendingEmailChange,
    enabled: isAuthenticated && !loading,
    staleTime: 30_000,
  });
}

export function useInitiateEmailChange() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: userCredentialService.initiateEmailChange,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: PENDING_KEY }),
  });
}

export function useConfirmEmailChange() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: userCredentialService.confirmEmailChange,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: PENDING_KEY }),
  });
}

export function useCancelEmailChange() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: userCredentialService.cancelEmailChange,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: PENDING_KEY }),
  });
}
