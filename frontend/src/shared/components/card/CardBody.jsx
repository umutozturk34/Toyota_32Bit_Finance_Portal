const cx = (...parts) => parts.filter(Boolean).join(' ');

const PADDINGS = {
  none: '',
  sm: 'p-2',
  md: 'p-3',
  lg: 'p-4',
};

/**
 * @typedef {Object} CardBodyProps
 * @property {boolean} [scrollable]
 * @property {'none'|'sm'|'md'|'lg'} [padding='none']
 * @property {string} [className]
 * @property {React.ReactNode} [children]
 */

/** @param {CardBodyProps} props */
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
