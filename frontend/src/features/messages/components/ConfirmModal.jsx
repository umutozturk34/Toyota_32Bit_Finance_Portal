import { motion } from 'framer-motion';
import { AlertTriangle } from 'lucide-react';

export default function ConfirmModal({ open, title, body, confirmLabel, danger, onCancel, onConfirm, pending }) {
  if (!open) return null;
  return (
    <div className="fixed inset-0 z-[80] flex items-center justify-center p-4">
      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        className="absolute inset-0 modal-overlay backdrop-blur-sm"
        onClick={onCancel}
      />
      <motion.div
        initial={{ opacity: 0, scale: 0.94, y: 12 }}
        animate={{ opacity: 1, scale: 1, y: 0 }}
        transition={{ type: 'spring', stiffness: 380, damping: 30 }}
        className="relative w-full max-w-sm rounded-2xl border border-border-default modal-panel p-5"
      >
        <div className="flex items-center gap-3 mb-4">
          <div className={`flex items-center justify-center w-10 h-10 rounded-xl ${danger ? 'bg-danger/10' : 'bg-accent/10'}`}>
            <AlertTriangle className={`h-4 w-4 ${danger ? 'text-danger' : 'text-accent'}`} />
          </div>
          <h3 className="text-sm font-bold text-fg">{title}</h3>
        </div>
        <p className="text-xs text-fg-muted leading-relaxed mb-5">{body}</p>
        <div className="flex gap-2">
          <button
            type="button"
            onClick={onCancel}
            disabled={pending}
            className="flex-1 rounded-lg py-2 text-xs font-semibold text-fg border border-border-default bg-bg-base hover:bg-surface transition-all cursor-pointer disabled:opacity-50"
          >
            Vazgeç
          </button>
          <motion.button
            type="button"
            onClick={onConfirm}
            disabled={pending}
            whileTap={{ scale: 0.96 }}
            className={`flex-1 rounded-lg py-2 text-xs font-semibold text-white border-none cursor-pointer disabled:opacity-50 ${
              danger ? 'bg-danger hover:bg-danger/90' : 'bg-accent hover:bg-accent-bright'
            }`}
          >
            {pending ? '…' : confirmLabel}
          </motion.button>
        </div>
      </motion.div>
    </div>
  );
}
