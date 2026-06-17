import { useCallback, useEffect, useRef, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { motion, AnimatePresence } from 'framer-motion';
import { ChevronDown, Plus, Pencil, Trash2, Check, X, Wallet, CornerDownLeft, CandlestickChart, Landmark } from 'lucide-react';
import { useCreatePortfolio, useRenamePortfolio, useDeletePortfolio } from '../hooks/usePortfolioData';
import { portfolioService } from '../services/portfolioService';
import { fixedIncomeService } from '../services/fixedIncomeService';
import { useMoney } from '../../../shared/hooks/useMoney';
import { extractApiError } from '../../../shared/utils/apiError';
import { portfolioName } from '../../../shared/utils/portfolioName';
import ConfirmDialog from '../../../shared/components/modal/ConfirmDialog';

const PANEL_TRANSITION = { duration: 0.18, ease: [0.16, 1, 0.3, 1] };

const PORTFOLIO_TYPES = [
  { id: 'SPOT', labelKey: 'portfolio.typeSwitcher.spot', Icon: CandlestickChart },
  { id: 'FIXED', labelKey: 'portfolio.typeSwitcher.fixed', Icon: Landmark },
];

// Each portfolio's current TRY total, fetched lazily only while the dropdown is open (so opening the switcher costs
// at most N small summary calls, never on every render). Spot and fixed-income read their own summary endpoint.
function PortfolioTotal({ portfolio, enabled }) {
  const isFixed = portfolio.type === 'FIXED';
  const { format: money } = useMoney({ lockBase: true });
  const { data } = useQuery({
    queryKey: ['switcherTotal', portfolio.id, portfolio.type],
    queryFn: () => (isFixed ? fixedIncomeService.summary(portfolio.id) : portfolioService.getSummary(portfolio.id)),
    enabled,
    staleTime: 60 * 1000,
  });
  const total = data?.totalValueTry ?? data?.totalValue ?? data?.value ?? null;
  if (total == null) return null;
  return (
    <span className="block text-[11px] font-mono tabular-nums text-fg-muted truncate">{money(total, 'TRY')}</span>
  );
}

function TypeBadge({ type }) {
  const { t } = useTranslation();
  const meta = PORTFOLIO_TYPES.find((x) => x.id === type) ?? PORTFOLIO_TYPES[0];
  const Icon = meta.Icon;
  return (
    <span className="inline-flex items-center gap-1 shrink-0 rounded-md bg-bg-base/70 px-1.5 py-0.5 text-[10px] font-medium text-fg-muted ring-1 ring-inset ring-border-default/60">
      <Icon className="h-3 w-3" />
      <span className="hidden sm:inline">{t(meta.labelKey)}</span>
    </span>
  );
}

export default function PortfolioSwitcher({ portfolios = [], activeId, onSelect, autoCreate = false, onAutoCreateConsumed }) {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);
  const [renameTarget, setRenameTarget] = useState(null);
  const [renameValue, setRenameValue] = useState('');
  const [creating, setCreating] = useState(false);
  const [newName, setNewName] = useState('');
  const [newType, setNewType] = useState('SPOT');
  const [deleteTarget, setDeleteTarget] = useState(null);
  const [error, setError] = useState(null);
  const ref = useRef(null);

  // External trigger (e.g. the sidebar's "New portfolio" entry → /portfolio?new=1): open the dropdown straight
  // into create mode, then tell the parent to clear the flag so it fires once. The state is then local, so
  // clearing the flag doesn't re-close the form. (Legit prop→UI sync; the generic set-state-in-effect lint
  // false-positives here.)
  useEffect(() => {
    if (!autoCreate) return;
    /* eslint-disable react-hooks/set-state-in-effect */
    setOpen(true);
    setCreating(true);
    setNewType('SPOT');
    setError(null);
    /* eslint-enable react-hooks/set-state-in-effect */
    onAutoCreateConsumed?.();
  }, [autoCreate, onAutoCreateConsumed]);

  const create = useCreatePortfolio();
  const rename = useRenamePortfolio();
  const remove = useDeletePortfolio();

  const active = portfolios.find((p) => p.id === activeId) || portfolios[0];

  const closePanel = useCallback(() => {
    setOpen(false);
    setCreating(false);
    setNewType('SPOT');
    setRenameTarget(null);
    setDeleteTarget(null);
    setError(null);
  }, []);

  useEffect(() => {
    if (!open) return undefined;
    const onClickOutside = (e) => {
      if (ref.current && !ref.current.contains(e.target)) {
        closePanel();
      }
    };
    document.addEventListener('mousedown', onClickOutside);
    return () => document.removeEventListener('mousedown', onClickOutside);
  }, [open, closePanel]);

  const submitCreate = async () => {
    if (!newName.trim()) return;
    setError(null);
    try {
      const created = await create.mutateAsync({ name: newName.trim(), type: newType });
      setCreating(false);
      setNewName('');
      setNewType('SPOT');
      onSelect?.(created.id);
    } catch (e) {
      setError(extractApiError(e, t('portfolioSwitcher.createFailed', { defaultValue: 'Oluşturma başarısız' })));
    }
  };

  const submitRename = async () => {
    if (!renameValue.trim() || !renameTarget) return;
    setError(null);
    try {
      await rename.mutateAsync({ portfolioId: renameTarget.id, name: renameValue.trim() });
      setRenameTarget(null);
    } catch (e) {
      setError(extractApiError(e, t('portfolioSwitcher.renameFailed', { defaultValue: 'Yeniden adlandırma başarısız' })));
    }
  };

  const submitDelete = async () => {
    if (!deleteTarget) return;
    setError(null);
    try {
      await remove.mutateAsync(deleteTarget.id);
      if (deleteTarget.id === activeId) {
        const remaining = portfolios.find((p) => p.id !== deleteTarget.id);
        if (remaining) onSelect?.(remaining.id);
      }
      setDeleteTarget(null);
    } catch (e) {
      setError(extractApiError(e, t('portfolioSwitcher.deleteFailed', { defaultValue: 'Silme başarısız' })));
    }
  };

  return (
    <div ref={ref} className="relative">
      <motion.button
        type="button"
        onClick={() => setOpen((v) => !v)}
        whileHover={{ y: -1 }}
        whileTap={{ scale: 0.98 }}
        className={`group relative flex items-center gap-2.5 rounded-xl border bg-bg-elevated/80 backdrop-blur-sm px-3.5 py-2 transition-colors cursor-pointer ${
          open
            ? 'border-accent/50 shadow-[0_10px_24px_-12px_rgba(99,102,241,0.45)]'
            : 'border-border-default/80 hover:border-accent/40'
        }`}
      >
        <span className="flex items-center justify-center w-7 h-7 rounded-lg bg-gradient-to-br from-accent/25 to-accent/10 ring-1 ring-inset ring-accent/30">
          <Wallet className="h-3.5 w-3.5 text-accent" />
        </span>
        <span className="text-sm font-semibold text-fg max-w-[160px] sm:max-w-[240px] truncate tracking-tight">
          {active ? portfolioName(t, active) : t('portfolio.headerTitle')}
        </span>
        {portfolios.length > 1 && (
          <span className="text-[10px] font-mono tabular-nums text-fg-muted bg-bg-base/80 px-1.5 py-0.5 rounded border border-border-default/60">
            {String(portfolios.length).padStart(2, '0')}
          </span>
        )}
        <ChevronDown className={`h-3.5 w-3.5 text-fg-muted transition-transform duration-200 ${open ? 'rotate-180 text-accent' : ''}`} />
      </motion.button>

      <AnimatePresence>
        {open && (
          <motion.div
            initial={{ opacity: 0, scale: 0.97, y: -6 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.97, y: -6 }}
            transition={PANEL_TRANSITION}
            style={{ background: 'var(--color-bg-deep)', backdropFilter: 'blur(20px)', WebkitBackdropFilter: 'blur(20px)' }}
            className="absolute right-0 z-50 mt-2 w-[min(26rem,calc(100vw-1.5rem))] max-h-[calc(100dvh-180px)] overflow-y-auto overscroll-contain rounded-2xl border border-border-default shadow-[0_30px_60px_-20px_var(--color-shadow)]"
          >
            <div className="flex items-center justify-between px-3 pt-3 pb-2">
              <span className="text-[10px] uppercase tracking-[0.18em] font-semibold text-fg-muted">
                {t('portfolioSwitcher.heading', { defaultValue: 'Portfolios' })}
              </span>
              <span className="text-[10px] font-mono tabular-nums text-fg-subtle">
                {portfolios.length} {t('portfolioSwitcher.totalAbbr', { defaultValue: 'total' })}
              </span>
            </div>

            <div className="px-2 max-h-72 overflow-y-auto">
              {portfolios.map((p) => {
                const isActive = p.id === activeId;
                const isRenaming = renameTarget?.id === p.id;
                return (
                  <div key={p.id} className="relative">
                    {isRenaming ? (
                      <motion.div
                        initial={{ opacity: 0, x: -4 }}
                        animate={{ opacity: 1, x: 0 }}
                        transition={{ duration: 0.14 }}
                        className="px-2.5 py-2.5 rounded-lg bg-accent/8 ring-1 ring-inset ring-accent/30 my-0.5"
                      >
                        <div className="flex items-center gap-1.5">
                          <input
                            autoFocus
                            type="text"
                            value={renameValue}
                            onChange={(e) => setRenameValue(e.target.value)}
                            onKeyDown={(e) => {
                              if (e.key === 'Enter') submitRename();
                              if (e.key === 'Escape') { setRenameTarget(null); setError(null); }
                            }}
                            maxLength={25}
                            autoComplete="off"
                            spellCheck={false}
                            className="flex-1 min-w-0 rounded-md bg-bg-base/80 border border-border-default px-2.5 py-1.5 text-sm text-fg outline-none focus:border-accent focus:ring-1 focus:ring-accent/40 transition-colors"
                          />
                          <button
                            onClick={submitRename}
                            disabled={!renameValue.trim() || rename.isPending}
                            className="flex items-center justify-center w-7 h-7 rounded-md text-success bg-success/15 hover:bg-success/25 transition border-none cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed"
                            aria-label={t('common.save', { defaultValue: 'Kaydet' })}
                          >
                            <Check className="h-3.5 w-3.5 translate-x-[1px]" />
                          </button>
                          <button
                            onClick={() => { setRenameTarget(null); setError(null); }}
                            className="flex items-center justify-center w-7 h-7 rounded-md text-fg-muted bg-bg-base/80 hover:bg-surface hover:text-fg transition border-none cursor-pointer"
                            aria-label={t('common.cancel')}
                          >
                            <X className="h-3.5 w-3.5" />
                          </button>
                        </div>
                        <div className="flex items-center gap-1.5 mt-2 px-0.5 text-[10px] text-fg-subtle font-mono">
                          <CornerDownLeft className="h-3 w-3" />
                          <span>{t('portfolioSwitcher.enterHint', { defaultValue: 'enter to save' })}</span>
                          <span className="text-fg-subtle/50">·</span>
                          <span>{t('portfolioSwitcher.escHint', { defaultValue: 'esc to cancel' })}</span>
                        </div>
                      </motion.div>
                    ) : (
                      <div
                        className={`group/item relative flex items-center gap-2 pl-3 pr-2 py-2 rounded-lg my-0.5 transition-colors ${
                          isActive ? 'bg-accent/8' : 'hover:bg-surface/60'
                        }`}
                      >
                        {isActive && (
                          <span className="absolute left-0 top-2 bottom-2 w-[2px] rounded-full bg-gradient-to-b from-accent via-accent to-accent/40" />
                        )}
                        <button
                          onClick={() => { onSelect?.(p.id); closePanel(); }}
                          className="flex-1 min-w-0 text-left bg-transparent border-none cursor-pointer flex items-center gap-2.5"
                        >
                          <span
                            className={`w-1.5 h-1.5 rounded-full shrink-0 transition-colors ${
                              isActive ? 'bg-accent shadow-[0_0_8px] shadow-accent/70' : 'bg-fg-muted/30 group-hover/item:bg-fg-muted/60'
                            }`}
                          />
                          <span className="min-w-0 flex flex-col">
                            <span className={`text-sm truncate ${isActive ? 'font-semibold text-fg' : 'font-medium text-fg/85'}`}>
                              {portfolioName(t, p)}
                            </span>
                            <PortfolioTotal portfolio={p} enabled={open} />
                          </span>
                        </button>
                        <TypeBadge type={p.type} />
                        <div className="flex items-center gap-0.5 sm:opacity-0 sm:group-hover/item:opacity-100 transition-opacity">
                          <button
                            onClick={(e) => { e.stopPropagation(); setRenameTarget(p); setRenameValue(p.name); setError(null); }}
                            className="flex items-center justify-center w-7 h-7 rounded-md text-fg-muted hover:text-accent hover:bg-accent/10 transition border-none cursor-pointer bg-transparent"
                            aria-label={t('common.edit')}
                          >
                            <Pencil className="h-3.5 w-3.5" />
                          </button>
                          {portfolios.length > 1 && (
                            <button
                              onClick={(e) => { e.stopPropagation(); setDeleteTarget(p); setError(null); }}
                              className="flex items-center justify-center w-7 h-7 rounded-md text-fg-muted hover:text-danger hover:bg-danger/10 transition border-none cursor-pointer bg-transparent"
                              aria-label={t('common.delete')}
                            >
                              <Trash2 className="h-3.5 w-3.5" />
                            </button>
                          )}
                        </div>
                      </div>
                    )}
                  </div>
                );
              })}
            </div>

            <div className="border-t border-border-default/60 mx-2 mt-1" />

            <div className="p-2">
              <AnimatePresence mode="wait" initial={false}>
                {creating ? (
                  <motion.div
                    key="creating"
                    initial={{ opacity: 0, y: 4 }}
                    animate={{ opacity: 1, y: 0 }}
                    exit={{ opacity: 0, y: 4 }}
                    transition={{ duration: 0.14 }}
                    className="px-2.5 py-2.5 rounded-lg bg-accent/8 ring-1 ring-inset ring-accent/30"
                  >
                    <div className="flex items-center gap-1.5">
                      <input
                        autoFocus
                        type="text"
                        value={newName}
                        onChange={(e) => setNewName(e.target.value)}
                        placeholder={t('portfolioSwitcher.namePlaceholder', { defaultValue: 'Portföy adı' })}
                        onKeyDown={(e) => {
                          if (e.key === 'Enter') submitCreate();
                          if (e.key === 'Escape') { setCreating(false); setNewName(''); setNewType('SPOT'); setError(null); }
                        }}
                        maxLength={25}
                        className="flex-1 min-w-0 rounded-md bg-bg-base/80 border border-border-default px-2.5 py-1.5 text-sm text-fg placeholder:text-fg-subtle outline-none focus:border-accent focus:ring-1 focus:ring-accent/40 transition-colors"
                      />
                      <button
                        onClick={submitCreate}
                        disabled={!newName.trim() || create.isPending}
                        className="flex items-center justify-center w-7 h-7 rounded-md text-success bg-success/15 hover:bg-success/25 transition border-none cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed"
                        aria-label={t('common.save', { defaultValue: 'Kaydet' })}
                      >
                        <Check className="h-3.5 w-3.5" />
                      </button>
                      <button
                        onClick={() => { setCreating(false); setNewName(''); setNewType('SPOT'); setError(null); }}
                        className="flex items-center justify-center w-7 h-7 rounded-md text-fg-muted bg-bg-base/80 hover:bg-surface hover:text-fg transition border-none cursor-pointer"
                        aria-label={t('common.cancel')}
                      >
                        <X className="h-3.5 w-3.5" />
                      </button>
                    </div>
                    <div className="mt-2.5 grid grid-cols-2 gap-1 rounded-lg bg-bg-base/60 p-1 ring-1 ring-inset ring-border-default/50">
                      {PORTFOLIO_TYPES.map(({ id, labelKey, Icon }) => {
                        const selected = newType === id;
                        return (
                          <button
                            key={id}
                            type="button"
                            onClick={() => { setNewType(id); setError(null); }}
                            aria-pressed={selected}
                            className={`relative flex items-center justify-center gap-1.5 rounded-md px-2 py-1.5 text-xs font-medium transition-colors border-none cursor-pointer ${
                              selected
                                ? 'text-accent bg-accent/15 ring-1 ring-inset ring-accent/40'
                                : 'text-fg-muted bg-transparent hover:text-fg hover:bg-surface/60'
                            }`}
                          >
                            <Icon className="h-3.5 w-3.5" />
                            <span className="truncate">{t(labelKey)}</span>
                          </button>
                        );
                      })}
                    </div>
                    <div className="flex items-center gap-1.5 mt-2 px-0.5 text-[10px] text-fg-subtle font-mono">
                      <CornerDownLeft className="h-3 w-3" />
                      <span>{t('portfolioSwitcher.enterHint', { defaultValue: 'enter to save' })}</span>
                      <span className="text-fg-subtle/50">·</span>
                      <span>{t('portfolioSwitcher.escHint', { defaultValue: 'esc to cancel' })}</span>
                    </div>
                  </motion.div>
                ) : (
                  <motion.button
                    key="cta"
                    type="button"
                    onClick={() => { setCreating(true); setNewType('SPOT'); setError(null); }}
                    whileHover={{ y: -1 }}
                    whileTap={{ scale: 0.98 }}
                    initial={{ opacity: 0 }}
                    animate={{ opacity: 1 }}
                    exit={{ opacity: 0 }}
                    transition={{ duration: 0.14 }}
                    className="group/cta w-full flex items-center justify-between gap-2 px-3 py-2.5 rounded-lg text-sm font-semibold text-accent bg-transparent border border-dashed border-accent/35 hover:border-accent hover:bg-accent/10 transition-all cursor-pointer"
                  >
                    <span className="flex items-center gap-2">
                      <span className="flex items-center justify-center w-5 h-5 rounded-md bg-accent/20 group-hover/cta:bg-accent/30 transition-colors">
                        <Plus className="h-3 w-3" />
                      </span>
                      {t('portfolioSwitcher.newPortfolio', { defaultValue: 'Yeni portföy' })}
                    </span>
                    <span className="text-[10px] font-mono text-fg-subtle tabular-nums group-hover/cta:text-accent/70 transition-colors">
                      N
                    </span>
                  </motion.button>
                )}
              </AnimatePresence>
            </div>

            <AnimatePresence>
              {error && (
                <motion.div
                  initial={{ opacity: 0, y: -4 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0, y: -4 }}
                  transition={{ duration: 0.16, ease: [0.16, 1, 0.3, 1] }}
                  className="mx-2 mb-2 text-xs text-danger bg-danger/10 border border-danger/20 px-3 py-2 rounded-lg"
                >
                  {error}
                </motion.div>
              )}
            </AnimatePresence>
          </motion.div>
        )}
      </AnimatePresence>

      <ConfirmDialog
        open={!!deleteTarget}
        title={t('portfolioSwitcher.deleteTitle', { defaultValue: 'Portföyü sil?' })}
        message={t('portfolioSwitcher.deleteMessage', {
          name: deleteTarget?.name,
          defaultValue: '«{{name}}» portföyü tüm pozisyon, snapshot ve geçmiş ile birlikte kalıcı olarak silinecek. Bu işlem geri alınamaz.',
        })}
        confirmLabel={t('common.delete')}
        cancelLabel={t('common.cancel')}
        variant="danger"
        loading={remove.isPending}
        onConfirm={submitDelete}
        onCancel={() => { setDeleteTarget(null); setError(null); }}
      />
    </div>
  );
}
