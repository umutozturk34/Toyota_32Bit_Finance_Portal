import { AlertCircle, AlertTriangle, CheckCircle, Info } from 'lucide-react';

const cx = (...parts) => parts.filter(Boolean).join(' ');

const TONES = {
  danger: { wrap: 'bg-danger/5 border-danger/20 text-danger', Icon: AlertCircle },
  warning: { wrap: 'bg-warning/5 border-warning/20 text-warning', Icon: AlertTriangle },
  success: { wrap: 'bg-success/5 border-success/20 text-success', Icon: CheckCircle },
  info: { wrap: 'bg-accent/5 border-accent/20 text-accent', Icon: Info },
};

export default function InlineAlert({ tone = 'danger', icon: IconOverride, message, action, className }) {
  const config = TONES[tone] ?? TONES.danger;
  const Icon = IconOverride ?? config.Icon;
  return (
    <div
      role={tone === 'danger' || tone === 'warning' ? 'alert' : 'status'}
      className={cx(
        'rounded-lg border px-3 py-2 flex items-center gap-2 text-xs',
        config.wrap,
        className,
      )}
    >
      <Icon className="h-3.5 w-3.5 shrink-0" />
      <span className="flex-1 min-w-0">{message}</span>
      {action && (
        <button
          type="button"
          onClick={action.onClick}
          className="font-semibold underline-offset-2 hover:underline transition-all bg-transparent border-none cursor-pointer text-current"
        >
          {action.label}
        </button>
      )}
    </div>
  );
}
