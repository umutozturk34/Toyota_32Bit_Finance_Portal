import { useMemo, useState } from 'react';
import { Calendar, Tag, ShoppingBag, TrendingUp, TrendingDown, Wand2 } from 'lucide-react';
import BaseModal from '../../../shared/components/modal/BaseModal';
import Button from '../../../shared/components/buttons/Button';
import DatePickerPopover from '../../../shared/components/form/DatePickerPopover';
import { extractApiError } from '../../../shared/utils/apiError';
import { useSellPosition } from '../hooks/usePortfolioData';
import { useRateHistory } from '../../../shared/hooks/useRateHistory';
import { usePositionCloseForm, todayIso, formatDateLabel } from '../hooks/usePositionCloseForm';
import { resolveNativeCurrency, FRACTIONAL_TYPES, preventDecimal } from '../lib/positionFormHelpers';
import { commodityLabel } from '../../../shared/utils/commodityName';
import { sanitizeNumberInput, MAX_MONEY, QUANTITY_DECIMALS, PRICE_DECIMALS } from '../../../shared/utils/numberInput';

const QTY_PRESETS = [
  { id: '25', factor: 0.25 },
  { id: '50', factor: 0.5 },
  { id: '75', factor: 0.75 },
  { id: 'max', factor: 1 },
];

