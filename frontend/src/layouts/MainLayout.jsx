import { useState, useEffect } from 'react';
import { Outlet, Link, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useTheme } from '../context/ThemeContext';
import { AnimatePresence, motion } from 'framer-motion';
import {
  Home, Newspaper, BarChart3, TrendingUp, Bitcoin,
  DollarSign, Gem, LineChart, Users, Shield, Info,
  LogOut, LogIn, Sun, Moon, Briefcase, Activity,
  ChevronLeft, ChevronRight, Menu, Landmark,
} from 'lucide-react';
import TasksPanel from '../components/TasksPanel';

const navItems = [
  { to: '/', label: 'Home', Icon: Home },
  { to: '/news', label: 'News', Icon: Newspaper },
  { to: '/market', label: 'Market', Icon: BarChart3 },
  { to: '/stocks', label: 'Stocks', Icon: TrendingUp },
  { to: '/crypto', label: 'Crypto', Icon: Bitcoin },
  { to: '/forex', label: 'Forex', Icon: DollarSign },
  { to: '/metals', label: 'Metals', Icon: Gem },
  { to: '/funds', label: 'Funds', Icon: Briefcase },
  { to: '/bonds', label: 'Bonds', Icon: Landmark },
  { to: '/charts', label: 'Charts', Icon: LineChart },
];

