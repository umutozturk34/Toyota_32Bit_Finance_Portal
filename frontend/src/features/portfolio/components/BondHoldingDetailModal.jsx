import { useState } from 'react';
import { createPortal } from 'react-dom';
import { useTranslation } from 'react-i18next';
import { motion } from 'framer-motion';
import {
  X,
  Landmark,
  Calendar,
  CalendarClock,
  Percent,
  Repeat,
  Hash,
  Tag,
  TrendingUp,
  Wallet,
  Coins,
  Layers,
  CheckCircle2,
  CircleDot,
  MinusCircle,
} from 'lucide-react';
import { useMoney } from '../../../shared/hooks/useMoney';
import { formatPercentSmart, changeColors, changeBg, getChangeClass } from '../../../shared/utils/formatters';
import { BOND_TYPE_COLORS } from '../../bond/lib/bondConstants';
import { useBondCouponSchedule } from '../hooks/useFixedIncomePositions';

const PAYMENTS_PER_YEAR = { ANNUAL: 1, SEMI_ANNUAL: 2, QUARTERLY: 4, MONTHLY: 12, ZERO_COUPON: 0 };
const CPI_TYPES = new Set(['FLOATING_CPI', 'SUKUK_CPI']);

function formatDate(val, localeTag) {
  if (!val) return '—';
  return new Date(val).toLocaleDateString(localeTag, { timeZone: 'Europe/Istanbul' });
}

function formatRate(val) {
  if (val == null) return '—';
  return `%${Number(val).toFixed(2)}`;
}

function daysUntil(dateStr, nowMs) {
  if (!dateStr) return null;
  return Math.ceil((new Date(dateStr).getTime() - nowMs) / (1000 * 60 * 60 * 24));
}

function StatCell({ icon: Icon, label, value, valueClass = 'text-fg', mono, wrap }) {
  return (
    <div className="space-y-1 min-w-0">
      <span className="flex items-center gap-1.5 text-[11px] text-fg-muted">
        <Icon className="h-3.5 w-3.5 shrink-0" /> {label}
      </span>
      <span className={`block text-sm leading-snug ${mono ? 'font-mono' : 'font-medium'} ${valueClass} ${wrap ? 'whitespace-normal' : 'truncate'}`}>{value}</span>
    </div>
  );
}

const SCHEDULE_STYLE = {
  received: { box: 'bg-success/5 border border-success/15', Icon: CheckCircle2, icon: 'text-success', label: 'text-success' },
  upcoming: { box: 'bg-accent/5 border border-accent/15', Icon: CircleDot, icon: 'text-accent', label: 'text-accent' },
  beforeEntry: { box: 'bg-bg-base/40', Icon: MinusCircle, icon: 'text-fg-subtle', label: 'text-fg-subtle' },
};

const STATUS_KEY = { RECEIVED: 'received', BEFORE_ENTRY: 'beforeEntry', UPCOMING: 'upcoming' };

