import { useTranslation } from 'react-i18next';
import { motion } from 'framer-motion';
import { AlertCircle, Star } from 'lucide-react';

export default function WatchViewTabs({ view, onChange, watchCount, alertsCount }) {
  const { t } = useTranslation();
  const tabs = [
    { id: 'watchlist', label: t('watch.tabs.watchlist'), Icon: Star, count: watchCount },
    { id: 'alerts', label: t('watch.tabs.alerts'), Icon: AlertCircle, count: alertsCount },
  ];
  return (
    <div className="flex gap-1 rounded-xl border border-border-default bg-bg-elevated p-1 self-start">
      {tabs.map(({ id, label, Icon, count }) => {
        const active = view === id;
        return (
          <button
            key={id}
            type="button"
            onClick={() => onChange(id)}
            className={`relative inline-flex items-center gap-2 px-4 py-2 rounded-lg text-xs font-semibold transition-colors border-none cursor-pointer ${
              active ? 'text-accent' : 'text-fg-muted hover:text-fg'
            } bg-transparent`}
          >
            {active && (
              <motion.span
                layoutId="watch-view-tab"
                className="absolute inset-0 rounded-lg bg-accent/12"
                transition={{ type: 'spring', stiffness: 320, damping: 28 }}
              />
            )}
            <Icon className="relative z-10 h-3.5 w-3.5" />
            <span className="relative z-10">{label}</span>
            {count != null && count > 0 && (
              <span className={`relative z-10 text-[10px] font-mono px-1.5 py-0.5 rounded-md ${
                active ? 'bg-accent/20 text-accent' : 'bg-surface text-fg-subtle'
              }`}>{count}</span>
            )}
          </button>
        );
      })}
    </div>
  );
}
