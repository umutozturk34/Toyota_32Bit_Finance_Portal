import { useState } from 'react';
import { Outlet, Link, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useTheme } from '../context/ThemeContext';
import { AnimatePresence, motion } from 'framer-motion';
import {
  Home, Newspaper, BarChart3, TrendingUp, Bitcoin,
  DollarSign, Gem, LineChart, Users, Shield, Info,
  LogOut, LogIn, Menu, X, Sun, Moon
} from 'lucide-react';
const navItems = [
  { to: '/', label: 'Home', Icon: Home },
  { to: '/news', label: 'News', Icon: Newspaper },
  { to: '/market', label: 'Market', Icon: BarChart3 },
  { to: '/stocks', label: 'Stocks', Icon: TrendingUp },
  { to: '/crypto', label: 'Crypto', Icon: Bitcoin },
  { to: '/forex', label: 'Forex', Icon: DollarSign },
  { to: '/metals', label: 'Metals', Icon: Gem },
  { to: '/charts', label: 'Charts', Icon: LineChart },
];
const MainLayout = () => {
  const { isAuthenticated, user, login, logout, hasRole } = useAuth();
  const { isDark, toggleTheme } = useTheme();
  const [menuOpen, setMenuOpen] = useState(false);
  const location = useLocation();
  const allNav = [
    ...navItems,
    ...(hasRole('ADMIN') ? [{ to: '/users', label: 'Users', Icon: Users }] : []),
    { to: '/2fa', label: '2FA', Icon: Shield },
    { to: '/about', label: 'About', Icon: Info },
  ];
  const isActive = (path) => location.pathname === path;
  const isHomePage = location.pathname === '/';
  return (
    <div className="flex flex-col min-h-screen bg-bg-base relative">
      {}
      {isDark && (
        <div className="fixed inset-0 pointer-events-none z-0 overflow-hidden" aria-hidden="true">
          {}
          <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_top,#0c0a14_0%,#050506_50%,#020203_100%)]" />
          {}
          <div className="absolute inset-0 opacity-[0.03]" style={{ backgroundImage: 'radial-gradient(rgba(140,130,220,0.8) 1px, transparent 1px)', backgroundSize: '24px 24px' }} />
          {}
          <div className="absolute -top-[200px] left-1/2 -translate-x-1/2 w-[900px] h-[600px] rounded-full bg-accent/[0.08] blur-[150px] animate-float-slow" />
          <div className="absolute top-[30%] -left-[200px] w-[600px] h-[500px] rounded-full bg-[#7c3aed]/[0.05] blur-[120px] animate-float-mid" />
          <div className="absolute top-[50%] -right-[150px] w-[500px] h-[400px] rounded-full bg-accent/[0.04] blur-[100px] animate-float-fast" />
          <div className="absolute bottom-0 left-1/2 -translate-x-1/2 w-[600px] h-[300px] rounded-full bg-[#7c3aed]/[0.05] blur-[120px] animate-pulse-glow" />
          {}
          <div className="absolute inset-0 noise-overlay" />
        </div>
      )}
      {}
      <header
        className="sticky top-0 z-50 border-b border-border-default"
        style={{
          background: isDark
            ? 'rgba(5, 5, 6, 0.65)'
            : 'rgba(255, 255, 255, 0.75)',
          backdropFilter: 'blur(16px) saturate(1.8)',
          WebkitBackdropFilter: 'blur(16px) saturate(1.8)',
        }}
      >
        {}
        <div className="absolute top-0 left-0 right-0 h-px bg-gradient-to-r from-transparent via-border-hover to-transparent" />
        <div className="max-w-7xl mx-auto px-4 sm:px-6 h-14 flex items-center justify-between gap-6 relative">
          {}
          <Link to="/" className="flex items-center gap-2.5 no-underline shrink-0 group">
            <span className="relative flex items-center justify-center w-8 h-8 rounded-lg bg-accent/15 text-accent group-hover:bg-accent/25 transition-all duration-200">
              <TrendingUp className="w-4.5 h-4.5" />
              <span className="absolute inset-0 rounded-lg opacity-0 group-hover:opacity-100 transition-opacity duration-300" style={{ boxShadow: '0 0 16px rgba(94,106,210,0.3)' }} />
            </span>
          </Link>
          {}
          <nav className="hidden lg:flex items-center gap-0.5">
            {allNav.map(({ to, label, Icon }) => (
              <Link
                key={to}
                to={to}
                className={`group relative flex items-center gap-1.5 px-3 py-1.5 rounded-md text-[13px] font-medium no-underline transition-all duration-150
                  ${isActive(to)
                    ? 'text-fg bg-surface'
                    : 'text-fg-muted hover:text-fg hover:bg-surface'
                  }`}
              >
                <Icon
                  size={14}
                  strokeWidth={1.6}
                  className={`transition-colors duration-150
                    ${isActive(to) ? 'text-accent' : 'text-transparent group-hover:text-fg-muted'}`}
                />
                {label}
                {isActive(to) && (
                  <motion.span
                    layoutId="nav-indicator"
                    className="absolute -bottom-[9px] left-3 right-3 h-px bg-accent"
                    transition={{ type: 'spring', stiffness: 500, damping: 35 }}
                  />
                )}
              </Link>
            ))}
          </nav>
          {}
          <div className="flex items-center gap-1.5 shrink-0">
            {}
            <button
              onClick={toggleTheme}
              className="group flex items-center justify-center w-8 h-8 rounded-md bg-transparent border-none cursor-pointer text-fg-muted hover:text-fg hover:bg-surface transition-all duration-150"
              aria-label={isDark ? 'Switch to light mode' : 'Switch to dark mode'}
              title={isDark ? 'Light mode' : 'Dark mode'}
            >
              {isDark
                ? <Sun size={16} strokeWidth={1.6} className="group-hover:text-warning transition-colors duration-150" />
                : <Moon size={16} strokeWidth={1.6} className="group-hover:text-accent transition-colors duration-150" />
              }
            </button>
            <div className="w-px h-5 bg-border-default" />
            {isAuthenticated ? (
              <>
                <div className="hidden sm:flex items-center gap-2 px-2.5 py-1 rounded-md bg-surface border border-border-default">
                  <span className="w-6 h-6 rounded-full bg-accent/80 text-white flex items-center justify-center text-[11px] font-semibold">
                    {user?.username?.charAt(0).toUpperCase() || '?'}
                  </span>
                  <span className="text-[13px] font-medium text-fg">{user?.username}</span>
                  {hasRole('ADMIN') && (
                    <span className="text-[10px] px-1.5 py-px rounded font-semibold uppercase bg-accent/15 text-accent tracking-wide">
                      admin
                    </span>
                  )}
                </div>
                <button
                  onClick={logout}
                  className="group flex items-center gap-1.5 px-2.5 py-1.5 bg-transparent text-fg-muted border-none rounded-md text-[13px] font-medium cursor-pointer transition-colors duration-150 hover:text-fg hover:bg-surface"
                >
                  <LogOut size={14} strokeWidth={1.6} className="text-transparent group-hover:text-fg-muted transition-colors duration-150" />
                  <span className="hidden sm:inline">Logout</span>
                </button>
              </>
            ) : (
              <button
                onClick={login}
                className="relative flex items-center gap-1.5 px-4 py-1.5 bg-accent text-white border-none rounded-md font-semibold text-[13px] cursor-pointer transition-all duration-150 hover:bg-accent-bright active:scale-[0.98]"
                style={{ boxShadow: isDark ? '0 0 0 1px rgba(94,106,210,0.5), 0 2px 8px rgba(94,106,210,0.25), inset 0 1px 0 0 rgba(255,255,255,0.15)' : undefined }}
              >
                <LogIn size={14} strokeWidth={1.6} />
                Login
              </button>
            )}
            {}
            <button
              className="flex lg:hidden items-center justify-center w-8 h-8 bg-transparent border-none cursor-pointer text-fg-muted hover:text-fg transition-colors"
              onClick={() => setMenuOpen(!menuOpen)}
              aria-label="Toggle menu"
            >
              {menuOpen ? <X size={18} /> : <Menu size={18} />}
            </button>
          </div>
        </div>
        {}
        <AnimatePresence>
          {menuOpen && (
            <motion.div
              initial={{ opacity: 0, height: 0 }}
              animate={{ opacity: 1, height: 'auto' }}
              exit={{ opacity: 0, height: 0 }}
              transition={{ duration: 0.15 }}
              className="lg:hidden overflow-hidden border-t border-border-default"
              style={{
                background: isDark ? 'rgba(5, 5, 6, 0.95)' : 'rgba(255, 255, 255, 0.95)',
                backdropFilter: 'blur(20px)',
                WebkitBackdropFilter: 'blur(20px)',
              }}
            >
              <nav className="flex flex-col px-4 py-2">
                {allNav.map(({ to, label, Icon }) => (
                  <Link
                    key={to}
                    to={to}
                    className={`flex items-center gap-3 px-2 py-2.5 text-sm font-medium no-underline border-b border-border-default last:border-b-0 transition-colors duration-150
                      ${isActive(to) ? 'text-fg' : 'text-fg-muted hover:text-fg'}`}
                    onClick={() => setMenuOpen(false)}
                  >
                    <Icon size={16} strokeWidth={1.6} className={isActive(to) ? 'text-accent' : 'text-fg-subtle'} />
                    {label}
                  </Link>
                ))}
              </nav>
            </motion.div>
          )}
        </AnimatePresence>
      </header>
      {}
      <main className="flex-1 w-full relative z-10">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 py-6">
          <Outlet />
        </div>
      </main>
      {}
      <footer className="relative z-10 border-t border-border-default">
        <div className="section-line" />
        <div className="max-w-7xl mx-auto px-4 sm:px-6 py-5 flex items-center justify-between">
          <p className="text-xs text-fg-subtle">&copy; 2026 Finance Portal</p>
          <p className="text-xs text-fg-subtle">Keycloak &middot; Spring Security</p>
        </div>
      </footer>
    </div>
  );
};
export default MainLayout;
