import { useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { marketOverviewService } from '../services/marketOverviewService';
import { useAuth } from '../../features/auth/AuthContext';

const QUERY_KEY = ['widgetDefinitions'];
const EMPTY_DEFS = Object.freeze({ widgets: [], limits: { maxWidgetsPerLayout: 0, maxAssetCardWidgetsPerLayout: 0, maxConfigLimit: 0 } });

export function useWidgetDefinitions() {
  const { isAuthenticated, loading } = useAuth();
  const query = useQuery({
    queryKey: QUERY_KEY,
    queryFn: marketOverviewService.getDefinitions,
    staleTime: Infinity,
    gcTime: Infinity,
    retry: false,
    enabled: isAuthenticated && !loading,
  });

  const data = query.data ?? EMPTY_DEFS;

  const byKind = useMemo(() => {
    const map = new Map();
    for (const w of data.widgets) map.set(w.kind, w);
    return map;
  }, [data.widgets]);

  return {
    ...query,
    definitions: data,
    byKind,
    sizeFor: (kind) => byKind.get(kind) ?? null,
    limits: data.limits,
  };
}