const MainLayout = () => {
  const { isAuthenticated, user, login, logout, hasRole } = useAuth();
  const { isDark, toggleTheme } = useTheme();
  const location = useLocation();
  const [collapsed, setCollapsed] = useState(() => {
    try { return localStorage.getItem('sidebar-collapsed') === 'true'; } catch { return false; }
  });
  const [mobileOpen, setMobileOpen] = useState(false);
  const [tasksOpen, setTasksOpen] = useState(false);

  useEffect(() => {
    try { localStorage.setItem('sidebar-collapsed', collapsed); } catch {}
  }, [collapsed]);

  useEffect(() => { setMobileOpen(false); }, [location.pathname]);

  const allNav = [
    ...navItems,
    ...(hasRole('ADMIN') ? [{ to: '/users', label: 'Users', Icon: Users }] : []),
    { to: '/2fa', label: '2FA', Icon: Shield },
    { to: '/about', label: 'About', Icon: Info },
  ];

  const isActive = (path) => location.pathname === path;
  const sidebarW = collapsed ? 'w-16' : 'w-52';

  const SidebarContent = ({ isMobile = false }) => (
    <div className="flex flex-col h-full">
      <div className={`flex items-center ${collapsed && !isMobile ? 'justify-center' : 'justify-between'} h-14 px-3 border-b border-border-default shrink-0`}>
        {(!collapsed || isMobile) && (
          <Link to="/" className="flex items-center gap-2 no-underline group">
            <span className="flex items-center justify-center w-8 h-8 rounded-lg bg-accent/15 text-accent group-hover:bg-accent/25 transition-all duration-200">
              <TrendingUp className="w-4 h-4" />
            </span>
            <span className="text-sm font-bold text-fg tracking-tight">Finance</span>
          </Link>
        )}
        {collapsed && !isMobile && (
          <Link to="/" className="flex items-center justify-center w-8 h-8 rounded-lg bg-accent/15 text-accent hover:bg-accent/25 transition-all no-underline">
            <TrendingUp className="w-4 h-4" />
          </Link>
        )}
        {!isMobile && (
          <button
            onClick={() => setCollapsed(!collapsed)}
            className="hidden lg:flex items-center justify-center w-7 h-7 rounded-md text-fg-muted hover:text-fg hover:bg-surface transition-colors bg-transparent border-none cursor-pointer"
            title={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
          >
            {collapsed ? <ChevronRight size={14} /> : <ChevronLeft size={14} />}
          </button>
        )}
      </div>

      <nav className="flex-1 overflow-y-auto px-2 py-2 space-y-0.5" style={{ scrollbarWidth: 'thin' }}>
        {allNav.map(({ to, label, Icon }) => (
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
            <Icon
              size={16}
              strokeWidth={1.6}
              className={`shrink-0 transition-colors duration-150 ${isActive(to) ? 'text-accent' : 'group-hover:text-fg-muted'}`}
            />
            {(!collapsed || isMobile) && (
              <span className="text-[13px] font-medium">{label}</span>
            )}
            {isActive(to) && (
              <motion.span
                layoutId="sidebar-indicator"
                className={`absolute left-0 top-1/2 -translate-y-1/2 w-[3px] rounded-r-full bg-accent ${collapsed && !isMobile ? 'h-5' : 'h-6'}`}
                transition={{ type: 'spring', stiffness: 500, damping: 35 }}
              />
            )}
          </Link>
        ))}
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
          onClick={toggleTheme}
          title={collapsed && !isMobile ? (isDark ? 'Light mode' : 'Dark mode') : undefined}
          className={`w-full group flex items-center gap-2.5 rounded-lg text-fg-muted hover:text-fg hover:bg-surface transition-all duration-150 bg-transparent border-none cursor-pointer ${
            collapsed && !isMobile ? 'justify-center px-0 py-2' : 'px-3 py-2'
          }`}
        >
          {isDark
            ? <Sun size={16} strokeWidth={1.6} className="shrink-0 group-hover:text-warning transition-colors" />
            : <Moon size={16} strokeWidth={1.6} className="shrink-0 group-hover:text-accent transition-colors" />
          }
          {(!collapsed || isMobile) && (
            <span className="text-[13px] font-medium">{isDark ? 'Light mode' : 'Dark mode'}</span>
          )}
        </button>
        {isAuthenticated ? (
          <>
            {(!collapsed || isMobile) && (
              <div className="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-surface border border-border-default">
                <span className="w-6 h-6 rounded-full bg-accent/80 text-white flex items-center justify-center text-[11px] font-semibold shrink-0">
                  {user?.username?.charAt(0).toUpperCase() || '?'}
                </span>
                <span className="text-[12px] font-medium text-fg truncate">{user?.username}</span>
                {hasRole('ADMIN') && (
                  <span className="text-[9px] px-1 py-px rounded font-semibold uppercase bg-accent/15 text-accent tracking-wide shrink-0">
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
          </>
        ) : (
          <button
            onClick={login}
            className={`w-full flex items-center gap-2.5 rounded-lg bg-accent text-white font-semibold text-[13px] border-none cursor-pointer transition-all duration-150 hover:bg-accent-bright ${
              collapsed && !isMobile ? 'justify-center px-0 py-2' : 'px-3 py-2'
            }`}
          >
            <LogIn size={16} strokeWidth={1.6} className="shrink-0" />
            {(!collapsed || isMobile) && <span>Login</span>}
          </button>
        )}
      </div>
    </div>
  );

  return (
    <div className="flex min-h-screen bg-bg-base relative">
      {isDark && (
        <div className="fixed inset-0 pointer-events-none z-0 overflow-hidden" aria-hidden="true">
          <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_top,#0c0a14_0%,#050506_50%,#020203_100%)]" />
          <div className="absolute inset-0 opacity-[0.03]" style={{ backgroundImage: 'radial-gradient(rgba(140,130,220,0.8) 1px, transparent 1px)', backgroundSize: '24px 24px' }} />
          <div className="absolute -top-[200px] left-1/2 -translate-x-1/2 w-[900px] h-[600px] rounded-full bg-accent/[0.08] blur-[150px] animate-float-slow" />
          <div className="absolute top-[30%] -left-[200px] w-[600px] h-[500px] rounded-full bg-[#7c3aed]/[0.05] blur-[120px] animate-float-mid" />
          <div className="absolute top-[50%] -right-[150px] w-[500px] h-[400px] rounded-full bg-accent/[0.04] blur-[100px] animate-float-fast" />
          <div className="absolute bottom-0 left-1/2 -translate-x-1/2 w-[600px] h-[300px] rounded-full bg-[#7c3aed]/[0.05] blur-[120px] animate-pulse-glow" />
          <div className="absolute inset-0 noise-overlay" />
        </div>
      )}

      <aside
        className={`hidden lg:flex flex-col fixed top-0 left-0 h-screen ${sidebarW} border-r border-border-default z-20 transition-all duration-200`}
        style={{
          background: isDark ? 'rgba(5, 5, 6, 0.85)' : 'rgba(255, 255, 255, 0.85)',
          backdropFilter: 'blur(16px) saturate(1.8)',
          WebkitBackdropFilter: 'blur(16px) saturate(1.8)',
        }}
      >
        <SidebarContent />
      </aside>
      <div className={`hidden lg:block shrink-0 ${sidebarW} transition-all duration-200`} />

      <div
        className="lg:hidden fixed top-0 left-0 right-0 z-40 h-12 flex items-center justify-between px-3 border-b border-border-default"
        style={{
          background: isDark ? 'rgba(5, 5, 6, 0.85)' : 'rgba(255, 255, 255, 0.85)',
          backdropFilter: 'blur(16px) saturate(1.8)',
          WebkitBackdropFilter: 'blur(16px) saturate(1.8)',
        }}
      >
        <button
          onClick={() => setMobileOpen(true)}
          className="flex items-center justify-center w-8 h-8 rounded-md text-fg-muted hover:text-fg hover:bg-surface transition-colors bg-transparent border-none cursor-pointer"
        >
          <Menu size={18} />
        </button>
        <Link to="/" className="flex items-center gap-2 no-underline">
          <span className="flex items-center justify-center w-7 h-7 rounded-lg bg-accent/15 text-accent">
            <TrendingUp className="w-3.5 h-3.5" />
          </span>
          <span className="text-sm font-bold text-fg">Finance</span>
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
              transition={{ type: 'spring', damping: 28, stiffness: 300 }}
              className="lg:hidden fixed top-0 left-0 bottom-0 z-50 w-60 border-r border-border-default"
              style={{
                background: isDark ? 'rgba(5, 5, 6, 0.97)' : 'rgba(255, 255, 255, 0.97)',
                backdropFilter: 'blur(20px)',
                WebkitBackdropFilter: 'blur(20px)',
              }}
            >
              <SidebarContent isMobile />
            </motion.aside>
          </>
        )}
      </AnimatePresence>

      <div className="flex-1 flex flex-col min-w-0 relative z-10">
        <main className="flex-1 w-full">
          <div className="max-w-7xl mx-auto px-4 sm:px-6 py-6 lg:py-6 pt-16 lg:pt-6">
            <Outlet />
          </div>
        </main>
        <footer className="relative border-t border-border-default">
          <div className="section-line" />
          <div className="max-w-7xl mx-auto px-4 sm:px-6 py-5 flex items-center justify-between">
            <p className="text-xs text-fg-subtle">&copy; 2026 Finance Portal</p>
            <p className="text-xs text-fg-subtle">Keycloak &middot; Spring Security</p>
          </div>
        </footer>
      </div>

      {hasRole('ADMIN') && (
        <TasksPanel open={tasksOpen} onClose={() => setTasksOpen(false)} />
      )}
    </div>
  );
};

export default MainLayout;
