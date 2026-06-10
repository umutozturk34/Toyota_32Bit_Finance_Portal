import { useCallback, useEffect, useRef, useState } from 'react';
import { useUserChartDrawings, useUpdateUserChartDrawings } from '../../../shared/hooks/useUserChartDrawings';
import { randomId } from '../../../shared/utils/id';

const genId = () => `d-${randomId()}`;
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
  const rangeRef = useRef(range);
  const persistRef = useRef(persistEnabled);

  const [drawings, setDrawings] = useState([]);
  const [activeTool, setActiveTool] = useState(null);
  const hydratedRef = useRef(false);
  const hydratedKeyRef = useRef(null);
  const drawingsRef = useRef(drawings);
  const debounceTimerRef = useRef(null);

  useEffect(() => {
    mutateRef.current = updateMutation.mutate;
    rangeRef.current = range;
    persistRef.current = persistEnabled;
    drawingsRef.current = drawings;
  });

  const [trackedPersistEnabled, setTrackedPersistEnabled] = useState(persistEnabled);
  if (persistEnabled !== trackedPersistEnabled) {
    setTrackedPersistEnabled(persistEnabled);
    setDrawings([]);
    setActiveTool(null);
  }

  const hydrationKey = enabled && isSuccess && persistEnabled
    ? `${assetType}:${assetCode}:${range || 'all'}`
    : null;
  const [trackedHydrationKey, setTrackedHydrationKey] = useState(null);
  if (hydrationKey !== null && hydrationKey !== trackedHydrationKey) {
    setTrackedHydrationKey(hydrationKey);
    setDrawings(rehydrate(data?.drawings));
  }

  useEffect(() => {
    hydratedRef.current = false;
    hydratedKeyRef.current = null;
  }, [persistEnabled]);

  useEffect(() => {
    if (trackedHydrationKey !== null) {
      hydratedRef.current = true;
      hydratedKeyRef.current = trackedHydrationKey;
    }
  }, [trackedHydrationKey]);

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

  // Post-hoc edit of one drawing (e.g. recolour from the objects list). Persistence follows via the same
  // debounced full-array PUT as add/remove, since the backend stores the drawings array as one opaque blob.
  const updateDrawing = useCallback((id, patch) => {
    setDrawings((prev) => prev.map((d) => (d.id === id ? { ...d, ...patch } : d)));
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
    updateDrawing,
    undoDrawing,
    clearDrawings,
    selectTool,
    cancelTool,
  };
}
