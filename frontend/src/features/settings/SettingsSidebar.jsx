import { motion, AnimatePresence } from 'framer-motion';
import { X, Settings as SettingsIcon, Palette, Languages, BarChart3, Bell, Shield } from 'lucide-react';
import { useUserPreferences, useUpdateUserPreferences } from '../../shared/hooks/useUserPreferences';
import TwoFactorPanel from '../auth/TwoFactorPanel';

const THEME_OPTIONS = [
  { value: 'LIGHT', label: 'Açık' },
  { value: 'DARK', label: 'Koyu' },
  { value: 'SYSTEM', label: 'Sistem' },
];

const LANGUAGE_OPTIONS = [
  { value: 'tr', label: 'Türkçe' },
  { value: 'en', label: 'English' },
];

const CHART_RANGE_OPTIONS = [
  { value: '1M', label: '1A' },
  { value: '3M', label: '3A' },
  { value: '6M', label: '6A' },
  { value: '1Y', label: '1Y' },
  { value: 'ALL', label: 'TÜM' },
];

const REPORT_OPTIONS = [
  { value: 'NEVER', label: 'Hiçbir Zaman' },
  { value: 'DAILY', label: 'Günlük' },
  { value: 'WEEKLY', label: 'Haftalık' },
  { value: 'MONTHLY', label: 'Aylık' },
];

function SegmentedControl({ options, value, onChange, layoutId }) {
  return (
    <div className="flex gap-0.5 rounded-lg border border-border-default bg-bg-elevated p-0.5 overflow-hidden">
      {options.map((opt) => (
        <button
          key={opt.value}
          type="button"
          onClick={() => onChange(opt.value)}
          className="relative flex-1 rounded-md px-2.5 py-1.5 text-[11px] font-medium transition-all border-none cursor-pointer bg-transparent"
        >
          {value === opt.value && (
            <motion.span
              layoutId={layoutId}
              className="absolute inset-0 rounded-md bg-accent/15"
              transition={{ type: 'spring', stiffness: 300, damping: 30 }}
            />
          )}
          <span className={`relative z-10 ${value === opt.value ? 'text-accent' : 'text-fg-muted hover:text-fg'}`}>
            {opt.label}
          </span>
        </button>
      ))}
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
  const { preferences } = useUserPreferences();
  const updatePreferences = useUpdateUserPreferences();

  const handleChange = (field) => (value) => {
    updatePreferences.mutate({ [field]: value });
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
            transition={{ type: 'spring', stiffness: 280, damping: 30 }}
            className="fixed top-0 right-0 z-50 h-full w-full sm:w-[380px] modal-panel border-l border-border-default flex flex-col"
          >
            <div className="absolute top-0 left-0 right-0 h-[1px] bg-gradient-to-r from-transparent via-accent/40 to-transparent" />

            <div className="flex items-center justify-between px-5 py-4 border-b border-border-default">
              <div className="flex items-center gap-3">
                <div className="flex items-center justify-center w-9 h-9 rounded-lg bg-accent/10">
                  <SettingsIcon className="h-4 w-4 text-accent" />
                </div>
                <div>
                  <h2 className="text-base font-semibold text-fg">Ayarlar</h2>
                  <p className="text-xs text-fg-muted">Tercihler anlık olarak kaydedilir</p>
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
              <Section icon={Palette} title="Tema">
                <SegmentedControl
                  options={THEME_OPTIONS}
                  value={preferences.theme}
                  onChange={handleChange('theme')}
                  layoutId="settings-theme"
                />
              </Section>

              <Section icon={Languages} title="Dil">
                <SegmentedControl
                  options={LANGUAGE_OPTIONS}
                  value={preferences.language}
                  onChange={handleChange('language')}
                  layoutId="settings-language"
                />
              </Section>

              <Section icon={BarChart3} title="Varsayılan Grafik Aralığı">
                <SegmentedControl
                  options={CHART_RANGE_OPTIONS}
                  value={preferences.defaultChartRange}
                  onChange={handleChange('defaultChartRange')}
                  layoutId="settings-chart-range"
                />
              </Section>

              <Section icon={Bell} title="Rapor Sıklığı">
                <SegmentedControl
                  options={REPORT_OPTIONS}
                  value={preferences.reportFrequency}
                  onChange={handleChange('reportFrequency')}
                  layoutId="settings-report"
                />
              </Section>

              <Section icon={Shield} title="İki Adımlı Doğrulama">
                <TwoFactorPanel />
              </Section>

              <div className="rounded-lg border border-border-default bg-bg-elevated px-3 py-2.5 text-[11px] text-fg-muted">
                <span className="font-mono text-fg">{preferences.timezone}</span> saat dilimi (sabit)
              </div>
            </div>
          </motion.aside>
        </>
      )}
    </AnimatePresence>
  );
}
