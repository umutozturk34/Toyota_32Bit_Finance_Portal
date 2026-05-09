import { useState, useEffect, useRef } from 'react';
import { motion } from 'framer-motion';
import { Outlet, Link, useLocation, useNavigationType } from 'react-router-dom';
import { useAuth } from '../../features/auth/AuthContext';
import { useTheme } from '../context/ThemeContext';
import useAppStore from '../stores/useAppStore';
import { AnimatePresence } from 'framer-motion';
import {
  Newspaper, BarChart3, TrendingUp, Bitcoin,
  DollarSign, Shield,
  LogOut, Briefcase, Activity, Settings, Bell, Eye,
  ChevronLeft, ChevronRight, Menu, Landmark, Wallet, Database, Gem, Users,
  MessageCircle,
} from 'lucide-react';
import { useUnreadMessageCount } from '../hooks/useMessages';
import TasksPanel from '../../features/admin/components/TasksPanel';
import SettingsSidebar from '../../features/settings/SettingsSidebar';
import NotificationPanel from '../../features/notifications/NotificationPanel';
import { useUnreadNotificationCount } from '../hooks/useNotifications';
import useNotificationStream from '../hooks/useNotificationStream';
import OnboardingGate from '../../features/onboarding/OnboardingGate';
import KeycloakActionToast from '../../features/auth/components/KeycloakActionToast';

const navItems = [
  { to: '/market', label: 'Market', Icon: BarChart3 },
  { to: '/news', label: 'News', Icon: Newspaper },
  { to: '/stocks', label: 'Stocks', Icon: TrendingUp },
  { to: '/crypto', label: 'Crypto', Icon: Bitcoin },
  { to: '/forex', label: 'Forex', Icon: DollarSign },
  { to: '/funds', label: 'Funds', Icon: Briefcase },
  { to: '/commodities', label: 'Emtia', Icon: Gem },
  { to: '/bonds', label: 'Bonds', Icon: Landmark },
  { to: '/portfolio', label: 'Portfolio', Icon: Wallet },
  { to: '/watch', label: 'Takip', Icon: Eye },
  { to: '/messages', label: 'Mesajlar', Icon: MessageCircle, badgeKey: 'messages' },
];

