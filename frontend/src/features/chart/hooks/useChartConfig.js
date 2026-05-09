import { useCallback, useRef } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import {
  PREF_KEY,
  useUserChartPreferences,
  useUpdateUserChartPreferences,
} from '../../../shared/hooks/useUserChartPreferences';

const SAVE_DEBOUNCE_MS = 600;

export default function useChartConfig(assetType, assetCode) {
  const enabled = !!assetType && !!assetCode;
  const queryClient = useQueryClient();
  const { data, isSuccess } = useUserChartPreferences(assetType, assetCode);
  const updateMutation = useUpdateUserChartPreferences(assetType, assetCode);
  const mutateRef = useRef(updateMutation.mutate);
  mutateRef.current = updateMutation.mutate;
  const saveTimerRef = useRef(null);

  const setField = useCallback((key, value) => {
    if (!enabled) return;
    const queryKey = PREF_KEY(assetType, assetCode);
    queryClient.setQueryData(queryKey, (old) => {
      const oldConfig = old?.config ?? {};
      const next = typeof value === 'function' ? value(oldConfig[key]) : value;
      return { ...(old ?? {}), config: { ...oldConfig, [key]: next } };
    });
    if (saveTimerRef.current) clearTimeout(saveTimerRef.current);
    saveTimerRef.current = setTimeout(() => {
      const latest = queryClient.getQueryData(queryKey)?.config;
      if (latest) mutateRef.current(latest);
    }, SAVE_DEBOUNCE_MS);
  }, [enabled, queryClient, assetType, assetCode]);

  return {
    config: data?.config ?? {},
    setField,
    hydrated: isSuccess,
  };
}
