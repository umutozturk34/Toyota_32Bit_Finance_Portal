import { create } from 'zustand';
import { persist } from 'zustand/middleware';

const useAppStore = create(
  persist(
    (set, get) => ({
      sidebarCollapsed: false,
      cooldowns: JSON.parse(sessionStorage.getItem('cooldowns') || '{}'),
      activeWatchlistId: null,

      toggleSidebar: () => set((s) => ({ sidebarCollapsed: !s.sidebarCollapsed })),

      setCooldown: (route, endTime) =>
        set((s) => {
          const next = { ...s.cooldowns, [route]: endTime };
          try { sessionStorage.setItem('cooldowns', JSON.stringify(next)); } catch {}
          return { cooldowns: next };
        }),
      getCooldownEnd: (route) => get().cooldowns[route] ?? 0,

      setActiveWatchlistId: (id) => set({ activeWatchlistId: id }),
    }),
    {
      name: 'finance-app-store',
      partialize: (state) => ({
        sidebarCollapsed: state.sidebarCollapsed,
        activeWatchlistId: state.activeWatchlistId,
      }),
    }
  )
);

export default useAppStore;
