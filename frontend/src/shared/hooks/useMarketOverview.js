import { useQuery } from '@tanstack/react-query';
import { marketOverviewService } from '../services/marketOverviewService';
import { useAuth } from '../../features/auth/useAuth';
import { STALE } from '../constants/query';

const EMPTY_WIDGETS = Object.freeze([]);

export function useMarketOverview(pageId) {
  const { isAuthenticated, loading } = useAuth();
  const query = useQuery({
    queryKey: ['marketOverview', pageId || 'default'],
    queryFn: () => marketOverviewService.get(pageId),
    staleTime: STALE.SHORT,
    refetchOnWindowFocus: true,
    retry: false,
    enabled: isAuthenticated && !loading,
  });
  const widgets = query.data ?? EMPTY_WIDGETS;
  return { ...query, widgets };
}

export function useWidgetData(widgets, sectionId) {
  return widgets.find((w) => w.sectionId === sectionId) ?? null;
}
