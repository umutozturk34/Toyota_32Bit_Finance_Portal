import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { LIMITS } from '../config/uiConfig';

const useAppStore = create(
  persist(
    (set, get) => ({
      sidebarCollapsed: false,
      cooldowns: JSON.parse(sessionStorage.getItem('cooldowns') || '{}'),
      activeWatchlistId: null,
      chartSidebarOpen: false,
      chartActiveTab: 'indicators',
      chartViewports: {},

      toggleSidebar: () => set((s) => ({ sidebarCollapsed: !s.sidebarCollapsed })),

      setCooldown: (route, endTime) =>
        set((s) => {
          const next = { ...s.cooldowns, [route]: endTime };
          try { sessionStorage.setItem('cooldowns', JSON.stringify(next)); } catch {}
          return { cooldowns: next };
        }),
      getCooldownEnd: (route) => get().cooldowns[route] ?? 0,

      setActiveWatchlistId: (id) => set({ activeWatchlistId: id }),

      setChartSidebarOpen: (open) => set({ chartSidebarOpen: !!open }),
      setChartActiveTab: (tab) => set({ chartActiveTab: tab }),

      setChartViewport: (type, code, range, viewport) =>
        set((s) => {
          if (!type || !code || !viewport) return s;
          const key = `${type}:${code}:${range || 'all'}`;
          const next = { ...s.chartViewports };
          delete next[key];
          next[key] = { from: viewport.from, to: viewport.to };
          const keys = Object.keys(next);
          if (keys.length > LIMITS.CHART_VIEWPORT_CACHE_SIZE) delete next[keys[0]];
          return { chartViewports: next };
        }),
      getChartViewport: (type, code, range) => {
        if (!type || !code) return null;
        return get().chartViewports[`${type}:${code}:${range || 'all'}`] ?? null;
      },
    }),
    {
      name: 'finance-app-store',
      partialize: (state) => ({
        sidebarCollapsed: state.sidebarCollapsed,
        activeWatchlistId: state.activeWatchlistId,
        chartSidebarOpen: state.chartSidebarOpen,
        chartActiveTab: state.chartActiveTab,
        chartViewports: state.chartViewports,
      }),
    }
  )
);

export default useAppStore;
