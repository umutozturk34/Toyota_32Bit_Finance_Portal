import { useEffect, useState } from 'react';
import { AnimatePresence } from 'framer-motion';
import { MessageCircle, Send, Loader2, X } from 'lucide-react';
import { useSendAdminMessage } from '../../../shared/hooks/useMessages';
import { toast } from '../../../shared/components/feedback/Toast';
import { extractApiError } from '../../../shared/utils/apiError';
import { MAX_BODY } from '../../messages/util';

export default function AdminUserMessageModal({ open, user, onClose }) {
  const [body, setBody] = useState('');
  const send = useSendAdminMessage();

  useEffect(() => {
    if (open) setBody('');
  }, [open]);

  const handleSubmit = async (e) => {
    e.preventDefault();
    const trimmed = body.trim();
    if (!trimmed || send.isPending || !user?.id) return;
    try {
      await send.mutateAsync({ recipientSub: user.id, body: trimmed });
      toast.success('Mesaj gönderildi', `${user.username} kullanıcısına iletildi`);
      onClose?.();
    } catch (err) {
      toast.error(extractApiError(err, 'Mesaj gönderilemedi'));
    }
  };

  return (
    <AnimatePresence>
      {open && user && (
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
                <MessageCircle className="h-4 w-4 text-accent" />
              </div>
              <div className="min-w-0">
                <h2 className="text-sm font-bold text-fg truncate">{user.username}</h2>
                <p className="text-[11px] font-mono text-fg-muted mt-0.5 truncate" title={user.email || user.id}>
                  {user.email || user.id}
                </p>
              </div>
            </div>

            <label className="block">
              <span className="text-[11px] font-semibold uppercase tracking-wider text-fg-muted">Mesaj</span>
              <textarea
                value={body}
                onChange={(e) => setBody(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' && !e.shiftKey) {
                    e.preventDefault();
                    handleSubmit(e);
                  }
                }}
                rows={5}
                maxLength={MAX_BODY}
                required
                placeholder="Kullanıcıya iletmek istediğin mesajı yaz…"
                className="mt-1.5 w-full resize-none rounded-xl border border-border-default bg-bg-elevated px-3 py-2.5 text-sm text-fg placeholder:text-fg-subtle focus:outline-none focus:border-accent/60 focus:shadow-[0_0_0_3px_rgba(99,102,241,0.15)] transition-all"
              />
              <div className="mt-1 flex items-center justify-between text-[10px] font-mono text-fg-subtle">
                <span>Enter gönder · Shift+Enter satır</span>
                <span className={body.length > MAX_BODY - 200 ? 'text-warning' : ''}>{body.length}/{MAX_BODY}</span>
              </div>
            </label>

            <div className="flex gap-2 mt-5">
              <button
                type="button"
                onClick={onClose}
                disabled={send.isPending}
                className="flex-1 rounded-lg py-2.5 text-sm font-semibold text-fg border border-border-default bg-bg-base hover:bg-surface transition-all cursor-pointer disabled:opacity-50"
              >
                Vazgeç
              </button>
              <motion.button
                type="submit"
                disabled={!body.trim() || send.isPending}
                whileTap={{ scale: 0.98 }}
                className="flex-1 relative flex items-center justify-center gap-2 rounded-lg py-2.5 text-sm font-semibold text-white overflow-hidden disabled:opacity-50 cursor-pointer border-none"
              >
                <span aria-hidden className="absolute inset-0 bg-gradient-to-r from-accent via-accent-bright to-accent" />
                <span className="relative flex items-center gap-1.5">
                  {send.isPending ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Send className="h-3.5 w-3.5" />}
                  {send.isPending ? 'Gönderiliyor…' : 'Gönder'}
                </span>
              </motion.button>
            </div>
          </motion.form>
        </div>
      )}
    </AnimatePresence>
  );
}
