import { useMemo, useState } from 'react';
import { createPortal } from 'react-dom';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { motion, AnimatePresence } from 'framer-motion';
import { Calendar, Landmark } from 'lucide-react';
import { AlertCircle } from '../../../shared/components/feedback/AnimatedIcons';
import DatePickerPopover from '../../../shared/components/form/DatePickerPopover';
import { useMoney } from '../../../shared/hooks/useMoney';
import { extractApiError } from '../../../shared/utils/apiError';
import {
  MAX_BOND_PRICE_TRY, MAX_QUANTITY, PRICE_DECIMALS, QUANTITY_DECIMALS,
  sanitizeNumberInput, toInputValue,
} from '../../../shared/utils/numberInput';
import {
  todayInputValue, isoToDateInput, toYearMonth,
} from '../lib/positionFormHelpers';
import { useAddBond, useUpdateBond } from '../hooks/useFixedIncomePositions';
import { bondService } from '../../bond/services/bondService';
import BondSeriesPicker from './BondSeriesPicker';
import BondFormHeader from './bondForm/BondFormHeader';
import QuantityField from './bondForm/QuantityField';
import EntryPriceField from './bondForm/EntryPriceField';
import CouponSection from './bondForm/CouponSection';
import TotalCostBanner from './bondForm/TotalCostBanner';
import { toast } from '../../../shared/components/feedback/toastBus';

const CPI_TYPES = new Set(['FLOATING_CPI', 'SUKUK_CPI']);
// Par-floaters carry a periodic ex-coupon price drop the backend can read to infer the real cadence; for these,
// an untouched default is sent as null so the server detects the cadence instead of forcing the type default.
const PAR_FLOATER_TYPES = new Set(['FLOATING_TLREF', 'FLOATING_AUCTION', 'SUKUK_FLOATING']);
const PAYMENTS_PER_YEAR = { ANNUAL: 1, SEMI_ANNUAL: 2, QUARTERLY: 4, MONTHLY: 12, ZERO_COUPON: 0 };
// The clean price may drift at most ±10% from the bond's own quoted price on the entry date — a hypothetical
// purchase still has to clear at a realistic level, so a fat-fingered or speculative price is rejected.
const PRICE_BAND = 0.10;

/** Type-based coupon cadence, mirroring the backend CouponFrequency.defaultFor (the coupon is DB-driven). */
function defaultFreqFor(type) {
  if (type === 'DISCOUNTED') return 'ZERO_COUPON';
  if (type === 'FLOATING_TLREF') return 'QUARTERLY';
  return 'SEMI_ANNUAL';
}

/**
 * Forward-filled clean price on or before {@code isoDate} from the bond's rate history (chronological asc):
 * the price quoted on the purchase day, carrying the prior trading day's quote over a weekend/holiday. Falls
 * back to the earliest stored price when the entry date predates the history. Null when no priced row exists.
 */
function priceOnOrBefore(history, isoDate) {
  if (!isoDate || !Array.isArray(history) || history.length === 0) return null;
  const day = String(isoDate).slice(0, 10);
  let chosen = null;
  for (const h of history) {
    if (h?.price == null) continue;
    const d = String(h.date ?? h.rateDate ?? '').slice(0, 10);
    if (!d) continue;
    if (d <= day) chosen = Number(h.price);
    else break;
  }
  if (chosen == null) {
    const first = history.find((h) => h?.price != null);
    if (first) chosen = Number(first.price);
  }
  return Number.isFinite(chosen) ? chosen : null;
}