export default function SellPositionDialog({ portfolioId, position, onClose }) {
  const { convertAt } = useRateHistory();
  const totalQty = Number(position?.quantity || 0);
  const entryPriceTry = Number(position?.entryPrice || 0);
  const suggestedPriceTry = position?.currentPriceTry != null ? Number(position.currentPriceTry) : null;
  const entryDateIso = position?.entryDate ? String(position.entryDate).slice(0, 10) : null;
  // Native currency must be resolved before the suggested-price conversion below: in ORIGINAL display
  // mode convertAt only converts TRY → the asset's native currency when given `natural`; without it a
  // USD-native asset's TRY price would be shown (and submitted) verbatim under a "$" label.
  const nativeCurrency = resolveNativeCurrency({
    assetType: position?.assetType,
    assetCode: position?.assetCode,
    metadata: position?.metadata,
  }, position);
  const suggestedPriceInDisplay = suggestedPriceTry != null
    ? Number(convertAt(suggestedPriceTry, 'TRY', todayIso(), nativeCurrency) ?? suggestedPriceTry)
    : null;

  const sell = useSellPosition(portfolioId);

  const form = usePositionCloseForm({
    availabilityAssetType: position?.assetType,
    availabilityAssetCode: position?.assetCode,
    entryDateIso,
    initialPrice: suggestedPriceInDisplay != null ? String(suggestedPriceInDisplay) : '',
    liveSuggestedPriceTry: suggestedPriceTry,
    nativeCurrency,
  });

  const {
    t, money, inputCurrency, localeTag,
    date: sellDate, setDate: setSellDate,
    price: sellPrice, setPrice: setSellPrice,
    parsedPrice, validPrice, validDate,
    setPriceTouched,
    error, setError,
    highlightedDates, viewLoading,
    dateSuggestedInDisplay: sellDateSuggestedInDisplay,
    applyDatePrice: applyCurrentPrice,
    handleMonthChange,
    datePresets,
  } = form;

  // Share-based assets (stocks, funds) trade in whole units — only crypto/forex/commodity are fractional.
  // Mirrors the buy form so a partial sell can't carve a non-integer remainder (e.g. 12.5 shares).
  const isFractional = FRACTIONAL_TYPES.includes(position?.assetType);
  const qtyDecimals = isFractional ? QUANTITY_DECIMALS : 0;

  const [sellQty, setSellQty] = useState(() => String(totalQty));
  const parsedQty = Number(sellQty);
  // A full close is always allowed (lets a legacy fractional lot be cleared); a partial sell of a
  // share-based asset must leave both a whole sold amount and a whole remainder, so no fractional piece
  // is ever created.
  const isFullClose = Number.isFinite(parsedQty) && Math.abs(parsedQty - totalQty) < 1e-9;
  const leavesWhole = Number.isInteger(parsedQty) && Number.isInteger(totalQty - parsedQty);
  const validQty = Number.isFinite(parsedQty) && parsedQty > 0 && parsedQty <= totalQty
    && (isFractional || isFullClose || leavesWhole);
  const canSubmit = validQty && validPrice && validDate && !sell.isPending;

  const entryPriceInDisplay = useMemo(() => {
    if (!entryPriceTry) return 0;
    return Number(convertAt(entryPriceTry, 'TRY', entryDateIso || todayIso(), nativeCurrency) ?? entryPriceTry);
  }, [entryPriceTry, entryDateIso, convertAt, nativeCurrency]);

  const proceeds = useMemo(
    () => (validQty && validPrice ? parsedQty * parsedPrice : null),
    [validQty, validPrice, parsedQty, parsedPrice]
  );
  const realizedPnl = useMemo(
    () => (proceeds != null ? proceeds - parsedQty * entryPriceInDisplay : null),
    [proceeds, parsedQty, entryPriceInDisplay]
  );
  const realizedPercent = useMemo(
    () => (realizedPnl != null && entryPriceInDisplay > 0 && parsedQty > 0
      ? (realizedPnl / (parsedQty * entryPriceInDisplay)) * 100
      : null),
    [realizedPnl, entryPriceInDisplay, parsedQty]
  );
  const priceDelta = useMemo(
    () => (validPrice && entryPriceInDisplay > 0 ? ((parsedPrice - entryPriceInDisplay) / entryPriceInDisplay) * 100 : null),
    [validPrice, parsedPrice, entryPriceInDisplay]
  );
  const isPartial = validQty && parsedQty < totalQty;
  const remainingQty = totalQty - (validQty ? parsedQty : 0);
  const pnlPositive = realizedPnl != null && realizedPnl >= 0;

  const presetQty = (factor) => {
    if (factor >= 1) return totalQty;
    const raw = totalQty * factor;
    return isFractional ? Math.round(raw * 1e6) / 1e6 : Math.round(raw);
  };
  const applyQtyPreset = (factor) => setSellQty(String(presetQty(factor)));

  const handleSubmit = async () => {
    setError(null);
    if (!Number.isFinite(parsedPrice) || parsedPrice <= 0) {
      setError(t('portfolio.sell.invalidPriceHint', { defaultValue: 'Geçersiz fiyat' }));
      return;
    }
    try {
      await sell.mutateAsync({
        positionId: position.id,
        payload: {
          quantity: parsedQty,
          exitPrice: parsedPrice,
          exitDate: `${sellDate}T12:00:00`,
          priceCurrency: inputCurrency,
        },
      });
      onClose?.();
    } catch (err) {
      setError(extractApiError(err, t('portfolio.sell.failed')));
    }
  };

  return (
    <BaseModal
      isOpen
      onClose={onClose}
      icon={ShoppingBag}
      title={t('portfolio.sell.title', { code: position?.assetCode })}
      size="md"
    >
      <div className="space-y-4">
        <div className="rounded-xl border border-border-default bg-bg-elevated px-4 py-3">
          <div className="flex items-center justify-between gap-3">
            <div className="min-w-0">
              <p className="text-[10px] uppercase tracking-wide text-fg-muted">
                {t('portfolio.sell.position', { defaultValue: 'Pozisyon' })}
              </p>
              <p className="text-sm font-semibold text-fg truncate">
                {position?.assetCode}
                {position?.assetName && (
                  <span className="text-xs text-fg-muted font-normal ml-1.5">· {commodityLabel(t, position.assetType, position.assetCode, position.assetName)}</span>
                )}
              </p>
            </div>
            <div className="text-right min-w-0 shrink">
              <p className="text-[10px] uppercase tracking-wide text-fg-muted">
                {t('portfolio.sell.holdingLabel')}
              </p>
              <p className="text-sm font-semibold font-mono text-fg truncate" title={totalQty.toLocaleString(localeTag, { maximumFractionDigits: 6 })}>
                {totalQty.toLocaleString(localeTag, { maximumFractionDigits: 6 })}
              </p>
            </div>
          </div>
          <div className="mt-2.5 pt-2.5 border-t border-border-default grid grid-cols-2 gap-3 text-[11px]">
            <div>
              <p className="text-fg-muted mb-0.5">{t('portfolio.sell.entryPriceLabel')}</p>
              <p className="font-mono text-fg font-semibold">{money(entryPriceInDisplay, inputCurrency)}</p>
            </div>
            <div>
              <p className="text-fg-muted mb-0.5">{t('portfolio.sell.entryDateLabel', { defaultValue: 'Giriş tarihi' })}</p>
              <p className="font-mono text-fg font-semibold">{entryDateIso ? formatDateLabel(entryDateIso, localeTag) : '—'}</p>
            </div>
          </div>
        </div>

        <div className="space-y-1.5">
          <div className="flex items-center justify-between gap-2 flex-wrap">
            <span className="text-xs text-fg-muted flex items-center gap-1.5">
              <Tag className="h-3 w-3" /> {t('portfolio.sell.quantityLabel')}
            </span>
            <div className="flex gap-1 flex-wrap">
              {QTY_PRESETS.map(({ id, factor }) => {
                const target = presetQty(factor);
                const active = validQty && Math.abs(parsedQty - target) < 1e-6;
                return (
                  <button
                    key={id}
                    type="button"
                    onClick={() => applyQtyPreset(factor)}
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
            max={totalQty}
            step={isFractional ? 'any' : '1'}
            inputMode={isFractional ? 'decimal' : 'numeric'}
            value={sellQty}
            onChange={(e) => setSellQty(sanitizeNumberInput(e.target.value, totalQty, qtyDecimals))}
            onKeyDown={isFractional ? undefined : preventDecimal}
            placeholder={isFractional ? '0.00' : '0'}
            className="w-full rounded-lg border border-border-default bg-bg-base px-3 py-2.5 text-sm text-fg font-mono placeholder:text-fg-subtle outline-none focus:ring-1 focus:ring-accent/50 transition-all"
          />
          {!validQty && sellQty !== '' && (
            <p className="text-[10px] text-danger">{t('portfolio.sell.invalidQuantityHint', { max: totalQty })}</p>
          )}
        </div>

        <div className="space-y-1.5">
          <div className="flex items-center justify-between gap-2 flex-wrap">
            <span className="text-xs text-fg-muted flex items-center gap-1.5">
              <ShoppingBag className="h-3 w-3" /> {t('portfolio.sell.exitPriceLabel')}
            </span>
            {sellDateSuggestedInDisplay != null && (
              <button
                type="button"
                onClick={applyCurrentPrice}
                className="text-[10px] flex items-center gap-1 text-accent hover:text-accent-bright font-semibold cursor-pointer bg-transparent border-none transition-colors"
              >
                <Wand2 className="h-2.5 w-2.5" />
                {sellDate === todayIso()
                  ? t('portfolio.sell.useCurrent', { defaultValue: 'Anlık fiyat' })
                  : t('portfolio.sell.useDatePrice', { defaultValue: 'O günkü fiyat' })}
                : {money(sellDateSuggestedInDisplay, inputCurrency)}
              </button>
            )}
            {sellDateSuggestedInDisplay == null && sellDate !== todayIso() && (
              <span className="text-[10px] text-fg-muted">
                {viewLoading
                  ? t('portfolio.sell.loadingPrice', { defaultValue: 'Fiyat yükleniyor...' })
                  : t('portfolio.sell.noPriceForDate', { defaultValue: 'Bu tarih için fiyat yok' })}
              </span>
            )}
          </div>
          <input
            type="number"
            min="0"
            max={MAX_MONEY}
            step="any"
            inputMode="decimal"
            value={sellPrice}
            onChange={(e) => setSellPrice(sanitizeNumberInput(e.target.value, MAX_MONEY, PRICE_DECIMALS))}
            placeholder="0.00"
            className="w-full rounded-lg border border-border-default bg-bg-base px-3 py-2.5 text-sm text-fg font-mono placeholder:text-fg-subtle outline-none focus:ring-1 focus:ring-accent/50 transition-all"
          />
          {priceDelta != null && (
            <p className={`text-[10px] font-mono ${priceDelta >= 0 ? 'text-success' : 'text-danger'}`}>
              {priceDelta >= 0 ? '▲' : '▼'} {Math.abs(priceDelta).toFixed(2)}% {t('portfolio.sell.vsEntry', { defaultValue: 'giriş fiyatına göre' })}
            </p>
          )}
        </div>

        <div className="space-y-1.5">
          <div className="flex items-center justify-between gap-2 flex-wrap">
            <span className="text-xs text-fg-muted flex items-center gap-1.5">
              <Calendar className="h-3 w-3" /> {t('portfolio.sell.exitDateLabel')}
            </span>
            <div className="flex gap-1 flex-wrap">
              {datePresets.map(({ id, iso, label }) => {
                const active = sellDate === iso;
                return (
                  <button
                    key={id}
                    type="button"
                    onClick={() => setSellDate(iso)}
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
            value={sellDate}
            onChange={(iso) => { setSellDate(iso); setPriceTouched(false); }}
            onMonthChange={handleMonthChange}
            minDate={entryDateIso || undefined}
            maxDate={todayIso()}
            highlightedDates={highlightedDates}
            loading={viewLoading}
          />
          {!validDate && sellDate && (
            <p className="text-[10px] text-danger">
              {t('portfolio.sell.invalidDateHint', { defaultValue: 'Giriş tarihinden önce veya gelecekte olamaz' })}
            </p>
          )}
        </div>

        {(realizedPnl != null || proceeds != null) && (
          <div className={`rounded-xl border bg-gradient-to-r px-4 py-3 transition-colors ${
            pnlPositive
              ? 'border-success/30 from-success/10 to-transparent'
              : 'border-danger/30 from-danger/10 to-transparent'
          }`}>
            <div className="flex items-center justify-between gap-3 mb-2">
              <div className="flex items-center gap-2">
                {pnlPositive
                  ? <TrendingUp className="h-4 w-4 text-success" />
                  : <TrendingDown className="h-4 w-4 text-danger" />}
                <span className="text-xs font-semibold text-fg">
                  {t('portfolio.sell.realizedPnlLabel')}
                </span>
              </div>
              {realizedPercent != null && (
                <span className={`text-[11px] font-mono font-semibold px-1.5 py-0.5 rounded ${
                  pnlPositive ? 'bg-success/15 text-success' : 'bg-danger/15 text-danger'
                }`}>
                  {pnlPositive ? '+' : ''}{realizedPercent.toFixed(2)}%
                </span>
              )}
            </div>
            {realizedPnl != null && (
              <p className={`text-lg sm:text-xl font-bold font-mono break-all ${pnlPositive ? 'text-success' : 'text-danger'}`}>
                {pnlPositive ? '+' : ''}{money(realizedPnl, inputCurrency)}
              </p>
            )}
            {proceeds != null && (
              <div className="mt-2 pt-2 border-t border-border-default flex items-center justify-between gap-2 text-[11px]">
                <span className="text-fg-muted shrink min-w-0 truncate">{t('portfolio.sell.proceedsLabel')}</span>
                <span className="font-mono font-semibold text-fg min-w-0 truncate" title={money(proceeds, inputCurrency)}>{money(proceeds, inputCurrency)}</span>
              </div>
            )}
            {isPartial && (
              <div className="mt-1.5 flex items-center justify-between text-[10px] text-fg-muted">
                <span>{t('portfolio.sell.remainingOpen', { defaultValue: 'Açık kalan' })}</span>
                <span className="font-mono">
                  {remainingQty.toLocaleString(localeTag, { maximumFractionDigits: 6 })}
                </span>
              </div>
            )}
          </div>
        )}

        {error && (
          <div className="text-xs text-danger bg-danger/10 border border-danger/20 px-3 py-2 rounded-lg">{error}</div>
        )}

        <div className="flex flex-col-reverse sm:flex-row gap-2 sm:justify-end pt-1">
          <Button variant="secondary" size="md" onClick={onClose}>
            {t('common.cancel')}
          </Button>
          <Button variant="primary" size="md" onClick={handleSubmit} disabled={!canSubmit} loading={sell.isPending}>
            {t('portfolio.sell.confirm')}
          </Button>
        </div>
      </div>
    </BaseModal>
  );
}
