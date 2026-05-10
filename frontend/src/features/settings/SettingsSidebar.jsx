import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { motion } from 'framer-motion';
import { AnimatePresence } from 'framer-motion';
import {
  X, Settings as SettingsIcon, Palette, Languages, BarChart3, Bell, Shield,
  Sun, Moon, LogOut, KeyRound, Mail,
} from 'lucide-react';
import { useUserPreferences, useUpdateUserPreferences } from '../../shared/hooks/useUserPreferences';
import { useTheme } from '../../shared/context/ThemeContext';
import { useAuth } from '../auth/AuthContext';
import { userCredentialService } from '../../shared/services/userCredentialService';
import { toast } from '../../shared/components/feedback/Toast';
import TwoFactorPanel from '../auth/components/TwoFactorPanel';
import NotificationPreferencesSection from './NotificationPreferencesSection';
import EmailChangeSection from './EmailChangeSection';

const THEME_OPTIONS = [
  { value: 'DARK', Icon: Moon, labelKey: 'theme.DARK' },
  { value: 'LIGHT', Icon: Sun, labelKey: 'theme.LIGHT' },
];

const LANGUAGE_OPTIONS = [
  { value: 'tr', labelKey: 'settings.language.tr' },
  { value: 'en', labelKey: 'settings.language.en' },
];

const CHART_RANGE_VALUES = ['1M', '3M', '6M', '1Y', '5Y', 'ALL'];
const REPORT_VALUES = ['NEVER', 'DAILY', 'WEEKLY', 'MONTHLY'];

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
  const [passwordSending, setPasswordSending] = useState(false);

  const chartRangeOptions = CHART_RANGE_VALUES.map((v) => ({ value: v, label: t(`ranges.${v}`) }));
  const reportOptions = REPORT_VALUES.map((v) => ({ value: v, label: t(`reports.${v}`) }));

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

  const handleChangePassword = async () => {
    setPasswordSending(true);
    try {
      await userCredentialService.initiatePasswordChange(`${window.location.origin}/`);
      toast.success(t('settings.password.success'), t('settings.password.successDesc'));
      onClose();
    } catch (err) {
      toast.error(t('settings.password.error'), err?.response?.data?.message || t('settings.password.errorDesc'));
    } finally {
      setPasswordSending(false);
    }
  };

  return (
    <AnimatePresence>
      {isOpen && (
        <>
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.2 }}
            onClick={onClose}
            className="fixed inset-0 z-40 modal-overlay backdrop-blur-sm"
          />
          <motion.aside
            initial={{ x: '100%' }}
            animate={{ x: 0 }}
            exit={{ x: '100%' }}
            transition={{ duration: 0.32, ease: [0.32, 0.72, 0, 1] }}
            className="fixed top-0 right-0 z-50 h-full w-full sm:w-[380px] modal-panel border-l border-border-default flex flex-col"
          >
            <div className="absolute top-0 left-0 right-0 h-[1px] bg-gradient-to-r from-transparent via-accent/40 to-transparent" />

            <div className="flex items-center justify-between px-5 py-4 border-b border-border-default">
              <div className="flex items-center gap-3">
                <div className="flex items-center justify-center w-9 h-9 rounded-lg bg-accent/10">
                  <SettingsIcon className="h-4 w-4 text-accent" />
                </div>
                <div>
                  <h2 className="text-base font-semibold text-fg">{t('settings.title')}</h2>
                  <p className="text-xs text-fg-muted">{t('settings.subtitle')}</p>
                </div>
              </div>
              <button
                onClick={onClose}
                className="flex items-center justify-center w-8 h-8 rounded-lg text-fg-muted hover:text-fg hover:bg-surface transition-colors bg-transparent border-none cursor-pointer"
              >
                <X className="h-4 w-4" />
              </button>
            </div>

            <div className="flex-1 overflow-y-auto px-5 py-5 space-y-6">
              <div className="grid grid-cols-[1fr_auto] gap-4 items-start">
                <Section icon={Palette} title={t('settings.theme')}>
                  <SegmentedControl
                    options={THEME_OPTIONS}
                    value={themePreference}
                    onChange={handleChange('theme')}
                    layoutId="settings-theme"
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

              <Section icon={Bell} title={t('settings.reportFrequency')}>
                <SegmentedControl
                  options={reportOptions}
                  value={preferences.reportFrequency}
                  onChange={handleChange('reportFrequency')}
                  layoutId="settings-report"
                />
              </Section>

              <Section icon={Bell} title={t('settings.notifications')}>
                <NotificationPreferencesSection />
              </Section>

              <Section icon={Shield} title={t('settings.twoFactor')}>
                <TwoFactorPanel />
              </Section>

              <Section icon={KeyRound} title={t('settings.password.title')}>
                <button
                  onClick={handleChangePassword}
                  disabled={passwordSending}
                  className="w-full flex items-center justify-between gap-2 rounded-lg border border-border-default bg-bg-elevated px-3 py-2.5 text-xs font-medium text-fg hover:bg-surface transition-colors cursor-pointer disabled:opacity-50"
                >
                  <span className="flex items-center gap-2">
                    <KeyRound className="h-3.5 w-3.5 text-accent" />
                    {t('settings.password.change')}
                  </span>
                  <span className="text-[10px] text-fg-muted">{passwordSending ? t('settings.password.sending') : t('settings.password.emailLink')}</span>
                </button>
                <p className="text-[10px] text-fg-subtle leading-relaxed px-1 mt-1.5">
                  {t('settings.password.hint')}
                </p>
              </Section>

              <Section icon={Mail} title={t('settings.email')}>
                <EmailChangeSection />
              </Section>

              <div className="rounded-lg border border-border-default bg-bg-elevated px-3 py-2.5 text-[11px] text-fg-muted">
                <span className="font-mono text-fg">{preferences.timezone}</span> {t('settings.timezoneSuffix')}
              </div>
            </div>

            <div className="border-t border-border-default px-5 py-3">
              <button
                onClick={handleLogout}
                className="w-full flex items-center justify-center gap-2 rounded-lg py-2.5 text-xs font-semibold text-danger border border-danger/30 bg-danger/5 hover:bg-danger/10 transition-all cursor-pointer"
              >
                <LogOut className="h-3.5 w-3.5" />
                {t('settings.logout')}
              </button>
            </div>
          </motion.aside>
        </>
      )}
    </AnimatePresence>
  );
}
