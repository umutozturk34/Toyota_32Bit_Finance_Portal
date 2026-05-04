import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { notificationPreferenceService } from '../services/notificationPreferenceService';
import { useAuth } from '../../features/auth/AuthContext';

const KEY = ['notificationPreferences'];

const FALLBACK = Object.freeze({
  emailEnabled: true,
  emailPriceAlerts: true,
  inappPriceAlerts: true,
  emailWatchlist: false,
  inappWatchlist: true,
  emailReports: true,
  inappReports: true,
  emailMessages: false,
  inappMessages: true,
  emailSystem: false,
  inappSystem: true,
  quietHoursStart: null,
  quietHoursEnd: null,
});

export function useNotificationPreferences() {
  const { isAuthenticated, loading } = useAuth();
  const query = useQuery({
    queryKey: KEY,
    queryFn: notificationPreferenceService.get,
    staleTime: Infinity,
    enabled: isAuthenticated && !loading,
  });
  return {
    ...query,
    preferences: query.data ?? FALLBACK,
  };
}

export function useUpdateNotificationPreferences() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: notificationPreferenceService.update,
    onMutate: async (partial) => {
      await queryClient.cancelQueries({ queryKey: KEY });
      const previous = queryClient.getQueryData(KEY) ?? FALLBACK;
      queryClient.setQueryData(KEY, { ...previous, ...partial });
      return { previous };
    },
    onError: (_err, _vars, context) => {
      if (context?.previous) queryClient.setQueryData(KEY, context.previous);
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: KEY });
    },
  });
}
