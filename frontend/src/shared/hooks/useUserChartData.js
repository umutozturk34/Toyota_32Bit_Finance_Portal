import { useQuery } from '@tanstack/react-query';
import { userChartDataService } from '../services/userChartService';
import { useAuth } from '../../features/auth/AuthContext';

export const CHART_DATA_KEY = (type, code, range) => ['userChartData', type, code, range || 'all'];

export function useUserChartData(type, code, range, enabled = true) {
  const { isAuthenticated, loading } = useAuth();
  return useQuery({
    queryKey: CHART_DATA_KEY(type, code, range),
    queryFn: () => userChartDataService.get(type, code, range),
    enabled: enabled && isAuthenticated && !loading && !!type && !!code,
    staleTime: Infinity,
    retry: 0,
  });
}
