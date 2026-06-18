import { useTranslation } from 'react-i18next';

export default function DepositInterestBreakdown({ breakdown, sym, localeTag, withholdingPct }) {
  const { t } = useTranslation();
  if (!(breakdown && breakdown.gross > 0)) return null;
  return (
    <div className="sm:col-span-2 rounded-xl border border-accent/25 bg-gradient-to-br from-accent/5 to-transparent px-4 py-3 space-y-1.5">
      <div className="flex items-center justify-between text-xs">
        <span className="text-fg-muted">{t('deposits.interest.gross')}</span>
        <span className="font-mono text-fg">{sym}{breakdown.gross.toLocaleString(localeTag, { maximumFractionDigits: 2 })}</span>
      </div>
      <div className="flex items-center justify-between text-xs">
        <span className="text-fg-muted">{t('deposits.interest.stopaj')} (%{Number(withholdingPct) || 0})</span>
        <span className="font-mono text-danger">−{sym}{breakdown.stopaj.toLocaleString(localeTag, { maximumFractionDigits: 2 })}</span>
      </div>
      <div className="flex items-center justify-between text-xs border-t border-border-default/50 pt-1.5">
        <span className="font-medium text-fg">{t('deposits.interest.net')}</span>
        <span className="font-mono font-semibold text-success">{sym}{breakdown.net.toLocaleString(localeTag, { maximumFractionDigits: 2 })}</span>
      </div>
      <div className="flex items-center justify-between text-xs">
        <span className="text-fg-muted">{t('deposits.interest.maturityValue')}</span>
        <span className="font-mono font-semibold text-accent">{sym}{breakdown.maturityValue.toLocaleString(localeTag, { maximumFractionDigits: 2 })}</span>
      </div>
    </div>
  );
}
