import { useState, useEffect, useMemo, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { motion, AnimatePresence } from 'framer-motion';
import { Outlet, Link, useLocation, useNavigationType } from 'react-router-dom';
import { useAuth } from '../../features/auth/useAuth';
import { useTheme } from '../context/useTheme';
import useAppStore from '../stores/useAppStore';
import { TrendingUp, Shield, Menu, FolderOpen, Search } from 'lucide-react';
import {
  PiHouseDuotone, PiChartLineUpDuotone, PiTrendUpDuotone, PiCurrencyBtcDuotone,
  PiCurrencyDollarSimpleDuotone, PiBriefcaseDuotone, PiDiamondDuotone, PiBankDuotone,
  PiStackDuotone, PiNewspaperClippingDuotone, PiWalletDuotone, PiEyeDuotone,
  PiChartPieSliceDuotone, PiDatabaseDuotone, PiUsersThreeDuotone, PiGraduationCapDuotone,
} from 'react-icons/pi';
import TasksPanel from '../../features/admin/components/TasksPanel';
import SettingsSidebar from '../../features/settings/SettingsSidebar';
import ProfileDrawer from '../../features/profile/ProfileDrawer';
import NotificationPanel from '../../features/notifications/NotificationPanel';
import { useUnreadNotificationCount } from '../hooks/useNotifications';
import useNotificationStream from '../hooks/useNotificationStream';
import useScrollRestoration from '../hooks/useScrollRestoration';
import useMediaQuery from '../hooks/useMediaQuery';
import useNavigationStore from '../stores/useNavigationStore';
import OnboardingGate from '../../features/onboarding/OnboardingGate';
import KeycloakActionToast from '../../features/auth/components/KeycloakActionToast';
import SidebarContent from './SidebarContent';
import CommandPalette from '../components/modal/CommandPalette';

const baseNavStructure = [
  { kind: 'item', to: '/market', labelKey: 'nav.overview', Icon: PiHouseDuotone },
  {
    kind: 'group', id: 'markets', labelKey: 'nav.groupMarkets', Icon: PiChartLineUpDuotone,
    items: [
      { to: '/stocks',      labelKey: 'nav.stocks',      subKey: 'nav.subStocks',      Icon: PiTrendUpDuotone },
      { to: '/crypto',      labelKey: 'nav.crypto',      subKey: 'nav.subCrypto',      Icon: PiCurrencyBtcDuotone },
      { to: '/forex',       labelKey: 'nav.forex',       subKey: 'nav.subForex',       Icon: PiCurrencyDollarSimpleDuotone },
      { to: '/funds',       labelKey: 'nav.funds',       subKey: 'nav.subFunds',       Icon: PiBriefcaseDuotone },
      { to: '/commodities', labelKey: 'nav.commodities', subKey: 'nav.subCommodities', Icon: PiDiamondDuotone },
      { to: '/bonds',       labelKey: 'nav.bonds',       subKey: 'nav.subBonds',       Icon: PiBankDuotone },
      { to: '/viop',        labelKey: 'nav.viop',        subKey: 'nav.subViop',        Icon: PiStackDuotone },
    ],
  },
  { kind: 'item', to: '/news', labelKey: 'nav.news', Icon: PiNewspaperClippingDuotone },
  {
    kind: 'group', id: 'my', labelKey: 'nav.groupMy', Icon: FolderOpen,
    items: [
      { to: '/portfolio', labelKey: 'nav.portfolio', subKey: 'nav.subPortfolio', Icon: PiWalletDuotone },
      { to: '/watch',     labelKey: 'nav.watch',     subKey: 'nav.subWatch',     Icon: PiEyeDuotone },
    ],
  },
  { kind: 'item', to: '/analytics', labelKey: 'nav.analytics', Icon: PiChartPieSliceDuotone },
  { kind: 'item', to: '/learn', labelKey: 'nav.learn', Icon: PiGraduationCapDuotone },
];

const adminGroup = {
  kind: 'group', id: 'admin', labelKey: 'nav.groupAdmin', Icon: Shield,
  items: [
    { to: '/admin/tracked-assets', labelKey: 'nav.adminTrackedAssets', subKey: 'nav.subAdminTrackedAssets', Icon: PiDatabaseDuotone },
    { to: '/admin/users',          labelKey: 'nav.adminUsers',         subKey: 'nav.subAdminUsers',         Icon: PiUsersThreeDuotone },
  ],
};

function findActiveGroupId(structure, pathname) {
  for (const node of structure) {
    if (node.kind !== 'group') continue;
    if (node.items.some((i) => i.to === pathname)) return node.id;
  }
  return null;
}

const MainLayout = () => {
  const { t } = useTranslation();
  const { user, logout, hasRole } = useAuth();
  const { isDark } = useTheme();
  const location = useLocation();
  const collapsed = useAppStore((s) => s.sidebarCollapsed);
  const toggleSidebar = useAppStore((s) => s.toggleSidebar);
  const openSearch = useAppStore((s) => s.openSearch);
  const toggleSearch = useAppStore((s) => s.toggleSearch);
  const [mobileOpen, setMobileOpen] = useState(false);
  const [tasksOpen, setTasksOpen] = useState(false);
  const [settingsOpen, setSettingsOpen] = useState(() => {
    const params = new URLSearchParams(window.location.search);
    return params.get('settings') === 'open';
  });
  const [notificationsOpen, setNotificationsOpen] = useState(false);
  const [profileOpen, setProfileOpen] = useState(false);
  const { data: unreadCount = 0 } = useUnreadNotificationCount();
  useNotificationStream();
  useScrollRestoration();

  // Auto-close the mobile drawer once the viewport grows into the lg breakpoint. Without this, a drawer
  // opened at mobile width leaves mobileOpen=true after enlarging, which keeps blurCls (pointer-events-none)
  // on the main content and hides the now-visible desktop sidebar behind the stale mobile overlay.
  // Adjusted during render (not in an effect) per the codebase's derived-state pattern, avoiding a wasted
  // render pass and the cascading-render lint rule.
  const isLargeScreen = useMediaQuery('(min-width: 1024px)');
  if (isLargeScreen && mobileOpen) setMobileOpen(false);

  const navType = useNavigationType();
  const setOriginInStore = useNavigationStore((s) => s.setOrigin);
  const lastFullPathRef = useRef(location.pathname + (location.search || ''));
  const [trackedPath, setTrackedPath] = useState(location.pathname + (location.search || ''));

  const currentPath = location.pathname + (location.search || '');
  if (currentPath !== trackedPath) {
    setTrackedPath(currentPath);
    setMobileOpen(false);
  }

  useEffect(() => {
    const next = location.pathname + (location.search || '');
    const prev = lastFullPathRef.current;
    if (prev === next) return;
    if (navType === 'PUSH') {
      setOriginInStore(prev);
      window.scrollTo(0, 0);
    }
    lastFullPathRef.current = next;
  }, [location.pathname, location.search, navType, setOriginInStore]);

  // Global search shortcut — bound to BOTH Cmd+K (macOS) and Ctrl+K (Windows/Linux) so it works on every
  // platform, not just Apple keyboards. The visible trigger (sidebar pill / mobile top-bar icon) is the
  // primary affordance; this is the power-user accelerator.
  useEffect(() => {
    const onKey = (e) => {
      if ((e.metaKey || e.ctrlKey) && (e.key === 'k' || e.key === 'K')) {
        e.preventDefault();
        toggleSearch();
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [toggleSearch]);

  const navStructure = useMemo(() => (
    hasRole('ADMIN') ? [...baseNavStructure, adminGroup] : baseNavStructure
  ), [hasRole]);

  const [expandedGroups, setExpandedGroups] = useState(() => {
    const active = findActiveGroupId(navStructure, location.pathname);
    return new Set(active ? [active] : ['markets']);
  });

  const [trackedActiveGroup, setTrackedActiveGroup] = useState(() =>
    findActiveGroupId(navStructure, location.pathname));
  const currentActiveGroup = findActiveGroupId(navStructure, location.pathname);
  if (currentActiveGroup && currentActiveGroup !== trackedActiveGroup) {
    setTrackedActiveGroup(currentActiveGroup);
    setExpandedGroups((prev) => {
      if (prev.has(currentActiveGroup)) return prev;
      const next = new Set(prev);
      next.add(currentActiveGroup);
      return next;
    });
  }

  const toggleGroup = (id) => {
    setExpandedGroups((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const anyOverlayOpen = settingsOpen || notificationsOpen || tasksOpen || mobileOpen || profileOpen;
  const blurCls = anyOverlayOpen ? 'blur-sm pointer-events-none select-none' : '';

  const isActive = (path) => location.pathname === path;
  // rem (not px) so the sidebar scales with the content on wider screens. The spacer must always match the
  // sidebar's actual width so content clears it: a previous Overview-only 4rem override left the page sliding
  // under the (now rem-scaled, up to ~1.8×) 14rem expanded sidebar on wide monitors.
  const sidebarWidth = collapsed ? '4rem' : '14rem';
  const spacerWidth = sidebarWidth;

  const sidebarProps = {
    collapsed,
    t,
    toggleSidebar,
    navStructure,
    isActive,
    hasRole,
    expandedGroups,
    toggleGroup,
    setTasksOpen,
    setNotificationsOpen,
    setSettingsOpen,
    setProfileOpen,
    openSearch: () => { setMobileOpen(false); openSearch(); },
    unreadCount,
    user,
    logout,
  };

  return (
    <div className="flex min-h-screen min-h-[100dvh] bg-bg-base relative">
      {isDark && (
        <div className="fixed inset-0 pointer-events-none z-0 overflow-hidden" aria-hidden="true">
          <div className="absolute top-0 left-1/4 w-[600px] h-[400px] rounded-full bg-accent/[0.04] blur-[150px]" />
          <div className="absolute bottom-0 right-1/4 w-[400px] h-[300px] rounded-full bg-[#7c3aed]/[0.03] blur-[120px]" />
        </div>
      )}

      <motion.aside
        animate={{ width: sidebarWidth }}
        initial={false}
        transition={{ type: 'spring', stiffness: 380, damping: 36, mass: 0.8 }}
        className="hidden lg:flex flex-col fixed top-0 left-0 h-screen h-[100dvh] border-r border-border-default z-30 overflow-visible"
        style={{
          background: 'var(--sidebar-bg)',
          backdropFilter: 'var(--sidebar-blur)',
          WebkitBackdropFilter: 'var(--sidebar-blur)',
          boxShadow: collapsed ? '4px 0 24px -8px rgba(0,0,0,0.25)' : '8px 0 40px -8px rgba(0,0,0,0.45)',
        }}
      >
        <div className="absolute inset-0 pointer-events-none">
          <div className="absolute inset-y-0 right-0 w-px bg-gradient-to-b from-transparent via-border-default to-transparent" />
        </div>
        <div className="relative flex flex-col h-full">
          <SidebarContent {...sidebarProps} />
        </div>
      </motion.aside>
      <div className="hidden lg:block shrink-0" style={{ width: spacerWidth }} />

      <div
        className={`lg:hidden fixed top-0 left-0 right-0 z-40 h-[calc(3rem+env(safe-area-inset-top))] pt-[env(safe-area-inset-top)] flex items-center justify-between px-3 border-b border-border-default ${blurCls}`}
        style={{
          background: 'var(--sidebar-bg)',
          backdropFilter: 'var(--sidebar-blur)',
          WebkitBackdropFilter: 'var(--sidebar-blur)',
        }}
      >
        <button
          onClick={() => setMobileOpen(true)}
          data-tour="mobile-burger-open"
          aria-label={t('common.openMenu')}
          className="flex items-center justify-center w-8 h-8 rounded-md text-fg-muted hover:text-fg hover:bg-surface transition-colors bg-transparent border-none cursor-pointer"
        >
          <Menu size={18} />
        </button>
        <Link to="/" className="flex items-center gap-2 no-underline">
          <span className="flex items-center justify-center w-7 h-7 rounded-xl logo-gradient text-white shadow-lg shadow-accent/25">
            <TrendingUp className="w-3.5 h-3.5" />
          </span>
          <span className="text-sm font-bold text-fg font-display">Finance</span>
        </Link>
        <button
          onClick={openSearch}
          aria-label={t('nav.search')}
          className="flex items-center justify-center w-8 h-8 rounded-md text-fg-muted hover:text-fg hover:bg-surface transition-colors bg-transparent border-none cursor-pointer"
        >
          <Search size={18} />
        </button>
      </div>

      <AnimatePresence>
        {mobileOpen && (
          <>
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              transition={{ duration: 0.15 }}
              className="lg:hidden fixed inset-0 z-[45] bg-black/40"
              data-tour="mobile-burger-close"
              onClick={() => setMobileOpen(false)}
            />
            <motion.aside
              initial={{ x: '-100%' }}
              animate={{ x: 0 }}
              exit={{ x: '-100%' }}
              transition={{ duration: 0.32, ease: [0.32, 0.72, 0, 1] }}
              className="lg:hidden fixed top-0 left-0 bottom-0 z-50 w-[82vw] max-w-[16rem] border-r pt-[env(safe-area-inset-top)] pb-[env(safe-area-inset-bottom)] border-border-default"
              style={{
                background: 'var(--sidebar-bg)',
                backdropFilter: 'var(--sidebar-blur)',
                WebkitBackdropFilter: 'var(--sidebar-blur)',
              }}
            >
              <SidebarContent {...sidebarProps} isMobile />
            </motion.aside>
          </>
        )}
      </AnimatePresence>

      <div className={`flex-1 flex flex-col min-w-0 relative overflow-x-clip ${blurCls}`}>
        <main className="flex-1 w-full">
          <div className="max-w-7xl mx-auto px-4 sm:px-6 py-6 lg:py-6 pt-[calc(4rem+env(safe-area-inset-top))] lg:pt-6">
            <Outlet />
          </div>
        </main>
        <footer className="border-t border-border-default">
          <div className="max-w-7xl mx-auto px-4 sm:px-6 py-4 flex flex-wrap items-center justify-between gap-2">
            <p className="text-[11px] sm:text-xs text-fg-subtle">{t('home.footer')}</p>
            <p className="text-[11px] sm:text-xs text-fg-subtle font-mono">v1.0.0</p>
          </div>
        </footer>
      </div>

      {hasRole('ADMIN') && (
        <TasksPanel open={tasksOpen} onClose={() => setTasksOpen(false)} />
      )}
      <SettingsSidebar isOpen={settingsOpen} onClose={() => setSettingsOpen(false)} />
      <ProfileDrawer isOpen={profileOpen} onClose={() => setProfileOpen(false)} />
      <NotificationPanel isOpen={notificationsOpen} onClose={() => setNotificationsOpen(false)} />
      <CommandPalette />
      <OnboardingGate />
      <KeycloakActionToast />
    </div>
  );
};

export default MainLayout;
