import { useCallback, useRef } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useUserChartPreferences, useUpdateUserChartPreferences } from '../../../shared/hooks/useUserChartPreferences';
import { CHART_DATA_KEY } from '../../../shared/hooks/useUserChartData';

export default function useChartConfig(assetType, assetCode, range, persistEnabled = true) {
  const enabled = !!assetType && !!assetCode && persistEnabled;
  const queryClient = useQueryClient();
  const { data, isSuccess } = useUserChartPreferences(assetType, assetCode, range, persistEnabled);
  const updateMutation = useUpdateUserChartPreferences(assetType, assetCode, range);
  const mutateRef = useRef(updateMutation.mutate);
  mutateRef.current = updateMutation.mutate;

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
    if (nextConfig) mutateRef.current(nextConfig);
  }, [enabled, queryClient, assetType, assetCode, range]);

  return {
    config: persistEnabled ? (data?.config ?? {}) : {},
    setField,
    hydrated: isSuccess,
  };
}
