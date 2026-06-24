import { useState } from 'react';
import { motion } from 'framer-motion';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { Pencil, Trash2, ShoppingBag, RotateCcw, Landmark, Percent, CalendarClock, Coins, Receipt } from 'lucide-react';
import { cardVariants } from '../../../shared/utils/animations';
import { formatPercentSmart, changeColors, changeBg, getChangeClass } from '../../../shared/utils/formatters';
import { useMoney } from '../../../shared/hooks/useMoney';
import Card from '../../../shared/components/card';
import ConfirmDialog from '../../../shared/components/modal/ConfirmDialog';
import PositionStatusBadge from './PositionStatusBadge';
import BondFormModal from './BondFormModal';
import BondSellDialog from './BondSellDialog';
import BondHoldingDetailModal from './BondHoldingDetailModal';
import { formatEntryDate } from '../lib/positionsTableHelpers';
import { useDeleteBond, useReopenBond } from '../hooks/useFixedIncomePositions';
import { toastApiError } from '../../../shared/utils/apiError';

const CPI_TYPES = new Set(['FLOATING_CPI', 'SUKUK_CPI']);
const GOLD_TYPES = new Set(['GOLD', 'SUKUK_GOLD']);
const PAYMENTS_PER_YEAR = { ANNUAL: 1, SEMI_ANNUAL: 2, QUARTERLY: 4, MONTHLY: 12, ZERO_COUPON: 0 };

/** A labeled metric cell in the adaptive bond card. */
function Stat({ label, children, valueClass = 'text-fg' }) {
  return (
    <div className="min-w-0 rounded-lg bg-bg-base/60 px-2.5 py-1.5">
      <p className="text-[10px] text-fg-muted truncate">{label}</p>
      <p className={`text-[11px] font-mono font-medium truncate ${valueClass}`}>{children}</p>
    </div>
  );
}

