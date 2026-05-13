import { useCallback, useEffect, useRef, useState } from 'react';
import { useUserChartDrawings, useUpdateUserChartDrawings } from '../../../shared/hooks/useUserChartDrawings';

const genId = () => `d-${crypto.randomUUID()}`;
const PERSIST_DEBOUNCE_MS = 300;

function rehydrate(remote) {
  if (!Array.isArray(remote)) return [];
  return remote.map((d) => ({ ...d, id: d.id || genId() }));
}

export default function useDrawings(assetType, assetCode, range, persistEnabled = true) {
  const enabled = !!assetType && !!assetCode;
  const { data, isSuccess } = useUserChartDrawings(assetType, assetCode, range, persistEnabled);
  const updateMutation = useUpdateUserChartDrawings(assetType, assetCode, range);
  const mutateRef = useRef(updateMutation.mutate);
  mutateRef.current = updateMutation.mutate;
  const rangeRef = useRef(range);
  rangeRef.current = range;
  const persistRef = useRef(persistEnabled);
  persistRef.current = persistEnabled;

  const [drawings, setDrawings] = useState([]);
  const [activeTool, setActiveTool] = useState(null);
  const hydratedRef = useRef(false);
  const hydratedKeyRef = useRef(null);
  const drawingsRef = useRef(drawings);
  const debounceTimerRef = useRef(null);
  drawingsRef.current = drawings;

  useEffect(() => {
    setDrawings([]);
    setActiveTool(null);
    hydratedRef.current = false;
    hydratedKeyRef.current = null;
  }, [persistEnabled]);

  useEffect(() => {
    if (!enabled || !isSuccess || !persistEnabled) return;
    const key = `${assetType}:${assetCode}:${range || 'all'}`;
    if (hydratedKeyRef.current === key) return;
    setDrawings(rehydrate(data?.drawings));
    hydratedRef.current = true;
    hydratedKeyRef.current = key;
  }, [enabled, isSuccess, data, assetType, assetCode, range, persistEnabled]);

  useEffect(() => {
    if (!enabled || !hydratedRef.current) return;
    if (!persistRef.current) return;
    if (debounceTimerRef.current) clearTimeout(debounceTimerRef.current);
    debounceTimerRef.current = setTimeout(() => {
      mutateRef.current(drawingsRef.current);
      debounceTimerRef.current = null;
    }, PERSIST_DEBOUNCE_MS);
  }, [enabled, drawings]);

  useEffect(() => () => {
    if (debounceTimerRef.current) {
      clearTimeout(debounceTimerRef.current);
      if (persistRef.current && hydratedRef.current) {
        mutateRef.current(drawingsRef.current);
      }
    }
  }, []);

  const addDrawing = useCallback((drawing) => {
    setDrawings((prev) => [...prev, { ...drawing, id: genId(), range: rangeRef.current }]);
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
