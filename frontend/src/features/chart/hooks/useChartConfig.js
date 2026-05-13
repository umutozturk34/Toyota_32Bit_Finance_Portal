import { useCallback, useEffect, useRef } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useUserChartPreferences, useUpdateUserChartPreferences } from '../../../shared/hooks/useUserChartPreferences';
import { CHART_DATA_KEY } from '../../../shared/hooks/useUserChartData';

const PERSIST_DEBOUNCE_MS = 300;

export default function useChartConfig(assetType, assetCode, range, persistEnabled = true) {
  const enabled = !!assetType && !!assetCode && persistEnabled;
  const queryClient = useQueryClient();
  const { data, isSuccess } = useUserChartPreferences(assetType, assetCode, range, persistEnabled);
  const updateMutation = useUpdateUserChartPreferences(assetType, assetCode, range);
  const mutateRef = useRef(updateMutation.mutate);
  useEffect(() => {
    mutateRef.current = updateMutation.mutate;
  });
  const pendingConfigRef = useRef(null);
  const debounceTimerRef = useRef(null);

  useEffect(() => () => {
    if (debounceTimerRef.current) {
      clearTimeout(debounceTimerRef.current);
      if (pendingConfigRef.current) {
        mutateRef.current(pendingConfigRef.current);
      }
    }
  }, []);

  const setField = useCallback((key, value) => {
    if (!enabled) return;
    const queryKey = CHART_DATA_KEY(assetType, assetCode, range);
    let nextConfig;
    queryClient.setQueryData(queryKey, (old) => {
      const oldConfig = old?.preferences?.config ?? {};
      const next = typeof value === 'function' ? value(oldConfig[key]) : value;
      nextConfig = { ...oldConfig, [key]: next };
      return {
        ...(old ?? {}),
        preferences: { ...(old?.preferences ?? {}), config: nextConfig },
      };
    });
    if (nextConfig) {
      pendingConfigRef.current = nextConfig;
      if (debounceTimerRef.current) clearTimeout(debounceTimerRef.current);
      debounceTimerRef.current = setTimeout(() => {
        if (pendingConfigRef.current) mutateRef.current(pendingConfigRef.current);
        pendingConfigRef.current = null;
        debounceTimerRef.current = null;
      }, PERSIST_DEBOUNCE_MS);
    }
  }, [enabled, queryClient, assetType, assetCode, range]);

  return {
    config: persistEnabled ? (data?.config ?? {}) : {},
    setField,
    hydrated: isSuccess,
  };
}
