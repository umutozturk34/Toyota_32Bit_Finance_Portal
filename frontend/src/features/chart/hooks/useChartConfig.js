import { useCallback, useRef } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import {
  PREF_KEY,
  useUserChartPreferences,
  useUpdateUserChartPreferences,
} from '../../../shared/hooks/useUserChartPreferences';

export default function useChartConfig(assetType, assetCode) {
  const enabled = !!assetType && !!assetCode;
  const queryClient = useQueryClient();
  const { data, isSuccess } = useUserChartPreferences(assetType, assetCode);
  const updateMutation = useUpdateUserChartPreferences(assetType, assetCode);
  const mutateRef = useRef(updateMutation.mutate);
  mutateRef.current = updateMutation.mutate;

  const setField = useCallback((key, value) => {
    if (!enabled) return;
    const queryKey = PREF_KEY(assetType, assetCode);
    let nextConfig;
    queryClient.setQueryData(queryKey, (old) => {
      const oldConfig = old?.config ?? {};
      const next = typeof value === 'function' ? value(oldConfig[key]) : value;
      nextConfig = { ...oldConfig, [key]: next };
      return { ...(old ?? {}), config: nextConfig };
    });
    if (nextConfig) mutateRef.current(nextConfig);
  }, [enabled, queryClient, assetType, assetCode]);

  return {
    config: data?.config ?? {},
    setField,
    hydrated: isSuccess,
  };
}
