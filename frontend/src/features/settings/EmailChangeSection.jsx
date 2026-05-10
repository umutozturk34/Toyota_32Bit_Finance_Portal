import { useState } from 'react';
import { motion } from 'framer-motion';
import { AnimatePresence } from 'framer-motion';
import { Mail, ShieldCheck, X as Cancel } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import {
  usePendingEmailChange,
  useInitiateEmailChange,
  useConfirmEmailChange,
  useCancelEmailChange,
} from '../../shared/hooks/useEmailChange';
import { useAuth } from '../auth/AuthContext';
import { toast } from '../../shared/components/feedback/Toast';

function CurrentEmailRow({ email }) {
  const { t } = useTranslation();
  return (
    <div className="flex items-center gap-2 rounded-lg border border-border-default bg-bg-elevated/60 px-3 py-2.5">
      <Mail className="h-3.5 w-3.5 text-accent shrink-0" />
      <span className="text-[11px] uppercase tracking-wider text-fg-subtle font-semibold">{t('emailChange.currentLabel')}</span>
      <span className="text-xs font-mono text-fg truncate">{email || '—'}</span>
    </div>
  );
}

function InitiateForm({ currentEmail, onInitiated }) {
  const { t } = useTranslation();
  const [draft, setDraft] = useState('');
  const initiate = useInitiateEmailChange();

  const handleSubmit = async (e) => {
    e.preventDefault();
    const trimmed = draft.trim();
    if (!trimmed || initiate.isPending) return;
    if (trimmed.toLowerCase() === (currentEmail || '').toLowerCase()) {
      toast.error(t('emailChange.toast.sameAddress'), t('emailChange.toast.sameAddressDesc'));
      return;
    }
    try {
      await initiate.mutateAsync(trimmed);
      toast.success(t('emailChange.toast.codeSent'), t('emailChange.toast.codeSentDesc', { email: currentEmail }));
      setDraft('');
      onInitiated?.();
    } catch (err) {
      toast.error(t('emailChange.toast.initiateError'), err?.response?.data?.message || t('emailChange.toast.codeNotSent'));
    }
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-2">
      <div className="relative group/input">
        <span className="pointer-events-none absolute inset-y-0 left-3 flex items-center text-fg-subtle group-focus-within/input:text-accent transition-colors">
          <Mail className="h-3.5 w-3.5" />
        </span>
        <input
          type="email"
          required
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          placeholder={t('emailChange.placeholder')}
          disabled={initiate.isPending}
          className="w-full rounded-lg border border-border-default bg-bg-elevated/80 backdrop-blur-sm pl-9 pr-3 py-2 text-xs text-fg placeholder:text-fg-subtle focus:outline-none focus:border-accent/60 focus:bg-bg-elevated focus:shadow-[0_0_0_3px_rgba(99,102,241,0.12),inset_0_1px_0_rgba(255,255,255,0.04)] transition-all disabled:opacity-50"
        />
      </div>
      <motion.button
        type="submit"
        disabled={initiate.isPending || !draft.trim()}
        whileTap={{ scale: 0.98 }}
        className="relative w-full flex items-center justify-between gap-2 rounded-lg overflow-hidden px-3 py-2.5 text-xs font-semibold text-white cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed group/cta"
      >
        <span aria-hidden className="absolute inset-0 bg-gradient-to-r from-accent via-accent-bright to-accent transition-opacity group-hover/cta:opacity-90 group-disabled/cta:from-bg-elevated group-disabled/cta:via-bg-elevated group-disabled/cta:to-bg-elevated" />
        <span aria-hidden className="absolute inset-0 opacity-0 group-hover/cta:opacity-100 transition-opacity bg-[radial-gradient(120%_120%_at_50%_-20%,rgba(255,255,255,0.18),transparent_60%)]" />
        <span aria-hidden className="absolute inset-x-0 top-0 h-px bg-white/20" />
        <span className="relative flex items-center gap-2">
          <ShieldCheck className="h-3.5 w-3.5" />
          {t('emailChange.sendCode')}
        </span>
        <span className="relative flex items-center gap-1 text-[10px] tracking-wide opacity-90">
          {initiate.isPending ? t('emailChange.sending') : t('emailChange.codeArrow')}
        </span>
      </motion.button>
      <p className="text-[10px] text-fg-subtle leading-relaxed px-1 mt-1.5">
        {t('emailChange.sendHintBefore')} <span className="font-mono text-fg-muted">{currentEmail}</span> {t('emailChange.sendHintAfter')}
      </p>
    </form>
  );
}

