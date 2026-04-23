import { changeColors, changeBg, getChangeClass, formatPercentAbs } from '../utils/formatters';

export default function ChangePercentBadge({
  value,
  positiveIcon,
  negativeIcon,
  children,
  size = 'md',
  className = '',
}) {
  if (value === null || value === undefined) return null;
  const cls = getChangeClass(value);
  const pad = size === 'sm' ? 'px-2 py-0.5 text-xs' : 'px-2.5 py-1 text-xs';
  const icon = value > 0 ? positiveIcon : value < 0 ? negativeIcon : null;
  return (
    <div className={`inline-flex items-center gap-1 rounded-md ${pad} font-medium ${changeBg[cls]} ${changeColors[cls]} ${className}`}>
      {icon}
      {formatPercentAbs(value)}
      {children}
    </div>
  );
}
