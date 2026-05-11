import { Mail, Bell, AlertCircle, Zap, Smartphone, Sunrise, Sunset, RefreshCw, Newspaper, Briefcase } from 'lucide-react';
import { motion } from 'framer-motion';
import { useTranslation } from 'react-i18next';
import {
  useNotificationPreferences,
  useUpdateNotificationPreferences,
} from '../../shared/hooks/useNotificationPreferences';
import MarketSelectionChips, { MARKET_CHIPS } from './MarketSelectionChips';
import Card from '../../shared/components/card';

const TYPE_ROWS = [
  { id: 'priceAlerts', Icon: AlertCircle },
  { id: 'watchlist', Icon: Zap },
  { id: 'system', Icon: Bell },
  { id: 'marketOpened', Icon: Sunrise },
  { id: 'marketClosed', Icon: Sunset },
  { id: 'marketDataUpdated', Icon: RefreshCw },
  { id: 'newsPublished', Icon: Newspaper },
  { id: 'portfolioUpdated', Icon: Briefcase },
];

function parseMarkets(csv) {
  if (!csv) return new Set();
  return new Set(csv.split(',').map((s) => s.trim()).filter(Boolean));
}

function serializeMarkets(set) {
  return MARKET_CHIPS.filter((m) => set.has(m.id)).map((m) => m.id).join(',');
}

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
  const { t } = useTranslation();
  const { preferences } = useNotificationPreferences();
  const update = useUpdateNotificationPreferences();

  const setField = (field, value) => update.mutate({ [field]: value });

  return (
    <div className="space-y-4">
      <Card variant="gradient" tone="gradient" gradientFrom="accent" gradientTo="accent-secondary" radius="xl" padding="none" className="border-accent/25 px-4 py-3.5">
        <div className="flex items-start justify-between gap-3">
          <div className="flex items-start gap-3 min-w-0">
            <div className="flex items-center justify-center w-9 h-9 rounded-lg bg-accent/15 shrink-0">
              <Mail className="h-4 w-4 text-accent" />
            </div>
            <div className="min-w-0">
              <h3 className="text-sm font-semibold text-fg leading-tight">{t('notificationPreferences.emailMaster.title')}</h3>
              <p className="text-[11px] text-fg-muted mt-0.5 leading-relaxed">
                {t('notificationPreferences.emailMaster.hint')}
              </p>
            </div>
          </div>
          <ToggleSwitch
            on={preferences.emailEnabled}
            onChange={(v) => setField('emailEnabled', v)}
            srLabel={t('notificationPreferences.emailMaster.toggleAria')}
          />
        </div>
      </Card>

      <Card variant="elevated" radius="xl" padding="none" interactive={false}>
        <div className="grid grid-cols-[1fr_2rem_2rem] gap-4 px-4 py-2.5 border-b border-border-default items-center">
          <h3 className="text-[11px] font-semibold uppercase tracking-wider text-fg-muted">{t('notificationPreferences.types.title')}</h3>
          <div className="flex items-center justify-center" title={t('notificationPreferences.channels.email')}>
            <Mail className="h-3.5 w-3.5 text-fg-subtle" />
          </div>
          <div className="flex items-center justify-center" title={t('notificationPreferences.channels.inapp')}>
            <Smartphone className="h-3.5 w-3.5 text-fg-subtle" />
          </div>
        </div>
        <div className="divide-y divide-border-default">
          {TYPE_ROWS.map(({ id, Icon }) => {
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
                    <div className="text-xs font-semibold text-fg">{t(`notificationPreferences.types.${id}.label`)}</div>
                    <div className="text-[10px] text-fg-subtle truncate">{t(`notificationPreferences.types.${id}.hint`)}</div>
                  </div>
                </div>
                <ChannelDot
                  active={emailActive && !masterOff}
                  disabled={masterOff}
                  Icon={Mail}
                  title={masterOff ? t('notificationPreferences.channelTitle.emailMasterOff') : emailActive ? t('notificationPreferences.channelTitle.emailOn') : t('notificationPreferences.channelTitle.emailOff')}
                  onClick={() => setField(emailField, !emailActive)}
                />
                <ChannelDot
                  active={inappActive}
                  Icon={Smartphone}
                  title={inappActive ? t('notificationPreferences.channelTitle.inappOn') : t('notificationPreferences.channelTitle.inappOff')}
                  onClick={() => setField(inappField, !inappActive)}
                />
              </div>
            );
          })}
        </div>
        <div className="px-4 py-2.5 border-t border-border-default bg-bg-base/40">
          <p className="text-[10px] text-fg-subtle leading-relaxed">
            {t('notificationPreferences.footer')}
          </p>
        </div>
      </Card>

      <MarketSelectionChips
        selected={parseMarkets(preferences.marketSessionMarkets)}
        onToggle={(next) => setField('marketSessionMarkets', serializeMarkets(next))}
      />
    </div>
  );
}
