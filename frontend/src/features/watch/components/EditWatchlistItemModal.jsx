import { useEffect, useState } from 'react';
import { AnimatePresence } from 'framer-motion';
import { X, Pencil, Save } from 'lucide-react';
import { useUpdateWatchlistItem } from '../../../shared/hooks/useWatchlist';
import { toast } from '../../../shared/components/feedback/Toast';
import { extractApiError } from '../../../shared/utils/apiError';

export default function EditWatchlistItemModal({ open, onClose, item, watchlistId }) {
  const [note, setNote] = useState('');
  const [threshold, setThreshold] = useState('');
  const update = useUpdateWatchlistItem(watchlistId);

  useEffect(() => {
    if (open && item) {
      setNote(item.note ?? '');
      setThreshold(item.deltaThreshold != null ? String(item.deltaThreshold) : '');
    }
  }, [open, item]);

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!item || update.isPending) return;
    const payload = {};
    payload.note = note.trim();
    const trimmed = threshold.trim().replace(',', '.');
    if (trimmed !== '') {
      const value = Number(trimmed);
      if (Number.isNaN(value)) {
        toast.error('Geçersiz eşik', 'Sayısal bir değer gir (örn 0,5 veya 5)');
        return;
      }
      payload.deltaThreshold = value;
    }
    try {
      await update.mutateAsync({ itemId: item.id, payload });
      toast.success('Güncellendi', `${item.assetCode} ayarları kaydedildi`);
      onClose?.();
    } catch (err) {
      toast.error(extractApiError(err, 'Güncelleme başarısız'));
    }
  };

  return (
    <AnimatePresence>
      {open && item && (
        <div className="fixed inset-0 z-[80] flex items-center justify-center p-4">
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="absolute inset-0 modal-overlay backdrop-blur-sm"
            onClick={onClose}
          />
          <motion.form
            onSubmit={handleSubmit}
            initial={{ opacity: 0, scale: 0.94, y: 12 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.94, y: 12 }}
            transition={{ type: 'spring', stiffness: 380, damping: 30 }}
            className="relative w-full max-w-md rounded-2xl border border-border-default modal-panel p-5 overflow-hidden"
          >
            <div className="absolute top-0 left-0 right-0 h-[1px] bg-gradient-to-r from-transparent via-accent/40 to-transparent" />

            <button
              type="button"
              onClick={onClose}
              className="absolute top-3 right-3 flex items-center justify-center w-7 h-7 rounded-lg text-fg-muted hover:text-fg hover:bg-surface transition-colors bg-transparent border-none cursor-pointer"
            >
              <X className="h-3.5 w-3.5" />
            </button>

            <div className="flex items-center gap-3 mb-5">
              <div className="flex items-center justify-center w-10 h-10 rounded-xl bg-accent/10">
                <Pencil className="h-4 w-4 text-accent" />
              </div>
              <div>
                <h2 className="text-sm font-bold text-fg">Asset ayarlarını düzenle</h2>
                <p className="text-xs font-mono text-fg-muted mt-0.5">{item.assetName ?? item.assetCode}</p>
              </div>
            </div>

            <div className="space-y-4">
              <label className="block">
                <span className="text-[11px] font-semibold uppercase tracking-wider text-fg-muted">Not</span>
                <input
                  type="text"
                  value={note}
                  onChange={(e) => setNote(e.target.value)}
                  maxLength={255}
                  placeholder="Bu varlık için kişisel notun"
                  className="mt-1.5 w-full rounded-lg border border-border-default bg-bg-elevated px-3 py-2.5 text-sm text-fg placeholder:text-fg-subtle focus:outline-none focus:border-accent/60 focus:bg-bg-elevated focus:shadow-[0_0_0_3px_rgba(99,102,241,0.12)] transition-all"
                />
              </label>

              <label className="block">
                <span className="text-[11px] font-semibold uppercase tracking-wider text-fg-muted">
                  Eşik (% değişim)
                </span>
                <input
                  type="text"
                  inputMode="decimal"
                  value={threshold}
                  onChange={(e) => setThreshold(e.target.value)}
                  placeholder="0 = her hareket / örn 5 = %5"
                  className="mt-1.5 w-full rounded-lg border border-border-default bg-bg-elevated px-3 py-2.5 text-sm font-mono text-fg placeholder:text-fg-subtle focus:outline-none focus:border-accent/60 focus:shadow-[0_0_0_3px_rgba(99,102,241,0.12)] transition-all"
                />
                <p className="mt-1 text-[10px] text-fg-subtle leading-relaxed">
                  Boş bırakırsan global varsayılan kullanılır. <span className="font-mono">0</span> = her hareket bildirim gönderir.
                </p>
              </label>
            </div>

            <div className="flex gap-2 mt-5">
              <button
                type="button"
                onClick={onClose}
                disabled={update.isPending}
                className="flex-1 rounded-lg py-2.5 text-sm font-semibold text-fg border border-border-default bg-bg-base hover:bg-surface transition-all cursor-pointer disabled:opacity-50"
              >
                Vazgeç
              </button>
              <motion.button
                type="submit"
                disabled={update.isPending}
                whileTap={{ scale: 0.98 }}
                className="flex-1 relative flex items-center justify-center gap-2 rounded-lg py-2.5 text-sm font-semibold text-white overflow-hidden disabled:opacity-50 cursor-pointer border-none"
              >
                <span aria-hidden className="absolute inset-0 bg-gradient-to-r from-accent via-accent-bright to-accent" />
                <span className="relative flex items-center gap-2">
                  <Save className="h-3.5 w-3.5" />
                  {update.isPending ? 'Kaydediliyor…' : 'Kaydet'}
                </span>
              </motion.button>
            </div>
          </motion.form>
        </div>
      )}
    </AnimatePresence>
  );
}
