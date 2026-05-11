import { AnimatePresence, motion } from 'framer-motion';
import { X } from 'lucide-react';
import IconButton from '../buttons/IconButton';

const cx = (...parts) => parts.filter(Boolean).join(' ');

const EASE = [0.32, 0.72, 0, 1];

const SIDES = {
  right: { initial: { x: '100%' }, exit: { x: '100%' }, classes: 'right-0 border-l' },
  left: { initial: { x: '-100%' }, exit: { x: '-100%' }, classes: 'left-0 border-r' },
};

/**
 * @typedef {Object} SideDrawerProps
 * @property {boolean} open
 * @property {() => void} onClose
 * @property {'right'|'left'} [side='right']
 * @property {string} [width='420px']
 * @property {React.ComponentType} [icon]
 * @property {string} [iconTint='text-accent']
 * @property {React.ReactNode} title
 * @property {React.ReactNode} [subtitle]
 * @property {React.ReactNode} [headerActions]
 * @property {React.ReactNode} [footer]
 * @property {React.ReactNode} children
 * @property {string} [className]
 * @property {string} [closeLabel='close']
 */

/** @param {SideDrawerProps} props */
export default function SideDrawer({
  open,
  onClose,
  side = 'right',
  width = '420px',
  icon: Icon,
  iconTint = 'text-accent',
  title,
  subtitle,
  headerActions,
  footer,
  children,
  className,
  closeLabel = 'close',
}) {
  const sideConfig = SIDES[side] ?? SIDES.right;
  return (
    <AnimatePresence>
      {open && (
        <>
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.18 }}
            className="fixed inset-0 z-[55] bg-black/40"
            onClick={onClose}
          />
          <motion.aside
            initial={sideConfig.initial}
            animate={{ x: 0 }}
            exit={sideConfig.exit}
            transition={{ duration: 0.32, ease: EASE }}
            style={{ width }}
            className={cx(
              'fixed top-0 bottom-0 z-[60] flex flex-col border-border-default bg-bg-deep w-full sm:w-auto',
              sideConfig.classes,
              className,
            )}
          >
            <header className="flex items-center justify-between px-5 h-14 border-b border-border-default shrink-0">
              <div className="flex items-center gap-2 min-w-0">
                {Icon && <Icon className={cx('h-4 w-4 shrink-0', iconTint)} />}
                <div className="flex flex-col min-w-0">
                  <h2 className="text-sm font-bold text-fg tracking-tight font-display truncate">{title}</h2>
                  {subtitle && <p className="text-[11px] text-fg-subtle truncate">{subtitle}</p>}
                </div>
              </div>
              <div className="flex items-center gap-1.5 shrink-0">
                {headerActions}
                <IconButton
                  variant="ghost"
                  size={7}
                  shape="square"
                  icon={<X className="h-4 w-4" />}
                  aria-label={closeLabel}
                  onClick={onClose}
                />
              </div>
            </header>
            <div className="flex-1 min-h-0 overflow-y-auto scrollbar-auto-hide">{children}</div>
            {footer && <div className="border-t border-border-default shrink-0">{footer}</div>}
          </motion.aside>
        </>
      )}
    </AnimatePresence>
  );
}
