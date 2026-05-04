import { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { X, ListPlus } from 'lucide-react';
import { useCreateWatchlist } from '../../shared/hooks/useWatchlist';
import { toast } from '../../shared/components/Toast';

export default function CreateWatchlistModal({ isOpen, onClose, onCreated }) {
  const create = useCreateWatchlist();
  const [name, setName] = useState('');

  useEffect(() => {
    if (isOpen) setName('');
  }, [isOpen]);

  const submit = async (e) => {
    e.preventDefault();
    const trimmed = name.trim();
    if (!trimmed) return toast.error('Liste adı gerekli');
    try {
      const created = await create.mutateAsync(trimmed);
      toast.success('Liste oluşturuldu');
      onCreated?.(created);
      onClose();
    } catch (err) {
      toast.error(err?.response?.data?.error?.message ?? 'Oluşturulamadı');
    }
  };

  return (
    <AnimatePresence>
      {isOpen && (
        <>
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="fixed inset-0 z-[70] bg-black/50 backdrop-blur-sm"
            onClick={onClose}
          />
          <motion.div
            initial={{ opacity: 0, scale: 0.96, y: 8 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.96, y: 8 }}
            transition={{ type: 'spring', damping: 28, stiffness: 280 }}
            className="fixed top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 z-[80] w-[92vw] max-w-sm rounded-2xl border border-border-default bg-bg-deep shadow-2xl"
          >
            <header className="flex items-center justify-between px-5 h-14 border-b border-border-default">
              <div className="flex items-center gap-2">
                <ListPlus className="h-4 w-4 text-accent" />
                <h2 className="text-sm font-bold text-fg tracking-tight font-display">Yeni liste</h2>
              </div>
              <button
                type="button"
                onClick={onClose}
                className="flex items-center justify-center w-7 h-7 rounded-md text-fg-muted hover:text-fg hover:bg-surface transition-colors bg-transparent border-none cursor-pointer"
              >
                <X className="h-4 w-4" />
              </button>
            </header>
            <form onSubmit={submit} className="p-5 space-y-4">
              <div className="space-y-1.5">
                <label className="text-[11px] font-mono uppercase tracking-wider text-fg-subtle">Liste adı</label>
                <input
                  type="text"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  maxLength={64}
                  autoFocus
                  placeholder="Crypto Yatırımları"
                  className="w-full rounded-lg border border-border-default bg-bg-elevated px-3 py-2 text-sm text-fg outline-none focus:border-accent transition-colors"
                />
                <p className="text-[10px] text-fg-subtle leading-relaxed">
                  En fazla 20 listen olabilir. Aynı isimde iki liste oluşturamazsın.
                </p>
              </div>
              <footer className="flex items-center justify-end gap-2 pt-2 border-t border-border-default">
                <button
                  type="button"
                  onClick={onClose}
                  className="px-3 py-2 rounded-lg text-xs font-medium text-fg-muted hover:text-fg hover:bg-surface transition-colors bg-transparent border border-border-default cursor-pointer"
                >
                  İptal
                </button>
                <button
                  type="submit"
                  disabled={create.isPending}
                  className="px-4 py-2 rounded-lg text-xs font-semibold text-white bg-accent hover:bg-accent-bright transition-colors border-none cursor-pointer disabled:opacity-50"
                >
                  {create.isPending ? 'Oluşturuluyor…' : 'Oluştur'}
                </button>
              </footer>
            </form>
          </motion.div>
        </>
      )}
    </AnimatePresence>
  );
}
