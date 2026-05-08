import { AnimatePresence } from 'framer-motion';
import { X } from 'lucide-react';
import { AlertTriangle } from '../feedback/AnimatedIcons';

export default function ConfirmDialog({
  open,
  title = 'Emin misiniz?',
  message,
  confirmLabel = 'Sil',
  cancelLabel = 'Vazgeç',
  variant = 'danger',
  icon: CustomIcon,
  onConfirm,
  onCancel,
  loading = false,
}) {
  const Icon = CustomIcon || AlertTriangle;

  const variants = {
    danger: {
      iconBg: 'bg-danger/10',
      iconColor: 'text-danger',
      btnBg: 'bg-danger hover:bg-danger/90',
    },
    warning: {
      iconBg: 'bg-warning/10',
      iconColor: 'text-warning',
      btnBg: 'bg-warning hover:bg-warning/90',
    },
    accent: {
      iconBg: 'bg-accent/10',
      iconColor: 'text-accent',
      btnBg: 'bg-accent hover:bg-accent/90',
    },
  };

  const v = variants[variant] || variants.danger;

  return (
    <AnimatePresence>
      {open && (
        <div className="fixed inset-0 z-[80] flex items-center justify-center p-4">
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="absolute inset-0 modal-overlay backdrop-blur-sm"
            onClick={onCancel}
          />
          <motion.div
            initial={{ opacity: 0, scale: 0.92, y: 12 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.92, y: 12 }}
            transition={{ type: 'spring', stiffness: 400, damping: 30 }}
            className="relative w-full max-w-xs rounded-2xl border border-border-default modal-panel p-5 overflow-hidden"
          >
            <div className="absolute top-0 left-0 right-0 h-[1px] bg-gradient-to-r from-transparent via-danger/40 to-transparent" />

            <button
              onClick={onCancel}
              disabled={loading}
              className="absolute top-3 right-3 flex items-center justify-center w-7 h-7 rounded-lg text-fg-muted hover:text-fg hover:bg-surface transition-colors bg-transparent border-none cursor-pointer disabled:opacity-30"
            >
              <X className="h-3.5 w-3.5" />
            </button>

            <div className="flex flex-col items-center gap-3 pt-1 pb-4">
              <motion.div
                initial={{ scale: 0.5, opacity: 0 }}
                animate={{ scale: 1, opacity: 1 }}
                transition={{ type: 'spring', stiffness: 300, damping: 20, delay: 0.05 }}
                className={`flex items-center justify-center w-12 h-12 rounded-full ${v.iconBg}`}
              >
                <Icon className={`h-6 w-6 ${v.iconColor}`} />
              </motion.div>
              <div className="text-center space-y-1">
                <p className="text-sm font-semibold text-fg">{title}</p>
                {message && (
                  <p className="text-xs text-fg-muted leading-relaxed">{message}</p>
                )}
              </div>
            </div>

            <div className="flex gap-2">
              <button
                onClick={onCancel}
                disabled={loading}
                className="flex-1 rounded-lg py-2.5 text-sm font-semibold text-fg border border-border-default bg-bg-base hover:bg-surface transition-all cursor-pointer disabled:opacity-50"
              >
                {cancelLabel}
              </button>
              <button
                onClick={onConfirm}
                disabled={loading}
                className={`flex-1 rounded-lg py-2.5 text-sm font-semibold text-white ${v.btnBg} transition-all border-none cursor-pointer disabled:opacity-50`}
              >
                {loading ? 'İşleniyor...' : confirmLabel}
              </button>
            </div>
          </motion.div>
        </div>
      )}
    </AnimatePresence>
  );
}
