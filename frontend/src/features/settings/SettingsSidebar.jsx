import { createPortal } from 'react-dom';
import { useTranslation } from 'react-i18next';
import { motion, AnimatePresence } from 'framer-motion';
import SideDrawer from '../../shared/components/modal/SideDrawer';
import {
  Settings as SettingsIcon, Palette, Languages, BarChart3, Bell,
  Sun, Moon, LogOut, Coins,
} from 'lucide-react';
import { useUserPreferences, useUpdateUserPreferences } from '../../shared/hooks/useUserPreferences';
import useAppStore from '../../shared/stores/useAppStore';
import { useTheme } from '../../shared/context/useTheme';
import { useAuth } from '../auth/useAuth';
import NotificationPreferencesSection from './NotificationPreferencesSection';
import { SUPPORTED_DISPLAY_CURRENCIES } from '../../shared/constants/currencies';

const CURRENCY_LABELS = {
  TRY: '₺ TRY',
  USD: '$ USD',
  EUR: '€ EUR',
};

const LANGUAGE_OPTIONS = [
  { value: 'tr', labelKey: 'settings.language.tr' },
  { value: 'en', labelKey: 'settings.language.en' },
];

const CHART_RANGE_VALUES = ['1W', '1M', '3M', '6M', '1Y', '3Y', '5Y', 'ALL'];

const CURRENCY_OPTIONS = [
  ...SUPPORTED_DISPLAY_CURRENCIES.map((code) => ({ value: code, label: CURRENCY_LABELS[code] })),
  { value: 'ORIGINAL', labelKey: 'settings.currencyOriginal' },
];

function SegmentedControl({ options, value, onChange, layoutId, compact = false }) {
  const { t } = useTranslation();
  const padding = compact ? 'px-2 py-1' : 'px-2.5 py-1.5';
  return (
    <div className="flex gap-0.5 rounded-lg border border-border-default bg-bg-elevated p-0.5 overflow-hidden">
      {options.map((opt) => {
        const Icon = opt.Icon;
        const active = value === opt.value;
        const label = opt.labelKey ? t(opt.labelKey) : opt.label;
        return (
          <button
            key={opt.value}
            type="button"
            onClick={() => onChange(opt.value)}
            title={label}
            className={`relative flex-1 rounded-md ${padding} text-[11px] font-medium transition-all border-none cursor-pointer bg-transparent flex items-center justify-center`}
          >
            {active && (
              <motion.span
                layoutId={layoutId}
                className="absolute inset-0 rounded-md bg-accent/15"
                transition={{ type: 'spring', stiffness: 300, damping: 30 }}
              />
            )}
            <span className={`relative z-10 flex items-center justify-center gap-1 ${active ? 'text-accent' : 'text-fg-muted hover:text-fg'}`}>
              {Icon ? <Icon className="h-3.5 w-3.5" /> : label}
            </span>
          </button>
        );
      })}
    </div>
  );
}

function ThemeMorphToggle({ value, onChange, t }) {
  const isDark = value === 'DARK';
  const handleClick = () => onChange(isDark ? 'LIGHT' : 'DARK');
  const label = t(isDark ? 'theme.LIGHT' : 'theme.DARK');
  return (
    <motion.button
      type="button"
      onClick={handleClick}
      whileTap={{ scale: 0.9 }}
      aria-label={label}
      title={label}
      className="relative overflow-hidden flex items-center justify-center w-9 h-9 rounded-lg border border-border-default bg-bg-elevated backdrop-blur-md text-fg-muted hover:text-fg hover:bg-surface transition-colors cursor-pointer"
    >
      <span
        aria-hidden="true"
        className={`pointer-events-none absolute inset-0 rounded-lg transition-opacity duration-500 ${isDark ? 'opacity-100' : 'opacity-0'}`}
        style={{ background: 'radial-gradient(circle at center, rgba(129,140,248,0.18), transparent 65%)' }}
      />
      <span
        aria-hidden="true"
        className={`pointer-events-none absolute inset-0 rounded-lg transition-opacity duration-500 ${isDark ? 'opacity-0' : 'opacity-100'}`}
        style={{ background: 'radial-gradient(circle at center, rgba(251,191,36,0.18), transparent 65%)' }}
      />
      <AnimatePresence mode="wait" initial={false}>
        <motion.span
          key={isDark ? 'sun' : 'moon'}
          initial={{ rotate: -120, scale: 0.4, opacity: 0 }}
          animate={{ rotate: 0, scale: 1, opacity: 1 }}
          exit={{ rotate: 120, scale: 0.4, opacity: 0 }}
          transition={{ duration: 0.28, ease: [0.4, 0, 0.2, 1] }}
          className="relative inline-flex"
        >
          {isDark ? <Sun size={15} /> : <Moon size={15} />}
        </motion.span>
      </AnimatePresence>
    </motion.button>
  );
}

