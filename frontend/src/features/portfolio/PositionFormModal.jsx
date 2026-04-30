import { useEffect, useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { motion, AnimatePresence } from 'framer-motion';
import { X, Calendar, Hash, Tag, Wallet } from 'lucide-react';
import { Loader2, Check, AlertCircle } from '../../shared/components/AnimatedIcons';
import DatePickerPopover from '../../shared/components/DatePickerPopover';
import { unifiedMarketService } from '../../shared/services/unifiedMarketService';
import { formatPriceTRY } from '../../shared/utils/formatters';
import { assetCodeLabel } from '../../shared/utils/assetCode';
import { useAddPosition, useUpdatePosition } from './usePortfolioData';

const FRACTIONAL_TYPES = ['CRYPTO', 'FOREX', 'COMMODITY'];
const ONE_HOUR_MS = 60 * 60 * 1000;

function todayInputValue() {
  const now = new Date();
  const offset = now.getTimezoneOffset();
  return new Date(now.getTime() - offset * 60_000).toISOString().slice(0, 10);
}

function isoToDateInput(iso) {
  if (!iso) return todayInputValue();
  return new Date(iso).toISOString().slice(0, 10);
}

function dateInputToIso(value) {
  if (!value) return null;
  return new Date(`${value}T12:00:00`).toISOString();
}

function buildInitialState(mode, asset, position) {
  if (mode === 'edit' && position) {
    return {
      entryDate: isoToDateInput(position.entryDate),
      entryPrice: String(position.entryPrice ?? ''),
      quantity: String(position.quantity ?? ''),
    };
  }
  return {
    entryDate: todayInputValue(),
    entryPrice: asset?.currentPrice ? String(asset.currentPrice) : '',
    quantity: '',
  };
}

function resolveTarget(mode, asset, position) {
  if (mode === 'edit' && position) {
    return {
      assetType: position.assetType,
      assetCode: position.assetCode,
      assetName: position.assetName,
      assetImage: position.assetImage,
    };
  }
  return {
    assetType: asset?.type,
    assetCode: asset?.code,
    assetName: asset?.name,
    assetImage: asset?.image,
  };
}

function buildPriceIndex(history) {
  const index = new Map();
  if (!Array.isArray(history)) return index;
  for (const candle of history) {
    if (!candle?.candleDate) continue;
    const key = candle.candleDate.slice(0, 10);
    index.set(key, Number(candle.close));
  }
  return index;
}

export default function PositionFormModal({ mode, portfolioId, asset, position, onClose, onComplete }) {
  const target = resolveTarget(mode, asset, position);
  const isFractional = FRACTIONAL_TYPES.includes(target.assetType);
  const isEdit = mode === 'edit';

  const [form, setForm] = useState(() => buildInitialState(mode, asset, position));
  const [error, setError] = useState(null);
  const [priceTouched, setPriceTouched] = useState(isEdit);

  const { data: history } = useQuery({
    queryKey: ['marketHistory', target.assetType, target.assetCode, 'ALL'],
    queryFn: () => unifiedMarketService.getHistory(target.assetType, target.assetCode, 'ALL'),
    enabled: Boolean(target.assetType && target.assetCode),
    staleTime: ONE_HOUR_MS,
  });

  const priceIndex = useMemo(() => buildPriceIndex(history), [history]);
  const highlightedDates = useMemo(() => new Set(priceIndex.keys()), [priceIndex]);
  const suggestedPrice = priceIndex.get(form.entryDate);
  const dataAvailable = suggestedPrice != null;

  const addMutation = useAddPosition(portfolioId);
  const updateMutation = useUpdatePosition(portfolioId);
  const mutation = isEdit ? updateMutation : addMutation;
  const loading = mutation.isPending;
  const success = mutation.isSuccess;

  useEffect(() => {
    if (priceTouched) return;
    if (suggestedPrice == null) return;
    setForm((prev) => ({ ...prev, entryPrice: String(suggestedPrice) }));
  }, [suggestedPrice, priceTouched]);

  const totalCost = useMemo(() => {
    const q = Number(form.quantity);
    const p = Number(form.entryPrice);
    return q > 0 && p > 0 ? q * p : null;
  }, [form.quantity, form.entryPrice]);

  const handleDateChange = (iso) => {
    setForm((prev) => ({ ...prev, entryDate: iso }));
    setPriceTouched(false);
    setError(null);
  };

  const handlePriceChange = (e) => {
    setForm((prev) => ({ ...prev, entryPrice: e.target.value }));
    setPriceTouched(true);
    setError(null);
  };

  const handleQuantityChange = (e) => {
    setForm((prev) => ({ ...prev, quantity: e.target.value }));
    setError(null);
  };

  const useSuggestedPrice = () => {
    if (suggestedPrice == null) return;
    setForm((prev) => ({ ...prev, entryPrice: String(suggestedPrice) }));
    setPriceTouched(false);
  };

  const validate = () => {
    if (!form.entryDate) return 'Giriş tarihi gerekli';
    if (!form.entryPrice || Number(form.entryPrice) <= 0) return 'Geçerli bir giriş fiyatı girin';
    if (!form.quantity || Number(form.quantity) <= 0) return 'Geçerli bir miktar girin';
    return null;
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    const validationError = validate();
    if (validationError) {
      setError(validationError);
      return;
    }
    const payload = {
      assetType: target.assetType,
      assetCode: target.assetCode,
      quantity: Number(form.quantity),
      entryDate: dateInputToIso(form.entryDate),
      entryPrice: Number(form.entryPrice),
    };
    const onError = (err) => setError(err?.response?.data?.message || (isEdit ? 'Güncelleme başarısız' : 'Pozisyon eklenemedi'));
    const onSettled = () => { onComplete?.(); setTimeout(onClose, 800); };

    if (isEdit) {
      updateMutation.mutate(
        { positionId: position.id, payload },
        { onSuccess: onSettled, onError }
      );
    } else {
      addMutation.mutate(payload, { onSuccess: onSettled, onError });
    }
  };

  const displayCode = assetCodeLabel(target.assetType, target.assetCode);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        exit={{ opacity: 0 }}
        className="absolute inset-0 modal-overlay backdrop-blur-sm"
        onClick={onClose}
      />
      <motion.div
        initial={{ opacity: 0, scale: 0.95, y: 10 }}
        animate={{ opacity: 1, scale: 1, y: 0 }}
        exit={{ opacity: 0, scale: 0.95, y: 10 }}
        transition={{ type: 'spring', stiffness: 300, damping: 30 }}
        className="relative w-full max-w-sm rounded-2xl border border-border-default modal-panel p-6 overflow-visible"
      >
        <div className="absolute top-0 left-0 right-0 h-[1px] bg-gradient-to-r from-transparent via-accent/40 to-transparent" />
        <div className="flex items-center justify-between mb-5">
          <div className="flex items-center gap-3">
            <div className="flex items-center justify-center w-9 h-9 rounded-lg bg-accent/10">
              <Wallet className="h-4 w-4 text-accent" />
            </div>
            <div>
              <h2 className="text-base font-semibold text-fg">
                {isEdit ? 'Pozisyonu Düzenle' : 'Pozisyon Ekle'}
              </h2>
              <p className="text-xs text-fg-muted">{target.assetName || displayCode}</p>
            </div>
          </div>
          <button
            onClick={onClose}
            disabled={loading}
            className="flex items-center justify-center w-8 h-8 rounded-lg text-fg-muted hover:text-fg hover:bg-surface transition-colors bg-transparent border-none cursor-pointer disabled:opacity-30"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        {success ? (
          <motion.div
            initial={{ scale: 0.8, opacity: 0 }}
            animate={{ scale: 1, opacity: 1 }}
            className="flex flex-col items-center justify-center gap-3 py-10"
          >
            <motion.div
              initial={{ scale: 0 }}
              animate={{ scale: 1 }}
              transition={{ type: 'spring', stiffness: 300, damping: 20 }}
              className="flex items-center justify-center w-16 h-16 rounded-full bg-success/15"
            >
              <Check className="h-8 w-8 text-success" strokeWidth={2.5} />
            </motion.div>
            <p className="text-sm font-semibold text-fg">
              {isEdit ? 'Pozisyon güncellendi' : 'Pozisyon eklendi'}
            </p>
            <p className="text-xs text-fg-muted">{displayCode}</p>
          </motion.div>
        ) : (
          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="space-y-1.5">
              <label className="text-xs font-medium text-fg-muted flex items-center gap-1.5">
                <Calendar className="h-3 w-3" />
                Giriş Tarihi
              </label>
              <DatePickerPopover
                value={form.entryDate}
                onChange={handleDateChange}
                maxDate={todayInputValue()}
                highlightedDates={highlightedDates}
              />
              <DataAvailabilityHint
                dataAvailable={dataAvailable}
                hasHistory={priceIndex.size > 0}
                suggestedPrice={suggestedPrice}
                onApply={useSuggestedPrice}
                applied={!priceTouched && suggestedPrice != null && Number(form.entryPrice) === suggestedPrice}
              />
            </div>

            <div className="space-y-1.5">
              <label className="text-xs font-medium text-fg-muted flex items-center gap-1.5">
                <Tag className="h-3 w-3" />
                Giriş Fiyatı (TRY)
              </label>
              <input
                type="number"
                step="any"
                value={form.entryPrice}
                onChange={handlePriceChange}
                placeholder={asset?.currentPrice ? `önerilen: ${formatPriceTRY(asset.currentPrice)}` : '0.00'}
                className="w-full rounded-lg border border-border-default bg-bg-base px-3 py-2.5 text-sm text-fg font-mono placeholder:text-fg-subtle outline-none focus:ring-1 focus:ring-accent/50 transition-all"
              />
            </div>

            <div className="space-y-1.5">
              <label className="text-xs font-medium text-fg-muted flex items-center gap-1.5">
                <Hash className="h-3 w-3" />
                Miktar
              </label>
              <input
                type="number"
                step={isFractional ? 'any' : '1'}
                value={form.quantity}
                onChange={handleQuantityChange}
                placeholder={isFractional ? '0.00' : 'min. 1 adet'}
                autoFocus
                className="w-full rounded-lg border border-border-default bg-bg-base px-3 py-2.5 text-sm text-fg font-mono placeholder:text-fg-subtle outline-none focus:ring-1 focus:ring-accent/50 transition-all"
              />
            </div>

            {totalCost != null && (
              <div className="rounded-xl border border-accent/30 bg-gradient-to-r from-accent/5 to-transparent px-4 py-3 flex items-center justify-between">
                <span className="text-xs font-semibold text-accent">Toplam Maliyet</span>
                <span className="text-lg font-bold font-mono text-accent">{formatPriceTRY(totalCost)}</span>
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

            <button
              type="submit"
              disabled={loading}
              className="w-full flex items-center justify-center gap-2 rounded-lg py-3 text-sm font-semibold text-white bg-accent hover:bg-accent-bright transition-all border-none cursor-pointer disabled:opacity-50"
            >
              {loading ? (
                <Loader2 className="h-4 w-4 animate-spin" />
              ) : (
                <Wallet className="h-4 w-4" />
              )}
              {isEdit ? 'Kaydet' : 'Pozisyon Ekle'}
            </button>
          </form>
        )}
      </motion.div>
    </div>
  );
}

function DataAvailabilityHint({ dataAvailable, hasHistory, suggestedPrice, onApply, applied }) {
  if (!hasHistory) return null;
  if (dataAvailable) {
    return (
      <div className="flex items-center justify-between gap-2 text-[11px] text-success bg-success/5 rounded-md px-2.5 py-1.5 border border-success/20">
        <div className="flex items-center gap-1.5">
          <Check className="h-3 w-3 shrink-0" />
          <span>Bu tarih için veri mevcut: <span className="font-mono font-semibold">{formatPriceTRY(suggestedPrice)}</span></span>
        </div>
        {!applied && (
          <button
            type="button"
            onClick={onApply}
            className="text-[10px] font-semibold text-success hover:underline bg-transparent border-none cursor-pointer"
          >
            Uygula
          </button>
        )}
      </div>
    );
  }
  return (
    <div className="flex items-center gap-1.5 text-[11px] text-warning bg-warning/5 rounded-md px-2.5 py-1.5 border border-warning/20">
      <AlertCircle className="h-3 w-3 shrink-0" />
      <span>Bu tarih için fiyat verisi yok, manuel girin.</span>
    </div>
  );
}
