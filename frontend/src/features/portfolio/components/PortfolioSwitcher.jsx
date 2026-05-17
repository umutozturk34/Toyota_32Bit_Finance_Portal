import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { motion, AnimatePresence } from 'framer-motion';
import { ChevronDown, Plus, Pencil, Trash2, Check, X, Wallet } from 'lucide-react';
import { useCreatePortfolio, useRenamePortfolio, useDeletePortfolio } from '../hooks/usePortfolioData';
import { extractApiError } from '../../../shared/utils/apiError';
import ConfirmDialog from '../../../shared/components/modal/ConfirmDialog';

export default function PortfolioSwitcher({ portfolios = [], activeId, onSelect }) {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);
  const [renameTarget, setRenameTarget] = useState(null);
  const [renameValue, setRenameValue] = useState('');
  const [creating, setCreating] = useState(false);
  const [newName, setNewName] = useState('');
  const [deleteTarget, setDeleteTarget] = useState(null);
  const [error, setError] = useState(null);
  const ref = useRef(null);

  const create = useCreatePortfolio();
  const rename = useRenamePortfolio();
  const remove = useDeletePortfolio();

  const active = portfolios.find((p) => p.id === activeId) || portfolios[0];

  useEffect(() => {
    if (!open) return undefined;
    const onClickOutside = (e) => {
      if (ref.current && !ref.current.contains(e.target)) {
        setOpen(false);
        setCreating(false);
        setRenameTarget(null);
        setDeleteTarget(null);
        setError(null);
      }
    };
    document.addEventListener('mousedown', onClickOutside);
    return () => document.removeEventListener('mousedown', onClickOutside);
  }, [open]);

  const submitCreate = async () => {
    if (!newName.trim()) return;
    setError(null);
    try {
      const created = await create.mutateAsync(newName.trim());
      setCreating(false);
      setNewName('');
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
        whileHover={{ scale: 1.02 }}
        whileTap={{ scale: 0.98 }}
        className={`flex items-center gap-2.5 rounded-xl border bg-bg-elevated px-3.5 py-2 transition-all cursor-pointer ${
          open
            ? 'border-accent/60 shadow-lg shadow-accent/10'
            : 'border-border-default hover:border-accent/40'
        }`}
      >
        <span className="flex items-center justify-center w-7 h-7 rounded-lg bg-accent/15">
          <Wallet className="h-3.5 w-3.5 text-accent" />
        </span>
        <span className="text-sm font-semibold text-fg max-w-[160px] truncate">{active?.name || t('portfolio.headerTitle')}</span>
        {portfolios.length > 1 && (
          <span className="text-[10px] font-mono text-fg-muted bg-bg-base px-1.5 py-0.5 rounded-md border border-border-default">
            {portfolios.length}
          </span>
        )}
        <ChevronDown className={`h-3.5 w-3.5 text-fg-muted transition-transform ${open ? 'rotate-180' : ''}`} />
      </motion.button>

      <AnimatePresence>
        {open && (
          <motion.div
            initial={{ opacity: 0, scale: 0.96, y: -4 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.96, y: -4 }}
            transition={{ duration: 0.16, ease: [0.16, 1, 0.3, 1] }}
            className="absolute right-0 z-50 mt-2 w-72 rounded-xl border border-border-default bg-bg-elevated shadow-2xl shadow-black/30 backdrop-blur-md p-2 space-y-0.5"
          >
            {portfolios.map((p) => {
              const isActive = p.id === activeId;
              const isRenaming = renameTarget?.id === p.id;
              return (
                <div key={p.id} className="rounded-lg overflow-hidden">
                  {isRenaming ? (
                    <div className="flex items-center gap-1.5 p-2 bg-accent/10">
                      <input
                        autoFocus
                        type="text"
                        value={renameValue}
                        onChange={(e) => setRenameValue(e.target.value)}
                        onKeyDown={(e) => {
                          if (e.key === 'Enter') submitRename();
                          if (e.key === 'Escape') { setRenameTarget(null); setError(null); }
                        }}
                        className="flex-1 rounded bg-bg-base border border-border-default px-2 py-1 text-sm text-fg outline-none focus:ring-1 focus:ring-accent/50"
                      />
                      <button
                        onClick={submitRename}
                        disabled={!renameValue.trim() || rename.isPending}
                        className="flex items-center justify-center w-6 h-6 rounded text-success bg-success/15 hover:bg-success/25 transition border-none cursor-pointer disabled:opacity-40"
                        aria-label={t('common.save', { defaultValue: 'Kaydet' })}
                      >
                        <Check className="h-3 w-3" />
                      </button>
                      <button
                        onClick={() => { setRenameTarget(null); setError(null); }}
                        className="flex items-center justify-center w-6 h-6 rounded text-fg-muted bg-bg-base hover:bg-surface transition border-none cursor-pointer"
                        aria-label={t('common.cancel')}
                      >
                        <X className="h-3 w-3" />
                      </button>
                    </div>
                  ) : (
                    <div className={`flex items-center gap-1.5 px-2 py-1.5 group hover:bg-surface/60 transition-colors ${isActive ? 'bg-accent/10' : ''}`}>
                      <button
                        onClick={() => { onSelect?.(p.id); setOpen(false); }}
                        className="flex-1 text-left text-sm font-medium text-fg cursor-pointer bg-transparent border-none flex items-center gap-2"
                      >
                        <span className={`w-1.5 h-1.5 rounded-full ${isActive ? 'bg-accent' : 'bg-fg-muted/30'}`} />
                        <span className="truncate">{p.name}</span>
                      </button>
                      <button
                        onClick={(e) => { e.stopPropagation(); setRenameTarget(p); setRenameValue(p.name); setError(null); }}
                        className="flex items-center justify-center w-6 h-6 rounded text-fg-muted hover:text-accent hover:bg-accent/10 opacity-0 group-hover:opacity-100 transition border-none cursor-pointer bg-transparent"
                        aria-label={t('common.edit')}
                      >
                        <Pencil className="h-3 w-3" />
                      </button>
                      {portfolios.length > 1 && (
                        <button
                          onClick={(e) => { e.stopPropagation(); setDeleteTarget(p); setError(null); }}
                          className="flex items-center justify-center w-6 h-6 rounded text-fg-muted hover:text-danger hover:bg-danger/10 opacity-0 group-hover:opacity-100 transition border-none cursor-pointer bg-transparent"
                          aria-label={t('common.delete')}
                        >
                          <Trash2 className="h-3 w-3" />
                        </button>
                      )}
                    </div>
                  )}
                </div>
              );
            })}

            <div className="border-t border-border-default pt-1.5 mt-1.5">
              {creating ? (
                <div className="flex items-center gap-1.5 p-2 bg-accent/10 rounded-lg">
                  <input
                    autoFocus
                    type="text"
                    value={newName}
                    onChange={(e) => setNewName(e.target.value)}
                    placeholder={t('portfolioSwitcher.namePlaceholder', { defaultValue: 'Portföy adı' })}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter') submitCreate();
                      if (e.key === 'Escape') { setCreating(false); setNewName(''); setError(null); }
                    }}
                    className="flex-1 rounded bg-bg-base border border-border-default px-2 py-1 text-sm text-fg outline-none focus:ring-1 focus:ring-accent/50"
                  />
                  <button
                    onClick={submitCreate}
                    disabled={!newName.trim() || create.isPending}
                    className="flex items-center justify-center w-6 h-6 rounded text-success bg-success/15 hover:bg-success/25 transition border-none cursor-pointer disabled:opacity-40"
                    aria-label={t('common.save', { defaultValue: 'Kaydet' })}
                  >
                    <Check className="h-3 w-3" />
                  </button>
                  <button
                    onClick={() => { setCreating(false); setNewName(''); setError(null); }}
                    className="flex items-center justify-center w-6 h-6 rounded text-fg-muted bg-bg-base hover:bg-surface transition border-none cursor-pointer"
                    aria-label={t('common.cancel')}
                  >
                    <X className="h-3 w-3" />
                  </button>
                </div>
              ) : (
                <button
                  type="button"
                  onClick={() => { setCreating(true); setError(null); }}
                  className="w-full flex items-center gap-2 px-2.5 py-2 rounded-lg text-xs font-semibold text-accent hover:bg-accent/10 transition cursor-pointer bg-transparent border-none border border-dashed border-accent/30"
                >
                  <Plus className="h-3.5 w-3.5" />
                  {t('portfolioSwitcher.newPortfolio', { defaultValue: 'Yeni portföy' })}
                </button>
              )}
            </div>

            {error && (
              <div className="text-[10px] text-danger bg-danger/10 px-2 py-1 rounded">{error}</div>
            )}
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
