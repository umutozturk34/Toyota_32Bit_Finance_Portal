import { useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import {
  X, Settings as SettingsIcon, Palette, Languages, BarChart3, Bell, Shield,
  Sun, Moon, LogOut, KeyRound, Mail,
} from 'lucide-react';
import { useUserPreferences, useUpdateUserPreferences } from '../../shared/hooks/useUserPreferences';
import { useAuth } from '../auth/AuthContext';
import { userCredentialService } from '../../shared/services/userCredentialService';
import { toast } from '../../shared/components/Toast';
import TwoFactorPanel from '../auth/TwoFactorPanel';
import NotificationPreferencesSection from './NotificationPreferencesSection';

const THEME_OPTIONS = [
  { value: 'DARK', Icon: Moon, label: 'Koyu' },
  { value: 'LIGHT', Icon: Sun, label: 'Açık' },
];

const LANGUAGE_OPTIONS = [
  { value: 'tr', label: 'TR' },
  { value: 'en', label: 'EN' },
];

const CHART_RANGE_OPTIONS = [
  { value: '1M', label: '1A' },
  { value: '3M', label: '3A' },
  { value: '6M', label: '6A' },
  { value: '1Y', label: '1Y' },
  { value: '5Y', label: '5Y' },
  { value: 'ALL', label: 'TÜM' },
];

const REPORT_OPTIONS = [
  { value: 'NEVER', label: 'Hiç' },
  { value: 'DAILY', label: 'Günlük' },
  { value: 'WEEKLY', label: 'Haftalık' },
  { value: 'MONTHLY', label: 'Aylık' },
];

function SegmentedControl({ options, value, onChange, layoutId, compact = false }) {
  const padding = compact ? 'px-2 py-1' : 'px-2.5 py-1.5';
  return (
    <div className="flex gap-0.5 rounded-lg border border-border-default bg-bg-elevated p-0.5 overflow-hidden">
      {options.map((opt) => {
        const Icon = opt.Icon;
        const active = value === opt.value;
        return (
          <button
            key={opt.value}
            type="button"
            onClick={() => onChange(opt.value)}
            title={opt.label}
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
              {Icon ? <Icon className="h-3.5 w-3.5" /> : opt.label}
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
  const { preferences } = useUserPreferences();
  const updatePreferences = useUpdateUserPreferences();
  const { logout } = useAuth();
  const [passwordSending, setPasswordSending] = useState(false);
  const [emailDraft, setEmailDraft] = useState('');
  const [emailSending, setEmailSending] = useState(false);

  const handleChange = (field) => (value) => {
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
      toast.success('E-posta gönderildi', 'Gelen kutunuzdaki linke tıklayarak şifrenizi değiştirin');
      onClose();
    } catch (err) {
      toast.error('İşlem başarısız', err?.response?.data?.message || 'E-posta gönderilemedi');
    } finally {
      setPasswordSending(false);
    }
  };

  const handleChangeEmail = async (event) => {
    event.preventDefault();
    const trimmed = emailDraft.trim();
    if (!trimmed) return;
    setEmailSending(true);
    try {
      await userCredentialService.initiateEmailChange(trimmed, `${window.location.origin}/`);
      toast.success('Doğrulama kodu gönderildi', `${trimmed} adresine kodu içeren e-posta atıldı`);
      setEmailDraft('');
      onClose();
    } catch (err) {
      toast.error('İşlem başarısız', err?.response?.data?.message || 'E-posta değiştirilemedi');
    } finally {
      setEmailSending(false);
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
              <div className="grid grid-cols-[1fr_auto] gap-4 items-start">
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
                    compact
                  />
                </Section>
              </div>

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

              <Section icon={Bell} title="Bildirim Tercihleri">
                <NotificationPreferencesSection />
              </Section>

              <Section icon={Shield} title="İki Adımlı Doğrulama">
                <TwoFactorPanel />
              </Section>

              <Section icon={KeyRound} title="Şifre">
                <button
                  onClick={handleChangePassword}
                  disabled={passwordSending}
                  className="w-full flex items-center justify-between gap-2 rounded-lg border border-border-default bg-bg-elevated px-3 py-2.5 text-xs font-medium text-fg hover:bg-surface transition-colors cursor-pointer disabled:opacity-50"
                >
                  <span className="flex items-center gap-2">
                    <KeyRound className="h-3.5 w-3.5 text-accent" />
                    Şifre Değiştir
                  </span>
                  <span className="text-[10px] text-fg-muted">{passwordSending ? '…' : 'E-posta →'}</span>
                </button>
                <p className="text-[10px] text-fg-subtle leading-relaxed px-1 mt-1.5">
                  E-posta adresine gelen linke tıklayarak yeni şifre belirleyebilirsin.
                </p>
              </Section>

              <Section icon={Mail} title="E-posta">
                <form onSubmit={handleChangeEmail} className="space-y-2">
                  <div className="relative group/input">
                    <span className="pointer-events-none absolute inset-y-0 left-3 flex items-center text-fg-subtle group-focus-within/input:text-accent transition-colors">
                      <Mail className="h-3.5 w-3.5" />
                    </span>
                    <input
                      type="email"
                      required
                      value={emailDraft}
                      onChange={(e) => setEmailDraft(e.target.value)}
                      placeholder="yeni@email.com"
                      disabled={emailSending}
                      className="w-full rounded-lg border border-border-default bg-bg-elevated/80 backdrop-blur-sm pl-9 pr-3 py-2 text-xs text-fg placeholder:text-fg-subtle focus:outline-none focus:border-accent/60 focus:bg-bg-elevated focus:shadow-[0_0_0_3px_rgba(99,102,241,0.12),inset_0_1px_0_rgba(255,255,255,0.04)] transition-all disabled:opacity-50"
                    />
                  </div>
                  <motion.button
                    type="submit"
                    disabled={emailSending || !emailDraft.trim()}
                    whileTap={{ scale: 0.98 }}
                    className="relative w-full flex items-center justify-between gap-2 rounded-lg overflow-hidden px-3 py-2.5 text-xs font-semibold text-white cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed group/cta"
                  >
                    <span aria-hidden className="absolute inset-0 bg-gradient-to-r from-accent via-accent-bright to-accent transition-opacity group-hover/cta:opacity-90 group-disabled/cta:from-bg-elevated group-disabled/cta:via-bg-elevated group-disabled/cta:to-bg-elevated" />
                    <span aria-hidden className="absolute inset-0 opacity-0 group-hover/cta:opacity-100 transition-opacity bg-[radial-gradient(120%_120%_at_50%_-20%,rgba(255,255,255,0.18),transparent_60%)]" />
                    <span aria-hidden className="absolute inset-x-0 top-0 h-px bg-white/20" />
                    <span className="relative flex items-center gap-2">
                      <Mail className="h-3.5 w-3.5" />
                      Doğrulama kodu iste
                    </span>
                    <span className="relative flex items-center gap-1 text-[10px] tracking-wide opacity-90">
                      {emailSending ? (
                        <span className="inline-flex items-center gap-1"><span className="h-1 w-1 rounded-full bg-white animate-pulse" />gönderiliyor</span>
                      ) : (
                        <span>Kod →</span>
                      )}
                    </span>
                  </motion.button>
                </form>
                <p className="text-[10px] text-fg-subtle leading-relaxed px-1 mt-2">
                  Yeni adresine <span className="font-mono text-fg-muted">6 haneli kod</span> gönderilir; Keycloak doğrulama sayfasında kodu girince e-posta güncellenir.
                </p>
              </Section>

              <div className="rounded-lg border border-border-default bg-bg-elevated px-3 py-2.5 text-[11px] text-fg-muted">
                <span className="font-mono text-fg">{preferences.timezone}</span> saat dilimi (sabit)
              </div>
            </div>

            <div className="border-t border-border-default px-5 py-3">
              <button
                onClick={handleLogout}
                className="w-full flex items-center justify-center gap-2 rounded-lg py-2.5 text-xs font-semibold text-danger border border-danger/30 bg-danger/5 hover:bg-danger/10 transition-all cursor-pointer"
              >
                <LogOut className="h-3.5 w-3.5" />
                Çıkış Yap
              </button>
            </div>
          </motion.aside>
        </>
      )}
    </AnimatePresence>
  );
}
