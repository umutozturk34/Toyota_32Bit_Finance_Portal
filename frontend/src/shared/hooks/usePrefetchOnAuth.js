import { useEffect, useRef } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useAuth } from '../../features/auth/useAuth';
import { userLayoutService } from '../services/userLayoutService';
import { userPreferenceService } from '../services/userPreferenceService';
import { marketOverviewService } from '../services/marketOverviewService';
import { STALE, GC } from '../constants/query';

export default function usePrefetchOnAuth() {
  const queryClient = useQueryClient();
  const { isAuthenticated, loading } = useAuth();
  const firedRef = useRef(false);

  useEffect(() => {
    if (firedRef.current) return;
    if (loading || !isAuthenticated) return;
    firedRef.current = true;

    queryClient.prefetchQuery({
      queryKey: ['userLayout'],
      queryFn: userLayoutService.get,
      staleTime: STALE.NEVER,
      gcTime: GC.NEVER,
    });
    queryClient.prefetchQuery({
      queryKey: ['userPreferences'],
      queryFn: userPreferenceService.get,
      staleTime: STALE.NEVER,
      gcTime: GC.NEVER,
    });
    queryClient.prefetchQuery({
      queryKey: ['marketOverview', 'default'],
      queryFn: () => marketOverviewService.get(),
      staleTime: STALE.SHORT,
    });
  }, [isAuthenticated, loading, queryClient]);
}
