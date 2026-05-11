import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Pencil, Save } from 'lucide-react';
import { useUpdateWatchlistItem } from '../../../shared/hooks/useWatchlist';
import { toast } from '../../../shared/components/feedback/Toast';
import { extractApiError } from '../../../shared/utils/apiError';
import BaseModal from '../../../shared/components/modal/BaseModal';
import Button from '../../../shared/components/buttons/Button';

export default function EditWatchlistItemModal({ open, onClose, item, watchlistId }) {
  const { t } = useTranslation();
  const [note, setNote] = useState('');
  const [threshold, setThreshold] = useState('');
  const update = useUpdateWatchlistItem(watchlistId);

  useEffect(() => {
    if (open && item) {
      setNote(item.note ?? '');
      setThreshold(item.deltaThreshold != null ? String(item.deltaThreshold) : '');
    }
  }, [open, item]);

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
      toast.error(extractApiError(err, t('watchlistItemEdit.updateFailed')));
    }
  };

  if (!item) return null;

  return (
    <BaseModal
      isOpen={open}
      onClose={onClose}
      icon={Pencil}
      title={t('watchlistItemEdit.title')}
      subtitle={item.assetName ?? item.assetCode}
      size="md"
    >
      <form onSubmit={handleSubmit} className="space-y-4">
        <label className="block">
          <span className="text-[11px] font-semibold uppercase tracking-wider text-fg-muted">{t('watchlistItemEdit.noteLabel')}</span>
          <input
            type="text"
            value={note}
            onChange={(e) => setNote(e.target.value)}
            maxLength={255}
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
            onChange={(e) => setThreshold(e.target.value)}
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
