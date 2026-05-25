import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import { Calendar, Hash, Tag, TrendingUp, TrendingDown, Check, AlertCircle } from 'lucide-react';
import BaseModal from '../../../shared/components/modal/BaseModal';
import Button from '../../../shared/components/buttons/Button';
import DatePickerPopover from '../../../shared/components/form/DatePickerPopover';
import { unifiedMarketService } from '../../../shared/services/unifiedMarketService';
import { useMoney } from '../../../shared/hooks/useMoney';
import { useRateHistory } from '../../../shared/hooks/useRateHistory';
import { extractApiError } from '../../../shared/utils/apiError';
import { ONE_HOUR_MS, toYearMonth, buildPriceIndex, resolveNativeCurrency } from '../lib/positionFormHelpers';
import { useOpenDerivativePosition, useUpdateDerivativePosition } from '../hooks/useDerivativePositions';

const today = () => new Date().toISOString().slice(0, 10);

function AvailabilityHint({ loading, price, currency, t }) {
  const { format: money } = useMoney();
  if (loading) {
    return <div className="h-[30px] rounded-md border border-border-default bg-surface/40 animate-pulse" />;
  }
  if (price != null) {
    return (
      <div className="flex items-center gap-1.5 text-[11px] text-success bg-success/15 rounded-md px-2.5 py-1.5 border border-success/40">
        <Check className="h-3 w-3 shrink-0" />
        <span>{t('portfolio.derivatives.dataAvailable', { price: money(price, currency) })}</span>
      </div>
    );
  }
  return (
    <div className="flex items-center gap-1.5 text-[11px] text-warning bg-warning/15 rounded-md px-2.5 py-1.5 border border-warning/40">
      <AlertCircle className="h-3 w-3 shrink-0" />
      <span>{t('portfolio.derivatives.dataUnavailable')}</span>
    </div>
  );
}

