import { useEffect, useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Megaphone, X, Loader2 } from 'lucide-react';
import { useBroadcast } from '../../shared/hooks/useMessages';
import { toast } from '../../shared/components/Toast';
import { extractApiError } from '../../shared/utils/apiError';

export default function BroadcastModal({ open, onClose }) {
  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');
  const broadcast = useBroadcast();

  useEffect(() => {
    if (open) { setTitle(''); setBody(''); }
  }, [open]);

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!title.trim() || !body.trim() || broadcast.isPending) return;
    try {
      const result = await broadcast.mutateAsync({ title: title.trim(), body: body.trim() });
      toast.success('Yayın gönderildi', `${result.dispatched}/${result.totalRecipients} kullanıcıya iletildi`);
      onClose?.();
    } catch (err) {
      toast.error(extractApiError(err, 'Yayın gönderilemedi'));
    }
  };

  return (
    <AnimatePresence>
      {open && (
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
            className="relative w-full max-w-md rounded-3xl border border-border-default modal-panel p-6 overflow-hidden card-hover"
          >
            <span aria-hidden className="absolute inset-x-0 top-0 h-[2px] bg-gradient-to-r from-transparent via-accent/60 to-transparent" />
            <button
              type="button"
              onClick={onClose}
              className="absolute top-3.5 right-3.5 flex items-center justify-center w-8 h-8 rounded-xl text-fg-muted hover:text-fg hover:bg-surface transition-all bg-transparent border-none cursor-pointer"
            >
              <X className="h-4 w-4" />
            </button>
            <div className="flex items-center gap-3 mb-5">
              <div className="relative flex items-center justify-center w-11 h-11 rounded-2xl bg-gradient-to-br from-accent/25 to-accent/5 border border-accent/20 shrink-0">
                <span aria-hidden className="absolute inset-0 rounded-2xl bg-accent/10 blur-md -z-10" />
                <Megaphone className="h-4 w-4 text-accent" />
              </div>
              <div>
                <h2 className="text-sm font-bold text-fg">Yayın gönder</h2>
                <p className="text-[11px] font-mono text-fg-muted mt-0.5 uppercase tracking-wide">Tüm kullanıcılara sistem bildirimi</p>
              </div>
            </div>
            <div className="space-y-3">
              <label className="block">
                <span className="text-[11px] font-semibold uppercase tracking-wider text-fg-muted">Başlık</span>
                <input
                  type="text"
                  value={title}
                  onChange={(e) => setTitle(e.target.value)}
                  maxLength={120}
                  required
                  className="mt-1.5 w-full rounded-lg border border-border-default bg-bg-elevated px-3 py-2.5 text-sm text-fg focus:outline-none focus:border-accent/60 focus:shadow-[0_0_0_3px_rgba(99,102,241,0.12)] transition-all"
                />
              </label>
              <label className="block">
                <span className="text-[11px] font-semibold uppercase tracking-wider text-fg-muted">Mesaj</span>
                <textarea
                  value={body}
                  onChange={(e) => setBody(e.target.value)}
                  rows={4}
                  maxLength={2000}
                  required
                  className="mt-1.5 w-full resize-none rounded-lg border border-border-default bg-bg-elevated px-3 py-2.5 text-sm text-fg focus:outline-none focus:border-accent/60 focus:shadow-[0_0_0_3px_rgba(99,102,241,0.12)] transition-all"
                />
              </label>
            </div>
            <div className="flex gap-2 mt-5">
              <button
                type="button"
                onClick={onClose}
                disabled={broadcast.isPending}
                className="flex-1 rounded-lg py-2.5 text-sm font-semibold text-fg border border-border-default bg-bg-base hover:bg-surface transition-all cursor-pointer disabled:opacity-50"
              >
                Vazgeç
              </button>
              <motion.button
                type="submit"
                disabled={broadcast.isPending}
                whileTap={{ scale: 0.98 }}
                className="flex-1 relative flex items-center justify-center gap-2 rounded-lg py-2.5 text-sm font-semibold text-white overflow-hidden disabled:opacity-50 cursor-pointer border-none"
              >
                <span aria-hidden className="absolute inset-0 bg-gradient-to-r from-accent via-accent-bright to-accent" />
                <span className="relative flex items-center gap-1.5">
                  {broadcast.isPending && <Loader2 className="h-3.5 w-3.5 animate-spin" />}
                  {broadcast.isPending ? 'Gönderiliyor…' : 'Yayınla'}
                </span>
              </motion.button>
            </div>
          </motion.form>
        </div>
      )}
    </AnimatePresence>
  );
}
