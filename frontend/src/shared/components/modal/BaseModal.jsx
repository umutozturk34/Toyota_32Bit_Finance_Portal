import { AnimatePresence, motion } from 'framer-motion';
import { X } from 'lucide-react';
import IconButton from '../buttons/IconButton';

const SIZE_CLASSES = {
  sm: 'max-w-sm',
  md: 'max-w-md',
  lg: 'max-w-lg',
  xl: 'max-w-xl',
  '2xl': 'max-w-2xl',
};

/**
 * @typedef {Object} BaseModalProps
 * @property {boolean} isOpen
 * @property {() => void} onClose
 * @property {React.ComponentType} [icon]
 * @property {React.ReactNode} title
 * @property {React.ReactNode} [subtitle]
 * @property {'sm'|'md'|'lg'|'xl'|'2xl'} [size='sm']
 * @property {React.ReactNode} [footer]
 * @property {React.ReactNode} children
 * @property {string} [closeLabel='close']
 */

/** @param {BaseModalProps} props */
export default function BaseModal({
  isOpen,
  onClose,
  icon: Icon,
  title,
  subtitle,
  size = 'sm',
  footer,
  children,
  closeLabel = 'close',
}) {
  return (
    <AnimatePresence>
      {isOpen && (
        <div className="fixed inset-0 z-[70] flex items-center justify-center p-4">
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="absolute inset-0 modal-overlay backdrop-blur-sm"
            onClick={onClose}
          />
          <motion.div
            initial={{ opacity: 0, scale: 0.95, y: 10 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.95, y: 10 }}
            transition={{ type: 'spring', stiffness: 300, damping: 30 }}
            className={`relative w-full ${SIZE_CLASSES[size] ?? SIZE_CLASSES.sm} rounded-2xl border border-border-default modal-panel p-6 overflow-visible`}
          >
            <div className="absolute top-0 left-0 right-0 h-[1px] bg-gradient-to-r from-transparent via-accent/40 to-transparent" />
            <div className="flex items-center justify-between mb-5">
              <div className="flex items-center gap-3 min-w-0">
                {Icon && (
                  <div className="flex items-center justify-center w-9 h-9 rounded-lg bg-accent/10 shrink-0">
                    <Icon className="h-4 w-4 text-accent" />
                  </div>
                )}
                <div className="min-w-0">
                  <h2 className="text-base font-semibold text-fg truncate">{title}</h2>
                  {subtitle && <p className="text-xs text-fg-muted truncate">{subtitle}</p>}
                </div>
              </div>
              <IconButton
                variant="ghost"
                size={8}
                shape="square"
                icon={<X className="h-4 w-4" />}
                aria-label={closeLabel}
                onClick={onClose}
              />
            </div>
            <div>{children}</div>
            {footer && <div className="mt-5">{footer}</div>}
          </motion.div>
        </div>
      )}
    </AnimatePresence>
  );
}
