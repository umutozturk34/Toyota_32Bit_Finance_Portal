import { useEffect, useMemo, useRef, useState } from 'react';
import { motion } from 'framer-motion';
import { MessageCircle, Send, Loader2, Inbox, Sparkles } from 'lucide-react';
import { useUserInbox, useUserSent, useSendMessage, useMarkMessageRead } from '../../shared/hooks/useMessages';
import { toast } from '../../shared/components/Toast';
import { extractApiError } from '../../shared/utils/apiError';

const MAX_BODY = 2000;
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
  const remaining = MAX_BODY - body.length;
  const isWarn = remaining < 200;

  return (
    <motion.div
      initial={{ opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.3 }}
      className="max-w-3xl mx-auto p-4 sm:p-6"
    >
      <section className="relative rounded-3xl border border-border-default bg-bg-elevated card-hover overflow-hidden flex flex-col h-[78vh] min-h-[520px]">
        <span aria-hidden className="absolute inset-x-0 top-0 h-[2px] bg-gradient-to-r from-transparent via-accent/60 to-transparent" />

        <header className="relative flex items-center gap-3 px-5 py-4 border-b border-border-default shrink-0">
          <div className="relative flex items-center justify-center w-11 h-11 rounded-2xl bg-gradient-to-br from-accent/25 to-accent/5 border border-accent/20 shrink-0">
            <span aria-hidden className="absolute inset-0 rounded-2xl bg-accent/10 blur-md -z-10" />
            <MessageCircle className="h-5 w-5 text-accent" />
          </div>
          <div className="min-w-0 flex-1">
            <h1 className="text-base font-bold text-fg tracking-tight">Mesajlar</h1>
            <p className="text-[11px] text-fg-muted mt-0.5">Yönetimle birebir yazışma</p>
          </div>
          <div className="flex items-center gap-1.5 text-[10px] font-mono uppercase tracking-wider text-fg-subtle shrink-0">
            <span className="relative flex w-2 h-2">
              <span aria-hidden className="absolute inset-0 rounded-full bg-success/60 animate-ping" />
              <span className="relative w-2 h-2 rounded-full bg-success" />
            </span>
            canlı
          </div>
        </header>

        <div
          ref={scrollRef}
          className="flex-1 overflow-y-auto px-5 py-5 space-y-3"
          style={{ scrollbarWidth: 'thin' }}
        >
          {isLoading ? (
            <div className="h-full flex items-center justify-center gap-2 text-sm text-fg-muted">
              <Loader2 className="h-4 w-4 animate-spin text-accent" />
              Yükleniyor…
            </div>
          ) : messages.length === 0 ? (
            <div className="h-full flex flex-col items-center justify-center gap-4 text-center px-6">
              <div className="relative">
                <span aria-hidden className="absolute inset-0 -m-4 rounded-full bg-accent/15 blur-2xl" />
                <span className="relative flex items-center justify-center w-16 h-16 rounded-3xl bg-gradient-to-br from-accent/30 to-accent/5 border border-accent/30 shadow-lg shadow-accent/10">
                  <Inbox className="h-7 w-7 text-accent" />
                </span>
              </div>
              <div className="space-y-1.5 max-w-xs">
                <p className="text-base font-bold text-fg">Sohbet henüz başlamadı</p>
                <p className="text-[12.5px] text-fg-muted leading-relaxed">
                  İlk mesajını gönder, yönetim ekibimiz seninle yazışsın. Genelde birkaç saat içinde dönüş yapıyoruz.
                </p>
              </div>
              <div className="flex items-center gap-1.5 text-[10px] font-mono uppercase tracking-wider text-accent">
                <Sparkles className="h-3 w-3" />
                Aşağıdan yazmaya başla
              </div>
            </div>
          ) : (
            messages.map((m) => {
              const fromAdmin = m.direction === 'ADMIN_TO_USER';
              return (
                <motion.div
                  key={m.id}
                  initial={{ opacity: 0, y: 6 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ duration: 0.2 }}
                  className={`flex ${fromAdmin ? 'justify-start' : 'justify-end'}`}
                >
                  <div className={`max-w-[78%] relative rounded-2xl px-4 py-2.5 text-sm leading-relaxed border ${
                    fromAdmin
                      ? 'bg-surface border-border-default text-fg'
                      : 'bg-gradient-to-br from-accent/15 to-accent/5 border-accent/30 text-fg shadow-sm shadow-accent/10'
                  }`}>
                    {fromAdmin && (
                      <div className="flex items-center gap-1.5 mb-1.5">
                        <span className="flex items-center justify-center w-4 h-4 rounded-full bg-accent/20">
                          <span className="w-1 h-1 rounded-full bg-accent" />
                        </span>
                        <span className="text-[10px] font-mono uppercase tracking-wider text-accent font-bold">Yönetim</span>
                      </div>
                    )}
                    <p className="whitespace-pre-wrap break-words">{m.body}</p>
                    <div className="mt-1.5 text-[10px] font-mono text-fg-subtle">{relTime(m.sentAt)}</div>
                  </div>
                </motion.div>
              );
            })
          )}
        </div>

        <form onSubmit={handleSend} className="relative border-t border-border-default bg-gradient-to-b from-transparent to-accent/[0.03] px-4 pt-3 pb-2.5 shrink-0">
          <div className="flex items-end gap-2">
            <textarea
              value={body}
              onChange={(e) => setBody(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter' && !e.shiftKey) {
                  e.preventDefault();
                  handleSend(e);
                }
              }}
              placeholder="Mesajını yaz…"
              rows={2}
              maxLength={MAX_BODY}
              className="flex-1 resize-none rounded-xl border border-border-default bg-bg-base/60 px-3 py-2.5 text-sm text-fg placeholder:text-fg-subtle focus:outline-none focus:border-accent/60 focus:shadow-[0_0_0_3px_rgba(99,102,241,0.15)] focus:bg-bg-base transition-all"
            />
            <motion.button
              type="submit"
              disabled={!body.trim() || sendMutation.isPending}
              whileTap={{ scale: 0.94 }}
              whileHover={{ y: -1 }}
              transition={{ type: 'spring', stiffness: 400, damping: 25 }}
              className="relative shrink-0 flex items-center justify-center w-11 h-11 rounded-xl text-white overflow-hidden disabled:opacity-40 disabled:cursor-not-allowed border-none cursor-pointer shadow-lg shadow-accent/30"
            >
              <span aria-hidden className="absolute inset-0 bg-gradient-to-br from-accent via-accent-bright to-accent" />
              {sendMutation.isPending ? (
                <Loader2 className="relative h-4 w-4 animate-spin" />
              ) : (
                <Send className="relative h-4 w-4" />
              )}
            </motion.button>
          </div>
          <div className="mt-1.5 flex items-center justify-between text-[10px] font-mono text-fg-subtle px-1">
            <span>Enter gönder · Shift+Enter satır</span>
            <span className={isWarn ? 'text-warning' : ''}>{body.length}/{MAX_BODY}</span>
          </div>
        </form>
      </section>
    </motion.div>
  );
}
