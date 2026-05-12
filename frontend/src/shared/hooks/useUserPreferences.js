import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { userPreferenceService } from '../services/userPreferenceService';
import { useAuth } from '../../features/auth/AuthContext';
import { STALE, GC } from '../constants/query';

const PREFERENCES_KEY = ['userPreferences'];

const FALLBACK = Object.freeze({
  userSub: null,
  theme: 'DARK',
  language: 'en',
  timezone: 'Europe/Istanbul',
  defaultChartRange: '1M',
  onboardingCompleted: false,
});

export function useUserPreferences() {
  const { isAuthenticated, loading } = useAuth();
  const query = useQuery({
    queryKey: PREFERENCES_KEY,
    queryFn: userPreferenceService.get,
    staleTime: STALE.NEVER,
    gcTime: GC.NEVER,
    retry: false,
    enabled: isAuthenticated && !loading,
  });
  return {
    ...query,
    preferences: query.data ?? FALLBACK,
    hasResolvedPreferences: query.isSuccess,
  };
}

export function useUpdateUserPreferences() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: userPreferenceService.update,
    onMutate: async (partial) => {
      await queryClient.cancelQueries({ queryKey: PREFERENCES_KEY });
      const previous = queryClient.getQueryData(PREFERENCES_KEY) ?? FALLBACK;
      queryClient.setQueryData(PREFERENCES_KEY, { ...previous, ...partial });
      return { previous };
    },
    onError: (_err, _vars, context) => {
      if (context?.previous) queryClient.setQueryData(PREFERENCES_KEY, context.previous);
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: PREFERENCES_KEY });
    },
  });
}
