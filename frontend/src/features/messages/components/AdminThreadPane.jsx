import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { motion } from 'framer-motion';
import {
  MessageCircle, Loader2, Lock, Unlock, Trash2, ChevronLeft, ShieldOff,
} from 'lucide-react';
import {
  useAdminConversation, useSendAdminMessage,
  useCloseConversation, useReopenConversation, useDeleteConversation,
} from '../../../shared/hooks/useMessages';
import { containerVariants } from '../../../shared/utils/animations';
import { toast } from '../../../shared/components/feedback/Toast';
import { extractApiError } from '../../../shared/utils/apiError';
import { relTime, shortSub } from '../util';
import MessageBubble from './MessageBubble';
import Composer from './Composer';
import ConfirmDialog from '../../../shared/components/modal/ConfirmDialog';

export default function AdminThreadPane({ userSub, onBack, onAfterDelete }) {
  const { t } = useTranslation();
  const { data: thread, isLoading } = useAdminConversation(userSub);
  const sendMutation = useSendAdminMessage();
  const closeMutation = useCloseConversation();
  const reopenMutation = useReopenConversation();
  const deleteMutation = useDeleteConversation();
  const [body, setBody] = useState('');
  const [confirmAction, setConfirmAction] = useState(null);
  const scrollRef = useRef(null);

  useEffect(() => {
    if (scrollRef.current) scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
  }, [thread?.messages?.length]);

  const handleSend = async (e) => {
    e.preventDefault();
    const trimmed = body.trim();
    if (!trimmed || sendMutation.isPending) return;
    try {
      await sendMutation.mutateAsync({ recipientSub: userSub, body: trimmed });
      setBody('');
    } catch (err) {
      toast.error(extractApiError(err, t('userMessages.sendFailed')));
    }
  };

  const runMutation = async (mutation, successTitle, successBody, after) => {
    try {
      await mutation.mutateAsync(userSub);
      toast.success(successTitle, successBody);
      setConfirmAction(null);
      after?.();
    } catch (err) {
      toast.error(extractApiError(err, t('adminThread.actionFailed')));
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
          <p className="text-sm font-bold text-fg">{t('adminThread.pickConversation')}</p>
          <p className="text-[11px] text-fg-muted mt-1 leading-relaxed">{t('adminThread.pickConversationHint')}</p>
        </div>
      </div>
    );
  }

  const username = thread?.username;
  const email = thread?.email;

  return (
    <section className="flex-1 flex flex-col bg-bg-elevated min-h-0">
      <header className="relative flex items-center justify-between gap-2 px-4 py-3.5 border-b border-border-default shrink-0">
        <div className="flex items-center gap-2.5 min-w-0">
          <button
            onClick={onBack}
            className="lg:hidden flex items-center justify-center w-8 h-8 rounded-lg text-fg-muted hover:text-fg hover:bg-surface transition-colors bg-transparent border-none cursor-pointer"
          >
            <ChevronLeft className="h-4 w-4" />
          </button>
          <div className="relative shrink-0">
            <span aria-hidden className="absolute inset-0 rounded-full bg-accent/15 blur-md -z-10" />
            <span className="relative flex items-center justify-center w-10 h-10 rounded-2xl bg-gradient-to-br from-accent/35 to-accent/5 border border-accent/25 text-[12px] font-mono font-bold text-accent uppercase">
              {userSub.slice(0, 2)}
            </span>
          </div>
          <div className="min-w-0">
            <div className="text-sm font-bold text-fg truncate" title={userSub}>{username || userSub}</div>
            {username ? (
              <>
                {email && (
                  <div className="text-[10px] font-mono text-fg-muted truncate" title={email}>
                    {email}
                  </div>
                )}
                <div className="mt-0.5 text-[9px] font-mono flex items-center gap-1 min-w-0" title={userSub}>
                  <span className="text-accent uppercase tracking-[0.14em] shrink-0">id</span>
                  <span className="text-fg-subtle truncate">{userSub}</span>
                </div>
              </>
            ) : thread?.closed ? (
              <div className="flex items-center gap-1 text-[10px] font-mono text-warning">
                <ShieldOff className="h-2.5 w-2.5" />
                <span>{t('adminThread.closedAt', { time: relTime(thread.closedAt) })}</span>
              </div>
            ) : (
              <div className="text-[10px] font-mono text-fg-muted">{t('adminThread.activeConversation')}</div>
            )}
          </div>
        </div>
        <div className="flex items-center gap-1 shrink-0">
          {thread?.closed ? (
            <button
              onClick={() => runMutation(reopenMutation, t('adminThread.reopenedTitle'), t('adminThread.reopenedBody', { user: shortSub(userSub) }))}
              disabled={reopenMutation.isPending}
              title={t('adminThread.reopenTooltip')}
              className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-[11px] font-semibold text-fg-muted hover:text-success hover:bg-success/10 transition-colors bg-transparent border-none cursor-pointer disabled:opacity-50"
            >
              <Unlock className="h-3 w-3" /> {t('adminThread.reopen')}
            </button>
          ) : (
            <button
              onClick={() => setConfirmAction('close')}
              title={t('adminThread.closeTooltip')}
              className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-[11px] font-semibold text-fg-muted hover:text-warning hover:bg-warning/10 transition-colors bg-transparent border-none cursor-pointer"
            >
              <Lock className="h-3 w-3" /> {t('adminThread.close')}
            </button>
          )}
          <button
            onClick={() => setConfirmAction('delete')}
            title={t('adminThread.deleteTooltip')}
            className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-[11px] font-semibold text-fg-muted hover:text-danger hover:bg-danger/10 transition-colors bg-transparent border-none cursor-pointer"
          >
            <Trash2 className="h-3 w-3" /> {t('common.delete')}
          </button>
        </div>
      </header>

      <div ref={scrollRef} className="flex-1 overflow-y-auto px-4 py-4 space-y-3" style={{ scrollbarWidth: 'thin' }}>
        {isLoading ? (
          <div className="flex items-center justify-center py-10 text-fg-muted">
            <Loader2 className="h-4 w-4 animate-spin text-accent" />
          </div>
        ) : (thread?.messages ?? []).length === 0 ? (
          <div className="text-center py-10 text-xs text-fg-subtle">{t('adminThread.emptyThread')}</div>
        ) : (
          <motion.div variants={containerVariants(0.03)} initial="hidden" animate="show" className="space-y-3">
            {thread.messages.map((m) => (
              <MessageBubble
                key={m.id}
                message={m}
                leftSide={m.direction === 'USER_TO_ADMIN'}
                label={m.direction === 'USER_TO_ADMIN' ? t('adminThread.userLabel') : null}
              />
            ))}
          </motion.div>
        )}
      </div>

      <Composer
        value={body}
        onChange={setBody}
        onSubmit={handleSend}
        disabled={!body.trim() || sendMutation.isPending || thread?.closed}
        placeholder={t('adminThread.composerPlaceholder')}
        pending={sendMutation.isPending}
        hint={thread?.closed ? t('adminThread.composerClosedHint') : null}
      />

      <ConfirmDialog
        open={confirmAction === 'close'}
        variant="warning"
        title={t('adminThread.closeConfirmTitle')}
        message={t('adminThread.closeConfirmBody')}
        confirmLabel={t('adminThread.close')}
        loading={closeMutation.isPending}
        onCancel={() => setConfirmAction(null)}
        onConfirm={() => runMutation(closeMutation, t('adminThread.closedTitle'), t('adminThread.closedBody', { user: shortSub(userSub) }))}
      />
      <ConfirmDialog
        open={confirmAction === 'delete'}
        variant="danger"
        title={t('adminThread.deleteConfirmTitle')}
        message={t('adminThread.deleteConfirmBody')}
        confirmLabel={t('common.delete')}
        loading={deleteMutation.isPending}
        onCancel={() => setConfirmAction(null)}
        onConfirm={() => runMutation(deleteMutation, t('adminThread.deletedTitle'), t('adminThread.deletedBody', { user: shortSub(userSub) }), onAfterDelete)}
      />
    </section>
  );
}