export default function BondHoldingDetailModal({ bond, portfolioId, onClose }) {
  const { t } = useTranslation();
  const { format: money } = useMoney({ lockBase: true });
  const localeTag = t('common.localeTag');
  // Capture "now" once at mount (lazy initializer) so the schedule/day-count math stays pure across re-renders.
  const [nowMs] = useState(() => Date.now());

  const isSold = !!bond.exitDate;
  const isCpi = CPI_TYPES.has(bond.bondType);
  // Gold-linked bonds are quoted PER CERTIFICATE, so the per-100 clean/dirty-price math below doesn't apply.
  const isPerUnit = bond.bondType === 'GOLD' || bond.bondType === 'SUKUK_GOLD';
  // CPI and gold both ride an index, but the note must name the RIGHT one (a gold bond is not "CPI-linked").
  const indexNoteKey = isPerUnit ? 'portfolio.bonds.coupon.goldNote' : 'portfolio.bonds.coupon.indexNote';
  const isDiscount = bond.bondType === 'DISCOUNTED' || bond.couponFrequency === 'ZERO_COUPON';
  // A discount bill redeems at PAR (100 per bond) at maturity → redemption value = 100 × quantity (adet). The whole
  // return is the price reaching par, so show what you'll get back and the gain to maturity (no coupons involved).
  const costTry = Number(bond.costTry) || 0;
  const redemptionValue = isDiscount ? (Number(bond.quantity) || 0) * 100 : null;
  const returnToMaturity = (redemptionValue != null && costTry > 0) ? redemptionValue - costTry : null;
  const returnPct = (returnToMaturity != null && costTry > 0) ? (returnToMaturity / costTry) * 100 : null;
  // How far the accreted value has climbed from cost toward par — drives the accretion progress bar.
  const accretionPct = (isDiscount && costTry > 0 && redemptionValue != null && redemptionValue > costTry)
    ? Math.min(100, Math.max(0, (((Number(bond.currentValueTry) || costTry) - costTry) / (redemptionValue - costTry)) * 100))
    : 0;
  const pnlClass = getChangeClass(bond.pnlTry);
  const typeColor = BOND_TYPE_COLORS[bond.bondType] || 'bg-accent/10 text-accent border-accent/20';
  const maturityDays = daysUntil(bond.maturityEnd, nowMs);
  const couponDays = daysUntil(bond.nextCouponDate, nowMs);
  // Time-based maturity roadmap (issue → maturity): % of the bond's life elapsed, for every type (the discount
  // bill already has a value-accretion bar; this is the calendar progress all types share).
  const maturityPct = (() => {
    if (!bond.maturityStart || !bond.maturityEnd) return null;
    const s = new Date(`${String(bond.maturityStart).slice(0, 10)}T00:00:00`).getTime();
    const e = new Date(`${String(bond.maturityEnd).slice(0, 10)}T00:00:00`).getTime();
    if (!(e > s)) return null;
    return Math.min(100, Math.max(0, ((nowMs - s) / (e - s)) * 100));
  })();
  const currentPrice = isSold ? bond.exitPrice : bond.currentPriceTry;

  const perYear = PAYMENTS_PER_YEAR[bond.couponFrequency] ?? 2;
  // Current per-payment coupon (TRY) at the LATEST rate — only a headline; each scheduled coupon below is priced
  // at its own historical rate. CPI and gold (isPerUnit) coupons ride the indexed / gold value, not the face
  // quantity, so a flat figure against quantity would badly mislead — suppressed for both (the schedule below
  // shows each coupon on its real indexed/gold base).
  const couponPerPaymentTry = !isCpi && !isPerUnit && perYear > 0 && bond.couponRate != null
    ? (Number(bond.couponRate) / perYear) * Number(bond.quantity)
    : null;

  // The coupon schedule is computed by the BACKEND (single source) — each coupon priced at its own historical
  // per-period rate, reconciling with the couponsReceived total. The UI no longer reconstructs the math.
  const { data: schedule = [] } = useBondCouponSchedule(portfolioId, bond.id);

  const couponFrequencyLabel = bond.couponFrequency
    ? t(`portfolio.bonds.detail.frequency.${bond.couponFrequency}`, { defaultValue: bond.couponFrequency })
    : '—';

  // Value breakdown: how much is nominal (clean) vs coupon income (received + accrued).
  const nominal = Number(bond.nominalValueTry ?? bond.currentValueTry) || 0;
  const accrued = Number(bond.accruedCouponTry) || 0;
  const received = Number(bond.couponsReceivedTry) || 0;
  const couponIncome = accrued + received;
  const breakdownTotal = nominal + couponIncome;
  const nominalShare = breakdownTotal > 0 ? (nominal / breakdownTotal) * 100 : 100;
  const couponShare = 100 - nominalShare;

  // Dirty (kirli) price = clean price + accrued coupon per 100. It's what you'd actually realize selling
  // mid-period, because the işlemiş kupon settles with the trade — you don't have to wait for redemption to
  // collect it. Only meaningful for an open, non-CPI coupon bond with accrued > 0.
  const qtyNum = Number(bond.quantity) || 0;
  // Accrued coupon PER BOND (adet) — the clean price is per adet, so the dirty price adds the per-adet accrued.
  const accruedPerUnit = qtyNum > 0 ? accrued / qtyNum : 0;
  const dirtyPrice = !isSold && !isCpi && !isPerUnit && accruedPerUnit > 0 && currentPrice != null
    ? Number(currentPrice) + accruedPerUnit
    : null;

  return createPortal(
    <div className="fixed inset-0 z-[70] flex items-center justify-center p-3 sm:p-4">
      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        exit={{ opacity: 0 }}
        className="absolute inset-0 modal-overlay backdrop-blur-sm"
        onClick={onClose}
      />
      <motion.div
        initial={{ opacity: 0, scale: 0.97 }}
        animate={{ opacity: 1, scale: 1 }}
        exit={{ opacity: 0, scale: 0.97 }}
        transition={{ duration: 0.18, ease: [0.16, 1, 0.3, 1] }}
        className="relative w-full max-w-lg sm:max-w-4xl max-h-[90dvh] flex flex-col overflow-clip rounded-2xl border border-border-default modal-panel"
      >
        <div className="absolute top-0 left-0 right-0 h-[1px] bg-gradient-to-r from-transparent via-accent/40 to-transparent" />
        <div aria-hidden className="pointer-events-none absolute -top-20 -right-12 h-48 w-48 rounded-full bg-accent/15 blur-[90px] opacity-60" />
        <div aria-hidden className="pointer-events-none absolute -bottom-24 -left-16 h-52 w-52 rounded-full bg-success/10 blur-[90px] opacity-50" />

        <div className="flex items-start justify-between gap-3 px-4 sm:px-6 pt-4 sm:pt-5 pb-3 shrink-0">
          <div className="flex items-center gap-3 min-w-0">
            <span className="flex items-center justify-center w-10 h-10 rounded-xl bg-accent/10 text-accent shrink-0">
              <Landmark className="h-5 w-5" />
            </span>
            <div className="min-w-0">
              <h2 className="text-base sm:text-lg font-bold text-fg leading-tight font-mono truncate">{bond.bondSeriesCode}</h2>
              {bond.bondName && bond.bondName !== bond.bondSeriesCode && (
                <p className="text-xs text-fg-muted truncate">{bond.bondName}</p>
              )}
              {bond.bondIsin && (
                <p className="text-[10px] text-fg-subtle font-mono truncate">{bond.bondIsin}</p>
              )}
            </div>
          </div>
          <div className="flex items-center gap-2 shrink-0">
            {isCpi && (
              <span className="rounded-lg border px-2 py-1 text-[10px] font-semibold tracking-wider bg-warning/15 text-warning border-warning/25">
                {t('portfolio.bonds.coupon.cpiLinked')}
              </span>
            )}
            {bond.bondType && (
              <span className={`rounded-lg border px-2 py-1 text-[10px] font-semibold tracking-wider ${typeColor}`}>
                {t(`market.bond.types.${bond.bondType}`, { defaultValue: bond.bondType })}
              </span>
            )}
            <button
              onClick={onClose}
              aria-label={t('common.close')}
              className="flex items-center justify-center w-8 h-8 rounded-lg text-fg-muted hover:text-fg hover:bg-surface transition-colors bg-transparent border-none cursor-pointer"
            >
              <X className="h-4 w-4" />
            </button>
          </div>
        </div>

        <div className="flex-1 min-h-0 overflow-y-auto px-4 sm:px-6 pb-4 sm:pb-5">
        <section className="rounded-2xl border border-border-default bg-bg-base/50 p-3.5 mb-3">
          <h3 className="text-xs font-semibold uppercase tracking-wider text-fg-muted mb-3">
            {t('portfolio.bonds.detail.positionTitle')}
          </h3>
          <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
            <StatCell icon={Hash} label={t('portfolio.positions.quantityCol')} value={Number(bond.quantity).toLocaleString(localeTag, { maximumFractionDigits: 6 })} mono />
            <StatCell icon={Calendar} label={t('portfolio.positions.entryDateCol')} value={formatDate(bond.entryDate, localeTag)} mono />
            <StatCell icon={Tag} label={t('portfolio.positions.entryPriceCol')} value={money(bond.entryPrice, 'TRY')} mono />
            {isSold && (
              <StatCell icon={Calendar} label={t('portfolio.positions.exitDateLabel')} value={formatDate(bond.exitDate, localeTag)} mono />
            )}
            <StatCell
              icon={Tag}
              label={dirtyPrice != null ? t('portfolio.bonds.detail.cleanPrice') : t('portfolio.bonds.detail.currentPrice')}
              value={money(currentPrice, 'TRY')}
              valueClass={isSold ? 'text-fg-muted italic' : 'text-fg'}
              mono
            />
            {dirtyPrice != null && (
              <StatCell
                icon={Coins}
                label={t('portfolio.bonds.detail.dirtyPrice')}
                value={money(dirtyPrice, 'TRY')}
                valueClass="text-success"
                mono
              />
            )}
            <StatCell
              icon={Wallet}
              label={t('portfolio.positions.marketValueCol')}
              value={money(bond.currentValueTry, 'TRY')}
              valueClass={isSold ? 'text-fg-muted italic' : 'text-fg'}
              mono
            />
            <StatCell
              icon={TrendingUp}
              label={t('portfolio.positions.pnlCol')}
              value={
                <span className="inline-flex items-center gap-1.5 flex-wrap">
                  <span className={changeColors[pnlClass]}>{money(bond.pnlTry, 'TRY')}</span>
                  <span className={`inline-flex items-center rounded px-1 py-0.5 text-[10px] font-medium ${changeBg[pnlClass]} ${changeColors[pnlClass]}`}>
                    {formatPercentSmart(bond.pnlPercent)}
                  </span>
                </span>
              }
              mono
            />
          </div>
        </section>

        {/* Value breakdown + coupon info sit side-by-side on desktop to cut the modal's height (less scroll). */}
        <div className="sm:grid sm:grid-cols-2 sm:gap-4 sm:items-start">
        <section className="rounded-2xl border border-border-default bg-bg-base/50 p-3.5 mb-3">
          <h3 className="text-xs font-semibold uppercase tracking-wider text-fg-muted mb-3">
            {t('portfolio.bonds.coupon.breakdown')}
          </h3>
          {(isCpi || isPerUnit) && (
            <p className="text-[11px] text-warning/90 bg-warning/10 rounded-lg px-3 py-2 mb-3">{t(indexNoteKey)}</p>
          )}
          {isDiscount ? (
            <div className="space-y-3.5">
              {/* Discount bill: its whole return is the price accreting from cost to par. The bar shows how far
                  the value has climbed; cost anchors the left, par (redemption) the right. */}
              <div className="grid grid-cols-3 gap-2">
                <div className="min-w-0">
                  <span className="block text-[10px] uppercase tracking-wider text-fg-subtle">{t('portfolio.bonds.form.totalCost')}</span>
                  <span className="block text-sm font-mono font-semibold text-fg truncate">{money(costTry, 'TRY')}</span>
                </div>
                <div className="min-w-0 text-center">
                  <span className="block text-[10px] uppercase tracking-wider text-fg-subtle">{t('portfolio.bonds.detail.currentValue')}</span>
                  <span className="block text-sm font-mono font-semibold text-accent truncate">{money(bond.currentValueTry, 'TRY')}</span>
                </div>
                <div className="min-w-0 text-right">
                  <span className="block text-[10px] uppercase tracking-wider text-fg-subtle">{t('portfolio.bonds.detail.redemptionValue')}</span>
                  <span className="block text-sm font-mono font-semibold text-success truncate">{money(redemptionValue, 'TRY')}</span>
                </div>
              </div>
              <div className="relative h-2.5 rounded-full bg-bg-base">
                <div
                  className="h-full rounded-full bg-gradient-to-r from-accent to-success transition-all"
                  style={{ width: `${Math.max(accretionPct, 3)}%` }}
                />
                <div
                  className="absolute top-1/2 h-3.5 w-3.5 -translate-y-1/2 rounded-full border-2 border-accent bg-bg-elevated shadow"
                  style={{ left: `calc(${accretionPct}% - 7px)` }}
                />
              </div>
              {returnToMaturity != null && (
                <div className="flex items-center justify-between rounded-xl border border-success/25 bg-gradient-to-r from-success/10 to-transparent px-3.5 py-2.5">
                  <span className="inline-flex items-center gap-1.5 text-[11px] font-medium text-fg-muted">
                    <CheckCircle2 className="h-3.5 w-3.5 text-success" />
                    {t('portfolio.bonds.detail.returnToMaturity')}
                    {maturityDays != null && maturityDays >= 0 && (
                      <span className="text-fg-subtle">· {maturityDays}{t('market.bond.daysSuffix')}</span>
                    )}
                  </span>
                  <span className="font-mono text-base font-bold text-success">
                    {money(returnToMaturity, 'TRY')} <span className="text-[11px] font-semibold">({formatPercentSmart(returnPct)})</span>
                  </span>
                </div>
              )}
            </div>
          ) : (
            <>
              <div className="grid grid-cols-3 gap-3 mb-3">
                <div className="min-w-0">
                  <span className="flex items-center gap-1.5 text-[11px] text-fg-muted"><Layers className="h-3.5 w-3.5 text-accent shrink-0" />{t('portfolio.bonds.coupon.nominalValue')}</span>
                  <span className="block text-sm font-mono font-semibold text-fg truncate">{money(nominal, 'TRY')}</span>
                </div>
                <div className="min-w-0">
                  <span className="flex items-center gap-1.5 text-[11px] text-fg-muted"><Coins className="h-3.5 w-3.5 text-success shrink-0" />{t('portfolio.bonds.coupon.received')}</span>
                  <span className="block text-sm font-mono font-semibold text-success truncate">{money(received, 'TRY')}</span>
                  <span className="text-[10px] text-fg-subtle">{t('portfolio.bonds.coupon.payments', { count: bond.couponsReceivedCount ?? 0 })}</span>
                </div>
                <div className="min-w-0">
                  <span className="flex items-center gap-1.5 text-[11px] text-fg-muted"><CalendarClock className="h-3.5 w-3.5 text-success shrink-0" />{t('portfolio.bonds.coupon.accrued')}</span>
                  <span className="block text-sm font-mono font-semibold text-success truncate">{money(accrued, 'TRY')}</span>
                  {couponPerPaymentTry != null && (
                    <span className="block text-[10px] leading-tight text-fg-subtle">{money(couponPerPaymentTry, 'TRY')} {t('portfolio.bonds.coupon.perPayment')}</span>
                  )}
                </div>
              </div>
              <div className="flex h-2 w-full overflow-hidden rounded-full bg-bg-base">
                <div className="h-full bg-accent transition-all" style={{ width: `${nominalShare}%` }} />
                <div className="h-full bg-success transition-all" style={{ width: `${couponShare}%` }} />
              </div>
              <div className="flex items-center justify-between mt-1 text-[10px] font-mono">
                <span className="text-accent">{t('portfolio.bonds.coupon.nominalValue')} {formatPercentSmart(nominalShare)}</span>
                <span className="text-success">{t('portfolio.bonds.coupon.income')} {formatPercentSmart(couponShare)}</span>
              </div>
            </>
          )}
        </section>

        <section className="rounded-2xl border border-border-default bg-bg-base/50 p-3.5 mb-3">
          <h3 className="text-xs font-semibold uppercase tracking-wider text-fg-muted mb-3">
            {t('portfolio.bonds.detail.couponTitle')}
          </h3>
          <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
            <StatCell icon={Percent} label={t('portfolio.bonds.detail.annualCouponRate')} value={formatRate(bond.couponRate)} mono />
            <StatCell icon={Repeat} label={t('portfolio.bonds.detail.frequencyLabel')} value={couponFrequencyLabel} />
            <StatCell
              icon={CalendarClock}
              label={t('market.bond.couponDateLabel')}
              value={
                <>
                  {formatDate(bond.nextCouponDate, localeTag)}{' '}
                  {couponDays != null && couponDays >= 0 && (
                    <span className="text-fg-subtle whitespace-nowrap">({couponDays}{t('market.bond.daysSuffix')})</span>
                  )}
                </>
              }
              mono
              wrap
            />
            <StatCell icon={Calendar} label={t('market.bond.startLabel')} value={formatDate(bond.maturityStart, localeTag)} mono />
            <StatCell
              icon={Calendar}
              label={t('market.bond.maturityLabel')}
              value={
                <>
                  {formatDate(bond.maturityEnd, localeTag)}{' '}
                  {maturityDays != null && maturityDays >= 0 && (
                    <span className="text-fg-subtle whitespace-nowrap">({maturityDays}{t('market.bond.daysSuffix')})</span>
                  )}
                </>
              }
              mono
              wrap
            />
          </div>
          {maturityPct != null && (
            <div className="mt-4">
              <div className="mb-1.5 flex items-center justify-between gap-2 text-[10px]">
                <span className="font-medium uppercase tracking-wider text-fg-muted">{t('portfolio.bonds.detail.maturityProgress')}</span>
                <span className="font-mono text-fg-muted">
                  %{maturityPct.toFixed(0)}
                  {maturityDays != null && maturityDays >= 0 && (
                    <span className="ml-1.5 text-fg-subtle">· {maturityDays}{t('market.bond.daysSuffix')}</span>
                  )}
                </span>
              </div>
              <div className="h-2 w-full overflow-hidden rounded-full bg-bg-base">
                <div className="h-full rounded-full bg-gradient-to-r from-accent to-accent-bright" style={{ width: `${Math.max(maturityPct, 2)}%` }} />
              </div>
              <div className="mt-1 flex items-center justify-between text-[10px] font-mono text-fg-subtle">
                <span>{formatDate(bond.maturityStart, localeTag)}</span>
                <span>{formatDate(bond.maturityEnd, localeTag)}</span>
              </div>
            </div>
          )}
        </section>
        </div>

        <section className="rounded-2xl border border-border-default bg-bg-base/50 p-3.5">
          <h3 className="text-xs font-semibold uppercase tracking-wider text-fg-muted mb-1">
            {t('portfolio.bonds.detail.scheduleTitle')}
          </h3>
          <p className="text-[11px] text-fg-subtle mb-3">{t('portfolio.bonds.detail.scheduleHint')}</p>
          {schedule.length === 0 ? (
            <p className="text-xs text-fg-muted">
              {(isCpi || isPerUnit) ? t(indexNoteKey) : t('portfolio.bonds.detail.scheduleEmpty')}
            </p>
          ) : (
            <>
              {(isCpi || isPerUnit) && (
                <p className="text-[11px] text-fg-muted mb-2">{t(indexNoteKey)}</p>
              )}
              <ul className="space-y-1.5 max-h-56 overflow-y-auto pr-1">
              {schedule.map((c) => {
                const sKey = STATUS_KEY[c.status] || 'upcoming';
                const style = SCHEDULE_STYLE[sKey];
                const StatusIcon = style.Icon;
                const statusLabel = sKey === 'received'
                  ? t('portfolio.bonds.coupon.scheduleReceived')
                  : sKey === 'beforeEntry'
                    ? t('portfolio.bonds.coupon.scheduleBeforeEntry')
                    : t('portfolio.bonds.detail.coupon.upcoming');
                return (
                  <li
                    key={c.date}
                    className={`flex items-center justify-between gap-3 rounded-lg px-3 py-2 text-xs ${style.box}`}
                  >
                    <span className="flex items-center gap-2 min-w-0">
                      <StatusIcon className={`h-3.5 w-3.5 shrink-0 ${style.icon}`} />
                      <span className={`font-mono truncate ${sKey === 'upcoming' ? 'text-fg font-medium' : 'text-fg-muted'}`}>
                        {formatDate(c.date, localeTag)}
                      </span>
                      {c.ratePer100 != null && (
                        <span className="font-mono text-[10px] text-fg-subtle shrink-0">%{Number(c.ratePer100).toFixed(2)}</span>
                      )}
                    </span>
                    <span className="flex items-center gap-2 shrink-0">
                      <span className={`font-mono ${sKey === 'received' ? 'text-success' : 'text-fg-muted'}`}>
                        {money(c.amountTry, 'TRY')}
                      </span>
                      <span className={`text-[10px] font-medium tracking-wider uppercase ${style.label}`}>{statusLabel}</span>
                    </span>
                  </li>
                );
              })}
              </ul>
            </>
          )}
        </section>
        </div>
      </motion.div>
    </div>,
    document.body,
  );
}
