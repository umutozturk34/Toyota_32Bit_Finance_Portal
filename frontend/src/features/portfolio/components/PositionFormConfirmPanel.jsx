import { useTranslation } from 'react-i18next';
import { motion } from 'framer-motion';
import { Wallet } from 'lucide-react';
import { AlertTriangle } from '../../../shared/components/feedback/AnimatedIcons';
import { currentLocaleTag } from '../../../shared/utils/formatters';
import { useMoney } from '../../../shared/hooks/useMoney';

function Row({ label, value }) {
  return (
    <div className="flex items-center justify-between gap-2 text-xs">
      <span className="text-fg-muted shrink-0">{label}</span>
      <span className="font-mono font-medium text-fg text-right break-all min-w-0">{value}</span>
    </div>
  );
}

export default function PositionFormConfirmPanel({ isEdit, displayCode, form, isFractional, totalCostTry, inputCurrency,
                                                    closeEnabled, exitDate, exitPrice, onCancel, onConfirm }) {
  const { t } = useTranslation();
  const { format: money, formatCompact } = useMoney();
  const qtyDisplay = Number(form.quantity).toLocaleString(currentLocaleTag(), { maximumFractionDigits: isFractional ? 6 : 0 });
  const priceDisplay = money(Number(form.entryPrice), inputCurrency);
  const dateDisplay = new Date(form.entryDate).toLocaleDateString(currentLocaleTag(), { day: '2-digit', month: 'long', year: 'numeric' });
  const exitDateDisplay = closeEnabled && exitDate
    ? new Date(exitDate).toLocaleDateString(currentLocaleTag(), { day: '2-digit', month: 'long', year: 'numeric' })
    : null;
  const exitPriceDisplay = closeEnabled && exitPrice ? money(Number(exitPrice), inputCurrency) : null;
  return (
    <motion.div
      initial={{ opacity: 0, y: 5 }}
      animate={{ opacity: 1, y: 0 }}
      className="space-y-5 py-2"
    >
      <div className="flex flex-col items-center gap-3">
        <div className="flex items-center justify-center w-12 h-12 rounded-full bg-warning/10">
          <AlertTriangle className="h-6 w-6 text-warning" />
        </div>
        <div className="text-center space-y-1">
          <p className="text-sm font-semibold text-fg">{t('positionForm.confirm.heading')}</p>
          <p className="text-xs text-fg-muted">
            <span dangerouslySetInnerHTML={{
              __html: t(isEdit ? 'positionForm.confirm.subEdit' : 'positionForm.confirm.subAdd', { code: displayCode }),
            }} />
          </p>
        </div>
      </div>
      <div className="rounded-xl border border-border-default bg-bg-base px-4 py-3 space-y-2">
        <Row label={t('positionForm.confirm.date')} value={dateDisplay} />
        <Row label={t('positionForm.confirm.quantity')} value={isFractional ? qtyDisplay : t('positionForm.confirm.quantityShares', { qty: qtyDisplay })} />
        <Row label={t('positionForm.confirm.unitPrice')} value={priceDisplay} />
        {exitDateDisplay && (
          <div className="border-t border-border-default pt-2 space-y-2">
            <Row label={t('positionForm.confirm.exitDate', { defaultValue: 'Çıkış tarihi' })} value={exitDateDisplay} />
            <Row label={t('positionForm.confirm.exitPrice', { defaultValue: 'Çıkış fiyatı' })} value={exitPriceDisplay} />
          </div>
        )}
        <div className="border-t border-border-default pt-2">
          <Row label={<span className="font-semibold">{t('positionForm.totalCost')}</span>} value={
            <span className="font-bold text-accent truncate" title={money(totalCostTry)}>
              {formatCompact(totalCostTry, 'TRY', 1_000_000_000)}
            </span>
          } />
        </div>
      </div>
      <div className="flex gap-2">
        <button
          onClick={onCancel}
          className="flex-1 rounded-lg py-2.5 text-sm font-semibold text-fg border border-border-default bg-bg-base hover:bg-surface transition-all cursor-pointer"
        >
          {t('common.cancel')}
        </button>
        <button
          onClick={onConfirm}
          className="flex-1 flex items-center justify-center gap-2 rounded-lg py-2.5 text-sm font-semibold text-white bg-accent hover:bg-accent-bright transition-all border-none cursor-pointer"
        >
          <Wallet className="h-4 w-4" />
          {t('common.confirm')}
        </button>
      </div>
    </motion.div>
  );
}