export default function BondRow({ portfolioId, bond }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { format: money, formatCompact: moneyCompact } = useMoney({ lockBase: true });
  const localeTag = t('common.localeTag');
  const bigMoney = (v) => moneyCompact(v, 'TRY', 100_000);

  const [editOpen, setEditOpen] = useState(false);
  const [sellOpen, setSellOpen] = useState(false);
  const [detailOpen, setDetailOpen] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);

  const deleteMutation = useDeleteBond(portfolioId);
  const reopenMutation = useReopenBond(portfolioId);

  // The optimistic delete drops this holding from the cached list, but React can re-render the unmounting row
  // with `bond` momentarily undefined before reconciliation removes it — guard so a stale render never throws
  // (reading bond.exitDate on undefined white-screened the whole app).
  if (!bond) return null;

  const isSold = !!bond.exitDate;
  // Any bond that reached maturity while still held is auto-redeemed — par for a plain bond, the indexed value for
  // a CPI bond, the gold value for a gold bond — so present it as settled (a redemption badge, no sell action)
  // even though it is not a user-recorded exit. The backend `redeemed` flag is date-driven, no scheduler needed.
  const isRedeemed = !isSold && !!bond.redeemed;
  const pnlClass = getChangeClass(bond.pnlTry);
  const valueClass = isSold ? 'text-fg-muted italic' : 'text-fg';

  const isCpi = CPI_TYPES.has(bond.bondType);
  const isGold = GOLD_TYPES.has(bond.bondType);
  const isTlref = bond.bondType === 'FLOATING_TLREF';
  const isZeroCoupon = bond.couponFrequency === 'ZERO_COUPON' || bond.bondType === 'DISCOUNTED';
  const freqLabel = bond.couponFrequency
    ? t(`portfolio.bonds.coupon.freq.${bond.couponFrequency}`, { defaultValue: bond.couponFrequency })
    : null;
  const couponRate = bond.couponRate != null
    ? Number(bond.couponRate).toLocaleString(localeTag, { maximumFractionDigits: 2 })
    : null;
  const accrued = Number(bond.accruedCouponTry) || 0;
  const received = Number(bond.couponsReceivedTry) || 0;
  const receivedCount = bond.couponsReceivedCount ?? 0;
  // Per-coupon TRY amount = (annual rate / payments per year) × nominal ÷ 100, i.e. perPeriodRate × quantity for a
  // plain/floater bond (face = quantity × 100). CPI and gold coupons ride the INDEXED / gold value, not the face,
  // so a flat per-payment figure against quantity would badly mislead (off by goldPrice/100 or the CPI factor) —
  // left null for both (the type badge already flags it; the schedule below shows each coupon on its real base).
  const perYear = PAYMENTS_PER_YEAR[bond.couponFrequency] ?? 0;
  const perPaymentTry = !isCpi && !isGold && !isZeroCoupon && perYear > 0 && bond.couponRate != null
    ? (Number(bond.couponRate) / perYear) * Number(bond.quantity)
    : null;

  const handleReopen = () => {
    reopenMutation.mutate(bond.id, {
      onError: (err) => toastApiError(err, t('portfolio.bonds.errors.reopenFailed')),
    });
  };

  const handleDelete = async () => {
    try {
      await deleteMutation.mutateAsync(bond.id);
      setConfirmDelete(false);
    } catch (err) {
      setConfirmDelete(false);
      toastApiError(err, t('portfolio.bonds.errors.deleteFailed'));
    }
  };

  return (
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
        {/* Header: identity + badges (left) · value + pnl (right) */}
        <div className="flex items-start justify-between gap-3">
          <button
            type="button"
            onClick={() => navigate(`/bonds/${encodeURIComponent(bond.bondSeriesCode)}`)}
            aria-label={t('portfolio.bonds.detail.goToPage', { code: bond.bondSeriesCode })}
            className="flex items-center gap-2.5 min-w-0 text-left bg-transparent border-none cursor-pointer p-0 hover:opacity-80 transition-opacity"
          >
            <span className="flex items-center justify-center w-9 h-9 rounded-lg bg-accent/10 text-accent shrink-0">
              <Landmark className="h-4 w-4" />
            </span>
            <div className="min-w-0">
              <div className="flex items-center gap-1.5 flex-wrap">
                <p className="font-semibold text-fg leading-tight text-sm font-mono truncate group-hover:text-accent transition-colors">
                  {bond.bondSeriesCode}
                </p>
                <PositionStatusBadge closed={isSold} />
                {isRedeemed && (
                  <span className="inline-flex items-center rounded px-1.5 py-0.5 text-[9px] font-semibold uppercase tracking-wide bg-success/15 text-success ring-1 ring-inset ring-success/25">
                    {t('portfolio.bonds.redeemed')}
                  </span>
                )}
                {isCpi && (
                  <span className="inline-flex items-center rounded px-1.5 py-0.5 text-[9px] font-semibold uppercase tracking-wide bg-warning/15 text-warning ring-1 ring-inset ring-warning/25">
                    {t('portfolio.bonds.coupon.cpiLinked')}
                  </span>
                )}
                {isTlref && (
                  <span className="inline-flex items-center rounded px-1.5 py-0.5 text-[9px] font-semibold uppercase tracking-wide bg-accent/12 text-accent ring-1 ring-inset ring-accent/25">
                    {t('portfolio.bonds.coupon.tlref')}
                  </span>
                )}
              </div>
              {bond.bondName && bond.bondName !== bond.bondSeriesCode && (
                <p className="text-[11px] text-fg-muted truncate">{bond.bondName}</p>
              )}
              {bond.bondIsin && (
                <p className="text-[10px] text-fg-subtle font-mono truncate">{bond.bondIsin}</p>
              )}
            </div>
          </button>
          <div className="text-right shrink-0">
            <p className={`text-sm font-mono font-semibold ${valueClass}`} title={money(bond.currentValueTry, 'TRY')}>
              {isSold ? money(bond.currentValueTry, 'TRY') : bigMoney(bond.currentValueTry)}
            </p>
            <div className="flex items-center justify-end gap-1.5 mt-0.5">
              <span className={`text-[11px] font-mono font-semibold ${changeColors[pnlClass]}`} title={money(bond.pnlTry, 'TRY')}>
                {isSold ? money(bond.pnlTry, 'TRY') : bigMoney(bond.pnlTry)}
              </span>
              <span className={`inline-flex items-center rounded px-1 py-0.5 text-[10px] font-mono font-medium ${changeBg[pnlClass]} ${changeColors[pnlClass]}`}>
                {formatPercentSmart(bond.pnlPercent)}
              </span>
            </div>
          </div>
        </div>

        {/* Metrics — wraps gracefully, never overflows */}
        <div className="grid grid-cols-2 sm:grid-cols-3 xl:grid-cols-4 gap-2">
          <Stat label={t('portfolio.positions.quantityCol')}>
            {Number(bond.quantity).toLocaleString(localeTag, { maximumFractionDigits: 6 })}
          </Stat>
          <Stat label={t('portfolio.positions.entryDateCol')}>{formatEntryDate(bond.entryDate, localeTag)}</Stat>
          {isSold && (
            <Stat label={t('portfolio.positions.exitDateLabel')}>{formatEntryDate(bond.exitDate, localeTag) || '—'}</Stat>
          )}
          <Stat label={t('portfolio.positions.entryPriceCol')}>{money(bond.entryPrice, 'TRY')}</Stat>
          <Stat label={t('portfolio.positions.currentPriceCol')} valueClass={valueClass}>
            {money(isSold ? bond.exitPrice : bond.currentPriceTry, 'TRY')}
          </Stat>
          <Stat label={t('portfolio.bonds.coupon.nominalValue')} valueClass={valueClass}>
            {bigMoney(bond.nominalValueTry ?? bond.currentValueTry)}
          </Stat>
          {bond.maturityStart && (
            <Stat label={t('market.bond.startLabel')}>{formatEntryDate(bond.maturityStart, localeTag)}</Stat>
          )}
          {bond.maturityEnd && (
            <Stat label={t('market.bond.maturityLabel')}>{formatEntryDate(bond.maturityEnd, localeTag)}</Stat>
          )}
        </div>

        {/* Coupon strip — frequency · rate · next payment date · accrued coupon */}
        <div className="flex flex-wrap items-center gap-x-3 gap-y-1.5 rounded-xl bg-bg-base/50 px-3 py-2 ring-1 ring-inset ring-border-default/40">
          <span className="inline-flex items-center gap-1.5 text-[11px]">
            <Percent className="h-3.5 w-3.5 text-accent shrink-0" />
            {isZeroCoupon ? (
              <span className="text-fg-muted">{t('portfolio.bonds.coupon.none')}</span>
            ) : (
              <>
                {freqLabel && <span className="font-mono font-semibold text-fg">{freqLabel}</span>}
                {couponRate != null && (
                  <>
                    <span className="text-fg-subtle">·</span>
                    <span className="font-mono text-fg">%{couponRate}</span>
                  </>
                )}
                {perPaymentTry != null && (
                  <>
                    <span className="text-fg-subtle">·</span>
                    <span className="font-mono text-fg-muted">
                      {money(perPaymentTry, 'TRY')} {t('portfolio.bonds.coupon.perPayment')}
                    </span>
                  </>
                )}
              </>
            )}
          </span>
          {bond.nextCouponDate && !isSold && !isZeroCoupon && (
            <span className="inline-flex items-center gap-1 text-[11px] text-fg-muted">
              <CalendarClock className="h-3.5 w-3.5 shrink-0" />
              {t('portfolio.bonds.coupon.next')}:
              <span className="font-mono text-fg">{formatEntryDate(bond.nextCouponDate, localeTag)}</span>
            </span>
          )}
          {received > 0 && (
            <span className="inline-flex items-center gap-1 text-[11px] text-fg-muted">
              <Coins className="h-3.5 w-3.5 shrink-0 text-success" />
              {t('portfolio.bonds.coupon.received')}:
              <span className="font-mono font-semibold text-success">{money(received, 'TRY')}</span>
              <span className="text-fg-subtle">({t('portfolio.bonds.coupon.payments', { count: receivedCount })})</span>
            </span>
          )}
          {!isSold && accrued > 0 && (
            <span className="inline-flex items-center gap-1 text-[11px] text-fg-muted sm:ml-auto">
              <CalendarClock className="h-3.5 w-3.5 shrink-0 text-warning" />
              {t('portfolio.bonds.coupon.accrued')}:
              <span className="font-mono font-semibold text-success">{money(accrued, 'TRY')}</span>
            </span>
          )}
        </div>

        {/* Actions */}
        <div className="flex justify-end gap-1.5">
          <button onClick={() => setDetailOpen(true)} className="flex items-center justify-center w-8 h-8 rounded-md text-fg-muted bg-surface/70 hover:text-fg hover:bg-surface transition-colors border-none cursor-pointer" aria-label={t('portfolio.bonds.detail.open', { code: bond.bondSeriesCode })} title={t('portfolio.bonds.detail.open', { code: bond.bondSeriesCode })}>
            <Receipt className="h-3.5 w-3.5" />
          </button>
          {!isSold && (
            <button onClick={() => setEditOpen(true)} className="flex items-center justify-center w-8 h-8 rounded-md text-accent bg-accent/10 hover:bg-accent/20 transition-colors border-none cursor-pointer" aria-label={t('common.edit')} title={t('common.edit')}>
              <Pencil className="h-3.5 w-3.5" />
            </button>
          )}
          {!isSold && !isRedeemed && (
            <button onClick={() => setSellOpen(true)} className="flex items-center justify-center w-8 h-8 rounded-md text-warning bg-warning/10 hover:bg-warning/20 transition-colors border-none cursor-pointer" aria-label={t('portfolio.bonds.sell.title')} title={t('portfolio.bonds.sell.title')}>
              <ShoppingBag className="h-3.5 w-3.5" />
            </button>
          )}
          {isSold && (
            <button onClick={handleReopen} className="flex items-center justify-center w-8 h-8 rounded-md text-success bg-success/10 hover:bg-success/20 transition-colors border-none cursor-pointer" aria-label={t('portfolio.bonds.reopen.title')} title={t('portfolio.bonds.reopen.title')}>
              <RotateCcw className="h-3.5 w-3.5" />
            </button>
          )}
          <button onClick={() => setConfirmDelete(true)} className="flex items-center justify-center w-8 h-8 rounded-md text-danger bg-danger/10 hover:bg-danger/20 transition-colors border-none cursor-pointer" aria-label={t('common.delete')} title={t('common.delete')}>
            <Trash2 className="h-3.5 w-3.5" />
          </button>
        </div>
      </div>

      {editOpen && (
        <BondFormModal
          mode="edit"
          portfolioId={portfolioId}
          bond={bond}
          onClose={() => setEditOpen(false)}
        />
      )}
      {sellOpen && (
        <BondSellDialog
          portfolioId={portfolioId}
          bond={bond}
          onClose={() => setSellOpen(false)}
        />
      )}
      {detailOpen && (
        <BondHoldingDetailModal
          bond={bond}
          portfolioId={portfolioId}
          onClose={() => setDetailOpen(false)}
        />
      )}
      <ConfirmDialog
        open={confirmDelete}
        title={t('portfolio.bonds.delete.title')}
        message={t('portfolio.bonds.delete.message', { code: bond.bondSeriesCode })}
        confirmLabel={t('common.delete')}
        loading={deleteMutation.isPending}
        onCancel={() => setConfirmDelete(false)}
        onConfirm={handleDelete}
      />
    </Card>
  );
}
