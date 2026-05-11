const cx = (...parts) => parts.filter(Boolean).join(' ');

const PADDINGS = {
  none: '',
  sm: 'p-2',
  md: 'p-3',
  lg: 'p-4',
};

/**
 * @typedef {Object} CardFooterProps
 * @property {boolean} [divider]
 * @property {'none'|'sm'|'md'|'lg'} [padding='md']
 * @property {string} [className]
 * @property {React.ReactNode} [children]
 */

/** @param {CardFooterProps} props */
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
