import { useQuery } from '@tanstack/react-query';
import { marketOverviewService } from '../services/marketOverviewService';
import { useAuth } from '../../features/auth/AuthContext';

const OVERVIEW_KEY = ['marketOverview'];
const STALE_MS = 30_000;

export function useMarketOverview() {
  const { isAuthenticated, loading } = useAuth();
  const query = useQuery({
    queryKey: OVERVIEW_KEY,
    queryFn: marketOverviewService.get,
    staleTime: STALE_MS,
    retry: false,
    enabled: isAuthenticated && !loading,
  });
  const widgets = query.data ?? [];
  return { ...query, widgets };
}

export function useWidgetData(widgets, sectionId) {
  return widgets.find((w) => w.sectionId === sectionId) ?? null;
}
