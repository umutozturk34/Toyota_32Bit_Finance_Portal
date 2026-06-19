import { useState } from 'react';
import { motion } from 'framer-motion';
import { useTranslation } from 'react-i18next';
import { Pencil, Trash2, XCircle, RotateCcw, Landmark, Calendar, Percent } from 'lucide-react';
import { cardVariants } from '../../../shared/utils/animations';
import { formatPercentSmart, changeColors, changeBg, getChangeClass, currentLocaleTag } from '../../../shared/utils/formatters';
import { useMoney } from '../../../shared/hooks/useMoney';
import Card from '../../../shared/components/card';
import Button from '../../../shared/components/buttons/Button';
import BaseModal from '../../../shared/components/modal/BaseModal';
import ConfirmDialog from '../../../shared/components/modal/ConfirmDialog';
import DatePickerPopover from '../../../shared/components/form/DatePickerPopover';
import PositionStatusBadge from './PositionStatusBadge';
import DepositFormModal from './DepositFormModal';
import { useCloseDeposit, useReopenDeposit, useDeleteDeposit } from '../hooks/useFixedIncomePositions';
import { extractApiError, toastApiError } from '../../../shared/utils/apiError';
import { currencySymbolOf } from '../../../shared/utils/priceCurrency';
import { formatEntryDate } from '../lib/positionsTableHelpers';
import { todayInputValue, isoToDateInput } from '../lib/positionFormHelpers';

const DAY_MS = 86_400_000;

/** A labeled metric cell in the adaptive deposit card. */
function Stat({ label, children, valueClass = 'text-fg' }) {
  return (
    <div className="min-w-0 rounded-lg bg-bg-base/60 px-2.5 py-1.5">
      <p className="text-[10px] text-fg-muted truncate">{label}</p>
      <p className={`text-[11px] font-mono font-medium truncate ${valueClass}`}>{children}</p>
    </div>
  );
}