export default function OpenDerivativePositionModal({ portfolioId, isOpen, onClose, lockedContract, editPosition = null }) {
  const { t } = useTranslation();
  const { format: money, currency: displayCurrency } = useMoney();
  const { convertAt, rateAt } = useRateHistory();
  const symbol = (lockedContract?.symbol || lockedContract?.code || '').toUpperCase();
  const meta = lockedContract?.metadata || {};
  const isOption = meta.kind === 'OPTION';
  const currency = meta.currency
    || resolveNativeCurrency({ assetType: 'VIOP', assetCode: symbol, metadata: meta }, lockedContract);
  const inputCurrency = displayCurrency === 'ORIGINAL' ? currency : displayCurrency;

  const isEditing = Boolean(editPosition);
  const editInitialEntryPriceDisplay = useMemo(() => {
    if (!editPosition?.entryPrice) return '';
    const dateStr = editPosition.entryDate;
    const target = displayCurrency === 'ORIGINAL' ? currency : displayCurrency;
    if (target === 'TRY') return String(editPosition.entryPrice);
    const rate = rateAt(target, dateStr);
    if (rate != null && rate > 0) return String(Number(editPosition.entryPrice) / rate);
    return String(editPosition.entryPrice);
  }, [editPosition, displayCurrency, currency, rateAt]);

  const [direction, setDirection] = useState(editPosition?.direction || 'LONG');
  const [quantityLot, setQuantityLot] = useState(editPosition?.quantityLot ? String(editPosition.quantityLot) : '1');
  const [entryDate, setEntryDate] = useState(editPosition?.entryDate || today());
  const [entryPrice, setEntryPrice] = useState(editInitialEntryPriceDisplay);
  const [entryPriceTouched, setEntryPriceTouched] = useState(Boolean(editPosition));
  const [closeEnabled, setCloseEnabled] = useState(false);
  const [closeDate, setCloseDate] = useState(today());
  const [closePrice, setClosePrice] = useState('');
  const [closePriceTouched, setClosePriceTouched] = useState(false);
  const [entryViewMonth, setEntryViewMonth] = useState(() => toYearMonth(editPosition?.entryDate || today()));
  const [closeViewMonth, setCloseViewMonth] = useState(() => toYearMonth(today()));
  const [error, setError] = useState(null);
  const open = useOpenDerivativePosition(portfolioId);
  const update = useUpdateDerivativePosition(portfolioId);

  const entryDateMonth = toYearMonth(entryDate);
  const closeDateMonth = toYearMonth(closeDate);

  const { data: entryViewAvail } = useQuery({
    queryKey: ['marketAvailability', 'VIOP', symbol, entryViewMonth],
    queryFn: () => unifiedMarketService.getMonthlyAvailability('VIOP', symbol, entryViewMonth),
    enabled: Boolean(symbol && entryViewMonth),
    staleTime: ONE_HOUR_MS,
    placeholderData: (prev) => prev,
  });
  const { data: closeViewAvail } = useQuery({
    queryKey: ['marketAvailability', 'VIOP', symbol, closeViewMonth],
    queryFn: () => unifiedMarketService.getMonthlyAvailability('VIOP', symbol, closeViewMonth),
    enabled: Boolean(symbol && closeEnabled && closeViewMonth),
    staleTime: ONE_HOUR_MS,
    placeholderData: (prev) => prev,
  });

  const { data: entryDateAvail, isFetching: entryLoading } = useQuery({
    queryKey: ['marketAvailability', 'VIOP', symbol, entryDateMonth],
    queryFn: () => unifiedMarketService.getMonthlyAvailability('VIOP', symbol, entryDateMonth),
    enabled: Boolean(symbol && entryDateMonth),
    staleTime: ONE_HOUR_MS,
    placeholderData: (prev) => prev,
  });
  const { data: closeDateAvail, isFetching: closeLoading } = useQuery({
    queryKey: ['marketAvailability', 'VIOP', symbol, closeDateMonth],
    queryFn: () => unifiedMarketService.getMonthlyAvailability('VIOP', symbol, closeDateMonth),
    enabled: Boolean(symbol && closeEnabled && closeDateMonth),
    staleTime: ONE_HOUR_MS,
    placeholderData: (prev) => prev,
  });

  const entryViewPrices = useMemo(() => buildPriceIndex(entryViewAvail), [entryViewAvail]);
  const closeViewPrices = useMemo(() => buildPriceIndex(closeViewAvail), [closeViewAvail]);
  const entryDatePrices = useMemo(() => buildPriceIndex(entryDateAvail), [entryDateAvail]);
  const closeDatePrices = useMemo(() => buildPriceIndex(closeDateAvail), [closeDateAvail]);
  const entryHighlights = useMemo(() => new Set(entryViewPrices.keys()), [entryViewPrices]);
  const closeHighlights = useMemo(() => new Set(closeViewPrices.keys()), [closeViewPrices]);

  const todayIso = today();
  const liveTodayInTry = useMemo(() => {
    if (lockedContract?.currentPrice == null) return null;
    const native = Number(lockedContract.currentPrice);
    if (!Number.isFinite(native)) return null;
    if (currency === 'TRY') return native;
    const rate = rateAt(currency, todayIso);
    return rate != null && rate > 0 ? native * rate : native;
  }, [lockedContract?.currentPrice, currency, rateAt, todayIso]);
  const entryNative = useMemo(() => {
    if (entryDate === todayIso && liveTodayInTry != null) return liveTodayInTry;
    return entryDatePrices.get(entryDate);
  }, [entryDate, todayIso, liveTodayInTry, entryDatePrices]);
  const closeNative = useMemo(() => {
    if (closeDate === todayIso && liveTodayInTry != null) return liveTodayInTry;
    return closeDatePrices.get(closeDate);
  }, [closeDate, todayIso, liveTodayInTry, closeDatePrices]);
  const entrySuggestedDisplay = useMemo(
    () => (entryNative != null ? convertAt(entryNative, 'TRY', entryDate, currency) : null),
    [entryNative, entryDate, convertAt, currency],
  );
  const closeSuggestedDisplay = useMemo(
    () => (closeNative != null ? convertAt(closeNative, 'TRY', closeDate, currency) : null),
    [closeNative, closeDate, convertAt, currency],
  );

  const [entrySyncKey, setEntrySyncKey] = useState(null);
  const eKey = entryPriceTouched ? null : `${entryDate}|${entrySuggestedDisplay ?? 'none'}`;
  if (eKey !== null && eKey !== entrySyncKey) {
    setEntrySyncKey(eKey);
    if (entrySuggestedDisplay != null) setEntryPrice(String(entrySuggestedDisplay));
  }
  const [closeSyncKey, setCloseSyncKey] = useState(null);
  const cKey = (!closeEnabled || closePriceTouched) ? null : `${closeDate}|${closeSuggestedDisplay ?? 'none'}`;
  if (cKey !== null && cKey !== closeSyncKey) {
    setCloseSyncKey(cKey);
    if (closeSuggestedDisplay != null) setClosePrice(String(closeSuggestedDisplay));
  }

  const notional = useMemo(() => {
    const p = Number(entryPrice);
    const q = Number(quantityLot);
    const size = Number(meta.contractSize);
    return p > 0 && q > 0 && size > 0 ? p * q * size : null;
  }, [entryPrice, quantityLot, meta.contractSize]);

  const submit = async (e) => {
    e.preventDefault();
    setError(null);
    if (isEditing) {
      const payload = {
        positionId: editPosition.id,
        direction,
        entryDate,
        entryPrice: entryPrice ? Number(entryPrice) : null,
        quantityLot: Number(quantityLot),
        priceCurrency: inputCurrency,
      };
      try {
        await update.mutateAsync(payload);
        onClose?.();
      } catch (err) {
        setError(extractApiError(err, t('portfolio.derivatives.updateFailed')));
      }
      return;
    }
    const payload = {
      contractSymbol: symbol.trim(),
      direction,
      entryDate,
      entryPrice: entryPrice ? Number(entryPrice) : null,
      quantityLot: Number(quantityLot),
      closeDate: closeEnabled ? (closeDate || null) : null,
      closePrice: closeEnabled && closePrice ? Number(closePrice) : null,
      priceCurrency: inputCurrency,
    };
    try {
      await open.mutateAsync(payload);
      onClose?.();
    } catch (err) {
      setError(extractApiError(err, t('portfolio.derivatives.openFailed')));
    }
  };

  const priceLabel = isOption ? t('portfolio.derivatives.premiumOptional') : t('portfolio.derivatives.entryPriceOptional');
  const submitting = isEditing ? update.isPending : open.isPending;
  const title = isEditing ? t('portfolio.derivatives.editEntryTitle') : t('portfolio.derivatives.openTitle');
  const submitLabel = isEditing ? t('portfolio.derivatives.submitUpdate') : t('portfolio.derivatives.submitOpen');

  return (
    <BaseModal isOpen={isOpen} onClose={onClose} title={title}>
      <form onSubmit={submit} className="space-y-4">
        <div className="rounded-lg border border-border-default bg-bg-elevated px-3 py-2">
          <div className="text-sm font-semibold text-fg">{symbol}</div>
          {lockedContract?.name && <div className="text-xs text-fg-muted truncate">{lockedContract.name}</div>}
        </div>

        <div className="grid grid-cols-2 gap-3">
          <div className="space-y-1.5">
            <span className="text-xs font-medium text-fg-muted">{t('portfolio.derivatives.direction')}</span>
            <div className="grid grid-cols-2 gap-1.5">
              <button
                type="button"
                onClick={() => setDirection('LONG')}
                className={`flex items-center justify-center gap-1 rounded-lg py-2 text-xs font-semibold border transition-all cursor-pointer ${
                  direction === 'LONG'
                    ? 'border-emerald-500/40 bg-emerald-500/10 text-emerald-400'
                    : 'border-border-default bg-bg-base text-fg-muted hover:text-fg'
                }`}
              >
                <TrendingUp className="h-3.5 w-3.5" /> {t('portfolio.derivatives.long', 'LONG')}
              </button>
              <button
                type="button"
                onClick={() => setDirection('SHORT')}
                className={`flex items-center justify-center gap-1 rounded-lg py-2 text-xs font-semibold border transition-all cursor-pointer ${
                  direction === 'SHORT'
                    ? 'border-rose-500/40 bg-rose-500/10 text-rose-400'
                    : 'border-border-default bg-bg-base text-fg-muted hover:text-fg'
                }`}
              >
                <TrendingDown className="h-3.5 w-3.5" /> {t('portfolio.derivatives.short', 'SHORT')}
              </button>
            </div>
          </div>
          <label className="space-y-1.5 block">
            <span className="text-xs font-medium text-fg-muted flex items-center gap-1.5">
              <Hash className="h-3 w-3" /> {t('portfolio.derivatives.qty')}
            </span>
            <input
              type="number" min="0.01" step="0.01" required value={quantityLot}
              onChange={(e) => setQuantityLot(e.target.value)}
              className="w-full rounded-lg border border-border-default bg-bg-base px-3 py-2.5 text-sm text-fg font-mono outline-none focus:ring-1 focus:ring-accent/50 transition-all"
            />
          </label>
        </div>

        <div className="space-y-1.5">
          <span className="text-xs font-medium text-fg-muted flex items-center gap-1.5">
            <Calendar className="h-3 w-3" /> {t('portfolio.derivatives.entryDate')}
          </span>
          <DatePickerPopover
            value={entryDate}
            onChange={(iso) => { setEntryDate(iso); setEntryPriceTouched(false); setError(null); }}
            onMonthChange={(y, m) => setEntryViewMonth(`${y}-${String(m + 1).padStart(2, '0')}`)}
            maxDate={today()}
            highlightedDates={entryHighlights}
            loading={entryLoading}
          />
          <AvailabilityHint loading={entryLoading} price={entrySuggestedDisplay} currency={inputCurrency} t={t} />
        </div>

        <label className="space-y-1.5 block">
          <span className="text-xs font-medium text-fg-muted flex items-center gap-1.5">
            <Tag className="h-3 w-3" /> {priceLabel}
          </span>
          <input
            type="number" step="0.0001" value={entryPrice}
            onChange={(e) => { setEntryPrice(e.target.value); setEntryPriceTouched(true); }}
            placeholder={t('portfolio.derivatives.autoFromHistory')}
            className="w-full rounded-lg border border-border-default bg-bg-base px-3 py-2.5 text-sm text-fg font-mono placeholder:text-fg-subtle outline-none focus:ring-1 focus:ring-accent/50 transition-all"
          />
        </label>

        {notional != null && (
          <div className="rounded-lg border border-accent/25 bg-accent/5 px-3 py-2 flex items-center justify-between text-xs">
            <span className="font-semibold text-accent">{t('portfolio.derivatives.notional')}</span>
            <span className="font-mono font-bold text-accent">{money(notional, inputCurrency)}</span>
          </div>
        )}

        {!isEditing && (
        <div className="rounded-lg border border-border-default bg-bg-base/60">
          <button
            type="button"
            onClick={() => setCloseEnabled((v) => !v)}
            className="w-full flex items-center justify-between px-3 py-2 text-xs font-medium text-fg-muted hover:text-fg cursor-pointer bg-transparent border-none"
          >
            <span>{t('portfolio.derivatives.alsoClosePast')}</span>
            <span className={`text-[10px] ${closeEnabled ? 'text-accent' : ''}`}>{closeEnabled ? '−' : '+'}</span>
          </button>
          {closeEnabled && (
            <div className="space-y-3 px-3 pb-3">
              <div className="space-y-1.5">
                <span className="text-xs font-medium text-fg-muted">{t('portfolio.derivatives.closeDate')}</span>
                <DatePickerPopover
                  value={closeDate}
                  onChange={(iso) => { setCloseDate(iso); setClosePriceTouched(false); }}
                  onMonthChange={(y, m) => setCloseViewMonth(`${y}-${String(m + 1).padStart(2, '0')}`)}
                  minDate={entryDate}
                  maxDate={today()}
                  highlightedDates={closeHighlights}
                  loading={closeLoading}
                />
                <AvailabilityHint loading={closeLoading} price={closeSuggestedDisplay} currency={inputCurrency} t={t} />
              </div>
              <label className="space-y-1.5 block">
                <span className="text-xs font-medium text-fg-muted">{t('portfolio.derivatives.closePrice')}</span>
                <input
                  type="number" step="0.0001" value={closePrice}
                  onChange={(e) => { setClosePrice(e.target.value); setClosePriceTouched(true); }}
                  placeholder={t('portfolio.derivatives.autoFromHistory')}
                  className="w-full rounded-lg border border-border-default bg-bg-base px-3 py-2.5 text-sm text-fg font-mono placeholder:text-fg-subtle outline-none focus:ring-1 focus:ring-accent/50 transition-all"
                />
              </label>
            </div>
          )}
        </div>
        )}

        {error && (
          <div className="flex items-center gap-2 text-xs text-danger bg-danger/5 rounded-lg px-3 py-2 border border-danger/20">
            <AlertCircle className="h-3.5 w-3.5 shrink-0" />
            {error}
          </div>
        )}

        <div className="flex flex-col-reverse sm:flex-row justify-end gap-2 pt-1">
          <Button type="button" variant="ghost" onClick={onClose}>{t('common.cancel')}</Button>
          <Button type="submit" disabled={submitting}>
            {submitting ? '...' : submitLabel}
          </Button>
        </div>
      </form>
    </BaseModal>
  );
}
