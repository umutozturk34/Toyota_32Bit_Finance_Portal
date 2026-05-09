import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { userChartDrawingService } from '../services/userChartService';
import { useAuth } from '../../features/auth/AuthContext';

const DRAW_KEY = (type, code) => ['userChartDrawings', type, code];

export function useUserChartDrawings(type, code) {
  const { isAuthenticated, loading } = useAuth();
  return useQuery({
    queryKey: DRAW_KEY(type, code),
    queryFn: () => userChartDrawingService.get(type, code),
    enabled: isAuthenticated && !loading && !!type && !!code,
    staleTime: Infinity,
    retry: 1,
  });
}

export function useUpdateUserChartDrawings(type, code) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (drawings) => userChartDrawingService.save({ type, code, drawings }),
    onMutate: async (drawings) => {
      await queryClient.cancelQueries({ queryKey: DRAW_KEY(type, code) });
      const previous = queryClient.getQueryData(DRAW_KEY(type, code));
      queryClient.setQueryData(DRAW_KEY(type, code), { drawings, updatedAt: new Date().toISOString() });
      return { previous };
    },
    onError: (_err, _vars, context) => {
      if (context?.previous !== undefined) {
        queryClient.setQueryData(DRAW_KEY(type, code), context.previous);
      }
    },
    onSuccess: (data) => {
      if (data) queryClient.setQueryData(DRAW_KEY(type, code), data);
    },
  });
}
