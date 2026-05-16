import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Calendar, RotateCcw, Tag, TrendingUp, TrendingDown, XCircle } from 'lucide-react';
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

const todayIso = () => new Date().toISOString().slice(0, 10);

export default function CloseDerivativePositionDialog({ portfolioId, position, onClose }) {
  const { t } = useTranslation();
  const { format: money, currency: displayCurrency } = useMoney();
  const { rateAt } = useRateHistory();
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

  if (!position) return null;

  const direction = (position.assetName && position.assetName.split(' · ')[0]) || 'LONG';
  const isLong = direction === 'LONG';
  const qty = Number(position.quantity || position.quantityLot || 1);
  const entryPrice = Number(position.entryPrice || 0);
  const symbol = position.contractSymbol || position.assetCode;

  const todayDate = todayIso();
  const isToday = closeDate === todayDate;
  const liveSuggested = position.currentPriceTry != null ? Number(position.currentPriceTry) : null;
  const syncKey = `${closeDate}|${isToday && liveSuggested != null ? liveSuggested : 'none'}`;
  const [trackedKey, setTrackedKey] = useState(syncKey);
  if (syncKey !== trackedKey) {
    setTrackedKey(syncKey);
    if (!priceTouched) {
      setClosePrice(isToday && liveSuggested != null ? String(liveSuggested) : '');
    }
  }

  const toTryOnDate = (value, dateStr) => {
    const num = Number(value);
    if (!Number.isFinite(num)) return null;
    const from = displayCurrency === 'ORIGINAL' ? 'TRY' : displayCurrency;
    if (from === 'TRY') return num;
    const rate = rateAt(from, dateStr);
    return rate != null && rate > 0 ? num * rate : num;
  };

  const sizeTimesQty = useMemo(() => {
    const notional = Number(position.entryValueTry);
    if (!Number.isFinite(notional) || !Number.isFinite(entryPrice) || entryPrice <= 0) return qty;
    return notional / entryPrice;
  }, [position.entryValueTry, entryPrice, qty]);

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

  const submit = async (e) => {
    e.preventDefault();
    setError(null);
    try {
      await close.mutateAsync({
        positionId: position.id,
        closeDate,
        closePrice: closePrice ? toTryOnDate(closePrice, closeDate) : null,
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
        </div>

        {/* Date */}
        <label className="space-y-1.5 block">
          <span className="text-xs font-medium text-fg-muted flex items-center gap-1.5">
            <Calendar className="h-3 w-3" /> {t('portfolio.derivatives.closeDate', 'Kapanış tarihi')}
          </span>
          <input
            type="date"
            required
            value={closeDate}
            max={todayDate}
            onChange={(e) => { setCloseDate(e.target.value); setPriceTouched(false); }}
            className="w-full rounded-lg border border-border-default bg-bg-base px-3 py-2.5 text-sm text-fg font-mono outline-none focus:ring-1 focus:ring-accent/50 transition-all"
          />
        </label>

        {/* Close price */}
        <label className="space-y-1.5 block">
          <span className="text-xs font-medium text-fg-muted flex items-center gap-1.5">
            <Tag className="h-3 w-3" /> {t('portfolio.derivatives.closePriceOptional', 'Kapanış fiyatı (boş → geçmişten)')}
          </span>
          <input
            type="number"
            step="0.0001"
            value={closePrice}
            onChange={(e) => { setClosePrice(e.target.value); setPriceTouched(true); }}
            placeholder={isToday ? '' : t('portfolio.derivatives.autoFromHistory', 'Boş bırak → geçmişten alınır')}
            className="w-full rounded-lg border border-border-default bg-bg-base px-3 py-2.5 text-sm text-fg font-mono placeholder:text-fg-subtle outline-none focus:ring-1 focus:ring-accent/50 transition-all"
          />
        </label>

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
          <Button type="submit" disabled={close.isPending} variant={isAlreadyClosed ? 'primary' : 'danger'}>
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
