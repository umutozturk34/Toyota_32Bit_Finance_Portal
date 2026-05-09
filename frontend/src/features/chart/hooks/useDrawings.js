import { useCallback, useEffect, useRef, useState } from 'react';
import { useUserChartDrawings, useUpdateUserChartDrawings } from '../../../shared/hooks/useUserChartDrawings';

const SAVE_DEBOUNCE_MS = 600;

const genId = () => `d-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`;

function rehydrate(remote) {
  if (!Array.isArray(remote)) return [];
  return remote.map((d) => ({ ...d, id: d.id || genId() }));
}

export default function useDrawings(assetType, assetCode) {
  const enabled = !!assetType && !!assetCode;
  const { data, isSuccess } = useUserChartDrawings(assetType, assetCode);
  const updateMutation = useUpdateUserChartDrawings(assetType, assetCode);

  const [drawings, setDrawings] = useState([]);
  const [activeTool, setActiveTool] = useState(null);
  const hydratedRef = useRef(false);
  const saveTimerRef = useRef(null);

  useEffect(() => {
    if (!enabled || !isSuccess) return;
    setDrawings(rehydrate(data?.drawings));
    hydratedRef.current = true;
    return undefined;
  }, [enabled, isSuccess, data]);

  useEffect(() => {
    if (!enabled || !hydratedRef.current) return undefined;
    if (saveTimerRef.current) clearTimeout(saveTimerRef.current);
    saveTimerRef.current = setTimeout(() => {
      updateMutation.mutate(drawings);
    }, SAVE_DEBOUNCE_MS);
    return () => {
      if (saveTimerRef.current) clearTimeout(saveTimerRef.current);
    };
  }, [enabled, drawings, updateMutation]);

  const addDrawing = useCallback((drawing) => {
    setDrawings((prev) => [...prev, { ...drawing, id: genId() }]);
  }, []);

  const removeDrawing = useCallback((id) => {
    setDrawings((prev) => prev.filter((d) => d.id !== id));
  }, []);

  const undoDrawing = useCallback(() => {
    setDrawings((prev) => prev.slice(0, -1));
  }, []);

  const clearDrawings = useCallback(() => {
    setDrawings([]);
    setActiveTool(null);
  }, []);

  const selectTool = useCallback((tool) => {
    setActiveTool((prev) => (prev === tool ? null : tool));
  }, []);

  const cancelTool = useCallback(() => {
    setActiveTool(null);
  }, []);

  return {
    drawings,
    activeTool,
    addDrawing,
    removeDrawing,
    undoDrawing,
    clearDrawings,
    selectTool,
    cancelTool,
  };
}
