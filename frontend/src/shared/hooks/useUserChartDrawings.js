import { useMutation, useQueryClient } from '@tanstack/react-query';
import { userChartDrawingService } from '../services/userChartService';
import { useUserChartData } from './useUserChartData';

export function useUserChartDrawings(type, code, range, enabled = true) {
  const bundle = useUserChartData(type, code, range, enabled);
  return {
    ...bundle,
    data: bundle.data?.drawings,
  };
}

export function useUpdateUserChartDrawings(type, code) {
  const queryClient = useQueryClient();
  const bundleScope = ['userChartData', type, code];
  return useMutation({
    mutationFn: (drawings) => userChartDrawingService.save({ type, code, drawings }),
    onMutate: async (drawings) => {
      await queryClient.cancelQueries({ queryKey: bundleScope, exact: false });
      const previous = queryClient.getQueriesData({ queryKey: bundleScope, exact: false });
      const optimistic = { drawings, updatedAt: new Date().toISOString() };
      queryClient.setQueriesData({ queryKey: bundleScope, exact: false }, (old) => ({
        ...(old ?? {}),
        drawings: optimistic,
      }));
      return { previous };
    },
    onError: (_err, _vars, context) => {
      if (Array.isArray(context?.previous)) {
        context.previous.forEach(([key, value]) => {
          queryClient.setQueryData(key, value);
        });
      }
    },
    onSuccess: (saved) => {
      if (!saved) return;
      queryClient.setQueriesData({ queryKey: bundleScope, exact: false }, (old) => ({
        ...(old ?? {}),
        drawings: saved,
      }));
    },
  });
}
