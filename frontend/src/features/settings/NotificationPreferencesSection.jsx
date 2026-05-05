import { motion } from 'framer-motion';
import { Mail, MessageSquare, Bell, AlertCircle, Zap, FileText, Smartphone } from 'lucide-react';
import {
  useNotificationPreferences,
  useUpdateNotificationPreferences,
} from '../../shared/hooks/useNotificationPreferences';

const TYPE_ROWS = [
  { id: 'priceAlerts', Icon: AlertCircle, label: 'Fiyat alarmı', hint: 'Eşik kırıldığında' },
  { id: 'watchlist', Icon: Zap, label: 'Takip listesi', hint: 'Sert hareket olduğunda' },
  { id: 'reports', Icon: FileText, label: 'Raporlar', hint: 'PDF rapor hazır olduğunda' },
  { id: 'messages', Icon: MessageSquare, label: 'Mesajlar', hint: 'Sistem mesajları' },
  { id: 'system', Icon: Bell, label: 'Sistem', hint: 'Bakım, güvenlik vb.' },
];

function ToggleSwitch({ on, onChange, srLabel, size = 'md' }) {
  const sizes = {
    sm: { track: 'h-4 w-7', thumb: 'h-3 w-3', off: 'ml-[2px]', on: 'ml-[14px]' },
    md: { track: 'h-5 w-9', thumb: 'h-3.5 w-3.5', off: 'ml-[3px]', on: 'ml-[18px]' },
  };
  const s = sizes[size];
  return (
    <button
      type="button"
      role="switch"
      aria-checked={on}
      aria-label={srLabel}
      onClick={() => onChange(!on)}
      className={`relative inline-flex ${s.track} shrink-0 items-center rounded-full transition-colors border-none cursor-pointer ${
        on ? 'bg-accent shadow-[0_0_12px_-2px_var(--color-accent)]/30' : 'bg-surface'
      }`}
    >
      <motion.span
        layout
        transition={{ type: 'spring', stiffness: 500, damping: 30 }}
        className={`inline-block ${s.thumb} rounded-full ${on ? 'bg-white' : 'bg-fg-muted'} shadow-sm ${on ? s.on : s.off}`}
      />
    </button>
  );
}

function ChannelDot({ active, disabled = false, Icon, onClick, title }) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      title={title}
      className={`flex h-8 w-8 items-center justify-center rounded-lg border transition-all cursor-pointer ${
        disabled
          ? 'border-border-default/30 bg-transparent text-fg-subtle/50 cursor-not-allowed'
          : active
          ? 'border-accent/60 bg-accent/15 text-accent shadow-[0_0_12px_-3px_var(--color-accent)]/30'
          : 'border-border-default bg-bg-elevated text-fg-muted hover:border-accent/40 hover:text-accent hover:bg-accent/5'
      }`}
    >
      <Icon className="h-3.5 w-3.5" />
    </button>
  );
}

function fieldName(typeId, channel) {
  const cap = typeId.charAt(0).toUpperCase() + typeId.slice(1);
  return `${channel}${cap}`;
}

export default function NotificationPreferencesSection() {
  const { preferences } = useNotificationPreferences();
  const update = useUpdateNotificationPreferences();

  const setField = (field, value) => update.mutate({ [field]: value });

  return (
    <div className="space-y-4">
      <div className="rounded-xl border border-accent/25 bg-gradient-to-br from-accent/8 to-accent-secondary/4 px-4 py-3.5">
        <div className="flex items-start justify-between gap-3">
          <div className="flex items-start gap-3 min-w-0">
            <div className="flex items-center justify-center w-9 h-9 rounded-lg bg-accent/15 shrink-0">
              <Mail className="h-4 w-4 text-accent" />
            </div>
            <div className="min-w-0">
              <h3 className="text-sm font-semibold text-fg leading-tight">E-posta ana anahtarı</h3>
              <p className="text-[11px] text-fg-muted mt-0.5 leading-relaxed">
                Kapalıyken altındaki e-posta seçimleri görmezden gelinir.
              </p>
            </div>
          </div>
          <ToggleSwitch
            on={preferences.emailEnabled}
            onChange={(v) => setField('emailEnabled', v)}
            srLabel="E-posta bildirimlerini aç/kapat"
          />
        </div>
      </div>

      <div className="rounded-xl border border-border-default bg-bg-elevated overflow-hidden">
        <div className="grid grid-cols-[1fr_2rem_2rem] gap-4 px-4 py-2.5 border-b border-border-default items-center">
          <h3 className="text-[11px] font-semibold uppercase tracking-wider text-fg-muted">Bildirim türleri</h3>
          <div className="flex items-center justify-center" title="E-posta">
            <Mail className="h-3.5 w-3.5 text-fg-subtle" />
          </div>
          <div className="flex items-center justify-center" title="Uygulama içi">
            <Smartphone className="h-3.5 w-3.5 text-fg-subtle" />
          </div>
        </div>
        <div className="divide-y divide-border-default">
          {TYPE_ROWS.map(({ id, Icon, label, hint }) => {
            const emailField = fieldName(id, 'email');
            const inappField = fieldName(id, 'inapp');
            const emailActive = !!preferences[emailField];
            const inappActive = !!preferences[inappField];
            const masterOff = !preferences.emailEnabled;
            return (
              <div key={id} className="grid grid-cols-[1fr_2rem_2rem] gap-4 px-4 py-3 items-center group hover:bg-accent/3 transition-colors">
                <div className="flex items-center gap-2.5 min-w-0">
                  <div className="flex items-center justify-center w-7 h-7 rounded-md bg-surface group-hover:bg-accent/10 transition-colors shrink-0">
                    <Icon className="h-3.5 w-3.5 text-fg-muted group-hover:text-accent transition-colors" />
                  </div>
                  <div className="min-w-0">
                    <div className="text-xs font-semibold text-fg">{label}</div>
                    <div className="text-[10px] text-fg-subtle truncate">{hint}</div>
                  </div>
                </div>
                <ChannelDot
                  active={emailActive && !masterOff}
                  disabled={masterOff}
                  Icon={Mail}
                  title={masterOff ? 'Ana e-posta anahtarı kapalı' : emailActive ? 'E-posta açık' : 'E-posta kapalı'}
                  onClick={() => setField(emailField, !emailActive)}
                />
                <ChannelDot
                  active={inappActive}
                  Icon={Smartphone}
                  title={inappActive ? 'Uygulama içi açık' : 'Uygulama içi kapalı'}
                  onClick={() => setField(inappField, !inappActive)}
                />
              </div>
            );
          })}
        </div>
        <div className="px-4 py-2.5 border-t border-border-default bg-bg-base/40">
          <p className="text-[10px] text-fg-subtle leading-relaxed">
            Uygulama içi bildirim her zaman çalışır. E-posta için ana anahtar açık olmalı.
          </p>
        </div>
      </div>
    </div>
  );
}
