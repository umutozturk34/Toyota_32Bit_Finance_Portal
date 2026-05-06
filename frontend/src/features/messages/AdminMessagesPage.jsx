import { useEffect, useMemo, useRef, useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import {
  MessageCircle, Send, Loader2, Inbox, Lock, Unlock, Trash2, Megaphone,
  ChevronLeft, ChevronRight, X, AlertTriangle, ShieldOff, Users,
} from 'lucide-react';
import {
  useAdminConversations, useAdminConversation,
  useSendAdminMessage, useCloseConversation, useReopenConversation,
  useDeleteConversation, useBroadcast,
} from '../../shared/hooks/useMessages';
import { toast } from '../../shared/components/Toast';
import { extractApiError } from '../../shared/utils/apiError';

const PAGE_SIZE = 20;
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

function shortSub(sub) {
  return sub?.length > 10 ? `${sub.slice(0, 6)}…${sub.slice(-3)}` : sub;
}

function ConfirmModal({ open, title, body, confirmLabel, danger, onCancel, onConfirm, pending }) {
  return (
    <AnimatePresence>
      {open && (
        <div className="fixed inset-0 z-[80] flex items-center justify-center p-4">
          <motion.div
            initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
            className="absolute inset-0 modal-overlay backdrop-blur-sm" onClick={onCancel}
          />
          <motion.div
            initial={{ opacity: 0, scale: 0.94, y: 12 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.94, y: 12 }}
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
              <button type="button" onClick={onCancel} disabled={pending}
                className="flex-1 rounded-lg py-2 text-xs font-semibold text-fg border border-border-default bg-bg-base hover:bg-surface transition-all cursor-pointer disabled:opacity-50">
                Vazgeç
              </button>
              <motion.button type="button" onClick={onConfirm} disabled={pending} whileTap={{ scale: 0.96 }}
                className={`flex-1 rounded-lg py-2 text-xs font-semibold text-white border-none cursor-pointer disabled:opacity-50 ${
                  danger ? 'bg-danger hover:bg-danger/90' : 'bg-accent hover:bg-accent-bright'}`}>
                {pending ? '…' : confirmLabel}
              </motion.button>
            </div>
          </motion.div>
        </div>
      )}
    </AnimatePresence>
  );
}

function BroadcastModal({ open, onClose }) {
  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');
  const broadcast = useBroadcast();

  useEffect(() => { if (open) { setTitle(''); setBody(''); } }, [open]);

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
          <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
            className="absolute inset-0 modal-overlay backdrop-blur-sm" onClick={onClose} />
          <motion.form onSubmit={handleSubmit}
            initial={{ opacity: 0, scale: 0.94, y: 12 }} animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.94, y: 12 }}
            transition={{ type: 'spring', stiffness: 380, damping: 30 }}
            className="relative w-full max-w-md rounded-2xl border border-border-default modal-panel p-5 overflow-hidden">
            <div className="absolute top-0 left-0 right-0 h-[1px] bg-gradient-to-r from-transparent via-accent/40 to-transparent" />
            <button type="button" onClick={onClose}
              className="absolute top-3 right-3 flex items-center justify-center w-7 h-7 rounded-lg text-fg-muted hover:text-fg hover:bg-surface transition-colors bg-transparent border-none cursor-pointer">
              <X className="h-3.5 w-3.5" />
            </button>
            <div className="flex items-center gap-3 mb-5">
              <div className="flex items-center justify-center w-10 h-10 rounded-xl bg-accent/10">
                <Megaphone className="h-4 w-4 text-accent" />
              </div>
              <div>
                <h2 className="text-sm font-bold text-fg">Yayın gönder</h2>
                <p className="text-xs font-mono text-fg-muted mt-0.5">Tüm kullanıcılara sistem bildirimi</p>
              </div>
            </div>
            <div className="space-y-3">
              <label className="block">
                <span className="text-[11px] font-semibold uppercase tracking-wider text-fg-muted">Başlık</span>
                <input type="text" value={title} onChange={(e) => setTitle(e.target.value)} maxLength={120} required
                  className="mt-1.5 w-full rounded-lg border border-border-default bg-bg-elevated px-3 py-2.5 text-sm text-fg focus:outline-none focus:border-accent/60 focus:shadow-[0_0_0_3px_rgba(99,102,241,0.12)] transition-all" />
              </label>
              <label className="block">
                <span className="text-[11px] font-semibold uppercase tracking-wider text-fg-muted">Mesaj</span>
                <textarea value={body} onChange={(e) => setBody(e.target.value)} rows={4} maxLength={2000} required
                  className="mt-1.5 w-full resize-none rounded-lg border border-border-default bg-bg-elevated px-3 py-2.5 text-sm text-fg focus:outline-none focus:border-accent/60 focus:shadow-[0_0_0_3px_rgba(99,102,241,0.12)] transition-all" />
              </label>
            </div>
            <div className="flex gap-2 mt-5">
              <button type="button" onClick={onClose} disabled={broadcast.isPending}
                className="flex-1 rounded-lg py-2.5 text-sm font-semibold text-fg border border-border-default bg-bg-base hover:bg-surface transition-all cursor-pointer disabled:opacity-50">
                Vazgeç
              </button>
              <motion.button type="submit" disabled={broadcast.isPending} whileTap={{ scale: 0.98 }}
                className="flex-1 relative flex items-center justify-center gap-2 rounded-lg py-2.5 text-sm font-semibold text-white overflow-hidden disabled:opacity-50 cursor-pointer border-none">
                <span aria-hidden className="absolute inset-0 bg-gradient-to-r from-accent via-accent-bright to-accent" />
                <span className="relative">{broadcast.isPending ? 'Gönderiliyor…' : 'Yayınla'}</span>
              </motion.button>
            </div>
          </motion.form>
        </div>
      )}
    </AnimatePresence>
  );
}

