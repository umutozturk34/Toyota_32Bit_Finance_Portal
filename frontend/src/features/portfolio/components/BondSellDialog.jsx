import { useMemo, useState } from 'react';
import { createPortal } from 'react-dom';
import { useTranslation } from 'react-i18next';
import { motion, AnimatePresence } from 'framer-motion';
import { X, Calendar, Tag, ShoppingBag } from 'lucide-react';
import { AlertCircle } from '../../../shared/components/feedback/AnimatedIcons';
import DatePickerPopover from '../../../shared/components/form/DatePickerPopover';
import { useMoney } from '../../../shared/hooks/useMoney';
import { extractApiError } from '../../../shared/utils/apiError';
import { MAX_BOND_PRICE_TRY, PRICE_DECIMALS, sanitizeNumberInput } from '../../../shared/utils/numberInput';
import { todayInputValue, isoToDateInput } from '../lib/positionFormHelpers';
import { useSellBond } from '../hooks/useFixedIncomePositions';

export default function BondSellDialog({ portfolioId, bond, onClose }) {
  const { t } = useTranslation();
  const { format: money } = useMoney({ lockBase: true });
  const [exitDate, setExitDate] = useState(todayInputValue());
  const [, setViewMonth] = useState(() => todayInputValue().slice(0, 7));
  const [exitPrice, setExitPrice] = useState(bond?.currentPriceTry != null ? String(bond.currentPriceTry) : '');
  const [error, setError] = useState(null);
  const sellMutation = useSellBond(portfolioId);

  const entryDateInput = bond?.entryDate ? isoToDateInput(bond.entryDate) : undefined;
  const maturityEndInput = bond?.maturityEnd ? isoToDateInput(bond.maturityEnd) : undefined;

  // A free sale may happen any day from entry up to and including maturity, but never in the future; cap the picker
  // at the earlier of today and the maturity date so dead future days are disabled client-side.
  const maxExitDate = useMemo(() => {
    const today = todayInputValue();
    if (!maturityEndInput) return today;
    return maturityEndInput < today ? maturityEndInput : today;
  }, [maturityEndInput]);

  // Quantity is the number of bonds (adet); proceeds = exit price × quantity (per unit) for every type.
  const proceeds = useMemo(() => {
    const q = Number(bond?.quantity);
    const p = Number(exitPrice);
    if (!(q > 0 && p > 0)) return null;
    return q * p;
  }, [bond?.quantity, exitPrice]);

  const submit = async (e) => {
    e.preventDefault();
    const p = Number(exitPrice);
    if (!exitDate) { setError(t('portfolio.bonds.errors.dateRequired')); return; }
    if (!exitPrice || p <= 0) { setError(t('portfolio.bonds.errors.priceInvalid')); return; }
    // Exit price is a clean price per 100 nominal: cap at the backend bond ceiling so a mistyped total-value is
    // caught before the round-trip (sell() now enforces the same 100,000 bound server-side).
    if (p > MAX_BOND_PRICE_TRY) { setError(t('portfolio.bonds.errors.priceTooHigh')); return; }
    if (entryDateInput && new Date(exitDate) < new Date(entryDateInput)) {
      setError(t('portfolio.bonds.errors.exitBeforeEntry'));
      return;
    }
    // A free sale is allowed any time before maturity, but not after it: an exit past maturityEnd is invalid.
    if (maturityEndInput && new Date(exitDate) > new Date(maturityEndInput)) {
      setError(t('portfolio.bonds.errors.exitAfterMaturity'));
      return;
    }
    setError(null);
    try {
      await sellMutation.mutateAsync({ bondId: bond.id, exitDate: exitDate.slice(0, 10), exitPrice: p });
      onClose();
    } catch (err) {
      setError(extractApiError(err, t('portfolio.bonds.errors.sellFailed')));
    }
  };

  return createPortal(
    <div className="fixed inset-0 z-[80] flex items-center justify-center p-3 sm:p-4">
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
        className="relative w-full max-w-sm max-h-[90dvh] flex flex-col overflow-clip rounded-2xl border border-border-default modal-panel"
      >
        <div className="absolute top-0 left-0 right-0 h-[1px] bg-gradient-to-r from-transparent via-warning/40 to-transparent" />
        <div className="flex items-center justify-between px-4 sm:px-6 pt-4 sm:pt-6 pb-4 shrink-0">
          <div className="flex items-center gap-3">
            <div className="flex items-center justify-center w-9 h-9 rounded-lg bg-warning/10">
              <ShoppingBag className="h-4 w-4 text-warning" />
            </div>
            <div>
              <h2 className="text-base font-semibold text-fg">{t('portfolio.bonds.sell.title')}</h2>
              <p className="text-xs text-fg-muted font-mono truncate">{bond?.bondSeriesCode}</p>
            </div>
          </div>
          <button
            onClick={onClose}
            aria-label={t('common.close')}
            className="flex items-center justify-center w-8 h-8 rounded-lg text-fg-muted hover:text-fg hover:bg-surface transition-colors bg-transparent border-none cursor-pointer"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        <form onSubmit={submit} noValidate className="flex-1 min-h-0 overflow-y-auto px-4 sm:px-6 pb-4 sm:pb-6 space-y-4">
          <div className="space-y-1.5">
            <label className="text-xs font-medium text-fg-muted flex items-center gap-1.5">
              <Calendar className="h-3 w-3" />
              {t('portfolio.bonds.sell.exitDate')}
            </label>
            <DatePickerPopover
              value={exitDate}
              onChange={(iso) => { setExitDate(iso); setError(null); }}
              onMonthChange={(y, m) => setViewMonth(`${y}-${String(m + 1).padStart(2, '0')}`)}
              minDate={entryDateInput}
              maxDate={maxExitDate}
            />
          </div>

          <div className="space-y-1.5">
            <label className="text-xs font-medium text-fg-muted flex items-center justify-between gap-1.5">
              <span className="inline-flex items-center gap-1.5">
                <Tag className="h-3 w-3" />
                {t('portfolio.bonds.sell.exitPrice')}
              </span>
              <span className="font-mono text-[10px] uppercase tracking-wider text-accent">
                {t('portfolio.bonds.form.fields.perUnit', { defaultValue: '1 adet' })}
              </span>
            </label>
            <div className="relative">
              <span className="absolute left-3 top-1/2 -translate-y-1/2 text-fg-subtle font-mono text-sm pointer-events-none">₺</span>
              <input
                type="number"
                step="any"
                min="0"
                max={MAX_BOND_PRICE_TRY}
                inputMode="decimal"
                value={exitPrice}
                onChange={(e) => { setExitPrice(sanitizeNumberInput(e.target.value, MAX_BOND_PRICE_TRY, PRICE_DECIMALS)); setError(null); }}
                placeholder="0.00"
                className="w-full rounded-lg border border-border-default bg-bg-base pl-7 pr-3 py-2.5 text-sm text-fg font-mono placeholder:text-fg-subtle outline-none focus:ring-1 focus:ring-accent/50 transition-all"
              />
            </div>
          </div>

          {proceeds != null && (
            <div className="rounded-xl border border-warning/30 bg-gradient-to-r from-warning/5 to-transparent px-4 py-3 flex items-center justify-between gap-3 min-w-0">
              <span className="text-xs font-semibold text-warning shrink-0">{t('portfolio.bonds.sell.proceeds')}</span>
              <span className="text-lg font-bold font-mono text-warning truncate" title={money(proceeds, 'TRY')}>{money(proceeds, 'TRY')}</span>
            </div>
          )}

          <AnimatePresence>
            {error && (
              <motion.div
                initial={{ opacity: 0, height: 0 }}
                animate={{ opacity: 1, height: 'auto' }}
                exit={{ opacity: 0, height: 0 }}
                className="flex items-center gap-2 text-xs text-danger bg-danger/5 rounded-lg px-3 py-2 border border-danger/20"
              >
                <AlertCircle className="h-3.5 w-3.5 shrink-0" />
                {error}
              </motion.div>
            )}
          </AnimatePresence>

          <div className="flex gap-2">
            <button
              type="button"
              onClick={onClose}
              className="flex-1 rounded-lg py-2.5 text-sm font-semibold text-fg border border-border-default bg-bg-base hover:bg-surface transition-all cursor-pointer"
            >
              {t('common.cancel')}
            </button>
            <button
              type="submit"
              disabled={sellMutation.isPending}
              className="flex-1 flex items-center justify-center gap-2 rounded-lg py-2.5 text-sm font-semibold text-white bg-warning hover:bg-warning/90 transition-all border-none cursor-pointer disabled:opacity-50"
            >
              <ShoppingBag className="h-4 w-4" />
              {sellMutation.isPending ? t('confirmDialog.processing') : t('portfolio.bonds.sell.confirm')}
            </button>
          </div>
        </form>
      </motion.div>
    </div>,
    document.body,
  );
}
