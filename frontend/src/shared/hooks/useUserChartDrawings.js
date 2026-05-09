import { useMutation, useQueryClient } from '@tanstack/react-query';
import { userChartDrawingService } from '../services/userChartService';
import { CHART_DATA_KEY, useUserChartData } from './useUserChartData';

export function useUserChartDrawings(type, code, range, enabled = true) {
  const bundle = useUserChartData(type, code, range, enabled);
  return {
    ...bundle,
    data: bundle.data?.drawings,
  };
}

export function useUpdateUserChartDrawings(type, code, range) {
  const queryClient = useQueryClient();
  const queryKey = CHART_DATA_KEY(type, code, range);
  return useMutation({
    mutationFn: (drawings) => userChartDrawingService.save({ type, code, drawings }),
    onMutate: async (drawings) => {
      await queryClient.cancelQueries({ queryKey });
      const previous = queryClient.getQueryData(queryKey);
      queryClient.setQueryData(queryKey, (old) => ({
        ...(old ?? {}),
        drawings: { drawings, updatedAt: new Date().toISOString() },
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
        drawings: saved,
      }));
    },
  });
}
