import { useState, useRef, useEffect, useMemo, useCallback } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { X, ShieldCheck } from 'lucide-react';
import { ArrowUpRight, Loader2, Check, AlertCircle, RefreshCw, AlertTriangle } from '../../shared/components/AnimatedIcons';
import { portfolioService } from './portfolioService';
import { formatPriceTRY } from '../../shared/utils/formatters';
import { assetCodeLabel } from '../../shared/utils/assetCode';
import PercentageSlider from '../../shared/components/PercentageSlider';

const PROCESSING_STEPS = [
  { label: 'Satış emri doğrulanıyor...', duration: 700 },
  { label: 'Piyasa fiyatı kontrol ediliyor...', duration: 600 },
  { label: 'Portföy güncelleniyor...', duration: 800 },
];

const FRACTIONAL_TYPES = ['CRYPTO', 'FOREX', 'COMMODITY'];


export default function SellModal({ portfolioId, position, onClose, onComplete }) {
  const isFractional = FRACTIONAL_TYPES.includes(position.assetType);
  const displayAssetCode = assetCodeLabel(position.assetType, position.assetCode);
  const [inputMode, setInputMode] = useState('quantity');
  const [amountTry, setAmountTry] = useState('');
  const [quantity, setQuantity] = useState('');
  const [sliderPercent, setSliderPercent] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [processingStep, setProcessingStep] = useState(-1);
  const [success, setSuccess] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const stepTimers = useRef([]);

  useEffect(() => {
    return () => stepTimers.current.forEach(clearTimeout);
  }, []);

  const commissionRate = Number(position.commissionRate) || 0;
  const currentPrice = Number(position.currentPriceTry) || Number(position.sellPriceTry) || 0;
  const sellPriceNet = Number(position.sellPriceTry) || currentPrice;
  const maxQuantity = Number(position.quantity);
  const maxValueTry = useMemo(() => {
    if (!sellPriceNet || maxQuantity <= 0) return 0;
    return sellPriceNet * maxQuantity;
  }, [sellPriceNet, maxQuantity]);

  const computedQuantity = useMemo(() => {
    if (isFractional && inputMode === 'amount') {
      const amt = Number(amountTry);
      if (!amt || amt <= 0 || !sellPriceNet) return null;
      return amt / sellPriceNet;
    }
    const qty = Number(quantity);
    return qty > 0 ? qty : null;
  }, [sellPriceNet, amountTry, quantity, inputMode, isFractional]);

  const grossValue = useMemo(() => {
    if (!computedQuantity || !currentPrice) return null;
    return computedQuantity * currentPrice;
  }, [computedQuantity, currentPrice]);

  const netTotal = useMemo(() => {
    if (!computedQuantity || !sellPriceNet) return null;
    return computedQuantity * sellPriceNet;
  }, [computedQuantity, sellPriceNet]);

  const commissionTry = useMemo(() => {
    if (!grossValue || !netTotal) return 0;
    return grossValue - netTotal;
  }, [grossValue, netTotal]);

  const handleSliderChange = useCallback((pct) => {
    setSliderPercent(pct);
    setError(null);
    if (isFractional && inputMode === 'amount') {
      const amount = (maxValueTry * pct) / 100;
      setAmountTry(pct === 0 ? '' : String(Math.floor(amount * 100) / 100));
    } else {
      const qty = isFractional
        ? (maxQuantity * pct) / 100
        : Math.floor((maxQuantity * pct) / 100);
      setQuantity(pct === 0 ? '' : String(qty));
    }
  }, [maxQuantity, maxValueTry, inputMode, isFractional]);

  const syncSliderFromInput = useCallback((val) => {
    if (isFractional && inputMode === 'amount') {
      if (maxValueTry <= 0) { setSliderPercent(0); return; }
      const pct = Math.min(100, Math.round((Number(val) / maxValueTry) * 100));
      setSliderPercent(pct > 0 ? pct : 0);
    } else {
      if (maxQuantity <= 0) { setSliderPercent(0); return; }
      const pct = Math.min(100, Math.round((Number(val) / maxQuantity) * 100));
      setSliderPercent(pct > 0 ? pct : 0);
    }
  }, [maxQuantity, maxValueTry, inputMode, isFractional]);

  const handleModeSwitch = (mode) => {
    setInputMode(mode);
    setAmountTry('');
    setQuantity('');
    setSliderPercent(0);
    setError(null);
  };

  const runProcessingAnimation = () => {
    return new Promise((resolve) => {
      let elapsed = 0;
      PROCESSING_STEPS.forEach((step, idx) => {
        const timer = setTimeout(() => setProcessingStep(idx), elapsed);
        stepTimers.current.push(timer);
        elapsed += step.duration;
      });
      const finalTimer = setTimeout(resolve, elapsed);
      stepTimers.current.push(finalTimer);
    });
  };

  const handleSubmit = (e) => {
    e.preventDefault();

    if (isFractional && inputMode === 'amount') {
      if (!Number(amountTry) || Number(amountTry) <= 0) {
        setError('Lütfen geçerli bir tutar girin');
        return;
      }
    } else {
      if (!Number(quantity) || Number(quantity) <= 0) {
        setError('Lütfen geçerli bir miktar girin');
        return;
      }
      if (Number(quantity) > maxQuantity) {
        setError(`Maksimum ${maxQuantity} adet satabilirsiniz`);
        return;
      }
    }
    setConfirming(true);
  };

  const handleConfirm = async () => {
    setConfirming(false);
    setLoading(true);
    setError(null);
    setProcessingStep(0);

    const payload = {
      assetType: position.assetType,
      assetCode: position.assetCode,
      side: 'SELL',
      feeTry: 0,
    };

    if (isFractional && inputMode === 'amount') {
      payload.amountTry = Number(amountTry);
    } else {
      payload.quantity = Number(quantity);
    }

    try {
      await Promise.all([
        portfolioService.executeTransaction(portfolioId, payload),
        runProcessingAnimation(),
      ]);
      setSuccess(true);
      setTimeout(() => onComplete(), 1800);
    } catch (err) {
      setProcessingStep(-1);
      setError(err.response?.data?.message || 'Satış başarısız');
    } finally {
      setLoading(false);
    }
  };

  const displayQuantity = isFractional && inputMode === 'amount'
    ? computedQuantity?.toLocaleString('tr-TR', { maximumFractionDigits: 6 })
    : Number(quantity).toLocaleString('tr-TR', { maximumFractionDigits: isFractional ? 6 : 0 });

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
        className="relative w-full max-w-sm rounded-2xl border border-border-default modal-panel p-6 overflow-hidden"
      >
        <div className="absolute top-0 left-0 right-0 h-[1px] bg-gradient-to-r from-transparent via-danger/40 to-transparent" />
        <div className="flex items-center justify-between mb-5">
          <div className="flex items-center gap-3">
            <div className="flex items-center justify-center w-9 h-9 rounded-lg bg-danger/10">
              <ArrowUpRight className="h-4.5 w-4.5 text-danger" />
            </div>
            <div>
              <h2 className="text-base font-semibold text-fg">Sat</h2>
              <p className="text-xs text-fg-muted">{displayAssetCode} · {position.assetType}</p>
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
              transition={{ type: 'spring', stiffness: 300, damping: 20, delay: 0.1 }}
              className="flex items-center justify-center w-16 h-16 rounded-full bg-success/15"
            >
              <Check className="h-8 w-8 text-success" strokeWidth={2.5} />
            </motion.div>
            <motion.div
              initial={{ opacity: 0, y: 8 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.25 }}
              className="text-center space-y-1"
            >
              <p className="text-sm font-semibold text-fg">İşleminiz onaylandı</p>
              <p className="text-xs text-fg-muted">
                {isFractional && inputMode === 'amount'
                  ? `${formatPriceTRY(Number(amountTry))} tutarında ${displayAssetCode} satıldı`
                  : `${displayQuantity} adet ${displayAssetCode} satıldı`}
              </p>
            </motion.div>
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              transition={{ delay: 0.4 }}
              className="flex items-center gap-1.5 text-[11px] text-success/70"
            >
              <ShieldCheck className="h-3.5 w-3.5" />
              İşlem başarıyla tamamlandı
            </motion.div>
          </motion.div>
        ) : loading ? (
          <div className="flex flex-col items-center justify-center gap-5 py-10">
            <div className="relative">
              <RefreshCw className="h-8 w-8 text-accent animate-spin" />
            </div>
            <div className="space-y-3 w-full">
              {PROCESSING_STEPS.map((step, idx) => (
                <motion.div
                  key={idx}
                  initial={{ opacity: 0, x: -8 }}
                  animate={{
                    opacity: processingStep >= idx ? 1 : 0.3,
                    x: 0,
                  }}
                  transition={{ duration: 0.3, delay: idx * 0.1 }}
                  className="flex items-center gap-2.5 px-3"
                >
                  {processingStep > idx ? (
                    <Check className="h-3.5 w-3.5 text-success shrink-0" />
                  ) : processingStep === idx ? (
                    <Loader2 className="h-3.5 w-3.5 text-accent animate-spin shrink-0" />
                  ) : (
                    <div className="h-3.5 w-3.5 rounded-full border border-border-default shrink-0" />
                  )}
                  <span className={`text-xs font-medium ${processingStep >= idx ? 'text-fg' : 'text-fg-subtle'}`}>
                    {step.label}
                  </span>
                </motion.div>
              ))}
            </div>
          </div>
        ) : confirming ? (
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
                  {isFractional && inputMode === 'amount'
                    ? <>{formatPriceTRY(Number(amountTry))} tutarında <span className="font-medium text-fg">{displayAssetCode}</span> satılacak</>
                    : <>{displayQuantity} adet <span className="font-medium text-fg">{displayAssetCode}</span> satılacak</>}
                </p>
              </div>
            </div>
            <div className="rounded-xl border border-border-default bg-bg-base px-4 py-3 space-y-2">
              {isFractional && inputMode === 'amount' ? (
                <>
                  <div className="flex items-center justify-between text-xs">
                    <span className="text-fg-muted">Tutar</span>
                    <span className="font-mono font-medium text-fg">{formatPriceTRY(Number(amountTry))}</span>
                  </div>
                  <div className="flex items-center justify-between text-xs">
                    <span className="text-fg-muted">Birim Fiyat</span>
                    <span className="font-mono font-medium text-fg">{formatPriceTRY(sellPrice)}</span>
                  </div>
                  {commissionRate > 0 && (
                    <div className="flex items-center justify-between text-xs">
                      <span className="text-fg-muted">Komisyon (%{(commissionRate * 100).toFixed(1)})</span>
                      <span className="font-mono text-warning">-{formatPriceTRY(commissionTry)}</span>
                    </div>
                  )}
                  <div className="flex items-center justify-between text-xs border-t border-border-default pt-2">
                    <span className="text-fg-muted font-semibold">Tahmini Miktar</span>
                    <span className="font-mono font-bold text-danger">{computedQuantity?.toLocaleString('tr-TR', { maximumFractionDigits: 6 })}</span>
                  </div>
                </>
              ) : (
                <>
                  <div className="flex items-center justify-between text-xs">
                    <span className="text-fg-muted">Miktar</span>
                    <span className="font-mono font-medium text-fg">{displayQuantity}</span>
                  </div>
                  <div className="flex items-center justify-between text-xs">
                    <span className="text-fg-muted">Birim Fiyat</span>
                    <span className="font-mono font-medium text-fg">{formatPriceTRY(sellPrice)}</span>
                  </div>
                  {commissionRate > 0 && (
                    <div className="flex items-center justify-between text-xs">
                      <span className="text-fg-muted">Komisyon (%{(commissionRate * 100).toFixed(1)})</span>
                      <span className="font-mono text-warning">-{formatPriceTRY(commissionTry)}</span>
                    </div>
                  )}
                  <div className="flex items-center justify-between text-xs border-t border-border-default pt-2">
                    <span className="text-fg-muted font-semibold">{commissionRate > 0 ? 'Net Tutar' : 'Satış Tutarı'}</span>
                    <span className="font-mono font-bold text-danger">{formatPriceTRY(netTotal)}</span>
                  </div>
                </>
              )}
            </div>
            <div className="flex gap-2">
              <button
                onClick={() => setConfirming(false)}
                className="flex-1 rounded-lg py-2.5 text-sm font-semibold text-fg border border-border-default bg-bg-base hover:bg-surface transition-all cursor-pointer"
              >
                Vazgeç
              </button>
              <button
                onClick={handleConfirm}
                className="flex-1 flex items-center justify-center gap-2 rounded-lg py-2.5 text-sm font-semibold text-white bg-danger hover:bg-danger/90 transition-all border-none cursor-pointer"
              >
                <ArrowUpRight className="h-4 w-4" />
                Onayla
              </button>
            </div>
          </motion.div>
        ) : (
          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="grid grid-cols-2 gap-3">
              <div className="rounded-lg border border-border-default bg-bg-base px-3 py-2.5">
                <p className="text-[11px] text-fg-muted mb-1">Satış Fiyatı</p>
                <p className="text-sm font-bold font-mono text-fg">{formatPriceTRY(sellPrice)}</p>
              </div>
              <div className="rounded-lg border border-border-default bg-bg-base px-3 py-2.5">
                <p className="text-[11px] text-fg-muted mb-1">Sahip Olunan</p>
                <p className="text-sm font-bold font-mono text-fg">
                  {maxQuantity.toLocaleString('tr-TR', { maximumFractionDigits: isFractional ? 6 : 0 })}
                </p>
              </div>
            </div>

            {isFractional && (
              <div className="flex rounded-lg border border-border-default bg-bg-base p-0.5">
                <button
                  type="button"
                  onClick={() => handleModeSwitch('quantity')}
                  className={`flex-1 rounded-md py-2 text-xs font-medium transition-all cursor-pointer border-none ${
                    inputMode === 'quantity'
                      ? 'bg-danger text-white'
                      : 'bg-transparent text-fg-muted hover:text-fg'
                  }`}
                >
                  Adet
                </button>
                <button
                  type="button"
                  onClick={() => handleModeSwitch('amount')}
                  className={`flex-1 rounded-md py-2 text-xs font-medium transition-all cursor-pointer border-none ${
                    inputMode === 'amount'
                      ? 'bg-danger text-white'
                      : 'bg-transparent text-fg-muted hover:text-fg'
                  }`}
                >
                  TRY Tutarı
                </button>
              </div>
            )}

            <div className="space-y-1.5">
              <label className="text-xs font-medium text-fg-muted">
                {isFractional && inputMode === 'amount' ? 'Tutar (TRY)' : 'Satılacak Miktar'}
              </label>
              {isFractional && inputMode === 'amount' ? (
                <input
                  type="number"
                  step="any"
                  value={amountTry}
                  onChange={(e) => { setAmountTry(e.target.value); syncSliderFromInput(e.target.value); setError(null); }}
                  placeholder="min. 10 TRY"
                  autoFocus
                  className="w-full rounded-lg border border-border-default bg-bg-base px-3 py-2.5 text-sm text-fg font-mono placeholder:text-fg-subtle outline-none focus:ring-1 focus:ring-accent/50 transition-all"
                />
              ) : (
                <input
                  type="number"
                  step={isFractional ? 'any' : '1'}
                  value={quantity}
                  onChange={(e) => { setQuantity(e.target.value); syncSliderFromInput(e.target.value); setError(null); }}
                  placeholder={isFractional ? '0.00' : 'min. 1 adet'}
                  autoFocus
                  className="w-full rounded-lg border border-border-default bg-bg-base px-3 py-2.5 text-sm text-fg font-mono placeholder:text-fg-subtle outline-none focus:ring-1 focus:ring-accent/50 transition-all"
                />
              )}
            </div>

            <div className="space-y-1">
              <div className="flex items-center justify-between text-[11px] text-fg-subtle px-0.5">
                <span>%{sliderPercent}</span>
                <span>
                  {isFractional && inputMode === 'amount'
                    ? formatPriceTRY((maxValueTry * sliderPercent) / 100)
                    : `${((maxQuantity * sliderPercent) / 100).toLocaleString('tr-TR', { maximumFractionDigits: isFractional ? 6 : 0 })} adet`}
                </span>
              </div>
              <PercentageSlider
                value={sliderPercent}
                onChange={handleSliderChange}
                color="danger"
              />
            </div>

            {isFractional && inputMode === 'amount' && computedQuantity != null && (
              <div className="flex items-center justify-between text-xs px-1">
                <span className="text-fg-muted">Tahmini Miktar</span>
                <span className="font-mono font-medium text-fg">
                  ~{computedQuantity.toLocaleString('tr-TR', { maximumFractionDigits: 6 })} {displayAssetCode}
                </span>
              </div>
            )}

            {grossValue != null && (
              <div className="space-y-2">
                <div className="rounded-xl border border-danger/30 bg-gradient-to-r from-danger/5 to-transparent px-4 py-3 flex items-center justify-between">
                  <span className="text-xs font-semibold text-danger">Satış Tutarı</span>
                  <span className="text-lg font-bold font-mono text-danger">{formatPriceTRY(grossValue)}</span>
                </div>
                {commissionRate > 0 && (
                  <div className="rounded-lg border border-border-default bg-bg-base px-4 py-2.5 space-y-1.5">
                    <div className="flex items-center justify-between text-xs">
                      <span className="text-fg-muted">Komisyon (%{(commissionRate * 100).toFixed(1)})</span>
                      <span className="font-mono text-warning">-{formatPriceTRY(commissionTry)}</span>
                    </div>
                    <div className="flex items-center justify-between text-xs border-t border-border-default pt-1.5">
                      <span className="text-fg-muted font-semibold">Net Tutar</span>
                      <span className="font-mono font-bold text-fg">{formatPriceTRY(netTotal)}</span>
                    </div>
                  </div>
                )}
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
              className="w-full flex items-center justify-center gap-2 rounded-lg py-3 text-sm font-semibold text-white bg-danger hover:bg-danger/90 transition-all border-none cursor-pointer disabled:opacity-50"
            >
              <ArrowUpRight className="h-4 w-4" />
              Sat
            </button>
          </form>
        )}
      </motion.div>
    </div>
  );
}