export default function DepositRow({ deposit, portfolioId }) {
  const { t } = useTranslation();
  const { format: money, formatCompact: moneyCompact } = useMoney({ lockBase: true });
  const localeTag = currentLocaleTag();

  const [editOpen, setEditOpen] = useState(false);
  const [closeOpen, setCloseOpen] = useState(false);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [closeDate, setCloseDate] = useState(todayInputValue());
  const [error, setError] = useState(null);
  // Capture "now" once at mount (lazy initializer) so the maturity-progress math stays pure across re-renders.
  const [nowMs] = useState(() => Date.now());

  const closeMutation = useCloseDeposit(portfolioId);
  const reopenMutation = useReopenDeposit(portfolioId);
  const deleteMutation = useDeleteDeposit(portfolioId);

  const isClosed = deposit.active === false || !!deposit.closedDate;
  const pnlClass = getChangeClass(deposit.pnlTry);
  const sym = currencySymbolOf(deposit.currency);
  const valueTone = isClosed ? 'text-fg-muted italic' : 'text-fg';
  const principalLabel = `${sym}${Number(deposit.principal).toLocaleString(localeTag)} ${deposit.currency}`;
  const bigValue = (v) => moneyCompact(v, 'TRY', 100_000);

  // Maturity progress: how far through the term we are, and how many days remain.
  const start = deposit.startDate ? new Date(deposit.startDate) : null;
  const maturity = deposit.maturityDate ? new Date(deposit.maturityDate) : null;
  let progress = 0;
  let daysLeft = 0;
  let matured = false;
  if (start && maturity && maturity > start) {
    progress = Math.min(1, Math.max(0, (nowMs - start.getTime()) / (maturity.getTime() - start.getTime())));
    daysLeft = Math.ceil((maturity.getTime() - nowMs) / DAY_MS);
    matured = daysLeft <= 0;
  }

  const handleClose = async () => {
    setError(null);
    try {
      await closeMutation.mutateAsync({ depositId: deposit.id, closeDate: closeDate.slice(0, 10) });
      setCloseOpen(false);
    } catch (err) {
      setError(extractApiError(err, t('deposits.errors.closeFailed')));
    }
  };

  const handleDelete = async () => {
    setError(null);
    try {
      await deleteMutation.mutateAsync(deposit.id);
      setDeleteOpen(false);
    } catch (err) {
      setDeleteOpen(false);
      toastApiError(err, t('deposits.errors.deleteFailed'));
    }
  };

  const handleReopen = () => {
    reopenMutation.mutate(deposit.id, {
      onError: (err) => toastApiError(err, t('deposits.errors.reopenFailed')),
    });
  };

  return (
    <>
      <Card
        as={motion.div}
        variants={cardVariants}
        variant="elevated"
        radius="2xl"
        padding="none"
        backdropBlur
        interactive={false}
        className="group"
      >
        <div className="p-3.5 sm:p-4 space-y-3">
          {/* Header: identity + status (left) · value + pnl (right) */}
          <div className="flex items-start justify-between gap-3">
            <div className="flex items-center gap-2.5 min-w-0">
              <span className="flex items-center justify-center w-9 h-9 rounded-lg bg-success/10 text-success shrink-0">
                <Landmark className="h-4 w-4" />
              </span>
              <div className="min-w-0">
                <div className="flex items-center gap-1.5 flex-wrap">
                  <p className="font-semibold text-fg leading-tight text-sm truncate">{principalLabel}</p>
                  <PositionStatusBadge closed={isClosed} isDerivative />
                </div>
                <p className="text-[11px] text-fg-muted truncate">{deposit.indicatorCode || t('deposits.fixedRate')}</p>
              </div>
            </div>
            <div className="text-right shrink-0">
              <p className={`text-sm font-mono font-semibold ${valueTone}`} title={money(deposit.currentValueTry, 'TRY')}>
                {isClosed ? money(deposit.currentValueTry, 'TRY') : bigValue(deposit.currentValueTry)}
              </p>
              <div className="flex items-center justify-end gap-1.5 mt-0.5">
                <span className={`text-[11px] font-mono font-semibold ${changeColors[pnlClass]} ${isClosed ? 'italic' : ''}`} title={money(deposit.pnlTry, 'TRY')}>
                  {isClosed ? money(deposit.pnlTry, 'TRY') : bigValue(deposit.pnlTry)}
                </span>
                <span className={`inline-flex items-center rounded px-1 py-0.5 text-[10px] font-mono font-medium ${changeBg[pnlClass]} ${changeColors[pnlClass]}`}>
                  {formatPercentSmart(deposit.pnlPercent)}
                </span>
              </div>
            </div>
          </div>

          {/* Metrics — wraps gracefully */}
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
            <Stat label={t('deposits.fields.annualRate')}>
              <span className="inline-flex items-center gap-1">
                <Percent className="h-3 w-3 text-success" />
                {Number(deposit.annualRate).toLocaleString(localeTag, { maximumFractionDigits: 4 })}
              </span>
            </Stat>
            <Stat label={t('deposits.fields.startDate')}>{formatEntryDate(deposit.startDate, localeTag)}</Stat>
            <Stat label={t('deposits.fields.maturityDate')}>{formatEntryDate(deposit.maturityDate, localeTag)}</Stat>
            {isClosed ? (
              <Stat label={t('deposits.fields.closedDate')}>{formatEntryDate(deposit.closedDate, localeTag) || '—'}</Stat>
            ) : (
              <Stat label={t('deposits.fields.currentValue')} valueClass={valueTone}>{bigValue(deposit.currentValueTry)}</Stat>
            )}
          </div>

          {/* Interest breakdown — gross · stopaj · net (active deposits with accrued interest) */}
          {!isClosed && Number(deposit.grossInterestTry) > 0 && (
            <div className="flex flex-wrap items-center gap-x-3 gap-y-1 rounded-xl bg-bg-base/50 px-3 py-2 ring-1 ring-inset ring-border-default/40 text-[11px]">
              <span className="inline-flex items-center gap-1 text-fg-muted">
                <Percent className="h-3.5 w-3.5 text-success shrink-0" />
                {t('deposits.interest.gross')}:
                <span className="font-mono text-fg">{money(deposit.grossInterestTry, 'TRY')}</span>
              </span>
              <span className="inline-flex items-center gap-1 text-fg-muted">
                {t('deposits.interest.stopaj')} (%{((Number(deposit.withholdingRate) || 0) * 100).toLocaleString(localeTag, { minimumFractionDigits: 2, maximumFractionDigits: 4 })}):
                <span className="font-mono text-danger">−{money(deposit.withholdingTaxTry, 'TRY')}</span>
              </span>
              <span className="inline-flex items-center gap-1 text-fg-muted sm:ml-auto">
                {t('deposits.interest.net')}:
                <span className="font-mono font-semibold text-success">{money(deposit.netInterestTry, 'TRY')}</span>
              </span>
            </div>
          )}

          {/* Maturity progress — colourful term bar (active deposits only) */}
          {!isClosed && start && maturity && (
            <div className="space-y-1">
              <div className="flex items-center justify-between text-[10px]">
                <span className="text-fg-muted">{t('deposits.term.progress')}</span>
                <span className={matured ? 'text-warning font-medium' : 'text-accent font-medium'}>
                  {matured ? t('deposits.term.matured') : t('deposits.term.daysLeft', { count: daysLeft })}
                </span>
              </div>
              <div className="h-1.5 w-full overflow-hidden rounded-full bg-bg-base">
                <div
                  className={`h-full rounded-full transition-all ${matured ? 'bg-warning' : 'bg-gradient-to-r from-success to-accent'}`}
                  style={{ width: `${Math.round(progress * 100)}%` }}
                />
              </div>
            </div>
          )}

          {/* Actions */}
          <div className="flex justify-end gap-1.5">
            {!isClosed && (
              <button onClick={() => setEditOpen(true)} className="flex items-center justify-center w-8 h-8 rounded-md text-accent bg-accent/10 hover:bg-accent/20 transition-colors border-none cursor-pointer" aria-label={t('common.edit')} title={t('common.edit')}>
                <Pencil className="h-3.5 w-3.5" />
              </button>
            )}
            {!isClosed && (
              <button onClick={() => { setCloseDate(todayInputValue()); setCloseOpen(true); }} className="flex items-center justify-center w-8 h-8 rounded-md text-warning bg-warning/10 hover:bg-warning/20 transition-colors border-none cursor-pointer" aria-label={t('deposits.actions.close')} title={t('deposits.actions.close')}>
                <XCircle className="h-3.5 w-3.5" />
              </button>
            )}
            {isClosed && (
              <button onClick={handleReopen} className="flex items-center justify-center w-8 h-8 rounded-md text-success bg-success/10 hover:bg-success/20 transition-colors border-none cursor-pointer" aria-label={t('deposits.actions.reopen')} title={t('deposits.actions.reopen')}>
                <RotateCcw className="h-3.5 w-3.5" />
              </button>
            )}
            <button onClick={() => setDeleteOpen(true)} className="flex items-center justify-center w-8 h-8 rounded-md text-danger bg-danger/10 hover:bg-danger/20 transition-colors border-none cursor-pointer" aria-label={t('common.delete')} title={t('common.delete')}>
              <Trash2 className="h-3.5 w-3.5" />
            </button>
          </div>
        </div>
      </Card>

      {editOpen && (
        <DepositFormModal
          mode="edit"
          portfolioId={portfolioId}
          deposit={{ ...deposit, startDate: isoToDateInput(deposit.startDate), maturityDate: isoToDateInput(deposit.maturityDate) }}
          onClose={() => setEditOpen(false)}
        />
      )}

      <BaseModal
        isOpen={closeOpen}
        onClose={() => setCloseOpen(false)}
        icon={XCircle}
        title={t('deposits.closeDialog.title')}
        subtitle={principalLabel}
        footer={(
          <div className="flex flex-col-reverse sm:flex-row sm:justify-end gap-2">
            <Button variant="secondary" onClick={() => setCloseOpen(false)}>{t('common.cancel')}</Button>
            <Button variant="warning" onClick={handleClose} loading={closeMutation.isPending}>{t('deposits.closeDialog.confirm')}</Button>
          </div>
        )}
      >
        <div className="space-y-4">
          <p className="text-xs text-fg-muted leading-relaxed">{t('deposits.closeDialog.hint')}</p>
          <div className="space-y-1.5">
            <span className="text-xs font-medium text-fg-muted flex items-center gap-1.5">
              <Calendar className="h-3 w-3" /> {t('deposits.closeDialog.dateLabel')}
            </span>
            <DatePickerPopover
              value={closeDate}
              onChange={(iso) => { setCloseDate(iso); setError(null); }}
              minDate={isoToDateInput(deposit.startDate)}
              maxDate={todayInputValue()}
            />
          </div>
          {error && (
            <div className="text-xs text-danger bg-danger/10 border border-danger/20 px-3 py-2 rounded-lg">{error}</div>
          )}
        </div>
      </BaseModal>

      <ConfirmDialog
        open={deleteOpen}
        title={t('deposits.deleteDialog.title')}
        message={t('deposits.deleteDialog.message')}
        confirmLabel={t('common.delete')}
        variant="danger"
        loading={deleteMutation.isPending}
        onConfirm={handleDelete}
        onCancel={() => setDeleteOpen(false)}
      />
    </>
  );
}
