import { useMutation, useQueryClient } from '@tanstack/react-query';
import { userChartPreferenceService } from '../services/userChartService';
import { CHART_DATA_KEY, useUserChartData } from './useUserChartData';

export function useUserChartPreferences(type, code, range, enabled = true) {
  const bundle = useUserChartData(type, code, range, enabled);
  return {
    ...bundle,
    data: bundle.data?.preferences,
  };
}

export function useUpdateUserChartPreferences(type, code, range) {
  const queryClient = useQueryClient();
  const queryKey = CHART_DATA_KEY(type, code, range);
  return useMutation({
    mutationFn: (config) => userChartPreferenceService.save({ type, code, config }),
    onMutate: async (config) => {
      await queryClient.cancelQueries({ queryKey });
      const previous = queryClient.getQueryData(queryKey);
      queryClient.setQueryData(queryKey, (old) => ({
        ...(old ?? {}),
        preferences: { config, updatedAt: new Date().toISOString() },
      }));
      return { previous };
    },
    onError: (_err, _vars, context) => {
      if (context?.previous !== undefined) {
        queryClient.setQueryData(queryKey, context.previous);
      }
    },
    onSuccess: (saved) => {
      if (!saved) return;
      queryClient.setQueryData(queryKey, (old) => ({
        ...(old ?? {}),
        preferences: saved,
      }));
    },
  });
}
