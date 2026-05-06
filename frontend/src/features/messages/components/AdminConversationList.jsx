import { useState } from 'react';
import { motion } from 'framer-motion';
import { Users, Loader2, MessagesSquare, Lock, ChevronLeft, ChevronRight } from 'lucide-react';
import { useAdminConversations } from '../../../shared/hooks/useMessages';
import { containerVariants, cardVariants } from '../../../shared/utils/animations';
import { PAGE_SIZE, relTime, shortSub } from '../util';

export default function AdminConversationList({ activeUser, onSelect }) {
  const [page, setPage] = useState(0);
  const { data, isLoading } = useAdminConversations({ page, size: PAGE_SIZE });
  const items = data?.content ?? [];
  const totalPages = data?.totalPages ?? 0;

  return (
    <aside className="flex flex-col h-full border-r border-border-default bg-bg-elevated min-h-0">
      <header className="flex items-center gap-2.5 px-4 py-3.5 border-b border-border-default shrink-0">
        <div className="flex items-center justify-center w-8 h-8 rounded-xl bg-gradient-to-br from-accent/25 to-accent/5 border border-accent/20 shrink-0">
          <Users className="h-3.5 w-3.5 text-accent" />
        </div>
        <div className="min-w-0">
          <h2 className="text-sm font-bold text-fg leading-tight">Sohbetler</h2>
          {data && <p className="text-[10px] font-mono text-fg-muted mt-0.5">{data.totalElements} kullanıcı</p>}
        </div>
      </header>
      <div className="flex-1 overflow-y-auto px-3 py-3" style={{ scrollbarWidth: 'thin' }}>
        {isLoading ? (
          <div className="flex items-center justify-center py-10 text-fg-muted">
            <Loader2 className="h-4 w-4 animate-spin text-accent" />
          </div>
        ) : items.length === 0 ? (
          <div className="flex flex-col items-center justify-center gap-3 py-16 text-center px-4">
            <span className="flex items-center justify-center w-12 h-12 rounded-2xl bg-gradient-to-br from-accent/15 to-accent/5 border border-accent/15">
              <MessagesSquare className="h-5 w-5 text-fg-subtle" />
            </span>
            <p className="text-xs font-semibold text-fg-muted">Henüz sohbet yok</p>
            <p className="text-[11px] text-fg-subtle leading-relaxed max-w-[200px]">Kullanıcı bir mesaj gönderdiğinde burada görünecek.</p>
          </div>
        ) : (
          <motion.ul variants={containerVariants(0.04)} initial="hidden" animate="show" className="space-y-2">
            {items.map((c) => {
              const active = activeUser === c.userSub;
              return (
                <motion.li
                  key={c.userSub}
                  variants={cardVariants}
                  whileHover={{ y: -1 }}
                  transition={{ type: 'spring', stiffness: 400, damping: 28 }}
                >
                  <button
                    onClick={() => onSelect(c.userSub)}
                    type="button"
                    className={`group relative w-full text-left p-3.5 rounded-2xl border transition-all flex items-start gap-3 cursor-pointer overflow-hidden ${
                      active
                        ? 'bg-gradient-to-br from-accent/15 to-accent/[0.03] border-accent/40 shadow-md shadow-accent/15'
                        : 'bg-bg-base/40 border-border-default hover:border-border-hover hover:bg-surface/40'
                    }`}
                  >
                    {active && <span aria-hidden className="absolute inset-x-0 top-0 h-[1px] bg-gradient-to-r from-transparent via-accent/60 to-transparent" />}
                    <span
                      className={`relative flex items-center justify-center w-11 h-11 rounded-2xl text-[12px] font-mono font-bold uppercase border transition-all shrink-0 ${
                        active
                          ? 'bg-gradient-to-br from-accent/40 to-accent/10 border-accent/50 text-accent shadow-sm shadow-accent/30'
                          : 'bg-gradient-to-br from-accent/20 to-accent/5 border-accent/15 text-accent group-hover:border-accent/30'
                      }`}
                    >
                      {c.userSub.slice(0, 2)}
                    </span>
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center justify-between gap-2">
                        <span
                          className={`text-[13px] font-bold truncate ${active ? 'text-accent' : 'text-fg'}`}
                          title={c.userSub}
                        >
                          {c.username || shortSub(c.userSub)}
                        </span>
                        <span className="text-[10px] font-mono text-fg-subtle shrink-0">{relTime(c.lastSentAt)}</span>
                      </div>
                      {c.username && (
                        <p className="mt-0.5 text-[10px] font-mono text-fg-subtle truncate">{shortSub(c.userSub)}</p>
                      )}
                      <p className="mt-0.5 text-[11px] text-fg-muted truncate leading-snug">{c.lastBody}</p>
                      {c.closed && (
                        <span className="mt-1.5 inline-flex items-center gap-0.5 text-[9px] font-mono uppercase tracking-wide px-1.5 py-0.5 rounded-md bg-warning/15 text-warning font-bold border border-warning/20">
                          <Lock className="h-2 w-2" /> kapalı
                        </span>
                      )}
                    </div>
                  </button>
                </motion.li>
              );
            })}
          </motion.ul>
        )}
      </div>
      {totalPages > 1 && (
        <footer className="flex items-center justify-between px-3 py-2 border-t border-border-default text-[11px] text-fg-muted shrink-0">
          <button
            onClick={() => setPage((p) => Math.max(0, p - 1))}
            disabled={page === 0}
            className="flex items-center gap-1 px-2 py-1 rounded-md hover:text-fg hover:bg-surface transition-colors bg-transparent border-none cursor-pointer disabled:opacity-30 disabled:cursor-not-allowed"
          >
            <ChevronLeft className="h-3 w-3" /> Önceki
          </button>
          <span className="font-mono">{page + 1} / {totalPages}</span>
          <button
            onClick={() => setPage((p) => p + 1)}
            disabled={page + 1 >= totalPages}
            className="flex items-center gap-1 px-2 py-1 rounded-md hover:text-fg hover:bg-surface transition-colors bg-transparent border-none cursor-pointer disabled:opacity-30 disabled:cursor-not-allowed"
          >
            Sonraki <ChevronRight className="h-3 w-3" />
          </button>
        </footer>
      )}
    </aside>
  );
}