function Section({ icon: Icon, title, children }) {
  return (
    <div className="space-y-2.5">
      <div className="flex items-center gap-2 text-xs font-semibold text-fg-muted uppercase tracking-wide">
        <Icon className="h-3.5 w-3.5" />
        {title}
      </div>
      {children}
    </div>
  );
}

export default function SettingsSidebar({ isOpen, onClose }) {
  const { t } = useTranslation();
  const { preferences } = useUserPreferences();
  const updatePreferences = useUpdateUserPreferences();
  const { themePreference, setThemePreference } = useTheme();
  const { logout } = useAuth();
  const displayCurrency = useAppStore((s) => s.displayCurrency);
  const setDisplayCurrency = useAppStore((s) => s.setDisplayCurrency);

  const chartRangeOptions = CHART_RANGE_VALUES.map((v) => ({ value: v, label: t(`ranges.${v}`) }));

  const handleChange = (field) => (value) => {
    if (field === 'theme') {
      setThemePreference(value);
      return;
    }
    updatePreferences.mutate({ [field]: value });
  };

  const handleLogout = () => {
    onClose();
    logout();
  };

  const footer = (
    <div className="px-5 py-3">
      <button
        onClick={handleLogout}
        className="w-full flex items-center justify-center gap-2 rounded-lg py-2.5 text-xs font-semibold text-danger border border-danger/30 bg-danger/5 hover:bg-danger/10 transition-all cursor-pointer"
      >
        <LogOut className="h-3.5 w-3.5" />
        {t('settings.logout')}
      </button>
    </div>
  );

  return createPortal(
    <SideDrawer
      open={isOpen}
      onClose={onClose}
      width="min(380px, 100vw)"
      icon={SettingsIcon}
      iconTint="text-accent"
      title={t('settings.title')}
      subtitle={t('settings.subtitle')}
      footer={footer}
      closeAttr="settings"
    >
            <div className="px-4 sm:px-5 py-5 space-y-6" data-tour="settings-main">
              <div className="grid grid-cols-1 sm:grid-cols-[1fr_auto] gap-4 items-start">
                <Section icon={Palette} title={t('settings.theme')}>
                  <ThemeMorphToggle
                    value={themePreference}
                    onChange={handleChange('theme')}
                    t={t}
                  />
                </Section>
                <Section icon={Languages} title={t('settings.language.title')}>
                  <SegmentedControl
                    options={LANGUAGE_OPTIONS}
                    value={preferences.language}
                    onChange={handleChange('language')}
                    layoutId="settings-language"
                    compact
                  />
                </Section>
              </div>

              <Section icon={BarChart3} title={t('settings.chartRange')}>
                <SegmentedControl
                  options={chartRangeOptions}
                  value={preferences.defaultChartRange}
                  onChange={handleChange('defaultChartRange')}
                  layoutId="settings-chart-range"
                />
              </Section>

              <Section icon={Coins} title={t('settings.displayCurrency')}>
                <SegmentedControl
                  options={CURRENCY_OPTIONS}
                  value={displayCurrency}
                  onChange={setDisplayCurrency}
                  layoutId="settings-currency"
                />
              </Section>

              <Section icon={Bell} title={t('settings.notifications')}>
                <NotificationPreferencesSection />
              </Section>

              <div className="rounded-lg border border-border-default bg-bg-elevated px-3 py-2.5 text-[11px] text-fg-muted">
                <span className="font-mono text-fg">{preferences.timezone}</span> {t('settings.timezoneSuffix')}
              </div>
            </div>

    </SideDrawer>,
    document.body
  );
}