function ConversationList({ activeUser, onSelect, onBroadcast }) {
  const [page, setPage] = useState(0);
  const { data, isLoading } = useAdminConversations({ page, size: PAGE_SIZE });
  const items = data?.content ?? [];
  const totalPages = data?.totalPages ?? 0;

  return (
    <aside className="flex flex-col h-full border-r border-border-default bg-bg-elevated">
      <header className="relative flex items-center justify-between gap-2 px-4 py-3.5 border-b border-border-default shrink-0">
        <div className="flex items-center gap-2.5 min-w-0">
          <div className="relative flex items-center justify-center w-8 h-8 rounded-xl bg-gradient-to-br from-accent/25 to-accent/5 border border-accent/20 shrink-0">
            <Users className="h-3.5 w-3.5 text-accent" />
          </div>
          <div className="min-w-0">
            <h2 className="text-sm font-bold text-fg leading-tight">Sohbetler</h2>
            {data && (
              <p className="text-[10px] font-mono text-fg-muted mt-0.5">{data.totalElements} kullanıcı</p>
            )}
          </div>
        </div>
        <motion.button onClick={onBroadcast} whileTap={{ scale: 0.94 }} whileHover={{ y: -1 }}
          transition={{ type: 'spring', stiffness: 400, damping: 25 }}
          className="relative flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-[11px] font-bold text-white overflow-hidden border-none cursor-pointer shadow-md shadow-accent/30">
          <span aria-hidden className="absolute inset-0 bg-gradient-to-br from-accent via-accent-bright to-accent" />
          <Megaphone className="relative h-3 w-3" /> <span className="relative">Yayın</span>
        </motion.button>
      </header>
      <div className="flex-1 overflow-y-auto" style={{ scrollbarWidth: 'thin' }}>
        {isLoading ? (
          <div className="flex items-center justify-center py-10 text-fg-muted">
            <Loader2 className="h-4 w-4 animate-spin text-accent" />
          </div>
        ) : items.length === 0 ? (
          <div className="px-4 py-12 text-center text-xs text-fg-subtle">Henüz sohbet yok</div>
        ) : (
          <ul className="divide-y divide-border-default">
            {items.map((c) => {
              const active = activeUser === c.userSub;
              return (
                <li key={c.userSub}>
                  <button onClick={() => onSelect(c.userSub)} type="button"
                    className={`group relative w-full text-left px-3.5 py-3 transition-all flex items-start gap-3 cursor-pointer border-none ${
                      active ? 'bg-gradient-to-r from-accent/15 to-accent/5' : 'bg-transparent hover:bg-surface/60'}`}>
                    {active && (
                      <span aria-hidden className="absolute left-0 top-1/2 -translate-y-1/2 w-[3px] h-7 rounded-r-full bg-gradient-to-b from-accent to-accent-bright" />
                    )}
                    <span className={`relative flex items-center justify-center w-10 h-10 rounded-2xl text-[12px] font-mono font-bold shrink-0 uppercase border transition-all ${
                      active
                        ? 'bg-gradient-to-br from-accent/40 to-accent/10 border-accent/40 text-accent shadow-sm shadow-accent/20'
                        : 'bg-gradient-to-br from-accent/20 to-accent/5 border-accent/15 text-accent group-hover:border-accent/30'}`}>
                      {c.userSub.slice(0, 2)}
                    </span>
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2">
                        <span className={`text-[12.5px] font-bold truncate ${active ? 'text-accent' : 'text-fg'}`}>
                          {shortSub(c.userSub)}
                        </span>
                        {c.closed && (
                          <span className="flex items-center gap-0.5 text-[9px] font-mono uppercase tracking-wide px-1.5 py-0.5 rounded-md bg-warning/15 text-warning shrink-0 font-bold">
                            <Lock className="h-2 w-2" /> kapalı
                          </span>
                        )}
                      </div>
                      <p className="mt-0.5 text-[11px] text-fg-muted truncate leading-snug">{c.lastBody}</p>
                      <p className="mt-1 text-[10px] font-mono text-fg-subtle">{relTime(c.lastSentAt)}</p>
                    </div>
                  </button>
                </li>
              );
            })}
          </ul>
        )}
      </div>
      {totalPages > 1 && (
        <footer className="flex items-center justify-between px-3 py-2 border-t border-border-default text-[11px] text-fg-muted shrink-0">
          <button onClick={() => setPage((p) => Math.max(0, p - 1))} disabled={page === 0}
            className="flex items-center gap-1 px-2 py-1 rounded-md hover:text-fg hover:bg-surface transition-colors bg-transparent border-none cursor-pointer disabled:opacity-30 disabled:cursor-not-allowed">
            <ChevronLeft className="h-3 w-3" /> Önceki
          </button>
          <span className="font-mono">{page + 1} / {totalPages}</span>
          <button onClick={() => setPage((p) => p + 1)} disabled={page + 1 >= totalPages}
            className="flex items-center gap-1 px-2 py-1 rounded-md hover:text-fg hover:bg-surface transition-colors bg-transparent border-none cursor-pointer disabled:opacity-30 disabled:cursor-not-allowed">
            Sonraki <ChevronRight className="h-3 w-3" />
          </button>
        </footer>
      )}
    </aside>
  );
}

