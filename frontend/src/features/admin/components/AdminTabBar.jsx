import { useTranslation } from 'react-i18next';
import { NavLink } from 'react-router-dom';
import { Database, Users } from 'lucide-react';

// Shared top-level admin navigation so the separate admin screens read as ONE panel: a single sidebar entry
// lands here and these tabs switch between the sections (preserving each section's own query state).
const TABS = [
  { to: '/admin/tracked-assets', labelKey: 'nav.adminTrackedAssets', Icon: Database },
  { to: '/admin/users', labelKey: 'nav.adminUsers', Icon: Users },
];

export default function AdminTabBar() {
  const { t } = useTranslation();
  return (
    <div className="flex flex-wrap gap-1 rounded-xl border border-border-default bg-bg-elevated p-1 backdrop-blur-md w-fit">
      {TABS.map(({ to, labelKey, Icon }) => (
        <NavLink
          key={to}
          to={to}
          end={false}
          className={({ isActive }) => `inline-flex items-center gap-2 rounded-lg px-3 py-2 text-sm font-semibold no-underline transition-colors ${
            isActive ? 'bg-accent/15 text-accent-bright' : 'text-fg-muted hover:bg-surface/60 hover:text-fg'
          }`}
        >
          <Icon className="h-4 w-4" />
          {t(labelKey)}
        </NavLink>
      ))}
    </div>
  );
}
