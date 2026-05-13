import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Pencil, Save, ArrowUp, ArrowDown, TrendingUp, TrendingDown } from 'lucide-react';
import { useUpdatePriceAlert } from '../../../shared/hooks/usePriceAlerts';
import { toast } from '../../../shared/components/feedback/toastBus';
import { extractApiError } from '../../../shared/utils/apiError';
import BaseModal from '../../../shared/components/modal/BaseModal';
import Button from '../../../shared/components/buttons/Button';

const DIRECTION_DEFS = [
  { value: 'ABOVE', Icon: ArrowUp },
  { value: 'BELOW', Icon: ArrowDown },
  { value: 'CHANGE_PCT_UP', Icon: TrendingUp },
  { value: 'CHANGE_PCT_DOWN', Icon: TrendingDown },
];

export default function EditPriceAlertModal({ open, onClose, alert }) {
  const { t } = useTranslation();
  const [direction, setDirection] = useState(() => alert?.direction ?? 'ABOVE');
  const [threshold, setThreshold] = useState(() =>
    alert?.threshold != null ? String(alert.threshold) : '',
  );
  const [sessionKey, setSessionKey] = useState(() => (open && alert ? alert.id : null));
  const update = useUpdatePriceAlert();
  const directionOptions = DIRECTION_DEFS.map(d => ({ ...d, label: t(`priceAlertEdit.direction.${d.value}`) }));

  const currentKey = open && alert ? alert.id : null;
  if (currentKey !== sessionKey) {
    setSessionKey(currentKey);
    if (open && alert) {
      setDirection(alert.direction ?? 'ABOVE');
      setThreshold(alert.threshold != null ? String(alert.threshold) : '');
    }
  }

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!alert || update.isPending) return;
    const trimmed = String(threshold).trim().replace(',', '.');
    const value = Number(trimmed);
    if (trimmed === '' || Number.isNaN(value)) {
      toast.error(t('priceAlertEdit.invalidThreshold'), t('priceAlertEdit.invalidThresholdHint'));
      return;
    }
    try {
      await update.mutateAsync({ id: alert.id, payload: { direction, threshold: value } });
      toast.success(t('priceAlertEdit.updated'), t('priceAlertEdit.updatedBody', { code: alert.assetCode }));
      onClose?.();
    } catch (err) {
      toast.error(extractApiError(err, t('priceAlertEdit.updateFailed')));
    }
  };

  if (!alert) return null;

  return (
    <BaseModal
      isOpen={open}
      onClose={onClose}
      icon={Pencil}
      title={t('priceAlertEdit.title')}
      subtitle={alert.assetName ?? alert.assetCode}
      size="md"
    >
      <form onSubmit={handleSubmit} className="space-y-4">
        <div>
          <span className="text-[11px] font-semibold uppercase tracking-wider text-fg-muted">{t('priceAlertEdit.directionLabel')}</span>
          <div className="grid grid-cols-2 gap-2 mt-1.5">
            {directionOptions.map(({ value, label, Icon }) => {
              const active = direction === value;
              return (
                <button
                  key={value}
                  type="button"
                  onClick={() => setDirection(value)}
                  className={`flex items-center gap-2 px-3 py-2.5 rounded-lg text-xs font-semibold transition-all border cursor-pointer ${
                    active
                      ? 'border-accent/50 bg-accent/10 text-accent'
                      : 'border-border-default bg-bg-elevated text-fg-muted hover:text-fg hover:border-border-hover'
                  }`}
                >
                  <Icon className="h-3.5 w-3.5" />
                  <span>{label}</span>
                </button>
              );
            })}
          </div>
        </div>

        <label className="block">
          <span className="text-[11px] font-semibold uppercase tracking-wider text-fg-muted">{t('priceAlertEdit.thresholdLabel')}</span>
          <input
            type="text"
            inputMode="decimal"
            value={threshold}
            onChange={(e) => setThreshold(e.target.value)}
            placeholder={t('priceAlertEdit.thresholdPlaceholder')}
            className="mt-1.5 w-full rounded-lg border border-border-default bg-bg-elevated px-3 py-2.5 text-sm font-mono text-fg placeholder:text-fg-subtle focus:outline-none focus:border-accent/60 focus:shadow-[0_0_0_3px_rgba(99,102,241,0.12)] transition-all"
          />
          <p className="mt-1 text-[10px] text-fg-subtle leading-relaxed">
            {t('priceAlertEdit.thresholdHint')}
          </p>
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
