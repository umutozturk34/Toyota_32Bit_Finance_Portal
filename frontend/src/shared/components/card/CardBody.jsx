const cx = (...parts) => parts.filter(Boolean).join(' ');

const PADDINGS = {
  none: '',
  sm: 'p-2',
  md: 'p-3',
  lg: 'p-4',
};

export default function CardBody({ scrollable = false, padding = 'none', className, children }) {
  return (
    <div
      className={cx(
        scrollable ? 'flex-1 min-h-0 overflow-y-auto scrollbar-auto-hide' : 'flex-1',
        PADDINGS[padding] ?? PADDINGS.none,
        className,
      )}
    >
      {children}
    </div>
  );
}
