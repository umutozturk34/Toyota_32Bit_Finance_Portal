const cx = (...parts) => parts.filter(Boolean).join(' ');

export default function CardHeader({
  icon: Icon,
  iconTint = 'text-accent',
  title,
  subtitle,
  action,
  onClick,
  divider = true,
  className,
}) {
  const Component = onClick ? 'button' : 'div';
  const interactive = Boolean(onClick);

  return (
    <Component
      type={interactive ? 'button' : undefined}
      onClick={onClick}
      className={cx(
        'flex items-center gap-2 w-full p-3 shrink-0',
        divider && 'border-b border-border-default',
        interactive && 'cursor-pointer hover:bg-surface/30 transition-colors group/header bg-transparent border-x-0 border-t-0',
        !interactive && 'border-none bg-transparent',
        className,
      )}
    >
      {Icon && (
        <span className={cx('flex items-center justify-center w-7 h-7 rounded-lg bg-accent/10 shrink-0', iconTint)}>
          <Icon className="h-3.5 w-3.5" />
        </span>
      )}
      <div className="flex flex-col items-start min-w-0 flex-1">
        <span className="font-display text-[13px] font-bold text-fg truncate w-full text-left">{title}</span>
        {subtitle && (
          <span className="font-mono text-[10px] text-fg-subtle truncate w-full text-left">{subtitle}</span>
        )}
      </div>
      {action && <div className="ml-auto flex items-center gap-1.5 shrink-0">{action}</div>}
    </Component>
  );
}
