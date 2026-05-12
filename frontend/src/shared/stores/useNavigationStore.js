import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';

const STALE_AFTER_MS = 600_000;
const SCROLL_RETENTION_LIMIT = 30;

const isFresh = (entry) => entry && Date.now() - entry.timestamp <= STALE_AFTER_MS;

const useNavigationStore = create(
  persist(
    (set, get) => ({
  origin: null,
  paginationByRoute: {},
  scrollByRoute: {},

  setOrigin: (route, scrollY = 0) => {
    if (!route) return;
    set({ origin: { route, scrollY, timestamp: Date.now() } });
  },

  consumeOrigin: () => {
    const current = get().origin;
    if (!isFresh(current)) {
      if (current) set({ origin: null });
      return null;
    }
    set({ origin: null });
    return { route: current.route, scrollY: current.scrollY };
  },

  pushPagination: (routeKey, params) => {
    if (!routeKey) return;
    set((state) => ({
      paginationByRoute: {
        ...state.paginationByRoute,
        [routeKey]: { params, timestamp: Date.now() },
      },
    }));
  },

  consumePagination: (routeKey) => {
    if (!routeKey) return null;
    const entry = get().paginationByRoute[routeKey];
    if (!isFresh(entry)) return null;
    set((state) => {
      const next = { ...state.paginationByRoute };
      delete next[routeKey];
      return { paginationByRoute: next };
    });
    return entry.params;
  },

  saveScroll: (pathname, y, h) => {
    if (!pathname) return;
    set((state) => {
      const next = { ...state.scrollByRoute, [pathname]: { y, h, timestamp: Date.now() } };
      const keys = Object.keys(next);
      if (keys.length > SCROLL_RETENTION_LIMIT) {
        const sorted = keys.sort((a, b) => next[a].timestamp - next[b].timestamp);
        for (let i = 0; i < keys.length - SCROLL_RETENTION_LIMIT; i++) {
          delete next[sorted[i]];
        }
      }
      return { scrollByRoute: next };
    });
  },

  consumeScroll: (pathname) => {
    if (!pathname) return null;
    const entry = get().scrollByRoute[pathname];
    if (!isFresh(entry)) return null;
    return { y: entry.y, h: entry.h };
  },

  pruneStale: () => {
    const now = Date.now();
    set((state) => {
      const nextPagination = {};
      for (const [k, v] of Object.entries(state.paginationByRoute)) {
        if (now - v.timestamp <= STALE_AFTER_MS) nextPagination[k] = v;
      }
      const nextScroll = {};
      for (const [k, v] of Object.entries(state.scrollByRoute)) {
        if (now - v.timestamp <= STALE_AFTER_MS) nextScroll[k] = v;
      }
      const origin = isFresh(state.origin) ? state.origin : null;
      return { paginationByRoute: nextPagination, scrollByRoute: nextScroll, origin };
    });
  },
    }),
    {
      name: 'finance-navigation-store',
      storage: createJSONStorage(() => sessionStorage),
      partialize: (state) => ({
        scrollByRoute: state.scrollByRoute,
        origin: state.origin,
        paginationByRoute: state.paginationByRoute,
      }),
    },
  ),
);

export default useNavigationStore;