export default function BondFormModal({ mode, portfolioId, portfolioPicker, bond, lockSeries = false, onClose, onComplete }) {
  const { t } = useTranslation();
  // Bonds are ALWAYS TRY — lock the base so the global display currency (e.g. USD) never converts the per-100
  // clean price into "$1.65", which made the entry-day hint read a nonsensical "₺$1.65".
  const { format: money, formatCompact } = useMoney({ lockBase: true });
  const isEdit = mode === 'edit';

  const [seriesCode, setSeriesCode] = useState(bond?.bondSeriesCode || '');
  const [seriesName, setSeriesName] = useState(bond?.bondName || '');
  const [isin, setIsin] = useState(bond?.bondIsin || '');
  const [maturityStart, setMaturityStart] = useState(bond?.maturityStart ? isoToDateInput(bond.maturityStart) : null);
  const [maturityEnd, setMaturityEnd] = useState(bond?.maturityEnd ? isoToDateInput(bond.maturityEnd) : null);
  const [bondType, setBondType] = useState(bond?.bondType || null);
  // The bond's published PER-PERIOD coupon (.ORAN) from the catalog — read-only; the user can't change the RATE,
  // only the payment frequency (the data carries no frequency, so it defaults from the type and is overridable).
  // On edit, recover the per-period rate from the stored annual coupon ÷ the stored frequency's payments-per-year.
  const [couponPerPeriod, setCouponPerPeriod] = useState(() => {
    // Market-add preset (not edit): the catalog couponRate IS the per-period .ORAN, use it directly.
    if (!isEdit) return bond?.couponRate != null ? Number(bond.couponRate) : null;
    const annual = bond?.publishedCouponRate != null ? Number(bond.publishedCouponRate)
      : (bond?.couponRate != null ? Number(bond.couponRate) : null);
    const perYear = PAYMENTS_PER_YEAR[bond?.couponFrequency || defaultFreqFor(bond?.bondType)] || 0;
    return annual != null && perYear > 0 ? annual / perYear : null;
  });
  const [couponFrequency, setCouponFrequency] = useState(bond?.couponFrequency || defaultFreqFor(bond?.bondType));
  // Like priceTouched: false until the user actually picks a frequency (on edit we start "touched" so the stored
  // value is preserved). When still false for a par-floater add, the cadence is left to the backend detector.
  const [freqTouched, setFreqTouched] = useState(isEdit);
  // The clean price (per 100 nominal). Suggested from the bond's quote on the entry date; the user may nudge it
  // within ±10%. `priceTouched` switches off auto-suggestion once the user types, and is reset whenever the
  // series or entry date changes so a fresh entry-day quote re-seeds the field.
  const [entryPrice, setEntryPrice] = useState(bond?.entryPrice != null ? String(bond.entryPrice) : '');
  const [priceTouched, setPriceTouched] = useState(isEdit);
  const [baseIndex, setBaseIndex] = useState(bond?.baseIndex ?? null);
  const [quantity, setQuantity] = useState(bond?.quantity != null ? String(bond.quantity) : '');
  const [entryDate, setEntryDate] = useState(isEdit && bond?.entryDate ? isoToDateInput(bond.entryDate) : todayInputValue());
  const [, setViewMonth] = useState(() => toYearMonth(isEdit && bond?.entryDate ? isoToDateInput(bond.entryDate) : todayInputValue()));
  const phase = 'form';
  const [error, setError] = useState(null);

  const addMutation = useAddBond(portfolioId);
  const updateMutation = useUpdateBond(portfolioId);

  // The submit is in-flight: keep the form on screen (no separate "confirming" screen) but lock it down.
  const submitting = addMutation.isPending || updateMutation.isPending;
  const dismissable = phase === 'form' && !submitting;
  const isCpi = CPI_TYPES.has(bondType);
  const isDiscount = bondType === 'DISCOUNTED';
  const isFloating = bondType === 'FLOATING_TLREF' || bondType === 'FLOATING_AUCTION';
  // An untouched par-floater on ADD: let the backend infer the cadence from the ex-coupon price drops rather than
  // forcing the type default. Any explicit pick (freqTouched) or an edit keeps the chosen/stored frequency.
  const freqAutoDetect = !isEdit && !freqTouched && PAR_FLOATER_TYPES.has(bondType);
  // A zero-coupon discount bill has no coupon line at all; everything else shows the (read-only) DB coupon.
  const couponHidden = isDiscount;
  // Annualized view of the per-period coupon, recomputed from the CHOSEN frequency (so the "≈ %x yıllık" caption
  // tracks the selector). Per-payment amount is always the per-period rate; only this annual label moves.
  const publishedAnnualRate = couponPerPeriod != null && PAYMENTS_PER_YEAR[couponFrequency] > 0
    ? couponPerPeriod * PAYMENTS_PER_YEAR[couponFrequency]
    : null;

  // The bond's rate history (shared cache with the detail page) drives the entry-day price suggestion.
  const { data: history = [] } = useQuery({
    queryKey: ['bondRateHistory', isin],
    queryFn: () => bondService.getRateHistory(isin),
    enabled: !!isin,
    staleTime: 5 * 60 * 1000,
  });

  // Suggested clean price = the bond's quote on the entry date (forward-filled), falling back to its latest
  // quoted index when no dated history is available yet.
  const suggestedPrice = useMemo(() => {
    const onDate = priceOnOrBefore(history, entryDate);
    if (onDate != null) return onDate;
    return baseIndex != null && Number.isFinite(baseIndex) ? baseIndex : null;
  }, [history, entryDate, baseIndex]);

  // The ±10% band the entered price must stay within.
  const priceBand = useMemo(() => {
    if (suggestedPrice == null || suggestedPrice <= 0) return null;
    return [suggestedPrice * (1 - PRICE_BAND), suggestedPrice * (1 + PRICE_BAND)];
  }, [suggestedPrice]);

  // The value shown/submitted: the user's typed price once they take the wheel, otherwise the entry-day
  // suggestion derived during render (no effect needed — we never copy the suggestion into state).
  const priceValue = priceTouched
    ? entryPrice
    : (suggestedPrice != null ? toInputValue(suggestedPrice, PRICE_DECIMALS, MAX_BOND_PRICE_TRY) : entryPrice);

  const clampToBand = (value) => {
    if (value === '' || priceBand == null) return value;
    const n = Number(value);
    if (!Number.isFinite(n)) return value;
    if (n < priceBand[0]) return toInputValue(priceBand[0], PRICE_DECIMALS, MAX_BOND_PRICE_TRY);
    if (n > priceBand[1]) return toInputValue(priceBand[1], PRICE_DECIMALS, MAX_BOND_PRICE_TRY);
    return value;
  };

  const resetToSuggested = () => {
    if (suggestedPrice == null) return;
    setEntryPrice(toInputValue(suggestedPrice, PRICE_DECIMALS, MAX_BOND_PRICE_TRY));
    setPriceTouched(false);
    setError(null);
  };

  // Quantity is the number of bonds (adet); one bond is one 100-nominal lot whose value IS the clean price, so the
  // cost is price × quantity (per unit) for every type — mirrors the backend valuation.
  const totalCostTry = useMemo(() => {
    const q = Number(quantity);
    const p = Number(priceValue);
    if (!(q > 0 && p > 0)) return null;
    return q * p;
  }, [quantity, priceValue]);

  // The entry date must fall before the series' maturity end (the backend rejects an on/after-maturity entry as
  // alreadyMatured); cap the picker and re-check in validate so the calendar disables dead dates client-side.
  const maxEntryDate = useMemo(() => {
    const today = todayInputValue();
    if (!maturityEnd) return today;
    const dayBefore = new Date(`${maturityEnd}T00:00:00`);
    dayBefore.setDate(dayBefore.getDate() - 1);
    const cap = dayBefore.toISOString().slice(0, 10);
    return cap < today ? cap : today;
  }, [maturityEnd]);

  const validate = () => {
    if (!seriesCode) return t('portfolio.bonds.errors.seriesRequired');
    const q = Number(quantity);
    if (!quantity || q <= 0 || q > MAX_QUANTITY) return t('portfolio.bonds.errors.quantityInvalid');
    const p = Number(priceValue);
    if (!priceValue || p <= 0 || p > MAX_BOND_PRICE_TRY) return t('portfolio.bonds.errors.priceTooHigh');
    // The price may only move within ±10% of the bond's entry-day quote.
    if (priceBand && (p < priceBand[0] - 1e-6 || p > priceBand[1] + 1e-6)) {
      return t('portfolio.bonds.errors.priceOutOfBand', {
        low: money(priceBand[0], 'TRY'), high: money(priceBand[1], 'TRY'),
      });
    }
    if (!entryDate) return t('portfolio.bonds.errors.dateRequired');
    if (maturityStart && entryDate < maturityStart) return t('portfolio.bonds.errors.beforeIssue');
    if (maturityEnd && entryDate >= maturityEnd) return t('portfolio.bonds.errors.alreadyMatured');
    return null;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    const validationError = validate();
    if (validationError) {
      setError(validationError);
      return;
    }
    setError(null);
    const payload = {
      bondSeriesCode: seriesCode.trim(),
      quantity: Number(quantity),
      entryPrice: Number(priceValue),
      entryDate: entryDate.slice(0, 10),
      // The coupon RATE is never user-editable — the backend reads the published rate from the bond record (so no
      // override is sent). The FREQUENCY is the holder's choice (the data carries none): a discount bill is always
      // ZERO_COUPON, everything else sends the selected cadence.
      couponRateOverride: null,
      couponPaymentFrequency: isDiscount ? 'ZERO_COUPON' : (freqAutoDetect ? null : couponFrequency),
    };
    const mutate = isEdit
      ? () => updateMutation.mutateAsync({ bondId: bond.id, ...payload })
      : () => addMutation.mutateAsync(payload);
    try {
      await mutate();
      toast.success(isEdit ? t('portfolio.bonds.form.success.titleEdit') : t('portfolio.bonds.form.success.titleAdd'),
        t('portfolio.bonds.form.success.subtitle', { code: seriesCode }));
      onComplete?.();
      onClose();
    } catch (err) {
      setError(extractApiError(err, isEdit ? t('portfolio.bonds.errors.updateFailed') : t('portfolio.bonds.errors.addFailed')));
    }
  };

  return createPortal(
    <div className="fixed inset-0 z-[70] flex items-center justify-center p-3 sm:p-4">
      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        exit={{ opacity: 0 }}
        className="absolute inset-0 modal-overlay backdrop-blur-sm"
        onClick={dismissable ? onClose : undefined}
      />
      <motion.div
        initial={{ opacity: 0, scale: 0.97 }}
        animate={{ opacity: 1, scale: 1 }}
        exit={{ opacity: 0, scale: 0.97 }}
        transition={{ duration: 0.18, ease: [0.16, 1, 0.3, 1] }}
        className="relative w-full max-w-sm sm:max-w-3xl max-h-[90dvh] flex flex-col overflow-clip rounded-2xl border border-border-default modal-panel"
      >
        <div className="absolute top-0 left-0 right-0 h-[1px] bg-gradient-to-r from-transparent via-accent/40 to-transparent" />
        <div aria-hidden className="pointer-events-none absolute -top-16 -right-10 h-40 w-40 rounded-full bg-accent/15 blur-[80px] opacity-60" />
        <div aria-hidden className="pointer-events-none absolute -bottom-20 -left-12 h-40 w-40 rounded-full bg-success/10 blur-[80px] opacity-50" />
        <BondFormHeader
          t={t}
          isEdit={isEdit}
          seriesName={seriesName}
          seriesCode={seriesCode}
          bondType={bondType}
          isCpi={isCpi}
          isFloating={isFloating}
          isDiscount={isDiscount}
          dismissable={dismissable}
          onClose={onClose}
        />

        <div className="flex-1 min-h-0 overflow-y-auto px-4 sm:px-6 pb-4 sm:pb-6">
        {phase === 'form' && (
          <form onSubmit={handleSubmit} noValidate className="grid grid-cols-1 sm:grid-cols-2 gap-x-4 gap-y-3 sm:items-start">
            {portfolioPicker && <div className="sm:col-span-2">{portfolioPicker}</div>}

            <div className="space-y-1.5 sm:col-span-2">
              <label className="text-xs font-medium text-fg-muted flex items-center gap-1.5">
                <Landmark className="h-3 w-3" />
                {t('portfolio.bonds.form.fields.series')}
              </label>
              <BondSeriesPicker
                value={seriesCode}
                disabled={isEdit || lockSeries}
                onSelect={(code, picked) => {
                  setSeriesCode(code);
                  setSeriesName(picked?.name || picked?.isinCode || '');
                  setIsin(picked?.isinCode || '');
                  setMaturityStart(picked?.maturityStart ? isoToDateInput(picked.maturityStart) : null);
                  setMaturityEnd(picked?.maturityEnd ? isoToDateInput(picked.maturityEnd) : null);
                  setBondType(picked?.bondType || null);
                  setCouponFrequency(defaultFreqFor(picked?.bondType));
                  // The catalog couponRate is the published PER-PERIOD rate (.ORAN); store it read-only and let the
                  // annualized view derive from the chosen frequency.
                  setCouponPerPeriod(picked?.couponRate != null ? Number(picked.couponRate) : null);
                  setBaseIndex(picked?.baseIndex != null ? Number(picked.baseIndex) : null);
                  // Re-suggest the entry-day price for the newly picked series.
                  setPriceTouched(false);
                  setError(null);
                }}
              />
            </div>

            <div className="space-y-1.5">
              <label className="text-xs font-medium text-fg-muted flex items-center gap-1.5">
                <Calendar className="h-3 w-3" />
                {t('portfolio.bonds.form.fields.entryDate')}
              </label>
              <DatePickerPopover
                value={entryDate}
                onChange={(iso) => { setEntryDate(iso); setPriceTouched(false); setError(null); }}
                onMonthChange={(y, m) => setViewMonth(`${y}-${String(m + 1).padStart(2, '0')}`)}
                minDate={maturityStart || undefined}
                maxDate={maxEntryDate}
              />
            </div>

            <QuantityField
              t={t}
              quantity={quantity}
              onChange={(e) => { setQuantity(sanitizeNumberInput(e.target.value, MAX_QUANTITY, QUANTITY_DECIMALS)); setError(null); }}
            />

            <EntryPriceField
              t={t}
              money={money}
              priceValue={priceValue}
              priceBand={priceBand}
              suggestedPrice={suggestedPrice}
              isCpi={isCpi}
              baseIndex={baseIndex}
              onPriceChange={(e) => {
                setEntryPrice(sanitizeNumberInput(e.target.value, MAX_BOND_PRICE_TRY, PRICE_DECIMALS));
                setPriceTouched(true);
                setError(null);
              }}
              onPriceBlur={() => { if (priceTouched) setEntryPrice((v) => clampToBand(v)); }}
              onReset={resetToSuggested}
            />

            {/* Coupon — read-only, sourced from the bond record (not user-editable) */}
            <CouponSection
              t={t}
              couponHidden={couponHidden}
              couponPerPeriod={couponPerPeriod}
              couponFrequency={couponFrequency}
              freqAutoDetect={freqAutoDetect}
              publishedAnnualRate={publishedAnnualRate}
              isCpi={isCpi}
              isFloating={isFloating}
              onSelectFrequency={(f) => { setCouponFrequency(f); setFreqTouched(true); setError(null); }}
            />

            {totalCostTry != null && (
              <TotalCostBanner t={t} money={money} formatCompact={formatCompact} totalCostTry={totalCostTry} />
            )}

            <AnimatePresence>
              {error && (
                <motion.div
                  initial={{ opacity: 0, height: 0 }}
                  animate={{ opacity: 1, height: 'auto' }}
                  exit={{ opacity: 0, height: 0 }}
                  className="sm:col-span-2 flex items-center gap-2 text-xs text-danger bg-danger/5 rounded-lg px-3 py-2 border border-danger/20"
                >
                  <AlertCircle className="h-3.5 w-3.5 shrink-0" />
                  {error}
                </motion.div>
              )}
            </AnimatePresence>

            <button
              type="submit"
              disabled={submitting}
              className="sm:col-span-2 w-full flex items-center justify-center gap-2 rounded-lg py-3 text-sm font-semibold text-white bg-accent hover:bg-accent-bright transition-all border-none cursor-pointer disabled:cursor-not-allowed disabled:opacity-70"
            >
              {submitting
                ? <span className="h-4 w-4 rounded-full border-2 border-white/40 border-t-white animate-spin" />
                : <Landmark className="h-4 w-4" />}
              {isEdit ? t('portfolio.bonds.form.submitEdit') : t('portfolio.bonds.form.submitAdd')}
            </button>
          </form>
        )}
        </div>
      </motion.div>
    </div>,
    document.body,
  );
}
