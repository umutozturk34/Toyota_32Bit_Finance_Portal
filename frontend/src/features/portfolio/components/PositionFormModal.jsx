import { useEffect, useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { motion, AnimatePresence } from 'framer-motion';
import { X, Calendar, Hash, Tag, Wallet, ShieldCheck } from 'lucide-react';
import { Check, AlertCircle, AlertTriangle } from '../../../shared/components/feedback/AnimatedIcons';
import DatePickerPopover from '../../../shared/components/form/DatePickerPopover';
import ProcessingSteps from '../../../shared/components/feedback/ProcessingSteps';
import useProcessingAnimation from '../../../shared/hooks/useProcessingAnimation';
import { unifiedMarketService } from '../../../shared/services/unifiedMarketService';
import { formatPriceTRY } from '../../../shared/utils/formatters';
import { assetCodeLabel } from '../../../shared/utils/assetCode';
import { useAddPosition, usePortfolioLimits, useUpdatePosition } from '../hooks/usePortfolioData';

const FRACTIONAL_TYPES = ['CRYPTO', 'FOREX', 'COMMODITY'];
const ONE_HOUR_MS = 60 * 60 * 1000;
const SUCCESS_HOLD_MS = 1100;
const PROCESSING_STEPS = [
  { label: 'İşlem doğrulanıyor...', duration: 400 },
  { label: 'Piyasa verisi kontrol ediliyor...', duration: 400 },
  { label: 'Portföy güncelleniyor...', duration: 400 },
];

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

function toYearMonth(isoDate) {
  return isoDate ? isoDate.slice(0, 7) : new Date().toISOString().slice(0, 7);
}

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;

function collectIsoDateEntries(node, out) {
  if (!node || typeof node !== 'object') return;
  for (const [key, value] of Object.entries(node)) {
    if (ISO_DATE.test(key) && (typeof value === 'number' || typeof value === 'string')) {
      out.set(key, Number(value));
    } else if (typeof value === 'object') {
      collectIsoDateEntries(value, out);
    }
  }
}

function buildPriceIndex(response) {
  const index = new Map();
  collectIsoDateEntries(response, index);
  return index;
}

const compactCurrencyFormatter = new Intl.NumberFormat('tr-TR', {
  notation: 'compact',
  style: 'currency',
  currency: 'TRY',
  maximumFractionDigits: 2,
});

function formatTotalCost(cost) {
  if (cost == null) return 'N/A';
  if (Math.abs(cost) >= 1_000_000_000) return compactCurrencyFormatter.format(cost);
  return formatPriceTRY(cost);
}

export default function PositionFormModal({ mode, portfolioId, asset, position, onClose, onComplete }) {
  const target = resolveTarget(mode, asset, position);
  const isFractional = FRACTIONAL_TYPES.includes(target.assetType);
  const isEdit = mode === 'edit';

  const [form, setForm] = useState(() => buildInitialState(mode, asset, position));
  const [error, setError] = useState(null);
  const [priceTouched, setPriceTouched] = useState(isEdit);
  const [phase, setPhase] = useState('form');
  const [viewMonth, setViewMonth] = useState(() => toYearMonth(buildInitialState(mode, asset, position).entryDate));

  const entryMonth = toYearMonth(form.entryDate);

  const { processingStep, runAnimation, reset: resetProcessing } = useProcessingAnimation();
  const { data: limits } = usePortfolioLimits();

  const { data: viewAvailability, isPending: viewLoading } = useQuery({
    queryKey: ['marketAvailability', target.assetType, target.assetCode, viewMonth],
    queryFn: () => unifiedMarketService.getMonthlyAvailability(target.assetType, target.assetCode, viewMonth),
    enabled: Boolean(target.assetType && target.assetCode && viewMonth),
    staleTime: ONE_HOUR_MS,
  });

  const entryLoading = viewLoading;
  const viewPrices = useMemo(() => buildPriceIndex(viewAvailability), [viewAvailability]);
  const highlightedDates = useMemo(() => new Set(viewPrices.keys()), [viewPrices]);
  const suggestedPrice = entryMonth === viewMonth ? viewPrices.get(form.entryDate) : undefined;
  const dataAvailable = suggestedPrice != null;

  const addMutation = useAddPosition(portfolioId);
  const updateMutation = useUpdatePosition(portfolioId);

  useEffect(() => {
    if (priceTouched) return;
    if (entryLoading) return;
    if (suggestedPrice != null) {
      setForm((prev) => ({ ...prev, entryPrice: String(suggestedPrice) }));
      return;
    }
    if (form.entryDate !== todayInputValue()) {
      setForm((prev) => prev.entryPrice ? { ...prev, entryPrice: '' } : prev);
    }
  }, [suggestedPrice, priceTouched, form.entryDate, entryLoading]);

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
    const raw = e.target.value;
    const value = isFractional ? raw : raw.replace(/[.,]/g, '');
    setForm((prev) => ({ ...prev, quantity: value }));
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
    const qty = Number(form.quantity);
    if (!qty || qty <= 0) return 'Geçerli bir miktar girin';
    if (!isFractional && !Number.isInteger(qty)) return 'Bu varlık için tam sayı miktar girin';
    return null;
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    const validationError = validate();
    if (validationError) {
      setError(validationError);
      return;
    }
    setPhase('confirm');
  };

  const handleConfirm = async () => {
    setError(null);
    setPhase('processing');
    const payload = {
      assetType: target.assetType,
      assetCode: target.assetCode,
      quantity: Number(form.quantity),
      entryDate: dateInputToIso(form.entryDate),
      entryPrice: Number(form.entryPrice),
    };
    const mutate = isEdit
      ? () => updateMutation.mutateAsync({ positionId: position.id, payload })
      : () => addMutation.mutateAsync(payload);
    try {
      await Promise.all([mutate(), runAnimation(PROCESSING_STEPS)]);
      setPhase('success');
      setTimeout(() => { onComplete?.(); onClose(); }, SUCCESS_HOLD_MS);
    } catch (err) {
      resetProcessing();
      setError(err?.response?.data?.message || (isEdit ? 'Güncelleme başarısız' : 'Pozisyon eklenemedi'));
      setPhase('form');
    }
  };

  const displayCode = assetCodeLabel(target.assetType, target.assetCode);
  const dismissable = phase === 'form' || phase === 'confirm';

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        exit={{ opacity: 0 }}
        className="absolute inset-0 modal-overlay backdrop-blur-sm"
        onClick={dismissable ? onClose : undefined}
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
            disabled={!dismissable}
            className="flex items-center justify-center w-8 h-8 rounded-lg text-fg-muted hover:text-fg hover:bg-surface transition-colors bg-transparent border-none cursor-pointer disabled:opacity-30"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        {phase === 'success' && (
          <SuccessPanel
            title={isEdit ? 'Pozisyon güncellendi' : 'Pozisyon eklendi'}
            subtitle={describeAction(isEdit, form, displayCode, isFractional)}
          />
        )}

        {phase === 'processing' && <ProcessingSteps steps={PROCESSING_STEPS} currentStep={processingStep} />}

        {phase === 'confirm' && (
          <ConfirmPanel
            isEdit={isEdit}
            displayCode={displayCode}
            form={form}
            isFractional={isFractional}
            totalCost={totalCost}
            onCancel={() => setPhase('form')}
            onConfirm={handleConfirm}
          />
        )}

        {phase === 'form' && (
          <form onSubmit={handleSubmit} noValidate className="space-y-4">
            <div className="space-y-1.5">
              <label className="text-xs font-medium text-fg-muted flex items-center gap-1.5">
                <Calendar className="h-3 w-3" />
                Giriş Tarihi
              </label>
              <DatePickerPopover
                value={form.entryDate}
                onChange={handleDateChange}
                onMonthChange={(y, m) => setViewMonth(`${y}-${String(m + 1).padStart(2, '0')}`)}
                minDate={limits?.minEntryDate}
                maxDate={limits?.maxEntryDate || todayInputValue()}
                highlightedDates={highlightedDates}
                loading={viewLoading}
              />
              <DataAvailabilityHint
                dataAvailable={dataAvailable}
                loading={entryLoading}
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
                placeholder="0.00"
                className="w-full rounded-lg border border-border-default bg-bg-base px-3 py-2.5 text-sm text-fg font-mono placeholder:text-fg-subtle outline-none focus:ring-1 focus:ring-accent/50 transition-all"
              />
            </div>

            <div className="space-y-1.5">
              <label className="text-xs font-medium text-fg-muted flex items-center gap-1.5">
                <Hash className="h-3 w-3" />
                Miktar {isFractional ? '' : '(adet)'}
              </label>
              <input
                type="number"
                step={isFractional ? 'any' : '1'}
                inputMode={isFractional ? 'decimal' : 'numeric'}
                value={form.quantity}
                onChange={handleQuantityChange}
                onKeyDown={isFractional ? undefined : preventDecimal}
                placeholder={isFractional ? '0.00' : 'min. 1 adet'}
                autoFocus
                className="w-full rounded-lg border border-border-default bg-bg-base px-3 py-2.5 text-sm text-fg font-mono placeholder:text-fg-subtle outline-none focus:ring-1 focus:ring-accent/50 transition-all"
              />
            </div>

            {totalCost != null && (
              <div className="rounded-xl border border-accent/30 bg-gradient-to-r from-accent/5 to-transparent px-4 py-3 flex items-center justify-between gap-3 min-w-0">
                <span className="text-xs font-semibold text-accent shrink-0">Toplam Maliyet</span>
                <span
                  className="text-lg font-bold font-mono text-accent truncate"
                  title={formatPriceTRY(totalCost)}
                >
                  {formatTotalCost(totalCost)}
                </span>
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
              className="w-full flex items-center justify-center gap-2 rounded-lg py-3 text-sm font-semibold text-white bg-accent hover:bg-accent-bright transition-all border-none cursor-pointer"
            >
              <Wallet className="h-4 w-4" />
              {isEdit ? 'Devam Et' : 'Pozisyon Ekle'}
            </button>
          </form>
        )}
      </motion.div>
    </div>
  );
}

function preventDecimal(e) {
  if (e.key === '.' || e.key === ',' || e.key === 'e' || e.key === 'E') e.preventDefault();
}

function describeAction(isEdit, form, displayCode, isFractional) {
  const qty = Number(form.quantity).toLocaleString('tr-TR', { maximumFractionDigits: isFractional ? 6 : 0 });
  return `${qty} ${isFractional ? '' : 'adet'} ${displayCode}`.trim();
}

function ConfirmPanel({ isEdit, displayCode, form, isFractional, totalCost, onCancel, onConfirm }) {
  const qtyDisplay = Number(form.quantity).toLocaleString('tr-TR', { maximumFractionDigits: isFractional ? 6 : 0 });
  const priceDisplay = formatPriceTRY(Number(form.entryPrice));
  const dateDisplay = new Date(form.entryDate).toLocaleDateString('tr-TR', { day: '2-digit', month: 'long', year: 'numeric' });
  return (
    <motion.div
      initial={{ opacity: 0, y: 5 }}
      animate={{ opacity: 1, y: 0 }}
      className="space-y-5 py-2"
    >
      <div className="flex flex-col items-center gap-3">
        <div className="flex items-center justify-center w-12 h-12 rounded-full bg-warning/10">
          <AlertTriangle className="h-6 w-6 text-warning" />
        </div>
        <div className="text-center space-y-1">
          <p className="text-sm font-semibold text-fg">İşleminizi onaylıyor musunuz?</p>
          <p className="text-xs text-fg-muted">
            <span className="font-medium text-fg">{displayCode}</span> için lot {isEdit ? 'güncellenecek' : 'eklenecek'}
          </p>
        </div>
      </div>
      <div className="rounded-xl border border-border-default bg-bg-base px-4 py-3 space-y-2">
        <Row label="Tarih" value={dateDisplay} />
        <Row label="Miktar" value={`${qtyDisplay}${isFractional ? '' : ' adet'}`} />
        <Row label="Birim Fiyat" value={priceDisplay} />
        <div className="border-t border-border-default pt-2">
          <Row label={<span className="font-semibold">Toplam Maliyet</span>} value={
            <span className="font-bold text-accent truncate" title={formatPriceTRY(totalCost)}>
              {formatTotalCost(totalCost)}
            </span>
          } />
        </div>
      </div>
      <div className="flex gap-2">
        <button
          onClick={onCancel}
          className="flex-1 rounded-lg py-2.5 text-sm font-semibold text-fg border border-border-default bg-bg-base hover:bg-surface transition-all cursor-pointer"
        >
          Vazgeç
        </button>
        <button
          onClick={onConfirm}
          className="flex-1 flex items-center justify-center gap-2 rounded-lg py-2.5 text-sm font-semibold text-white bg-accent hover:bg-accent-bright transition-all border-none cursor-pointer"
        >
          <Wallet className="h-4 w-4" />
          Onayla
        </button>
      </div>
    </motion.div>
  );
}

function Row({ label, value }) {
  return (
    <div className="flex items-center justify-between text-xs">
      <span className="text-fg-muted">{label}</span>
      <span className="font-mono font-medium text-fg">{value}</span>
    </div>
  );
}

function SuccessPanel({ title, subtitle }) {
  return (
    <motion.div
      initial={{ scale: 0.85, opacity: 0 }}
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
      <motion.div
        initial={{ opacity: 0, y: 8 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.2 }}
        className="text-center space-y-1"
      >
        <p className="text-sm font-semibold text-fg">{title}</p>
        <p className="text-xs text-fg-muted">{subtitle}</p>
      </motion.div>
      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={{ delay: 0.35 }}
        className="flex items-center gap-1.5 text-[11px] text-success/70"
      >
        <ShieldCheck className="h-3.5 w-3.5" />
        İşlem başarıyla tamamlandı
      </motion.div>
    </motion.div>
  );
}

function DataAvailabilityHint({ dataAvailable, suggestedPrice, onApply, applied, loading }) {
  if (loading) return null;
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
