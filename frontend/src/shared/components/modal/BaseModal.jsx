import { createPortal } from 'react-dom';
import { AnimatePresence, motion } from 'framer-motion';
import { X } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import IconButton from '../buttons/IconButton';
import useOverlayDismiss from '../../hooks/useOverlayDismiss';

const SIZE_CLASSES = {
  sm: 'max-w-sm',
  md: 'max-w-md',
  lg: 'max-w-lg',
  xl: 'max-w-xl',
  '2xl': 'max-w-2xl',
  '3xl': 'max-w-3xl',
  '4xl': 'max-w-4xl',
  '5xl': 'max-w-5xl',
  '6xl': 'max-w-6xl',
};

export default function BaseModal({
  isOpen,
  onClose,
  icon: Icon,
  title,
  subtitle,
  size = 'sm',
  footer,
  children,
  closeLabel,
}) {
  const { t } = useTranslation();
  const closeText = closeLabel ?? t('common.close');
  useOverlayDismiss(isOpen, onClose);
  return createPortal(
    <AnimatePresence>
      {isOpen && (
        <div className="fixed inset-0 z-[70] flex items-center justify-center p-3 sm:p-4">
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
            className={`relative w-full ${SIZE_CLASSES[size] ?? SIZE_CLASSES.sm} max-h-[90vh] max-h-[90dvh] landscape:max-h-[92dvh] rounded-2xl border border-border-default modal-panel p-4 landscape:p-3 sm:p-6 sm:landscape:p-5 pb-[max(1rem,env(safe-area-inset-bottom))] overflow-hidden flex flex-col`}
          >
            <div className="absolute top-0 left-0 right-0 h-[1px] bg-gradient-to-r from-transparent via-accent/40 to-transparent" />
            <div className="flex items-center justify-between mb-4 landscape:mb-3 sm:mb-5 sm:landscape:mb-3 shrink-0">
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
                aria-label={closeText}
                onClick={onClose}
              />
            </div>
            <div className="flex-1 min-h-0 overflow-y-auto -mx-1 px-1">{children}</div>
            {footer && <div className="mt-4 sm:mt-5 shrink-0">{footer}</div>}
          </motion.div>
        </div>
      )}
    </AnimatePresence>,
    document.body,
  );
}
