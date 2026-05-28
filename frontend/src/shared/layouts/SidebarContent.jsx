import { Link } from 'react-router-dom';
import {
  TrendingUp, LogOut, Activity, Settings, Bell, User as UserIcon,
  ChevronLeft, ChevronRight,
} from 'lucide-react';
import SidebarNav from './SidebarNav';

const SidebarContent = ({
  isMobile = false,
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
  unreadCount,
  user,
  logout,
}) => (
  <div className="flex flex-col h-full">
    <div className={`flex items-center ${collapsed && !isMobile ? 'justify-center' : 'justify-between'} h-14 landscape:h-12 lg:landscape:h-14 px-3 border-b border-border-default shrink-0`}>
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

    <SidebarNav
      structure={navStructure}
      t={t}
      collapsed={collapsed}
      isMobile={isMobile}
      isActive={isActive}
      expandedGroups={expandedGroups}
      toggleGroup={toggleGroup}
    />

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
        data-tour="notifications-bell"
        title={collapsed && !isMobile ? t('nav.notifications') : undefined}
        className={`w-full group relative flex items-center gap-2.5 rounded-lg text-fg-muted hover:text-fg hover:bg-surface transition-all duration-150 bg-transparent border-none cursor-pointer ${
          collapsed && !isMobile ? 'justify-center px-0 py-2' : 'px-3 py-2'
        }`}
      >
        <span className="relative shrink-0">
          <Bell size={16} strokeWidth={1.6} className="group-hover:text-accent transition-colors" />
          {unreadCount > 0 && (
            <span className="absolute -top-0.5 -right-0.5 w-2 h-2 rounded-full bg-accent ring-2 ring-bg-base" aria-label={t('common.unreadNotifications', { count: unreadCount })} />
          )}
        </span>
        {(!collapsed || isMobile) && <span className="text-[13px] font-medium">{t('nav.notifications')}</span>}
        {(!collapsed || isMobile) && unreadCount > 0 && (
          <span className="ml-auto text-[10px] font-mono text-accent">{unreadCount}</span>
        )}
      </button>
      <button
        onClick={() => setProfileOpen(true)}
        data-tour="profile-menu"
        title={collapsed && !isMobile ? t('nav.profile') : undefined}
        className={`w-full group flex items-center gap-2.5 rounded-lg text-fg-muted hover:text-fg hover:bg-surface transition-all duration-150 bg-transparent border-none cursor-pointer ${
          collapsed && !isMobile ? 'justify-center px-0 py-2' : 'px-3 py-2'
        }`}
      >
        <UserIcon size={16} strokeWidth={1.6} className="shrink-0 group-hover:text-accent transition-colors" />
        {(!collapsed || isMobile) && <span className="text-[13px] font-medium">{t('nav.profile')}</span>}
      </button>
      <button
        onClick={() => setSettingsOpen(true)}
        data-tour="settings-menu"
        title={collapsed && !isMobile ? t('nav.settings') : undefined}
        className={`w-full group flex items-center gap-2.5 rounded-lg text-fg-muted hover:text-fg hover:bg-surface transition-all duration-150 bg-transparent border-none cursor-pointer ${
          collapsed && !isMobile ? 'justify-center px-0 py-2' : 'px-3 py-2'
        }`}
      >
        <Settings size={16} strokeWidth={1.6} className="shrink-0 group-hover:text-accent transition-colors" />
        {(!collapsed || isMobile) && <span className="text-[13px] font-medium">{t('nav.settings')}</span>}
      </button>
      {(!collapsed || isMobile) && (
        <button
          type="button"
          onClick={() => setProfileOpen(true)}
          className="w-full flex items-center gap-2 px-3 py-1.5 rounded-lg bg-surface border border-border-default hover:border-accent/40 hover:bg-accent/5 transition-colors cursor-pointer text-left"
        >
          <span className="w-6 h-6 rounded-full bg-gradient-accent text-white flex items-center justify-center text-[11px] font-bold shrink-0">
            {user?.username?.charAt(0).toUpperCase() || '?'}
          </span>
          <span className="text-[12px] font-medium text-fg truncate flex-1">{user?.username}</span>
          {hasRole('ADMIN') && (
            <span className="text-[9px] px-1.5 py-0.5 rounded-full font-bold uppercase bg-accent/10 text-accent tracking-wide shrink-0">
              {t('nav.adminBadge')}
            </span>
          )}
        </button>
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

export default SidebarContent;
