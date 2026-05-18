import { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Calendar, RotateCcw, Tag, TrendingUp, TrendingDown, Wand2, XCircle } from 'lucide-react';
import DatePickerPopover from '../../../shared/components/form/DatePickerPopover';
import BaseModal from '../../../shared/components/modal/BaseModal';
import Button from '../../../shared/components/buttons/Button';
import { extractApiError } from '../../../shared/utils/apiError';
import {
  useCloseDerivativePosition,
  useReopenDerivativePosition,
  useUpdateCloseDerivativePosition,
} from '../hooks/useDerivativePositions';
import { useMoney } from '../../../shared/hooks/useMoney';
import { useRateHistory } from '../../../shared/hooks/useRateHistory';
import { unifiedMarketService } from '../../../shared/services/unifiedMarketService';
import { ONE_HOUR_MS, toYearMonth, buildPriceIndex } from '../lib/positionFormHelpers';

const todayIso = () => new Date().toISOString().slice(0, 10);
const yesterdayIso = () => {
  const d = new Date();
  d.setDate(d.getDate() - 1);
  return d.toISOString().slice(0, 10);
};
function formatDateLabel(iso, localeTag) {
  if (!iso) return '';
  return new Date(`${iso}T00:00:00`).toLocaleDateString(localeTag, {
    day: '2-digit', month: 'short', year: 'numeric',
  });
}

