import { useState, useEffect, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { motion } from 'framer-motion';
import { Outlet, Link, useLocation, useNavigationType } from 'react-router-dom';
import { useAuth } from '../../features/auth/useAuth';
import { useTheme } from '../context/useTheme';
import useAppStore from '../stores/useAppStore';
import { AnimatePresence } from 'framer-motion';
import {
  Newspaper, BarChart3, TrendingUp, Bitcoin,
  DollarSign, Shield,
  LogOut, Briefcase, Activity, Settings, Bell, Eye,
  ChevronLeft, ChevronRight, Menu, Landmark, Wallet, Database, Gem, Users, Layers,
} from 'lucide-react';
import TasksPanel from '../../features/admin/components/TasksPanel';
import SettingsSidebar from '../../features/settings/SettingsSidebar';
import NotificationPanel from '../../features/notifications/NotificationPanel';
import { useUnreadNotificationCount } from '../hooks/useNotifications';
import useNotificationStream from '../hooks/useNotificationStream';
import useScrollRestoration from '../hooks/useScrollRestoration';
import useNavigationStore from '../stores/useNavigationStore';
import OnboardingGate from '../../features/onboarding/OnboardingGate';
import KeycloakActionToast from '../../features/auth/components/KeycloakActionToast';

const navItems = [
  { to: '/market', labelKey: 'nav.market', Icon: BarChart3 },
  { to: '/news', labelKey: 'nav.news', Icon: Newspaper },
  { to: '/stocks', labelKey: 'nav.stocks', Icon: TrendingUp },
  { to: '/crypto', labelKey: 'nav.crypto', Icon: Bitcoin },
  { to: '/forex', labelKey: 'nav.forex', Icon: DollarSign },
  { to: '/funds', labelKey: 'nav.funds', Icon: Briefcase },
  { to: '/commodities', labelKey: 'nav.commodities', Icon: Gem },
  { to: '/bonds', labelKey: 'nav.bonds', Icon: Landmark },
  { to: '/viop', labelKey: 'nav.viop', Icon: Layers },
  { to: '/portfolio', labelKey: 'nav.portfolio', Icon: Wallet },
  { to: '/watch', labelKey: 'nav.watch', Icon: Eye },
];

const SidebarContent = ({
  isMobile = false,
  collapsed,
  t,
  toggleSidebar,
  allNav,
  isActive,
  hasRole,
  setTasksOpen,
  setNotificationsOpen,
  setSettingsOpen,
  unreadCount,
  user,
  logout,
}) => (
  <div className="flex flex-col h-full">
    <div className={`flex items-center ${collapsed && !isMobile ? 'justify-center' : 'justify-between'} h-14 px-3 border-b border-border-default shrink-0`}>
      {(!collapsed || isMobile) && (
        <Link to="/" className="flex items-center gap-2.5 no-underline group">
          <span className="flex items-center justify-center w-8 h-8 rounded-xl logo-gradient text-white shadow-lg shadow-accent/25 group-hover:shadow-accent/50 group-hover:scale-105 transition-all duration-300">
            <TrendingUp className="w-4 h-4" />
          </span>
          <span className="text-sm font-bold text-fg tracking-tight font-display">Finance</span>
        </Link>
      )}
      {collapsed && !isMobile && (
        <Link to="/" className="flex items-center justify-center w-8 h-8 rounded-xl logo-gradient text-white shadow-lg shadow-accent/25 hover:shadow-accent/50 hover:scale-105 transition-all duration-300 no-underline">
          <TrendingUp className="w-4 h-4" />
        </Link>
      )}
      {!isMobile && (
        <button
          onClick={toggleSidebar}
          className="hidden lg:flex items-center justify-center w-7 h-7 rounded-md text-fg-muted hover:text-fg hover:bg-surface transition-colors bg-transparent border-none cursor-pointer"
          title={collapsed ? t('nav.expandSidebar') : t('nav.collapseSidebar')}
        >
          {collapsed ? <ChevronRight size={14} /> : <ChevronLeft size={14} />}
        </button>
      )}
    </div>

    <nav className="flex-1 overflow-y-auto px-2 py-2 space-y-0.5 scrollbar-auto-hide">
      {allNav.map(({ to, labelKey, Icon }) => {
        const label = t(labelKey);
        const active = isActive(to);
        return (
          <Link
            key={to}
            to={to}
            title={collapsed && !isMobile ? label : undefined}
            className={`group relative flex items-center gap-2.5 rounded-lg no-underline overflow-hidden transition-all duration-200 ease-out ${
              collapsed && !isMobile ? 'justify-center px-0 py-2' : 'px-3 py-2'
            } ${
              active
                ? 'text-fg shadow-[0_4px_20px_-6px_rgba(99,102,241,0.35)]'
                : 'text-fg-muted hover:text-fg hover:translate-x-0.5'
            }`}
          >
            {active && (
              <motion.span
                layoutId="sidebar-active-bg"
                className="absolute inset-0 rounded-lg bg-gradient-to-r from-accent/20 via-accent/10 to-accent/5 border border-accent/20"
                transition={{ type: 'spring', stiffness: 380, damping: 32 }}
              />
            )}
            {!active && (
              <span className="absolute inset-0 rounded-lg bg-surface/0 group-hover:bg-surface/60 transition-colors duration-200" />
            )}
            <Icon
              size={16}
              strokeWidth={1.6}
              className={`relative shrink-0 transition-all duration-200 ${
                active
                  ? 'text-accent scale-110 drop-shadow-[0_0_6px_rgba(99,102,241,0.5)]'
                  : 'group-hover:text-fg-muted group-hover:scale-105'
              }`}
            />
            <AnimatePresence initial={false}>
              {(!collapsed || isMobile) && (
                <motion.span
                  initial={{ opacity: 0, x: -4 }}
                  animate={{ opacity: 1, x: 0 }}
                  exit={{ opacity: 0, x: -4 }}
                  transition={{ duration: 0.16, ease: 'easeOut' }}
                  className={`relative text-[13px] font-medium whitespace-nowrap ${active ? 'font-semibold' : ''}`}
                >
                  {label}
                </motion.span>
              )}
            </AnimatePresence>
            {active && (
              <motion.span
                layoutId="sidebar-indicator"
                className={`absolute left-0 top-1/2 -translate-y-1/2 w-[3px] rounded-r-full bg-accent shadow-[0_0_8px_rgba(99,102,241,0.7)] ${collapsed && !isMobile ? 'h-5' : 'h-6'}`}
                transition={{ type: 'spring', stiffness: 500, damping: 35 }}
              />
            )}
          </Link>
        );
      })}
    </nav>

    {hasRole('ADMIN') && (
      <div className="px-2 py-1.5 border-t border-border-default">
        <button
          onClick={() => setTasksOpen(true)}
          title={collapsed && !isMobile ? t('nav.tasks') : undefined}
          className={`w-full group flex items-center gap-2.5 rounded-lg text-fg-muted hover:text-fg hover:bg-surface transition-all duration-150 bg-transparent border-none cursor-pointer ${
            collapsed && !isMobile ? 'justify-center px-0 py-2' : 'px-3 py-2'
          }`}
        >
          <Activity size={16} strokeWidth={1.6} className="shrink-0 group-hover:text-accent transition-colors" />
          {(!collapsed || isMobile) && <span className="text-[13px] font-medium">{t('nav.tasks')}</span>}
        </button>
      </div>
    )}

    <div className="border-t border-border-default px-2 py-2 space-y-1 shrink-0">
      <button
        onClick={() => setNotificationsOpen(true)}
        title={collapsed && !isMobile ? t('nav.notifications') : undefined}
        className={`w-full group relative flex items-center gap-2.5 rounded-lg text-fg-muted hover:text-fg hover:bg-surface transition-all duration-150 bg-transparent border-none cursor-pointer ${
          collapsed && !isMobile ? 'justify-center px-0 py-2' : 'px-3 py-2'
        }`}
      >
        <span className="relative shrink-0">
          <Bell size={16} strokeWidth={1.6} className="group-hover:text-accent transition-colors" />
          {unreadCount > 0 && (
            <span className="absolute -top-0.5 -right-0.5 w-2 h-2 rounded-full bg-accent ring-2 ring-bg-base" aria-label={`${unreadCount} unread`} />
          )}
        </span>
        {(!collapsed || isMobile) && <span className="text-[13px] font-medium">{t('nav.notifications')}</span>}
        {(!collapsed || isMobile) && unreadCount > 0 && (
          <span className="ml-auto text-[10px] font-mono text-accent">{unreadCount}</span>
        )}
      </button>
      <button
        onClick={() => setSettingsOpen(true)}
        title={collapsed && !isMobile ? t('nav.settings') : undefined}
        className={`w-full group flex items-center gap-2.5 rounded-lg text-fg-muted hover:text-fg hover:bg-surface transition-all duration-150 bg-transparent border-none cursor-pointer ${
          collapsed && !isMobile ? 'justify-center px-0 py-2' : 'px-3 py-2'
        }`}
      >
        <Settings size={16} strokeWidth={1.6} className="shrink-0 group-hover:text-accent transition-colors" />
        {(!collapsed || isMobile) && <span className="text-[13px] font-medium">{t('nav.settings')}</span>}
      </button>
      {(!collapsed || isMobile) && (
        <div className="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-surface border border-border-default">
          <span className="w-6 h-6 rounded-full bg-gradient-accent text-white flex items-center justify-center text-[11px] font-bold shrink-0">
            {user?.username?.charAt(0).toUpperCase() || '?'}
          </span>
          <span className="text-[12px] font-medium text-fg truncate">{user?.username}</span>
          {hasRole('ADMIN') && (
            <span className="text-[9px] px-1.5 py-0.5 rounded-full font-bold uppercase bg-accent/10 text-accent tracking-wide shrink-0">
              {t('nav.adminBadge')}
            </span>
          )}
        </div>
      )}
      <button
        onClick={logout}
        title={collapsed && !isMobile ? t('nav.logout') : undefined}
        className={`w-full group flex items-center gap-2.5 rounded-lg text-fg-muted hover:text-fg hover:bg-surface transition-all duration-150 bg-transparent border-none cursor-pointer ${
          collapsed && !isMobile ? 'justify-center px-0 py-2' : 'px-3 py-2'
        }`}
      >
        <LogOut size={16} strokeWidth={1.6} className="shrink-0 group-hover:text-danger transition-colors" />
        {(!collapsed || isMobile) && <span className="text-[13px] font-medium">{t('nav.logout')}</span>}
      </button>
    </div>
  </div>
);

const MainLayout = () => {
  const { t } = useTranslation();
  const { user, logout, hasRole } = useAuth();
  const { isDark } = useTheme();
  const location = useLocation();
  const collapsed = useAppStore((s) => s.sidebarCollapsed);
  const toggleSidebar = useAppStore((s) => s.toggleSidebar);
  const [mobileOpen, setMobileOpen] = useState(false);
  const [tasksOpen, setTasksOpen] = useState(false);
  const [settingsOpen, setSettingsOpen] = useState(() => {
    const params = new URLSearchParams(window.location.search);
    return params.get('settings') === 'open';
  });
  const [notificationsOpen, setNotificationsOpen] = useState(false);
  const { data: unreadCount = 0 } = useUnreadNotificationCount();
  useNotificationStream();
  useScrollRestoration();

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

  const anyOverlayOpen = settingsOpen || notificationsOpen || tasksOpen || mobileOpen;
  const blurCls = anyOverlayOpen ? 'blur-sm pointer-events-none select-none' : '';

  const allNav = [
    ...navItems,
    ...(hasRole('ADMIN')
      ? [
          { to: '/admin/tracked-assets', labelKey: 'nav.adminTrackedAssets', Icon: Database },
          { to: '/admin/users', labelKey: 'nav.adminUsers', Icon: Users },
        ]
      : []),
  ];

  const isActive = (path) => location.pathname === path;
  const sidebarWidth = collapsed ? 64 : 208;

  const sidebarProps = {
    collapsed,
    t,
    toggleSidebar,
    allNav,
    isActive,
    hasRole,
    setTasksOpen,
    setNotificationsOpen,
    setSettingsOpen,
    unreadCount,
    user,
    logout,
  };

  return (
    <div className="flex min-h-screen bg-bg-base relative">
      {isDark && (
        <div className="fixed inset-0 pointer-events-none z-0 overflow-hidden" aria-hidden="true">
          <div className="absolute top-0 left-1/4 w-[600px] h-[400px] rounded-full bg-accent/[0.04] blur-[150px] animate-float-slow" />
          <div className="absolute bottom-0 right-1/4 w-[400px] h-[300px] rounded-full bg-[#7c3aed]/[0.03] blur-[120px] animate-float-mid" />
        </div>
      )}

      <motion.aside
        animate={{ width: sidebarWidth }}
        initial={false}
        transition={{ type: 'spring', stiffness: 380, damping: 36, mass: 0.8 }}
        className={`hidden lg:flex flex-col fixed top-0 left-0 h-screen border-r border-border-default z-30 ${blurCls} overflow-hidden`}
        style={{
          background: 'var(--sidebar-bg)',
          backdropFilter: 'var(--sidebar-blur)',
          WebkitBackdropFilter: 'var(--sidebar-blur)',
          boxShadow: collapsed ? '4px 0 24px -8px rgba(0,0,0,0.25)' : '8px 0 40px -8px rgba(0,0,0,0.45)',
        }}
      >
        <div className="absolute inset-0 pointer-events-none">
          <div className="absolute inset-y-0 right-0 w-px bg-gradient-to-b from-transparent via-accent/20 to-transparent" />
          <div className="absolute top-0 inset-x-0 h-32 bg-gradient-to-b from-accent/[0.03] to-transparent" />
        </div>
        <div className="relative flex flex-col h-full">
          <SidebarContent {...sidebarProps} />
        </div>
      </motion.aside>
      <div className="hidden lg:block shrink-0 w-16" />

      <div
        className={`lg:hidden fixed top-0 left-0 right-0 z-40 h-12 flex items-center justify-between px-3 border-b border-border-default ${blurCls}`}
        style={{
          background: 'var(--sidebar-bg)',
          backdropFilter: 'var(--sidebar-blur)',
          WebkitBackdropFilter: 'var(--sidebar-blur)',
        }}
      >
        <button
          onClick={() => setMobileOpen(true)}
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
        <div className="w-8" />
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
              onClick={() => setMobileOpen(false)}
            />
            <motion.aside
              initial={{ x: '-100%' }}
              animate={{ x: 0 }}
              exit={{ x: '-100%' }}
              transition={{ duration: 0.32, ease: [0.32, 0.72, 0, 1] }}
              className="lg:hidden fixed top-0 left-0 bottom-0 z-50 w-60 border-r border-border-default"
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

      <div className={`flex-1 flex flex-col min-w-0 relative overflow-x-hidden ${blurCls}`}>
        <main className="flex-1 w-full">
          <div className="max-w-7xl mx-auto px-4 sm:px-6 py-6 lg:py-6 pt-16 lg:pt-6">
            <Outlet />
          </div>
        </main>
        <footer className="border-t border-border-default">
          <div className="max-w-7xl mx-auto px-4 sm:px-6 py-4 flex items-center justify-between">
            <p className="text-xs text-fg-subtle">{t('home.footer')}</p>
            <p className="text-xs text-fg-subtle font-mono">v0.18.0</p>
          </div>
        </footer>
      </div>

      {hasRole('ADMIN') && (
        <TasksPanel open={tasksOpen} onClose={() => setTasksOpen(false)} />
      )}
      <SettingsSidebar isOpen={settingsOpen} onClose={() => setSettingsOpen(false)} />
      <NotificationPanel isOpen={notificationsOpen} onClose={() => setNotificationsOpen(false)} />
      <OnboardingGate />
      <KeycloakActionToast />
    </div>
  );
};

export default MainLayout;
