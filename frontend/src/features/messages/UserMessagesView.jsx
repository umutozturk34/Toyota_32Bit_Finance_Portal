import { useEffect, useMemo, useRef, useState } from 'react';
import { motion } from 'framer-motion';
import { MessageCircle, Loader2, Inbox, Sparkles } from 'lucide-react';
import { useUserInbox, useUserSent, useSendMessage, useMarkMessageRead } from '../../shared/hooks/useMessages';
import { containerVariants } from '../../shared/utils/animations';
import { toast } from '../../shared/components/Toast';
import { extractApiError } from '../../shared/utils/apiError';
import MessageBubble from './components/MessageBubble';
import Composer from './components/Composer';

export default function UserMessagesView() {
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
    if (scrollRef.current) scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
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
      className="max-w-3xl mx-auto p-4 sm:p-6"
    >
      <section className="relative rounded-3xl border border-border-default bg-bg-elevated card-hover overflow-hidden flex flex-col h-[78vh] min-h-[520px]">
        <span aria-hidden className="absolute inset-x-0 top-0 h-[2px] bg-gradient-to-r from-transparent via-accent/60 to-transparent" />

        <header className="flex items-center gap-3 px-5 py-4 border-b border-border-default shrink-0">
          <div className="relative flex items-center justify-center w-11 h-11 rounded-2xl bg-gradient-to-br from-accent/25 to-accent/5 border border-accent/20 shrink-0">
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

        <div ref={scrollRef} className="flex-1 overflow-y-auto px-5 py-5 space-y-3" style={{ scrollbarWidth: 'thin' }}>
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
                <p className="text-[12.5px] text-fg-muted leading-relaxed">İlk mesajını gönder, yönetim ekibimiz seninle yazışsın.</p>
              </div>
              <div className="flex items-center gap-1.5 text-[10px] font-mono uppercase tracking-wider text-accent">
                <Sparkles className="h-3 w-3" />
                Aşağıdan yazmaya başla
              </div>
            </div>
          ) : (
            <motion.div variants={containerVariants(0.03)} initial="hidden" animate="show" className="space-y-3">
              {messages.map((m) => (
                <MessageBubble
                  key={m.id}
                  message={m}
                  leftSide={m.direction === 'ADMIN_TO_USER'}
                  label={m.direction === 'ADMIN_TO_USER' ? 'Yönetim' : null}
                />
              ))}
            </motion.div>
          )}
        </div>

        <Composer
          value={body}
          onChange={setBody}
          onSubmit={handleSend}
          disabled={!body.trim() || sendMutation.isPending}
          placeholder="Mesajını yaz…"
          pending={sendMutation.isPending}
        />
      </section>
    </motion.div>
  );
}
