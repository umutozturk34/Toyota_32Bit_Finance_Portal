import { useCallback, useSyncExternalStore } from 'react';

// Reactive media-query match via useSyncExternalStore (the idiomatic way to read an external source like
// matchMedia without a setState-in-effect). Re-renders on breakpoint changes, so charts/layouts driven by
// it rebuild when the viewport crosses the query — which ECharts `media` options don't do reliably under
// notMerge (they can stick on the last-matched layout).
export default function useMediaQuery(query) {
  const subscribe = useCallback((onChange) => {
    if (typeof window === 'undefined' || !window.matchMedia) return () => {};
    const mql = window.matchMedia(query);
    mql.addEventListener('change', onChange);
    return () => mql.removeEventListener('change', onChange);
  }, [query]);

  const getSnapshot = () => (typeof window !== 'undefined' && window.matchMedia
    ? window.matchMedia(query).matches
    : false);

  return useSyncExternalStore(subscribe, getSnapshot, () => false);
}