function ConfirmForm({ pending, onCleared, onConfirmed }) {
  const { t, i18n } = useTranslation();
  const [code, setCode] = useState('');
  const confirm = useConfirmEmailChange();
  const cancel = useCancelEmailChange();

  const handleConfirm = async (e) => {
    e.preventDefault();
    if (!/^\d{6}$/.test(code) || confirm.isPending) return;
    try {
      await confirm.mutateAsync(code);
      await onConfirmed?.();
      toast.success(t('emailChange.toast.updated'), t('emailChange.toast.updatedDesc', { email: pending.newEmail }));
      setCode('');
      onCleared?.();
    } catch (err) {
      toast.error(t('emailChange.toast.invalidCode'), err?.response?.data?.message || t('emailChange.toast.codeNotVerified'));
    }
  };

  const handleCancel = async () => {
    try {
      await cancel.mutateAsync();
      toast.success(t('emailChange.toast.cancelled'), t('emailChange.toast.cancelledDesc'));
      onCleared?.();
    } catch (err) {
      toast.error(t('emailChange.toast.cancelError'), err?.response?.data?.message || t('emailChange.toast.tryAgain'));
    }
  };

  const localeTag = i18n.language === 'en' ? 'en-US' : 'tr-TR';
  const expiresAtFormatted = pending.expiresAt
    ? new Date(pending.expiresAt).toLocaleTimeString(localeTag, { hour: '2-digit', minute: '2-digit' })
    : null;

  return (
    <div className="space-y-2.5">
      <div className="rounded-lg border border-accent/30 bg-accent/5 px-3 py-2.5 space-y-1">
        <div className="flex items-center gap-1.5 text-[10px] uppercase tracking-wider font-semibold text-accent">
          <ShieldCheck className="h-3 w-3" />
          <span>{t('emailChange.awaitingConfirmation')}</span>
        </div>
        <p className="text-xs font-mono text-fg break-all">{pending.newEmail}</p>
        {expiresAtFormatted && (
          <p className="text-[10px] text-fg-subtle">
            {t('emailChange.expiresPrefix')} <span className="font-mono text-fg-muted">{expiresAtFormatted}</span>{t('emailChange.expiresSuffix')}
          </p>
        )}
      </div>
      <form onSubmit={handleConfirm} className="space-y-2">
        <input
          type="text"
          inputMode="numeric"
          pattern="\d{6}"
          maxLength={6}
          required
          value={code}
          onChange={(e) => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
          placeholder="••••••"
          disabled={confirm.isPending}
          className="w-full rounded-lg border border-border-default bg-bg-elevated/80 backdrop-blur-sm px-3 py-2.5 text-center text-base font-mono tracking-[0.5em] text-fg placeholder:text-fg-subtle focus:outline-none focus:border-accent/60 focus:bg-bg-elevated focus:shadow-[0_0_0_3px_rgba(99,102,241,0.12),inset_0_1px_0_rgba(255,255,255,0.04)] transition-all disabled:opacity-50"
        />
        <div className="flex gap-2">
          <button
            type="button"
            onClick={handleCancel}
            disabled={cancel.isPending || confirm.isPending}
            className="flex-1 flex items-center justify-center gap-1.5 rounded-lg border border-border-default bg-bg-elevated px-3 py-2.5 text-xs font-medium text-fg-muted hover:text-danger hover:border-danger/40 hover:bg-danger/5 transition-colors cursor-pointer disabled:opacity-40"
          >
            <Cancel className="h-3 w-3" />
            {t('emailChange.cancel')}
          </button>
          <motion.button
            type="submit"
            disabled={confirm.isPending || !/^\d{6}$/.test(code)}
            whileTap={{ scale: 0.98 }}
            className="flex-1 relative flex items-center justify-center gap-1.5 rounded-lg overflow-hidden px-3 py-2.5 text-xs font-semibold text-white cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed group/cta"
          >
            <span aria-hidden className="absolute inset-0 bg-gradient-to-r from-accent via-accent-bright to-accent transition-opacity group-hover/cta:opacity-90" />
            <span aria-hidden className="absolute inset-x-0 top-0 h-px bg-white/20" />
            <span className="relative flex items-center gap-1.5">
              <ShieldCheck className="h-3 w-3" />
              {confirm.isPending ? t('emailChange.verifying') : t('emailChange.confirm')}
            </span>
          </motion.button>
        </div>
      </form>
    </div>
  );
}

export default function EmailChangeSection() {
  const { user, refreshUser } = useAuth();
  const { data: pending, refetch } = usePendingEmailChange();

  return (
    <div className="space-y-2.5">
      <CurrentEmailRow email={user?.email} />
      <AnimatePresence mode="wait" initial={false}>
        {pending ? (
          <motion.div
            key="confirm"
            initial={{ opacity: 0, y: 4 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -4 }}
            transition={{ duration: 0.18 }}
          >
            <ConfirmForm pending={pending} onCleared={refetch} onConfirmed={refreshUser} />
          </motion.div>
        ) : (
          <motion.div
            key="initiate"
            initial={{ opacity: 0, y: 4 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -4 }}
            transition={{ duration: 0.18 }}
          >
            <InitiateForm currentEmail={user?.email} onInitiated={refetch} />
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
