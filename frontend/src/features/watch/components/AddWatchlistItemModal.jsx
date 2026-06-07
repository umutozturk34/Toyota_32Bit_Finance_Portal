import { useState, useEffect, useMemo, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { motion } from 'framer-motion';
import { AnimatePresence } from 'framer-motion';
import { Eye, ListChecks, FileText, Percent, Search, Star, ChevronDown, Check, Info } from 'lucide-react';
import BaseModal from '../../../shared/components/modal/BaseModal';
import SearchSuggestions from '../../../shared/components/form/SearchSuggestions';
import {
  useWatchlists,
  useWatchlistItems,
  useAddWatchlistItem,
  useAddToFavorites,
} from '../../../shared/hooks/useWatchlist';
import { toast } from '../../../shared/components/feedback/toastBus';
import { extractApiError } from '../../../shared/utils/apiError';
import { watchlistName } from '../../../shared/utils/watchlistName';
import { commodityLabel } from '../../../shared/utils/commodityName';

export default function AddWatchlistItemModal({
  isOpen,
  onClose,
  watchlistId,
  defaultMarketType,
  defaultAssetCode,
}) {
  const { t } = useTranslation();
  const lists = useWatchlists();
  const watchlists = useMemo(() => lists.data ?? [], [lists.data]);
  const add = useAddWatchlistItem();
  const addToFavorites = useAddToFavorites();

  const buildInitialAsset = () =>
    defaultMarketType && defaultAssetCode
      ? { type: defaultMarketType, code: defaultAssetCode, name: defaultAssetCode }
      : null;
  const resolveInitialListId = () => {
    if (watchlistId != null) return watchlistId;
    const def = watchlists.find((w) => w.isDefault) ?? watchlists[0];
    return def?.id ?? null;
  };

  const [selectedListId, setSelectedListId] = useState(resolveInitialListId);
  const [selectedAsset, setSelectedAsset] = useState(buildInitialAsset);
  const [note, setNote] = useState('');
  const [deltaThreshold, setDeltaThreshold] = useState('');
  const [listMenuOpen, setListMenuOpen] = useState(false);
  const [wasOpen, setWasOpen] = useState(isOpen);
  const listMenuRef = useRef(null);

  const targetItems = useWatchlistItems(selectedListId, { enabled: isOpen && selectedListId != null });
  const existingEntry = useMemo(() => {
    if (!selectedAsset || !targetItems.data) return null;
    return targetItems.data.find(
      (it) => it.marketType === selectedAsset.type && it.assetCode === selectedAsset.code,
    ) ?? null;
  }, [targetItems.data, selectedAsset]);

  const [trackedEntryId, setTrackedEntryId] = useState(existingEntry?.id ?? null);

  if (isOpen && !wasOpen) {
    setWasOpen(true);
    setSelectedAsset(buildInitialAsset());
    setSelectedListId(resolveInitialListId());
  } else if (!isOpen && wasOpen) {
    setWasOpen(false);
  }

  if (isOpen && (existingEntry?.id ?? null) !== trackedEntryId) {
    setTrackedEntryId(existingEntry?.id ?? null);
    if (existingEntry) {
      setNote(existingEntry.note ?? '');
      setDeltaThreshold(
        existingEntry.deltaThreshold != null ? String(existingEntry.deltaThreshold) : '',
      );
    } else {
      setNote('');
      setDeltaThreshold('');
    }
  }

  useEffect(() => {
    if (!listMenuOpen) return;
    const handler = (e) => {
      if (!listMenuRef.current?.contains(e.target)) setListMenuOpen(false);
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, [listMenuOpen]);

  const submit = async (e) => {
    e.preventDefault();
    if (!selectedAsset) return toast.error(t('addWatchlistItem.errorAsset'));
    if (selectedListId == null) return toast.error(t('addWatchlistItem.errorList'));
    let numericThreshold = null;
    if (deltaThreshold !== '') {
      const parsed = Number.parseFloat(deltaThreshold);
      if (Number.isNaN(parsed) || parsed <= 0) {
        return toast.error(t('addWatchlistItem.errorThreshold'));
      }
      numericThreshold = parsed;
    }
    const payload = {
      marketType: selectedAsset.type,
      assetCode: selectedAsset.code,
      note: note.trim() || null,
      deltaThreshold: numericThreshold,
    };
    try {
      const targetList = watchlists.find((w) => w.id === selectedListId);
      if (targetList?.isDefault) {
        await addToFavorites.mutateAsync(payload);
      } else {
        await add.mutateAsync({ watchlistId: selectedListId, ...payload });
      }
      const targetName = targetList?.name ?? t('addWatchlistItem.fallbackListName');
      toast.success(t(existingEntry ? 'addWatchlistItem.updated' : 'addWatchlistItem.added', {
        code: selectedAsset.code,
        list: targetName,
      }));
      onClose();
    } catch (err) {
      toast.error(extractApiError(err, t('addWatchlistItem.failed')));
    }
  };

  const pending = add.isPending || addToFavorites.isPending;
  const subtitle = selectedAsset
    ? `${selectedAsset.code} (${t(`assets.labels.${selectedAsset.type}`, { defaultValue: selectedAsset.type })})`
    : t('addWatchlistItem.subtitlePrompt');
  const selectedList = watchlists.find((w) => w.id === selectedListId);
  const isUpdate = existingEntry != null;

  return (
    <BaseModal
      isOpen={isOpen}
      onClose={onClose}
      icon={Eye}
      title={isUpdate ? t('addWatchlistItem.titleUpdate') : t('addWatchlistItem.titleCreate')}
      subtitle={subtitle}
      size="md"
    >
      <form onSubmit={submit} noValidate className="space-y-4">
        {!defaultAssetCode && (
          <div className="space-y-1.5">
            <label className="text-xs font-medium text-fg-muted flex items-center gap-1.5">
              <Search className="h-3 w-3" />
              {t('addWatchlistItem.searchLabel')}
            </label>
            {selectedAsset ? (
              <div className="flex items-center justify-between gap-2 rounded-lg border border-accent/40 bg-accent/5 px-3 py-2.5">
                <div className="flex items-center gap-2 min-w-0">
                  {selectedAsset.image && (/^https?:\/\//i.test(selectedAsset.image)
                    ? <img src={selectedAsset.image} alt={selectedAsset.code} className="w-6 h-6 rounded shrink-0" />
                    : <span className="w-6 h-6 rounded shrink-0 flex items-center justify-center text-base leading-none">{selectedAsset.image}</span>)}
                  <span className="text-sm font-mono font-semibold text-fg truncate">{selectedAsset.code}</span>
                  {selectedAsset.name && (
                    <span className="text-xs text-fg-muted truncate">· {commodityLabel(t, selectedAsset.type, selectedAsset.code, selectedAsset.name)}</span>
                  )}
                </div>
                <button
                  type="button"
                  onClick={() => setSelectedAsset(null)}
                  className="text-[11px] font-medium text-fg-muted hover:text-fg transition-colors bg-transparent border-none cursor-pointer"
                >
                  {t('addWatchlistItem.change')}
                </button>
              </div>
            ) : (
              <SearchSuggestions
                placeholder={t('addWatchlistItem.searchPlaceholder')}
                navigateOnSelect={false}
                excludeTypes={['MACRO', 'BOND']}
                onSelect={(asset) => setSelectedAsset(asset)}
              />
            )}
          </div>
        )}

        <div className="space-y-1.5">
          <label className="text-xs font-medium text-fg-muted flex items-center gap-1.5">
            <ListChecks className="h-3 w-3" />
            {t('addWatchlistItem.listLabel')}
          </label>
          <div ref={listMenuRef} className="relative">
            <button
              type="button"
              onClick={() => setListMenuOpen((o) => !o)}
              className="w-full flex items-center justify-between gap-2 rounded-lg border border-border-default bg-bg-base px-3 py-2.5 text-sm text-fg outline-none hover:border-border-hover focus:ring-1 focus:ring-accent/50 transition-all cursor-pointer"
            >
              <span className="flex items-center gap-2 min-w-0">
                {selectedList?.isDefault && (
                  <Star className="h-3.5 w-3.5 text-warning fill-warning shrink-0" />
                )}
                <span className="truncate">{selectedList ? watchlistName(t, selectedList) : t('addWatchlistItem.pickList')}</span>
                {selectedList && (
                  <span className="text-[11px] font-mono text-fg-subtle shrink-0">
                    {selectedList.itemCount}
                  </span>
                )}
              </span>
              <ChevronDown
                className={`h-4 w-4 text-fg-muted transition-transform shrink-0 ${
                  listMenuOpen ? 'rotate-180' : ''
                }`}
              />
            </button>
            <AnimatePresence>
              {listMenuOpen && (
                <motion.div
                  initial={{ opacity: 0, y: -4, scale: 0.98 }}
                  animate={{ opacity: 1, y: 0, scale: 1 }}
                  exit={{ opacity: 0, y: -4, scale: 0.98 }}
                  transition={{ duration: 0.14 }}
                  style={{ background: 'rgb(20, 20, 28)' }}
                  className="absolute z-50 left-0 right-0 mt-1.5 rounded-lg border border-border-default shadow-xl overflow-hidden max-h-60 overflow-y-auto"
                >
                  {watchlists.map((w) => {
                    const active = w.id === selectedListId;
                    return (
                      <button
                        key={w.id}
                        type="button"
                        onClick={() => {
                          setSelectedListId(w.id);
                          setListMenuOpen(false);
                        }}
                        className={`w-full flex items-center gap-2 px-3 py-2.5 text-sm text-left transition-colors border-none cursor-pointer ${
                          active
                            ? 'bg-accent/15 text-accent'
                            : 'bg-transparent text-fg hover:bg-accent/8'
                        }`}
                      >
                        {w.isDefault ? (
                          <Star className="h-3.5 w-3.5 text-warning fill-warning shrink-0" />
                        ) : (
                          <span className="w-3.5 h-3.5 shrink-0" />
                        )}
                        <span className="flex-1 truncate">{watchlistName(t, w)}</span>
                        <span className="text-[11px] font-mono text-fg-subtle">{w.itemCount}</span>
                        {active && <Check className="h-3.5 w-3.5 text-accent shrink-0" />}
                      </button>
                    );
                  })}
                </motion.div>
              )}
            </AnimatePresence>
          </div>
        </div>

        <div className="space-y-1.5">
          <label className="text-xs font-medium text-fg-muted flex items-center gap-1.5">
            <FileText className="h-3 w-3" />
            {t('addWatchlistItem.noteLabel')} <span className="text-fg-subtle font-normal">{t('addWatchlistItem.optional')}</span>
          </label>
          <input
            type="text"
            value={note}
            onChange={(e) => setNote(e.target.value)}
            maxLength={255}
            placeholder={t('addWatchlistItem.notePlaceholder')}
            className="w-full rounded-lg border border-border-default bg-bg-base px-3 py-2.5 text-sm text-fg placeholder:text-fg-subtle outline-none focus:ring-1 focus:ring-accent/50 transition-all"
          />
        </div>

        <div className="space-y-1.5">
          <label className="text-xs font-medium text-fg-muted flex items-center gap-1.5">
            <Percent className="h-3 w-3" />
            {t('addWatchlistItem.thresholdLabel')} <span className="text-fg-subtle font-normal">{t('addWatchlistItem.optional')}</span>
          </label>
          <input
            type="number"
            step="0.1"
            min="0"
            value={deltaThreshold}
            onChange={(e) => setDeltaThreshold(e.target.value)}
            placeholder={t('addWatchlistItem.thresholdPlaceholder')}
            className="w-full rounded-lg border border-border-default bg-bg-base px-3 py-2.5 text-sm text-fg font-mono outline-none focus:ring-1 focus:ring-accent/50 transition-all"
          />
          <p className="text-[11px] text-fg-subtle leading-relaxed">
            {t('addWatchlistItem.thresholdHint')}
          </p>
        </div>

        {isUpdate && (
          <div className="flex items-start gap-2 rounded-lg border border-warning/40 bg-warning/8 px-3 py-2.5 text-[11px] text-warning leading-relaxed">
            <Info className="h-3.5 w-3.5 shrink-0 mt-0.5" />
            <span dangerouslySetInnerHTML={{
              __html: t('addWatchlistItem.duplicateWarn', { code: selectedAsset?.code ?? '' }),
            }} />
          </div>
        )}

        <button
          type="submit"
          disabled={pending || !selectedAsset || selectedListId == null}
          className="w-full flex items-center justify-center gap-2 rounded-lg py-3 text-sm font-semibold text-white bg-accent hover:bg-accent-bright transition-all border-none cursor-pointer disabled:opacity-50"
        >
          {pending
            ? (isUpdate ? t('addWatchlistItem.updating') : t('addWatchlistItem.adding'))
            : (isUpdate ? t('addWatchlistItem.updateCta') : t('addWatchlistItem.addCta'))}
        </button>
      </form>
    </BaseModal>
  );
}
