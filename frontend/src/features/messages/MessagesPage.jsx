import { useEffect, useMemo, useRef, useState } from 'react';
import { motion } from 'framer-motion';
import { MessageCircle, Send, Loader2, Inbox } from 'lucide-react';
import { useUserInbox, useUserSent, useSendMessage, useMarkMessageRead } from '../../shared/hooks/useMessages';
import { toast } from '../../shared/components/Toast';
import { extractApiError } from '../../shared/utils/apiError';

const RELATIVE = new Intl.RelativeTimeFormat('tr-TR', { numeric: 'auto' });

function relTime(iso) {
  const ts = new Date(iso).getTime();
  const diff = Math.round((ts - Date.now()) / 1000);
  const abs = Math.abs(diff);
  if (abs < 60) return RELATIVE.format(diff, 'second');
  if (abs < 3600) return RELATIVE.format(Math.round(diff / 60), 'minute');
  if (abs < 86400) return RELATIVE.format(Math.round(diff / 3600), 'hour');
  return RELATIVE.format(Math.round(diff / 86400), 'day');
}

export default function MessagesPage() {
  const inbox = useUserInbox({ page: 0, size: 100 });
  const sent = useUserSent({ page: 0, size: 100 });
  const sendMutation = useSendMessage();
  const markRead = useMarkMessageRead();
  const [body, setBody] = useState('');
  const scrollRef = useRef(null);

  const messages = useMemo(() => {
    const all = [...(inbox.data?.content ?? []), ...(sent.data?.content ?? [])];
    return all.sort((a, b) => new Date(a.sentAt) - new Date(b.sentAt));
  }, [inbox.data, sent.data]);

  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [messages.length]);

  useEffect(() => {
    (inbox.data?.content ?? []).forEach((m) => {
      if (!m.readAt) markRead.mutate(m.id);
    });
  }, [inbox.data]);

  const handleSend = async (e) => {
    e.preventDefault();
    const trimmed = body.trim();
    if (!trimmed || sendMutation.isPending) return;
    try {
      await sendMutation.mutateAsync(trimmed);
      setBody('');
    } catch (err) {
      toast.error(extractApiError(err, 'Mesaj gönderilemedi'));
    }
  };

  const isLoading = inbox.isLoading || sent.isLoading;

  return (
    <motion.div
      initial={{ opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.3 }}
      className="max-w-3xl mx-auto p-4 sm:p-6 space-y-4"
    >
      <header className="flex items-center gap-3">
        <div className="flex items-center justify-center w-10 h-10 rounded-2xl bg-accent/10 shrink-0">
          <MessageCircle className="h-5 w-5 text-accent" />
        </div>
        <div>
          <h1 className="text-xl font-bold text-fg tracking-tight">Mesajlar</h1>
          <p className="text-xs text-fg-muted mt-0.5">Yönetimle birebir yazışma</p>
        </div>
      </header>

      <section className="rounded-2xl border border-border-default bg-bg-elevated overflow-hidden flex flex-col h-[70vh]">
        <div className="absolute inset-x-0 top-0 h-[1px] bg-gradient-to-r from-transparent via-accent/40 to-transparent" />

        <div ref={scrollRef} className="flex-1 overflow-y-auto px-4 py-4 space-y-3" style={{ scrollbarWidth: 'thin' }}>
          {isLoading ? (
            <div className="flex items-center justify-center gap-2 py-12 text-sm text-fg-muted">
              <Loader2 className="h-4 w-4 animate-spin text-accent" />
              Yükleniyor…
            </div>
          ) : messages.length === 0 ? (
            <div className="flex flex-col items-center justify-center gap-2 py-16 text-center">
              <Inbox className="h-6 w-6 text-fg-subtle" />
              <p className="text-sm font-semibold text-fg-muted">Henüz mesaj yok</p>
              <p className="text-[11px] text-fg-subtle">İlk mesajını gönder, yönetim sana yanıt versin.</p>
            </div>
          ) : (
            messages.map((m) => {
              const fromAdmin = m.direction === 'ADMIN_TO_USER';
              return (
                <motion.div
                  key={m.id}
                  initial={{ opacity: 0, y: 4 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ duration: 0.18 }}
                  className={`flex ${fromAdmin ? 'justify-start' : 'justify-end'}`}
                >
                  <div className={`max-w-[78%] rounded-2xl px-3.5 py-2 text-sm leading-relaxed border ${
                    fromAdmin
                      ? 'bg-surface border-border-default text-fg'
                      : 'bg-accent/10 border-accent/30 text-fg'
                  }`}
                  >
                    {fromAdmin && (
                      <div className="text-[10px] font-mono uppercase tracking-wider text-accent mb-1">Yönetim</div>
                    )}
                    <p className="whitespace-pre-wrap break-words">{m.body}</p>
                    <div className="mt-1 text-[10px] font-mono text-fg-subtle">{relTime(m.sentAt)}</div>
                  </div>
                </motion.div>
              );
            })
          )}
        </div>

        <form onSubmit={handleSend} className="border-t border-border-default bg-bg-base/40 px-3 py-3 flex items-end gap-2">
          <textarea
            value={body}
            onChange={(e) => setBody(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                handleSend(e);
              }
            }}
            placeholder="Mesajını yaz… (Enter gönder, Shift+Enter yeni satır)"
            rows={2}
            maxLength={2000}
            className="flex-1 resize-none rounded-xl border border-border-default bg-bg-elevated px-3 py-2 text-sm text-fg placeholder:text-fg-subtle focus:outline-none focus:border-accent/60 focus:shadow-[0_0_0_3px_rgba(99,102,241,0.12)] transition-all"
          />
          <motion.button
            type="submit"
            disabled={!body.trim() || sendMutation.isPending}
            whileTap={{ scale: 0.96 }}
            className="relative shrink-0 flex items-center justify-center w-11 h-11 rounded-xl text-white overflow-hidden disabled:opacity-50 disabled:cursor-not-allowed border-none cursor-pointer"
          >
            <span aria-hidden className="absolute inset-0 bg-gradient-to-br from-accent via-accent-bright to-accent" />
            <Send className="relative h-4 w-4" />
          </motion.button>
        </form>
      </section>
    </motion.div>
  );
}