export default function CloseDerivativePositionDialog({ portfolioId, position, onClose }) {
  const { t } = useTranslation();
  const { format: money, currency: displayCurrency } = useMoney();
  const { convertAt, rateAt } = useRateHistory();
  const localeTag = t('common.localeTag');
  const entryDateIso = position?.entryDate ? String(position.entryDate).slice(0, 10) : null;
  const isAlreadyClosed = position?.assetName && position.assetName.includes('KAPALI');
  const [closeDate, setCloseDate] = useState(() => todayIso());
  const [closePrice, setClosePrice] = useState(() =>
    position?.currentPriceTry != null ? String(position.currentPriceTry) : '');
  const [priceTouched, setPriceTouched] = useState(false);
  const [error, setError] = useState(null);
  const closeOpen = useCloseDerivativePosition(portfolioId);
  const updateClosed = useUpdateCloseDerivativePosition(portfolioId);
  const reopen = useReopenDerivativePosition(portfolioId);
  const close = isAlreadyClosed ? updateClosed : closeOpen;

  const handleReopen = async () => {
    setError(null);
    try {
      await reopen.mutateAsync(position.id);
      onClose?.();
    } catch (err) {
      setError(extractApiError(err, t('portfolio.derivatives.reopenFailed')));
    }
  };

  const direction = (position?.assetName && position.assetName.split(' · ')[0]) || 'LONG';
  const isLong = direction === 'LONG';
  const qty = Number(position?.quantity || position?.quantityLot || 1);
  const entryPrice = Number(position?.entryPrice || 0);
  const symbol = position?.contractSymbol || position?.assetCode;
  const [closeQty, setCloseQty] = useState(() => String(qty));
  const parsedCloseQty = Number(closeQty);
  const validCloseQty = !isAlreadyClosed
      ? (Number.isFinite(parsedCloseQty) && parsedCloseQty > 0 && parsedCloseQty <= qty)
      : true;
  const effectiveQty = !isAlreadyClosed && validCloseQty ? parsedCloseQty : qty;
  const isPartial = !isAlreadyClosed && validCloseQty && parsedCloseQty < qty;

  const todayDate = todayIso();
  const isToday = closeDate === todayDate;
  const [viewMonth, setViewMonth] = useState(() => toYearMonth(todayIso()));
  const availabilityCode = symbol || position?.assetCode;
  const { data: viewAvailability, isFetching: viewLoading } = useQuery({
    queryKey: ['marketAvailability', 'VIOP', availabilityCode, viewMonth],
    queryFn: () => unifiedMarketService.getMonthlyAvailability('VIOP', availabilityCode, viewMonth),
    enabled: Boolean(availabilityCode && viewMonth),
    staleTime: ONE_HOUR_MS,
    placeholderData: (prev) => prev,
  });
  const viewPrices = useMemo(() => buildPriceIndex(viewAvailability), [viewAvailability]);
  const highlightedDates = useMemo(() => new Set(viewPrices.keys()), [viewPrices]);
  const closeDateMonth = useMemo(() => toYearMonth(closeDate), [closeDate]);
  const historicalPriceTry = closeDateMonth === viewMonth ? viewPrices.get(closeDate) : undefined;
  const inputCurrency = displayCurrency === 'ORIGINAL' || !displayCurrency ? 'TRY' : displayCurrency;
  const historicalPriceDisplay = historicalPriceTry != null
    ? Number(convertAt(historicalPriceTry, 'TRY', closeDate) ?? historicalPriceTry)
    : null;
  const liveSuggested = position?.currentPriceTry != null ? Number(position.currentPriceTry) : null;
  const liveSuggestedInDisplay = liveSuggested != null
    ? Number(convertAt(liveSuggested, 'TRY', todayDate) ?? liveSuggested)
    : null;
  const closeDateSuggestedInDisplay = useMemo(() => {
    if (historicalPriceDisplay != null) return historicalPriceDisplay;
    if (isToday && liveSuggestedInDisplay != null) return liveSuggestedInDisplay;
    return null;
  }, [historicalPriceDisplay, isToday, liveSuggestedInDisplay]);
  const applyDatePrice = () => {
    if (closeDateSuggestedInDisplay != null) setClosePrice(String(closeDateSuggestedInDisplay));
  };
  const syncKey = `${closeDate}|${closeDateSuggestedInDisplay ?? 'none'}`;
  const [trackedKey, setTrackedKey] = useState(syncKey);
  if (syncKey !== trackedKey) {
    setTrackedKey(syncKey);
    if (!priceTouched && closeDateSuggestedInDisplay != null) {
      setClosePrice(String(closeDateSuggestedInDisplay));
    }
  }
  const parsedClosePrice = Number(closePrice);
  const validClosePrice = Number.isFinite(parsedClosePrice) && parsedClosePrice > 0;
  const validCloseDate = closeDate && (!entryDateIso || closeDate >= entryDateIso) && closeDate <= todayDate;
  const priceDelta = validClosePrice && entryPrice > 0
    ? ((parsedClosePrice - entryPrice) / entryPrice) * 100
    : null;
  const datePresets = [
    { id: 'today', iso: todayDate, label: t('portfolio.sell.today', { defaultValue: 'Bugün' }) },
    { id: 'yesterday', iso: yesterdayIso(), label: t('portfolio.sell.yesterday', { defaultValue: 'Dün' }) },
  ];

  const toTryOnDate = (value, dateStr) => {
    const num = Number(value);
    if (!Number.isFinite(num)) return null;
    const from = displayCurrency === 'ORIGINAL' ? 'TRY' : displayCurrency;
    if (from === 'TRY') return num;
    const rate = rateAt(from, dateStr);
    return rate != null && rate > 0 ? num * rate : num;
  };

  const sizeTimesQty = useMemo(() => {
    const notional = Number(position?.entryValueTry);
    if (!Number.isFinite(notional) || !Number.isFinite(entryPrice) || entryPrice <= 0) return effectiveQty;
    const sizePerLot = notional / entryPrice / qty;
    return sizePerLot * effectiveQty;
  }, [position?.entryValueTry, entryPrice, qty, effectiveQty]);

  const realizedPnl = useMemo(() => {
    const exit = Number(closePrice);
    if (!Number.isFinite(exit) || !Number.isFinite(entryPrice) || entryPrice <= 0) return null;
    const diff = isLong ? exit - entryPrice : entryPrice - exit;
    return diff * sizeTimesQty;
  }, [closePrice, entryPrice, isLong, sizeTimesQty]);

  const realizedPnlPercent = useMemo(() => {
    if (realizedPnl == null || entryPrice <= 0) return null;
    const notional = entryPrice * sizeTimesQty;
    return notional > 0 ? (realizedPnl / notional) * 100 : null;
  }, [realizedPnl, entryPrice, sizeTimesQty]);

  if (!position) return null;

  const submit = async (e) => {
    e.preventDefault();
    setError(null);
    try {
      await close.mutateAsync({
        positionId: position.id,
        closeDate,
        closePrice: closePrice ? toTryOnDate(closePrice, closeDate) : null,
        closeQuantityLot: !isAlreadyClosed && isPartial ? parsedCloseQty : null,
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
        {/* Position info card with direction badge */}
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
                {money(entryPrice)}
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
            <div className="flex items-center justify-between">
              <span className="text-xs font-medium text-fg-muted flex items-center gap-1.5">
                <Tag className="h-3 w-3" /> {t('portfolio.derivatives.closeQtyLabel', 'Kapatılacak lot')}
              </span>
              <div className="flex gap-1">
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
              min="0"
              max={qty}
              step="any"
              value={closeQty}
              onChange={(e) => setCloseQty(e.target.value)}
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

        {/* Date */}
        <div className="space-y-1.5">
          <div className="flex items-center justify-between">
            <span className="text-xs font-medium text-fg-muted flex items-center gap-1.5">
              <Calendar className="h-3 w-3" /> {t('portfolio.derivatives.closeDate', 'Kapanış tarihi')}
            </span>
            <div className="flex gap-1">
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
            onMonthChange={(y, m) => setViewMonth(`${y}-${String(m + 1).padStart(2, '0')}`)}
            highlightedDates={highlightedDates}
            loading={viewLoading}
          />
          {!validCloseDate && closeDate && (
            <p className="text-[10px] text-danger">
              {t('portfolio.sell.invalidDateHint', { defaultValue: 'Giriş tarihinden önce veya gelecekte olamaz' })}
            </p>
          )}
        </div>

        {/* Close price */}
        <div className="space-y-1.5">
          <div className="flex items-center justify-between">
            <span className="text-xs font-medium text-fg-muted flex items-center gap-1.5">
              <Tag className="h-3 w-3" /> {t('portfolio.derivatives.closePriceLabel', { defaultValue: 'Kapanış fiyatı' })}
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
            step="0.0001"
            value={closePrice}
            onChange={(e) => { setClosePrice(e.target.value); setPriceTouched(true); }}
            placeholder="0.00"
            className="w-full rounded-lg border border-border-default bg-bg-base px-3 py-2.5 text-sm text-fg font-mono placeholder:text-fg-subtle outline-none focus:ring-1 focus:ring-accent/50 transition-all"
          />
          {priceDelta != null && (
            <p className={`text-[10px] font-mono ${priceDelta >= 0 ? 'text-success' : 'text-danger'}`}>
              {priceDelta >= 0 ? '▲' : '▼'} {Math.abs(priceDelta).toFixed(2)}% {t('portfolio.sell.vsEntry', { defaultValue: 'giriş fiyatına göre' })}
            </p>
          )}
        </div>

        {/* Realized P&L preview */}
        {realizedPnl != null && (
          <div className={`rounded-lg border px-3 py-2 flex items-center justify-between text-xs ${
            realizedPnl >= 0
              ? 'border-emerald-500/30 bg-emerald-500/10'
              : 'border-rose-500/30 bg-rose-500/10'
          }`}>
            <span className={`font-semibold ${realizedPnl >= 0 ? 'text-emerald-400' : 'text-rose-400'}`}>
              {t('portfolio.derivatives.realizedPnl', 'Realize K/Z')}
            </span>
            <div className="text-right">
              <div className={`font-mono font-bold ${realizedPnl >= 0 ? 'text-emerald-400' : 'text-rose-400'}`}>
                {realizedPnl >= 0 ? '+' : ''}{money(realizedPnl)}
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

        <div className="flex justify-end gap-2 pt-1 flex-wrap">
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
