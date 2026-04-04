import { useState, useRef, useEffect, useMemo } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { X, ArrowUpRight, Loader2, Check, AlertCircle, ShieldCheck, RefreshCw, AlertTriangle } from 'lucide-react';
import { portfolioService } from './portfolioService';
import { formatPriceTRY } from '../../shared/utils/formatters';

const ASSET_TYPE_LABELS = {
  CRYPTO: 'Kripto',
  STOCK: 'Hisse',
  FOREX: 'Döviz',
  FUND: 'Fon',
};

const PROCESSING_STEPS = [
  { label: 'Satış emri doğrulanıyor...', duration: 700 },
  { label: 'Piyasa fiyatı kontrol ediliyor...', duration: 600 },
  { label: 'Portföy güncelleniyor...', duration: 800 },
];

export default function SellModal({ portfolioId, position, onClose, onComplete }) {
  const [quantity, setQuantity] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [processingStep, setProcessingStep] = useState(-1);
  const [success, setSuccess] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const stepTimers = useRef([]);

  useEffect(() => {
    return () => stepTimers.current.forEach(clearTimeout);
  }, []);

  const maxQuantity = Number(position.quantity);

  const totalValue = useMemo(() => {
    if (!position.currentPriceTry || !quantity || Number(quantity) <= 0) return null;
    return Number(position.currentPriceTry) * Number(quantity);
  }, [position.currentPriceTry, quantity]);

  const handleSetMax = () => {
    setQuantity(String(maxQuantity));
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
    const qty = Number(quantity);
    if (!qty || qty <= 0) {
      setError('Lütfen geçerli bir miktar girin');
      return;
    }
    if (qty > maxQuantity) {
      setError(`Maksimum ${maxQuantity} adet satabilirsiniz`);
      return;
    }
    setConfirming(true);
  };

  const handleConfirm = async () => {
    setConfirming(false);
    setLoading(true);
    setError(null);
    setProcessingStep(0);
    try {
      await Promise.all([
        portfolioService.executeTransaction(portfolioId, {
          assetType: position.assetType,
          assetCode: position.assetCode,
          side: 'SELL',
          quantity: Number(quantity),
          feeTry: 0,
        }),
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
              <p className="text-xs text-fg-muted">
                {position.assetCode} · {ASSET_TYPE_LABELS[position.assetType] || position.assetType}
              </p>
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
              <p className="text-xs text-fg-muted">{quantity} adet {position.assetCode} satıldı</p>
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
                  {quantity} adet <span className="font-medium text-fg">{position.assetCode}</span> satılacak
                </p>
              </div>
            </div>
            <div className="rounded-xl border border-border-default bg-bg-base px-4 py-3 space-y-2">
              <div className="flex items-center justify-between text-xs">
                <span className="text-fg-muted">Miktar</span>
                <span className="font-mono font-medium text-fg">{Number(quantity).toLocaleString('tr-TR', { maximumFractionDigits: 6 })}</span>
              </div>
              <div className="flex items-center justify-between text-xs">
                <span className="text-fg-muted">Birim Fiyat</span>
                <span className="font-mono font-medium text-fg">{formatPriceTRY(position.currentPriceTry)}</span>
              </div>
              <div className="flex items-center justify-between text-xs border-t border-border-default pt-2">
                <span className="text-fg-muted font-semibold">Toplam Tutar</span>
                <span className="font-mono font-bold text-danger">{formatPriceTRY(totalValue)}</span>
              </div>
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
                <p className="text-[11px] text-fg-muted mb-1">Güncel Fiyat</p>
                <p className="text-sm font-bold font-mono text-fg">{formatPriceTRY(position.currentPriceTry)}</p>
              </div>
              <div className="rounded-lg border border-border-default bg-bg-base px-3 py-2.5">
                <p className="text-[11px] text-fg-muted mb-1">Sahip Olunan</p>
                <p className="text-sm font-bold font-mono text-fg">
                  {maxQuantity.toLocaleString('tr-TR', { maximumFractionDigits: 6 })}
                </p>
              </div>
            </div>

            <div className="space-y-1.5">
              <div className="flex items-center justify-between">
                <label className="text-xs font-medium text-fg-muted">Satılacak Miktar</label>
                <button
                  type="button"
                  onClick={handleSetMax}
                  className="text-[11px] font-medium text-accent hover:text-accent-bright transition-colors bg-transparent border-none cursor-pointer"
                >
                  Tümünü Sat
                </button>
              </div>
              <input
                type="number"
                step="any"
                min="0"
                max={maxQuantity}
                value={quantity}
                onChange={(e) => { setQuantity(e.target.value); setError(null); }}
                placeholder="0.00"
                autoFocus
                className="w-full rounded-lg border border-border-default bg-bg-base px-3 py-2.5 text-sm text-fg font-mono placeholder:text-fg-subtle outline-none focus:ring-1 focus:ring-accent/50 transition-all"
              />
            </div>

            {totalValue != null && (
              <div className="rounded-xl border border-danger/30 bg-gradient-to-r from-danger/5 to-transparent px-4 py-3 flex items-center justify-between">
                <span className="text-xs font-semibold text-danger">Satış Tutarı</span>
                <span className="text-lg font-bold font-mono text-danger">{formatPriceTRY(totalValue)}</span>
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
              disabled={loading || !quantity || Number(quantity) <= 0}
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
