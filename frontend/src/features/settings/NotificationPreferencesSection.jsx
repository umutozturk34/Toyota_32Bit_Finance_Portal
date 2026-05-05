import { motion } from 'framer-motion';
import { Mail, MessageSquare, Bell, AlertCircle, Zap, FileText, Smartphone, Moon } from 'lucide-react';
import {
  useNotificationPreferences,
  useUpdateNotificationPreferences,
} from '../../shared/hooks/useNotificationPreferences';

const TYPE_ROWS = [
  { id: 'priceAlerts', Icon: AlertCircle, label: 'Fiyat alarmı' },
  { id: 'watchlist', Icon: Zap, label: 'Takip listesi' },
  { id: 'reports', Icon: FileText, label: 'Raporlar' },
  { id: 'messages', Icon: MessageSquare, label: 'Mesajlar' },
  { id: 'system', Icon: Bell, label: 'Sistem' },
];

function ToggleSwitch({ on, onChange, srLabel }) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={on}
      aria-label={srLabel}
      onClick={() => onChange(!on)}
      className={`relative inline-flex h-5 w-9 shrink-0 items-center rounded-full transition-colors border-none cursor-pointer ${
        on ? 'bg-accent' : 'bg-surface'
      }`}
    >
      <motion.span
        layout
        transition={{ type: 'spring', stiffness: 500, damping: 30 }}
        className={`inline-block h-3.5 w-3.5 rounded-full bg-fg shadow-sm ${on ? 'ml-[18px]' : 'ml-[3px]'}`}
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
      className={`flex h-7 w-7 items-center justify-center rounded-md border transition-all cursor-pointer ${
        disabled
          ? 'border-border-default/40 bg-transparent text-fg-subtle cursor-not-allowed'
          : active
          ? 'border-accent/60 bg-accent/15 text-accent'
          : 'border-border-default bg-bg-elevated text-fg-muted hover:border-border-hover'
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
  const setTime = (field, value) => update.mutate({ [field]: value || null });

  return (
    <div className="space-y-3.5">
      <div className="flex items-center justify-between rounded-lg border border-accent/20 bg-accent/5 px-3 py-2.5">
        <div className="flex items-center gap-2">
          <Mail className="h-3.5 w-3.5 text-accent" />
          <span className="text-xs font-semibold text-fg">E-posta bildirimleri</span>
        </div>
        <ToggleSwitch
          on={preferences.emailEnabled}
          onChange={(v) => setField('emailEnabled', v)}
          srLabel="E-posta bildirimlerini aç/kapat"
        />
      </div>

      <div className="rounded-lg border border-border-default bg-bg-elevated overflow-hidden">
        <div className="grid grid-cols-[1fr_auto_auto] gap-3 px-3 py-2 border-b border-border-default text-[11px] font-semibold uppercase tracking-wide text-fg-muted">
          <span>Tür</span>
          <span className="w-7 text-center">E-posta</span>
          <span className="w-7 text-center">Uyg.</span>
        </div>
        <div className="divide-y divide-border-default">
          {TYPE_ROWS.map(({ id, Icon, label }) => {
            const emailField = fieldName(id, 'email');
            const inappField = fieldName(id, 'inapp');
            const emailActive = !!preferences[emailField];
            const inappActive = !!preferences[inappField];
            const masterOff = !preferences.emailEnabled;
            return (
              <div key={id} className="grid grid-cols-[1fr_auto_auto] gap-3 px-3 py-2.5 items-center">
                <div className="flex items-center gap-2 min-w-0">
                  <Icon className="h-3.5 w-3.5 text-fg-muted shrink-0" />
                  <span className="text-xs text-fg truncate">{label}</span>
                </div>
                <ChannelDot
                  active={emailActive && !masterOff}
                  disabled={masterOff}
                  Icon={Mail}
                  title={masterOff ? 'Master e-posta kapalı' : emailActive ? 'E-posta açık' : 'E-posta kapalı'}
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
      </div>

      <div className="rounded-lg border border-border-default bg-bg-elevated px-3 py-2.5 space-y-2">
        <div className="flex items-center gap-2">
          <Moon className="h-3.5 w-3.5 text-fg-muted" />
          <span className="text-[11px] font-semibold uppercase tracking-wide text-fg-muted">Sessiz saatler</span>
        </div>
        <div className="flex items-center gap-2">
          <input
            type="time"
            value={preferences.quietHoursStart ?? ''}
            onChange={(e) => setTime('quietHoursStart', e.target.value)}
            className="flex-1 rounded-md border border-border-default bg-bg-deep px-2 py-1.5 text-[11px] font-mono text-fg outline-none focus:border-accent transition-colors"
          />
          <span className="text-[10px] text-fg-subtle font-mono">→</span>
          <input
            type="time"
            value={preferences.quietHoursEnd ?? ''}
            onChange={(e) => setTime('quietHoursEnd', e.target.value)}
            className="flex-1 rounded-md border border-border-default bg-bg-deep px-2 py-1.5 text-[11px] font-mono text-fg outline-none focus:border-accent transition-colors"
          />
        </div>
        <p className="text-[10px] text-fg-subtle leading-relaxed">
          Bu saat aralığında e-posta bildirimleri ertelenmez, gönderilmez. Uygulama içi bildirimler her zaman görünür.
        </p>
      </div>
    </div>
  );
}
