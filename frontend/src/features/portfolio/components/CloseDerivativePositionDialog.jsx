import { useMemo, useState } from 'react';
import { Calendar, RotateCcw, Tag, TrendingUp, TrendingDown, Wand2, XCircle } from 'lucide-react';
import DatePickerPopover from '../../../shared/components/form/DatePickerPopover';
import BaseModal from '../../../shared/components/modal/BaseModal';
import Button from '../../../shared/components/buttons/Button';
import { extractApiError } from '../../../shared/utils/apiError';
import { useRateHistory } from '../../../shared/hooks/useRateHistory';
import { currencySymbolOf } from '../../../shared/utils/priceCurrency';
import {
  useCloseDerivativePosition,
  useReopenDerivativePosition,
  useUpdateCloseDerivativePosition,
} from '../hooks/useDerivativePositions';
import { usePositionCloseForm, formatDateLabel, todayIso } from '../hooks/usePositionCloseForm';
import { resolveNativeCurrency } from '../lib/positionFormHelpers';
import { sanitizeNumberInput, MAX_MONEY, LOT_DECIMALS, PRICE_DECIMALS } from '../../../shared/utils/numberInput';

export default function CloseDerivativePositionDialog({ portfolioId, position, onClose }) {
  const entryDateIso = position?.entryDate ? String(position.entryDate).slice(0, 10) : null;
  const isAlreadyClosed = position?.assetName && position.assetName.includes('KAPALI');
  const closeOpen = useCloseDerivativePosition(portfolioId);
  const updateClosed = useUpdateCloseDerivativePosition(portfolioId);
  const reopen = useReopenDerivativePosition(portfolioId);
  const close = isAlreadyClosed ? updateClosed : closeOpen;

  const direction = (position?.assetName && position.assetName.split(' · ')[0]) || 'LONG';
  const isLong = direction === 'LONG';
  const qty = Number(position?.quantity || position?.quantityLot || 1);
  const entryPrice = Number(position?.entryPrice || 0);
  const symbol = position?.contractSymbol || position?.assetCode;
  const liveSuggested = position?.currentPriceTry != null ? Number(position.currentPriceTry) : null;

  const nativeCurrency = resolveNativeCurrency({
    assetType: 'VIOP',
    assetCode: symbol || position?.assetCode,
    metadata: position?.metadata,
  }, position);

  const { convertAt } = useRateHistory();
  const suggestedPriceInDisplay = liveSuggested != null
    ? Number(convertAt(liveSuggested, 'TRY', todayIso(), nativeCurrency) ?? liveSuggested)
    : null;
  // entryPrice is stored in TRY; the close price is entered/shown in the display (native) currency, so
  // the realized-P&L preview must compare like-with-like — convert the entry to display at its entry date.
  const entryPriceInDisplay = entryPrice
    ? Number(convertAt(entryPrice, 'TRY', entryDateIso || todayIso(), nativeCurrency) ?? entryPrice)
    : 0;

  const form = usePositionCloseForm({
    availabilityAssetType: 'VIOP',
    availabilityAssetCode: symbol || position?.assetCode,
    entryDateIso,
    initialPrice: suggestedPriceInDisplay != null ? String(suggestedPriceInDisplay) : '',
    liveSuggestedPriceTry: liveSuggested,
    nativeCurrency,
  });

  const {
    t, money, inputCurrency, localeTag,
    today: todayDate, isToday,
    date: closeDate, setDate: setCloseDate,
    price: closePrice, setPrice: setClosePrice,
    parsedPrice: parsedClosePrice, validPrice: validClosePrice, validDate: validCloseDate,
    setPriceTouched,
    error, setError,
    highlightedDates, viewLoading,
    dateSuggestedInDisplay: closeDateSuggestedInDisplay,
    applyDatePrice,
    handleMonthChange,
    datePresets,
  } = form;

  const [closeQty, setCloseQty] = useState(() => String(qty));
  const parsedCloseQty = Number(closeQty);
  const validCloseQty = !isAlreadyClosed
      ? (Number.isFinite(parsedCloseQty) && parsedCloseQty > 0 && parsedCloseQty <= qty)
      : true;
  const effectiveQty = !isAlreadyClosed && validCloseQty ? parsedCloseQty : qty;
  const isPartial = !isAlreadyClosed && validCloseQty && parsedCloseQty < qty;

  const priceDelta = validClosePrice && entryPriceInDisplay > 0
    ? ((parsedClosePrice - entryPriceInDisplay) / entryPriceInDisplay) * 100
    : null;

  // sizeTimesQty is a contract-units ratio (entryValueTry / entryPrice are BOTH TRY, so they cancel);
  // it must keep the raw TRY entryPrice — converting it would mismatch the TRY entryValueTry.
  const sizeTimesQty = useMemo(() => {
    const notional = Number(position?.entryValueTry);
    if (!Number.isFinite(notional) || !Number.isFinite(entryPrice) || entryPrice <= 0) return effectiveQty;
    const sizePerLot = notional / entryPrice / qty;
    return sizePerLot * effectiveQty;
  }, [position?.entryValueTry, entryPrice, qty, effectiveQty]);

  const realizedPnl = useMemo(() => {
    const exit = Number(closePrice);
    if (!Number.isFinite(exit) || !Number.isFinite(entryPriceInDisplay) || entryPriceInDisplay <= 0) return null;
    const diff = isLong ? exit - entryPriceInDisplay : entryPriceInDisplay - exit;
    return diff * sizeTimesQty;
  }, [closePrice, entryPriceInDisplay, isLong, sizeTimesQty]);

  const realizedPnlPercent = useMemo(() => {
    if (realizedPnl == null || entryPriceInDisplay <= 0) return null;
    const notional = entryPriceInDisplay * sizeTimesQty;
    return notional > 0 ? (realizedPnl / notional) * 100 : null;
  }, [realizedPnl, entryPriceInDisplay, sizeTimesQty]);

  if (!position) return null;

  const handleReopen = async () => {
    setError(null);
    try {
      await reopen.mutateAsync(position.id);
      onClose?.();
    } catch (err) {
      setError(extractApiError(err, t('portfolio.derivatives.reopenFailed')));
    }
  };

  const submit = async (e) => {
    e.preventDefault();
    setError(null);
    // Close price is optional (blank → resolved from history), but a supplied price must be strictly
    // positive. Validated in JS (not native min=) so the message is translated.
    const closePriceNum = closePrice ? Number(closePrice) : null;
    if (closePriceNum != null && !(closePriceNum > 0)) {
      setError(t('portfolio.derivatives.priceMustBePositive'));
      return;
    }
    try {
      await close.mutateAsync({
        positionId: position.id,
        closeDate,
        closePrice: closePriceNum,
        closeQuantityLot: !isAlreadyClosed && isPartial ? parsedCloseQty : null,
        priceCurrency: inputCurrency,
      });
      onClose?.();
    } catch (err) {
      setError(extractApiError(err, t('portfolio.derivatives.closeFailed', 'Pozisyon kapatılamadı')));
    }
  };

  return (
    <BaseModal
      isOpen={Boolean(position)}
      onClose={onClose}
      icon={XCircle}
      title={isAlreadyClosed
        ? t('portfolio.derivatives.editCloseTitle', 'Kapanışı Düzenle')
        : t('portfolio.derivatives.closeTitle', 'Pozisyon Kapat')}
      subtitle={symbol}
    >
      <form onSubmit={submit} className="space-y-4">
        <div className={`rounded-lg border px-3 py-2.5 ${
          isLong
            ? 'border-emerald-500/30 bg-emerald-500/5'
            : 'border-rose-500/30 bg-rose-500/5'
        }`}>
          <div className="flex items-center justify-between gap-2">
            <div className="flex items-center gap-2">
              <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded text-[11px] font-bold ${
                isLong
                  ? 'bg-emerald-500/20 text-emerald-400'
                  : 'bg-rose-500/20 text-rose-400'
              }`}>
                {isLong
                  ? <TrendingUp className="h-3 w-3" />
                  : <TrendingDown className="h-3 w-3" />}
                {direction}
              </span>
              <span className="text-xs text-fg-muted font-mono">{qty} lot</span>
            </div>
            <div className="text-right">
              <div className="text-[10px] text-fg-muted uppercase tracking-wide">
                {t('portfolio.derivatives.entryPrice', 'Giriş')}
              </div>
              <div className="text-sm font-mono font-semibold text-fg">
                {money(entryPriceInDisplay, inputCurrency)}
              </div>
            </div>
          </div>
          {entryDateIso && (
            <div className="mt-2 pt-2 border-t border-border-default flex items-center justify-between text-[10px]">
              <span className="text-fg-muted uppercase tracking-wide">
                {t('portfolio.sell.entryDateLabel', { defaultValue: 'Giriş tarihi' })}
              </span>
              <span className="font-mono text-fg font-semibold">{formatDateLabel(entryDateIso, localeTag)}</span>
            </div>
          )}
        </div>

        {!isAlreadyClosed && qty > 0 && (
          <div className="space-y-1.5">
            <div className="flex items-center justify-between gap-2 flex-wrap">
              <span className="text-xs font-medium text-fg-muted flex items-center gap-1.5">
                <Tag className="h-3 w-3" /> {t('portfolio.derivatives.closeQtyLabel', 'Kapatılacak lot')}
              </span>
              <div className="flex gap-1 flex-wrap">
                {[
                  { id: '25', factor: 0.25 },
                  { id: '50', factor: 0.5 },
                  { id: '75', factor: 0.75 },
                  { id: 'max', factor: 1 },
                ].map(({ id, factor }) => {
                  const target = factor >= 1 ? qty : Math.round(qty * factor * 1e6) / 1e6;
                  const active = validCloseQty && Math.abs(parsedCloseQty - target) < 1e-6;
                  return (
                    <button
                      key={id}
                      type="button"
                      onClick={() => setCloseQty(String(target))}
                      className={`text-[10px] font-mono font-semibold px-2 py-0.5 rounded transition-all border-none cursor-pointer ${
                        active
                          ? 'bg-accent/20 text-accent'
                          : 'bg-bg-base text-fg-muted hover:bg-bg-elevated hover:text-fg'
                      }`}
                    >
                      {id === 'max' ? 'MAX' : `${id}%`}
                    </button>
                  );
                })}
              </div>
            </div>
            <input
              type="number"
              max={qty}
              step="any"
              inputMode="decimal"
              value={closeQty}
              onChange={(e) => setCloseQty(sanitizeNumberInput(e.target.value, qty, LOT_DECIMALS))}
              placeholder="0.00"
              className="w-full rounded-lg border border-border-default bg-bg-base px-3 py-2.5 text-sm text-fg font-mono placeholder:text-fg-subtle outline-none focus:ring-1 focus:ring-accent/50 transition-all"
            />
            {!validCloseQty && closeQty !== '' && (
              <p className="text-[10px] text-danger">
                {t('portfolio.derivatives.closeQtyHint', { max: qty, defaultValue: 'Miktar 0 ile {{max}} arasında olmalı' })}
              </p>
            )}
            {isPartial && (
              <p className="text-[10px] text-fg-muted">
                {t('portfolio.derivatives.partialHint', { remaining: qty - parsedCloseQty, defaultValue: 'Kalan {{remaining}} lot açık devam edecek' })}
              </p>
            )}
          </div>
        )}

        <div className="space-y-1.5">
          <div className="flex items-center justify-between gap-2 flex-wrap">
            <span className="text-xs font-medium text-fg-muted flex items-center gap-1.5">
              <Calendar className="h-3 w-3" /> {t('portfolio.derivatives.closeDate', 'Kapanış tarihi')}
            </span>
            <div className="flex gap-1 flex-wrap">
              {datePresets.map(({ id, iso, label }) => {
                const active = closeDate === iso;
                return (
                  <button
                    key={id}
                    type="button"
                    onClick={() => { setCloseDate(iso); setPriceTouched(false); }}
                    className={`text-[10px] font-semibold px-2 py-0.5 rounded transition-all border-none cursor-pointer ${
                      active
                        ? 'bg-accent/20 text-accent'
                        : 'bg-bg-base text-fg-muted hover:bg-bg-elevated hover:text-fg'
                    }`}
                  >
                    {label}
                  </button>
                );
              })}
            </div>
          </div>
          <DatePickerPopover
            value={closeDate}
            onChange={(iso) => { setCloseDate(iso); setPriceTouched(false); }}
            minDate={entryDateIso || undefined}
            maxDate={todayDate}
            onMonthChange={handleMonthChange}
            highlightedDates={highlightedDates}
            loading={viewLoading}
          />
          {!validCloseDate && closeDate && (
            <p className="text-[10px] text-danger">
              {t('portfolio.sell.invalidDateHint', { defaultValue: 'Giriş tarihinden önce veya gelecekte olamaz' })}
            </p>
          )}
        </div>

        <div className="space-y-1.5">
          <div className="flex items-center justify-between gap-2 flex-wrap">
            <span className="text-xs font-medium text-fg-muted flex items-center gap-1.5">
              <Tag className="h-3 w-3" /> {t('portfolio.derivatives.closePriceLabel', { defaultValue: 'Kapanış fiyatı' })}
              <span className="font-mono text-[10px] uppercase tracking-wider text-accent">
                {inputCurrency} ({currencySymbolOf(inputCurrency)})
              </span>
            </span>
            {closeDateSuggestedInDisplay != null && (
              <button
                type="button"
                onClick={applyDatePrice}
                className="text-[10px] flex items-center gap-1 text-accent hover:text-accent-bright font-semibold cursor-pointer bg-transparent border-none transition-colors"
              >
                <Wand2 className="h-2.5 w-2.5" />
                {isToday
                  ? t('portfolio.sell.useCurrent', { defaultValue: 'Anlık fiyat' })
                  : t('portfolio.sell.useDatePrice', { defaultValue: 'O günkü fiyat' })}
                : {money(closeDateSuggestedInDisplay, inputCurrency)}
              </button>
            )}
            {closeDateSuggestedInDisplay == null && !isToday && (
              <span className="text-[10px] text-fg-muted">
                {viewLoading
                  ? t('portfolio.sell.loadingPrice', { defaultValue: 'Fiyat yükleniyor...' })
                  : t('portfolio.sell.noPriceForDate', { defaultValue: 'Bu tarih için fiyat yok' })}
              </span>
            )}
          </div>
          <input
            type="number"
            max={MAX_MONEY}
            step="0.0001"
            inputMode="decimal"
            value={closePrice}
            onChange={(e) => { setClosePrice(sanitizeNumberInput(e.target.value, MAX_MONEY, PRICE_DECIMALS)); setPriceTouched(true); }}
            placeholder="0.00"
            className="w-full rounded-lg border border-border-default bg-bg-base px-3 py-2.5 text-sm text-fg font-mono placeholder:text-fg-subtle outline-none focus:ring-1 focus:ring-accent/50 transition-all"
          />
          {priceDelta != null && (
            <p className={`text-[10px] font-mono ${priceDelta >= 0 ? 'text-success' : 'text-danger'}`}>
              {priceDelta >= 0 ? '▲' : '▼'} {Math.abs(priceDelta).toFixed(2)}% {t('portfolio.sell.vsEntry', { defaultValue: 'giriş fiyatına göre' })}
            </p>
          )}
        </div>

        {realizedPnl != null && (
          <div className={`rounded-lg border px-3 py-2 flex items-center justify-between gap-2 text-xs ${
            realizedPnl >= 0
              ? 'border-emerald-500/30 bg-emerald-500/10'
              : 'border-rose-500/30 bg-rose-500/10'
          }`}>
            <span className={`font-semibold shrink-0 ${realizedPnl >= 0 ? 'text-emerald-400' : 'text-rose-400'}`}>
              {t('portfolio.derivatives.realizedPnl', 'Realize K/Z')}
            </span>
            <div className="text-right min-w-0">
              <div className={`font-mono font-bold break-all ${realizedPnl >= 0 ? 'text-emerald-400' : 'text-rose-400'}`}>
                {realizedPnl >= 0 ? '+' : ''}{money(realizedPnl, inputCurrency)}
              </div>
              {realizedPnlPercent != null && (
                <div className={`font-mono text-[10px] ${realizedPnl >= 0 ? 'text-emerald-400/80' : 'text-rose-400/80'}`}>
                  {realizedPnl >= 0 ? '+' : ''}{realizedPnlPercent.toFixed(2)}%
                </div>
              )}
            </div>
          </div>
        )}

        {error && (
          <div className="rounded-lg border border-rose-500/30 bg-rose-500/10 px-3 py-2 text-xs text-rose-400">
            {error}
          </div>
        )}

        <div className="flex flex-col-reverse sm:flex-row sm:justify-end gap-2 pt-1">
          <Button type="button" variant="ghost" onClick={onClose}>
            {t('common.cancel', 'İptal')}
          </Button>
          {isAlreadyClosed && (
            <Button
              type="button"
              variant="ghost"
              onClick={handleReopen}
              disabled={reopen.isPending}
              className="text-warning"
            >
              <RotateCcw className="h-3.5 w-3.5 mr-1" />
              {reopen.isPending ? '...' : t('portfolio.derivatives.reopen')}
            </Button>
          )}
          <Button type="submit" disabled={close.isPending || !validCloseDate || (!isAlreadyClosed && !validCloseQty)} variant={isAlreadyClosed ? 'primary' : 'danger'}>
            {close.isPending
              ? '...'
              : isAlreadyClosed
                ? t('portfolio.derivatives.editCloseConfirm', 'Güncelle')
                : t('portfolio.derivatives.closeConfirm', 'Pozisyonu Kapat')}
          </Button>
        </div>
      </form>
    </BaseModal>
  );
}
