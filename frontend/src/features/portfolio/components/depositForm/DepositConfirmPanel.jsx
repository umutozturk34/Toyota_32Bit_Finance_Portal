import { useTranslation } from 'react-i18next';
import { motion } from 'framer-motion';
import { Wallet } from 'lucide-react';
import { AlertTriangle } from '../../../../shared/components/feedback/AnimatedIcons';

function ConfirmRow({ label, value }) {
  return (
    <div className="flex items-center justify-between gap-2 text-xs">
      <span className="text-fg-muted shrink-0">{label}</span>
      <span className="font-mono font-medium text-fg text-right break-all min-w-0">{value}</span>
    </div>
  );
}

export default function DepositConfirmPanel({ sym, principal, localeTag, currency, rateValue, selectedRate, startDisplay, maturityDisplay, onCancel, onConfirm }) {
  const { t } = useTranslation();
  return (
    <motion.div initial={{ opacity: 0, y: 5 }} animate={{ opacity: 1, y: 0 }} className="space-y-5 py-2">
      <div className="flex flex-col items-center gap-3">
        <div className="flex items-center justify-center w-12 h-12 rounded-full bg-warning/10">
          <AlertTriangle className="h-6 w-6 text-warning" />
        </div>
        <div className="text-center space-y-1">
          <p className="text-sm font-semibold text-fg">{t('deposits.form.confirm.heading')}</p>
          <p className="text-xs text-fg-muted">{t('deposits.form.confirm.sub')}</p>
        </div>
      </div>
      <div className="rounded-xl border border-border-default bg-bg-base px-4 py-3 space-y-2">
        <ConfirmRow label={t('deposits.fields.principal')} value={`${sym}${Number(principal).toLocaleString(localeTag)} ${currency}`} />
        <ConfirmRow label={t('deposits.fields.annualRate')} value={`%${Number(rateValue).toLocaleString(localeTag)}`} />
        {selectedRate && <ConfirmRow label={t('deposits.fields.depositType')} value={t(`marketOverview.macro.maturity${selectedRate.maturity}`)} />}
        <div className="border-t border-border-default pt-2 space-y-2">
          <ConfirmRow label={t('deposits.fields.startDate')} value={startDisplay} />
          <ConfirmRow label={t('deposits.fields.maturityDate')} value={maturityDisplay} />
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
