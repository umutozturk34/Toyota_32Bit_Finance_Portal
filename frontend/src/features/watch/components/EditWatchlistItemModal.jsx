import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Pencil, Save } from 'lucide-react';
import { useUpdateWatchlistItem } from '../../../shared/hooks/useWatchlist';
import { toast } from '../../../shared/components/feedback/toastBus';
import { toastApiError } from '../../../shared/utils/apiError';
import BaseModal from '../../../shared/components/modal/BaseModal';
import Button from '../../../shared/components/buttons/Button';
import { commodityLabel } from '../../../shared/utils/commodityName';
import { clampNumberInput, MAX_PERCENT } from '../../../shared/utils/numberInput';

export default function EditWatchlistItemModal({ open, onClose, item, watchlistId }) {
  const { t } = useTranslation();
  const [note, setNote] = useState(() => item?.note ?? '');
  const [threshold, setThreshold] = useState(() =>
    item?.deltaThreshold != null ? String(item.deltaThreshold) : '',
  );
  const [sessionKey, setSessionKey] = useState(() => (open && item ? item.id : null));
  const update = useUpdateWatchlistItem(watchlistId);

  const currentKey = open && item ? item.id : null;
  if (currentKey !== sessionKey) {
    setSessionKey(currentKey);
    if (open && item) {
      setNote(item.note ?? '');
      setThreshold(item.deltaThreshold != null ? String(item.deltaThreshold) : '');
    }
  }

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!item || update.isPending) return;
    const payload = {};
    payload.note = note.trim();
    const trimmed = threshold.trim().replace(',', '.');
    if (trimmed !== '') {
      const value = Number(trimmed);
      if (Number.isNaN(value)) {
        toast.error(t('watchlistItemEdit.invalidThreshold'), t('watchlistItemEdit.invalidThresholdHint'));
        return;
      }
      payload.deltaThreshold = value;
    }
    try {
      await update.mutateAsync({ itemId: item.id, payload });
      toast.success(t('watchlistItemEdit.updated'), t('watchlistItemEdit.updatedBody', { code: item.assetCode }));
      onClose?.();
    } catch (err) {
      toastApiError(err, t('watchlistItemEdit.updateFailed'));
    }
  };

  if (!item) return null;

  return (
    <BaseModal
      isOpen={open}
      onClose={onClose}
      icon={Pencil}
      title={t('watchlistItemEdit.title')}
      subtitle={commodityLabel(t, item.marketType, item.assetCode, item.assetName ?? item.assetCode)}
      size="md"
    >
      <form onSubmit={handleSubmit} className="space-y-4">
        <label className="block">
          <span className="text-[11px] font-semibold uppercase tracking-wider text-fg-muted">{t('watchlistItemEdit.noteLabel')}</span>
          <input
            type="text"
            value={note}
            onChange={(e) => setNote(e.target.value)}
            maxLength={50}
            placeholder={t('watchlistItemEdit.notePlaceholder')}
            className="mt-1.5 w-full rounded-lg border border-border-default bg-bg-elevated px-3 py-2.5 text-sm text-fg placeholder:text-fg-subtle focus:outline-none focus:border-accent/60 focus:bg-bg-elevated focus:shadow-[0_0_0_3px_rgba(99,102,241,0.12)] transition-all"
          />
        </label>

        <label className="block">
          <span className="text-[11px] font-semibold uppercase tracking-wider text-fg-muted">
            {t('watchlistItemEdit.thresholdLabel')}
          </span>
          <input
            type="text"
            inputMode="decimal"
            value={threshold}
            onChange={(e) => {
              const raw = e.target.value;
              // Threshold accepts comma decimals (TR locale), so normalise before the cap check; keep the
              // raw keystroke unless the value exceeds max, in which case fall back to the capped string.
              const capped = clampNumberInput(raw.replace(',', '.'), MAX_PERCENT);
              setThreshold(capped === raw.replace(',', '.') ? raw : capped);
            }}
            placeholder={t('watchlistItemEdit.thresholdPlaceholder')}
            className="mt-1.5 w-full rounded-lg border border-border-default bg-bg-elevated px-3 py-2.5 text-sm font-mono text-fg placeholder:text-fg-subtle focus:outline-none focus:border-accent/60 focus:shadow-[0_0_0_3px_rgba(99,102,241,0.12)] transition-all"
          />
          <p className="mt-1 text-[10px] text-fg-subtle leading-relaxed" dangerouslySetInnerHTML={{ __html: t('watchlistItemEdit.thresholdHint') }} />
        </label>

        <div className="flex gap-2 pt-1">
          <Button
            type="button"
            variant="secondary"
            size="lg"
            fullWidth
            onClick={onClose}
            disabled={update.isPending}
          >
            {t('common.cancel')}
          </Button>
          <Button
            type="submit"
            variant="gradient"
            size="lg"
            fullWidth
            loading={update.isPending}
            leftIcon={<Save className="h-3.5 w-3.5" />}
            motionPreset="tap"
          >
            {update.isPending ? t('priceAlertEdit.saving') : t('common.save')}
          </Button>
        </div>
      </form>
    </BaseModal>
  );
}