const MainLayout = () => {
  const { user, logout, hasRole } = useAuth();
  const { isDark } = useTheme();
  const location = useLocation();
  const collapsed = useAppStore((s) => s.sidebarCollapsed);
  const toggleSidebar = useAppStore((s) => s.toggleSidebar);
  const [mobileOpen, setMobileOpen] = useState(false);
  const [tasksOpen, setTasksOpen] = useState(false);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [notificationsOpen, setNotificationsOpen] = useState(false);
  const { data: unreadCount = 0 } = useUnreadNotificationCount();
  const { data: unreadMessageCount = 0 } = useUnreadMessageCount();
  useNotificationStream();

  const navType = useNavigationType();
  const lastPathRef = useRef(location.pathname);

  useEffect(() => {
    setMobileOpen(false);
    if (lastPathRef.current === location.pathname) return;
    lastPathRef.current = location.pathname;
    if (navType !== 'POP') {
      window.scrollTo(0, 0);
    }
  }, [location.pathname, navType]);

  const allNav = [
    ...navItems,
    ...(hasRole('ADMIN')
      ? [
          { to: '/admin/tracked-assets', label: 'Tracked Assets', Icon: Database },
          { to: '/admin/users', label: 'Users', Icon: Users },
        ]
      : []),
  ];

  const isActive = (path) => location.pathname === path;
  const sidebarW = collapsed ? 'w-16' : 'w-52';

  const SidebarContent = ({ isMobile = false }) => (
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
            title={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
          >
            {collapsed ? <ChevronRight size={14} /> : <ChevronLeft size={14} />}
          </button>
        )}
      </div>

      <nav className="flex-1 overflow-y-auto px-2 py-2 space-y-0.5" style={{ scrollbarWidth: 'thin' }}>
        {allNav.map(({ to, label, Icon, badgeKey }) => {
          const badge = badgeKey === 'messages' ? unreadMessageCount : 0;
          return (
            <Link
              key={to}
              to={to}
              title={collapsed && !isMobile ? label : undefined}
              className={`group relative flex items-center gap-2.5 rounded-lg no-underline transition-all duration-150 ${
                collapsed && !isMobile ? 'justify-center px-0 py-2' : 'px-3 py-2'
              } ${
                isActive(to)
                  ? 'bg-accent/10 text-fg'
                  : 'text-fg-muted hover:text-fg hover:bg-surface'
              }`}
            >
              <span className="relative shrink-0">
                <Icon
                  size={16}
                  strokeWidth={1.6}
                  className={`transition-colors duration-150 ${isActive(to) ? 'text-accent' : 'group-hover:text-fg-muted'}`}
                />
                {badge > 0 && collapsed && !isMobile && (
                  <span className="absolute -top-1 -right-1 min-w-[14px] h-[14px] px-1 rounded-full bg-accent text-white text-[8px] font-bold flex items-center justify-center font-mono leading-none">
                    {badge > 9 ? '9+' : badge}
                  </span>
                )}
              </span>
              {(!collapsed || isMobile) && (
                <span className="text-[13px] font-medium">{label}</span>
              )}
              {(!collapsed || isMobile) && badge > 0 && (
                <span className="ml-auto text-[10px] font-mono text-accent">{badge > 99 ? '99+' : badge}</span>
              )}
              {isActive(to) && (
                <motion.span
                  layoutId="sidebar-indicator"
                  className={`absolute left-0 top-1/2 -translate-y-1/2 w-[3px] rounded-r-full bg-gradient-accent ${collapsed && !isMobile ? 'h-5' : 'h-6'}`}
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
            title={collapsed && !isMobile ? 'Tasks' : undefined}
            className={`w-full group flex items-center gap-2.5 rounded-lg text-fg-muted hover:text-fg hover:bg-surface transition-all duration-150 bg-transparent border-none cursor-pointer ${
              collapsed && !isMobile ? 'justify-center px-0 py-2' : 'px-3 py-2'
            }`}
          >
            <Activity size={16} strokeWidth={1.6} className="shrink-0 group-hover:text-accent transition-colors" />
            {(!collapsed || isMobile) && <span className="text-[13px] font-medium">Tasks</span>}
          </button>
        </div>
      )}

      <div className="border-t border-border-default px-2 py-2 space-y-1 shrink-0">
        <button
          onClick={() => setNotificationsOpen(true)}
          title={collapsed && !isMobile ? 'Bildirimler' : undefined}
          className={`w-full group relative flex items-center gap-2.5 rounded-lg text-fg-muted hover:text-fg hover:bg-surface transition-all duration-150 bg-transparent border-none cursor-pointer ${
            collapsed && !isMobile ? 'justify-center px-0 py-2' : 'px-3 py-2'
          }`}
        >
          <span className="relative shrink-0">
            <Bell size={16} strokeWidth={1.6} className="group-hover:text-accent transition-colors" />
            {unreadCount > 0 && (
              <span className="absolute -top-1 -right-1 min-w-[14px] h-[14px] px-1 rounded-full bg-accent text-white text-[8px] font-bold flex items-center justify-center font-mono leading-none">
                {unreadCount > 99 ? '99+' : unreadCount}
              </span>
            )}
          </span>
          {(!collapsed || isMobile) && <span className="text-[13px] font-medium">Bildirimler</span>}
          {(!collapsed || isMobile) && unreadCount > 0 && (
            <span className="ml-auto text-[10px] font-mono text-accent">{unreadCount}</span>
          )}
        </button>
        <button
          onClick={() => setSettingsOpen(true)}
          title={collapsed && !isMobile ? 'Ayarlar' : undefined}
          className={`w-full group flex items-center gap-2.5 rounded-lg text-fg-muted hover:text-fg hover:bg-surface transition-all duration-150 bg-transparent border-none cursor-pointer ${
            collapsed && !isMobile ? 'justify-center px-0 py-2' : 'px-3 py-2'
          }`}
        >
          <Settings size={16} strokeWidth={1.6} className="shrink-0 group-hover:text-accent transition-colors" />
          {(!collapsed || isMobile) && <span className="text-[13px] font-medium">Ayarlar</span>}
        </button>
        {(!collapsed || isMobile) && (
          <div className="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-surface border border-border-default">
            <span className="w-6 h-6 rounded-full bg-gradient-accent text-white flex items-center justify-center text-[11px] font-bold shrink-0">
              {user?.username?.charAt(0).toUpperCase() || '?'}
            </span>
            <span className="text-[12px] font-medium text-fg truncate">{user?.username}</span>
            {hasRole('ADMIN') && (
              <span className="text-[9px] px-1.5 py-0.5 rounded-full font-bold uppercase bg-accent/10 text-accent tracking-wide shrink-0">
                admin
              </span>
            )}
          </div>
        )}
        <button
          onClick={logout}
          title={collapsed && !isMobile ? 'Logout' : undefined}
          className={`w-full group flex items-center gap-2.5 rounded-lg text-fg-muted hover:text-fg hover:bg-surface transition-all duration-150 bg-transparent border-none cursor-pointer ${
            collapsed && !isMobile ? 'justify-center px-0 py-2' : 'px-3 py-2'
          }`}
        >
          <LogOut size={16} strokeWidth={1.6} className="shrink-0 group-hover:text-danger transition-colors" />
          {(!collapsed || isMobile) && <span className="text-[13px] font-medium">Logout</span>}
        </button>
      </div>
    </div>
  );

  return (
    <div className="flex min-h-screen bg-bg-base relative">
      {isDark && (
        <div className="fixed inset-0 pointer-events-none z-0 overflow-hidden" aria-hidden="true">
          <div className="absolute top-0 left-1/4 w-[600px] h-[400px] rounded-full bg-accent/[0.04] blur-[150px] animate-float-slow" />
          <div className="absolute bottom-0 right-1/4 w-[400px] h-[300px] rounded-full bg-[#7c3aed]/[0.03] blur-[120px] animate-float-mid" />
        </div>
      )}

      <aside
        className={`hidden lg:flex flex-col fixed top-0 left-0 h-screen ${sidebarW} border-r border-border-default z-20 transition-all duration-200`}
        style={{
          background: 'var(--sidebar-bg)',
          backdropFilter: 'var(--sidebar-blur)',
          WebkitBackdropFilter: 'var(--sidebar-blur)',
        }}
      >
        <SidebarContent />
      </aside>
      <div className="hidden lg:block shrink-0 w-16" />

      <div
        className="lg:hidden fixed top-0 left-0 right-0 z-40 h-12 flex items-center justify-between px-3 border-b border-border-default"
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
              <SidebarContent isMobile />
            </motion.aside>
          </>
        )}
      </AnimatePresence>

      <div className="flex-1 flex flex-col min-w-0 relative overflow-x-hidden">
        <main className="flex-1 w-full">
          <div className="max-w-7xl mx-auto px-4 sm:px-6 py-6 lg:py-6 pt-16 lg:pt-6">
            <Outlet />
          </div>
        </main>
        <footer className="border-t border-border-default">
          <div className="max-w-7xl mx-auto px-4 sm:px-6 py-4 flex items-center justify-between">
            <p className="text-xs text-fg-subtle">&copy; 2026 Finance Portal</p>
            <p className="text-xs text-fg-subtle font-mono">v0.10.0</p>
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
