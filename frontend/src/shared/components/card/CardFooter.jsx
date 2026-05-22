const cx = (...parts) => parts.filter(Boolean).join(' ');

const PADDINGS = {
  none: '',
  sm: 'p-2',
  md: 'p-3',
  lg: 'p-4',
};

export default function CardFooter({ divider = false, padding = 'md', className, children }) {
  return (
    <div
      className={cx(
        'shrink-0',
        divider && 'border-t border-border-default',
        PADDINGS[padding] ?? PADDINGS.md,
        className,
      )}
    >
      {children}
    </div>
  );
}