function ThreadPane({ userSub, onBack, onAfterDelete }) {
  const { data: thread, isLoading } = useAdminConversation(userSub);
  const sendMutation = useSendAdminMessage();
  const closeMutation = useCloseConversation();
  const reopenMutation = useReopenConversation();
  const deleteMutation = useDeleteConversation();
  const [body, setBody] = useState('');
  const [confirmAction, setConfirmAction] = useState(null);
  const scrollRef = useRef(null);

  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [thread?.messages?.length]);

  const handleSend = async (e) => {
    e.preventDefault();
    const trimmed = body.trim();
    if (!trimmed || sendMutation.isPending) return;
    try {
      await sendMutation.mutateAsync({ recipientSub: userSub, body: trimmed });
      setBody('');
    } catch (err) {
      toast.error(extractApiError(err, 'Mesaj gönderilemedi'));
    }
  };

  const handleClose = async () => {
    try {
      await closeMutation.mutateAsync(userSub);
      toast.success('Sohbet kapatıldı', `${shortSub(userSub)} artık yeni mesaj atamaz`);
      setConfirmAction(null);
    } catch (err) {
      toast.error(extractApiError(err, 'Kapatma başarısız'));
    }
  };

  const handleReopen = async () => {
    try {
      await reopenMutation.mutateAsync(userSub);
      toast.success('Sohbet yeniden açıldı', `${shortSub(userSub)} mesajlaşmaya devam edebilir`);
    } catch (err) {
      toast.error(extractApiError(err, 'Yeniden açma başarısız'));
    }
  };

  const handleDelete = async () => {
    try {
      await deleteMutation.mutateAsync(userSub);
      toast.success('Sohbet silindi', `${shortSub(userSub)} sohbeti tamamen kaldırıldı`);
      setConfirmAction(null);
      onAfterDelete?.();
    } catch (err) {
      toast.error(extractApiError(err, 'Silme başarısız'));
    }
  };

  if (!userSub) {
    return (
      <div className="flex-1 flex flex-col items-center justify-center text-center px-6 gap-3">
        <div className="relative">
          <span aria-hidden className="absolute inset-0 -m-3 rounded-full bg-accent/10 blur-2xl" />
          <span className="relative flex items-center justify-center w-14 h-14 rounded-2xl bg-gradient-to-br from-accent/20 to-accent/5 border border-accent/20">
            <MessageCircle className="h-6 w-6 text-accent" />
          </span>
        </div>
        <div className="max-w-xs">
          <p className="text-sm font-bold text-fg">Bir sohbet seç</p>
          <p className="text-[11px] text-fg-muted mt-1 leading-relaxed">Soldaki listeden kullanıcı seç, geçmişi görüp yanıt yaz veya yayın gönder.</p>
        </div>
      </div>
    );
  }

  return (
    <section className="flex-1 flex flex-col bg-bg-elevated min-h-0">
      <header className="relative flex items-center justify-between gap-2 px-4 py-3.5 border-b border-border-default shrink-0">
        <div className="flex items-center gap-2.5 min-w-0">
          <button onClick={onBack} className="lg:hidden flex items-center justify-center w-8 h-8 rounded-lg text-fg-muted hover:text-fg hover:bg-surface transition-colors bg-transparent border-none cursor-pointer">
            <ChevronLeft className="h-4 w-4" />
          </button>
          <div className="relative shrink-0">
            <span aria-hidden className="absolute inset-0 rounded-full bg-accent/15 blur-md -z-10" />
            <span className="relative flex items-center justify-center w-10 h-10 rounded-2xl bg-gradient-to-br from-accent/35 to-accent/5 border border-accent/25 text-[12px] font-mono font-bold text-accent uppercase">
              {userSub.slice(0, 2)}
            </span>
          </div>
          <div className="min-w-0">
            <div className="text-sm font-bold text-fg truncate" title={userSub}>{shortSub(userSub)}</div>
            {thread?.closed ? (
              <div className="flex items-center gap-1 text-[10px] font-mono text-warning">
                <ShieldOff className="h-2.5 w-2.5" />
                <span>Kapatıldı · {relTime(thread.closedAt)}</span>
              </div>
            ) : (
              <div className="text-[10px] font-mono text-fg-muted">Aktif sohbet</div>
            )}
          </div>
        </div>
        <div className="flex items-center gap-1">
          {thread?.closed ? (
            <button onClick={handleReopen} disabled={reopenMutation.isPending} title="Sohbeti yeniden aç"
              className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-[11px] font-semibold text-fg-muted hover:text-success hover:bg-success/10 transition-colors bg-transparent border-none cursor-pointer disabled:opacity-50">
              <Unlock className="h-3 w-3" /> Yeniden aç
            </button>
          ) : (
            <button onClick={() => setConfirmAction('close')} title="Sohbeti kapat"
              className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-[11px] font-semibold text-fg-muted hover:text-warning hover:bg-warning/10 transition-colors bg-transparent border-none cursor-pointer">
              <Lock className="h-3 w-3" /> Kapat
            </button>
          )}
          <button onClick={() => setConfirmAction('delete')} title="Sohbeti sil"
            className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-[11px] font-semibold text-fg-muted hover:text-danger hover:bg-danger/10 transition-colors bg-transparent border-none cursor-pointer">
            <Trash2 className="h-3 w-3" /> Sil
          </button>
        </div>
      </header>

      <div ref={scrollRef} className="flex-1 overflow-y-auto px-4 py-4 space-y-3" style={{ scrollbarWidth: 'thin' }}>
        {isLoading ? (
          <div className="flex items-center justify-center py-10 text-fg-muted">
            <Loader2 className="h-4 w-4 animate-spin text-accent" />
          </div>
        ) : (thread?.messages ?? []).length === 0 ? (
          <div className="text-center py-10 text-xs text-fg-subtle">Bu sohbette henüz mesaj yok.</div>
        ) : (
          (thread.messages).map((m) => {
            const fromAdmin = m.direction === 'ADMIN_TO_USER';
            return (
              <motion.div key={m.id}
                initial={{ opacity: 0, y: 6 }} animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.2 }}
                className={`flex ${fromAdmin ? 'justify-end' : 'justify-start'}`}>
                <div className={`max-w-[78%] relative rounded-2xl px-4 py-2.5 text-sm leading-relaxed border ${
                  fromAdmin
                    ? 'bg-gradient-to-br from-accent/15 to-accent/5 border-accent/30 text-fg shadow-sm shadow-accent/10'
                    : 'bg-surface border-border-default text-fg'}`}>
                  {!fromAdmin && (
                    <div className="text-[10px] font-mono uppercase tracking-wider text-fg-muted mb-1.5">Kullanıcı</div>
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
          {thread?.closed ? (
            <div className="flex-1 flex items-center gap-2 text-[11px] text-warning bg-warning/10 border border-warning/30 rounded-xl px-3 py-2.5">
              <ShieldOff className="h-3.5 w-3.5 shrink-0" />
              <span>Sohbet kapalı — kullanıcı yeni mesaj atamaz. Yeniden açmak için üst aksiyon barı.</span>
            </div>
          ) : (
            <textarea value={body} onChange={(e) => setBody(e.target.value)}
              onKeyDown={(e) => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); handleSend(e); } }}
              placeholder="Kullanıcıya mesaj yaz…" rows={2} maxLength={2000}
              className="flex-1 resize-none rounded-xl border border-border-default bg-bg-base/60 px-3 py-2.5 text-sm text-fg placeholder:text-fg-subtle focus:outline-none focus:border-accent/60 focus:shadow-[0_0_0_3px_rgba(99,102,241,0.15)] focus:bg-bg-base transition-all" />
          )}
          <motion.button type="submit" disabled={!body.trim() || sendMutation.isPending || thread?.closed}
            whileTap={{ scale: 0.94 }} whileHover={{ y: -1 }}
            transition={{ type: 'spring', stiffness: 400, damping: 25 }}
            className="relative shrink-0 flex items-center justify-center w-11 h-11 rounded-xl text-white overflow-hidden disabled:opacity-40 disabled:cursor-not-allowed border-none cursor-pointer shadow-lg shadow-accent/30">
            <span aria-hidden className="absolute inset-0 bg-gradient-to-br from-accent via-accent-bright to-accent" />
            {sendMutation.isPending ? (
              <Loader2 className="relative h-4 w-4 animate-spin" />
            ) : (
              <Send className="relative h-4 w-4" />
            )}
          </motion.button>
        </div>
        {!thread?.closed && (
          <div className="mt-1.5 flex items-center justify-between text-[10px] font-mono text-fg-subtle px-1">
            <span>Enter gönder · Shift+Enter satır</span>
            <span className={body.length > 1800 ? 'text-warning' : ''}>{body.length}/2000</span>
          </div>
        )}
      </form>

      <ConfirmModal
        open={confirmAction === 'close'}
        title="Sohbeti kapat?"
        body="Kullanıcı bu sohbete yeni mesaj gönderemez. Mevcut mesajlar kalır, geçmişi okumaya devam edebilir."
        confirmLabel="Kapat"
        pending={closeMutation.isPending}
        onCancel={() => setConfirmAction(null)}
        onConfirm={handleClose}
      />
      <ConfirmModal
        open={confirmAction === 'delete'}
        danger
        title="Sohbeti sil?"
        body="Bu sohbetin tüm mesajları geri alınamaz şekilde silinir. Onay veriyor musun?"
        confirmLabel="Sil"
        pending={deleteMutation.isPending}
        onCancel={() => setConfirmAction(null)}
        onConfirm={handleDelete}
      />
    </section>
  );
}

export default function AdminMessagesPage() {
  const [activeUser, setActiveUser] = useState(null);
  const [broadcastOpen, setBroadcastOpen] = useState(false);

  return (
    <motion.div
      initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.3 }}
      className="h-[calc(100vh-2rem)] p-3 sm:p-4"
    >
      <div className="relative rounded-3xl border border-border-default bg-bg-elevated card-hover overflow-hidden h-full grid grid-cols-1 lg:grid-cols-[340px_1fr]">
        <span aria-hidden className="absolute inset-x-0 top-0 h-[2px] bg-gradient-to-r from-transparent via-accent/60 to-transparent z-10" />
        <div className={`${activeUser ? 'hidden lg:flex' : 'flex'} flex-col min-h-0`}>
          <ConversationList
            activeUser={activeUser}
            onSelect={setActiveUser}
            onBroadcast={() => setBroadcastOpen(true)}
          />
        </div>
        <div className={`${activeUser ? 'flex' : 'hidden lg:flex'} flex-col min-h-0`}>
          <ThreadPane
            userSub={activeUser}
            onBack={() => setActiveUser(null)}
            onAfterDelete={() => setActiveUser(null)}
          />
        </div>
      </div>
      <BroadcastModal open={broadcastOpen} onClose={() => setBroadcastOpen(false)} />
    </motion.div>
  );
}
