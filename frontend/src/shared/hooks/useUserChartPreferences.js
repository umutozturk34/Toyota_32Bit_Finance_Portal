import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { userChartPreferenceService } from '../services/userChartService';
import { useAuth } from '../../features/auth/AuthContext';

export const PREF_KEY = (type, code) => ['userChartPreferences', type, code];

export function useUserChartPreferences(type, code) {
  const { isAuthenticated, loading } = useAuth();
  return useQuery({
    queryKey: PREF_KEY(type, code),
    queryFn: () => userChartPreferenceService.get(type, code),
    enabled: isAuthenticated && !loading && !!type && !!code,
    staleTime: Infinity,
    retry: 0,
  });
}

export function useUpdateUserChartPreferences(type, code) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (config) => userChartPreferenceService.save({ type, code, config }),
    onMutate: async (config) => {
      await queryClient.cancelQueries({ queryKey: PREF_KEY(type, code) });
      const previous = queryClient.getQueryData(PREF_KEY(type, code));
      queryClient.setQueryData(PREF_KEY(type, code), { config, updatedAt: new Date().toISOString() });
      return { previous };
    },
    onError: (_err, _vars, context) => {
      if (context?.previous !== undefined) {
        queryClient.setQueryData(PREF_KEY(type, code), context.previous);
      }
    },
    onSuccess: (data) => {
      if (data) queryClient.setQueryData(PREF_KEY(type, code), data);
    },
  });
}
