import { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { X, AlertCircle, ArrowUp, ArrowDown, TrendingUp, TrendingDown } from 'lucide-react';
import { useCreatePriceAlert } from '../../shared/hooks/usePriceAlerts';
import { toast } from '../../shared/components/Toast';

const MARKET_OPTIONS = [
  { value: 'CRYPTO', label: 'Crypto' },
  { value: 'STOCK', label: 'Stock' },
  { value: 'FOREX', label: 'Forex' },
  { value: 'FUND', label: 'Fund' },
  { value: 'COMMODITY', label: 'Emtia' },
  { value: 'BOND', label: 'Bond' },
];

const DIRECTION_OPTIONS = [
  { value: 'ABOVE', label: 'Üstüne çıkarsa', Icon: ArrowUp, hint: 'fiyat eşiğin üstüne çıkarsa' },
  { value: 'BELOW', label: 'Altına düşerse', Icon: ArrowDown, hint: 'fiyat eşiğin altına düşerse' },
  { value: 'CHANGE_PCT_UP', label: '% yükselirse', Icon: TrendingUp, hint: 'referans fiyattan % yükselişte' },
  { value: 'CHANGE_PCT_DOWN', label: '% düşerse', Icon: TrendingDown, hint: 'referans fiyattan % düşüşte' },
];

export default function AddPriceAlertModal({
  isOpen,
  onClose,
  defaultMarketType,
  defaultAssetCode,
  defaultReferencePrice,
}) {
  const create = useCreatePriceAlert();
  const [marketType, setMarketType] = useState(defaultMarketType ?? 'CRYPTO');
  const [assetCode, setAssetCode] = useState(defaultAssetCode ?? '');
  const [direction, setDirection] = useState('ABOVE');
  const [threshold, setThreshold] = useState('');
  const [referencePrice, setReferencePrice] = useState(
    defaultReferencePrice != null ? String(defaultReferencePrice) : ''
  );

  useEffect(() => {
    if (isOpen) {
      setMarketType(defaultMarketType ?? 'CRYPTO');
      setAssetCode(defaultAssetCode ?? '');
      setDirection('ABOVE');
      setThreshold('');
      setReferencePrice(defaultReferencePrice != null ? String(defaultReferencePrice) : '');
    }
  }, [isOpen, defaultMarketType, defaultAssetCode, defaultReferencePrice]);

  const isPercent = direction === 'CHANGE_PCT_UP' || direction === 'CHANGE_PCT_DOWN';
  const requiresReference = isPercent;

  const submit = async (e) => {
    e.preventDefault();
    if (!assetCode.trim()) return toast.error('Asset kodu gerekli');
    const numericThreshold = Number.parseFloat(threshold);
    if (Number.isNaN(numericThreshold) || numericThreshold <= 0) {
      return toast.error('Eşik geçerli bir sayı olmalı');
    }
    let numericReference = null;
    if (requiresReference) {
      numericReference = Number.parseFloat(referencePrice);
      if (Number.isNaN(numericReference) || numericReference <= 0) {
        return toast.error('Yüzde alarmı için referans fiyat gerekli');
      }
    }
    try {
      await create.mutateAsync({
        marketType,
        assetCode: assetCode.trim().toUpperCase(),
        direction,
        threshold: numericThreshold,
        currency: 'TRY',
        referencePrice: numericReference,
      });
      toast.success('Fiyat alarmı oluşturuldu');
      onClose();
    } catch (err) {
      toast.error(err?.response?.data?.error?.message ?? 'Alarm oluşturulamadı');
    }
  };

  return (
    <AnimatePresence>
      {isOpen && (
        <>
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="fixed inset-0 z-[70] bg-black/50 backdrop-blur-sm"
            onClick={onClose}
          />
          <motion.div
            initial={{ opacity: 0, scale: 0.96, y: 8 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.96, y: 8 }}
            transition={{ type: 'spring', damping: 28, stiffness: 280 }}
            className="fixed top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 z-[80] w-[92vw] max-w-md rounded-2xl border border-border-default bg-bg-deep shadow-2xl"
          >
            <header className="flex items-center justify-between px-5 h-14 border-b border-border-default">
              <div className="flex items-center gap-2">
                <AlertCircle className="h-4 w-4 text-accent" />
                <h2 className="text-sm font-bold text-fg tracking-tight font-display">Fiyat alarmı oluştur</h2>
              </div>
              <button
                type="button"
                onClick={onClose}
                className="flex items-center justify-center w-7 h-7 rounded-md text-fg-muted hover:text-fg hover:bg-surface transition-colors bg-transparent border-none cursor-pointer"
              >
                <X className="h-4 w-4" />
              </button>
            </header>

            <form onSubmit={submit} className="p-5 space-y-4">
              <div className="space-y-1.5">
                <label className="text-[11px] font-mono uppercase tracking-wider text-fg-subtle">Pazar</label>
                <select
                  value={marketType}
                  onChange={(e) => setMarketType(e.target.value)}
                  disabled={!!defaultMarketType}
                  className="w-full rounded-lg border border-border-default bg-bg-elevated px-3 py-2 text-sm text-fg outline-none focus:border-accent transition-colors disabled:opacity-60"
                >
                  {MARKET_OPTIONS.map((opt) => (
                    <option key={opt.value} value={opt.value}>{opt.label}</option>
                  ))}
                </select>
              </div>

              <div className="space-y-1.5">
                <label className="text-[11px] font-mono uppercase tracking-wider text-fg-subtle">Asset kodu</label>
                <input
                  type="text"
                  value={assetCode}
                  onChange={(e) => setAssetCode(e.target.value)}
                  disabled={!!defaultAssetCode}
                  placeholder="örn. BTC, AAPL, USDTRY"
                  className="w-full rounded-lg border border-border-default bg-bg-elevated px-3 py-2 text-sm font-mono text-fg outline-none focus:border-accent transition-colors disabled:opacity-60"
                />
              </div>

              <div className="space-y-1.5">
                <label className="text-[11px] font-mono uppercase tracking-wider text-fg-subtle">Tetikleme</label>
                <div className="grid grid-cols-2 gap-1.5">
                  {DIRECTION_OPTIONS.map(({ value, label, Icon }) => {
                    const active = direction === value;
                    return (
                      <button
                        key={value}
                        type="button"
                        onClick={() => setDirection(value)}
                        className={`flex items-center gap-1.5 rounded-lg border px-2.5 py-2 text-xs font-medium transition-all bg-transparent cursor-pointer ${
                          active
                            ? 'border-accent/60 bg-accent/10 text-accent'
                            : 'border-border-default bg-bg-elevated text-fg-muted hover:border-border-hover hover:text-fg'
                        }`}
                      >
                        <Icon className="h-3.5 w-3.5 shrink-0" />
                        <span className="text-left leading-tight">{label}</span>
                      </button>
                    );
                  })}
                </div>
                <p className="text-[10px] text-fg-subtle leading-relaxed">
                  {DIRECTION_OPTIONS.find((d) => d.value === direction)?.hint}
                </p>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div className="space-y-1.5">
                  <label className="text-[11px] font-mono uppercase tracking-wider text-fg-subtle">
                    {isPercent ? 'Eşik (%)' : 'Eşik fiyat'}
                  </label>
                  <input
                    type="number"
                    step={isPercent ? '0.1' : '0.0001'}
                    min="0"
                    value={threshold}
                    onChange={(e) => setThreshold(e.target.value)}
                    placeholder={isPercent ? '5' : '100000'}
                    className="w-full rounded-lg border border-border-default bg-bg-elevated px-3 py-2 text-sm font-mono text-fg outline-none focus:border-accent transition-colors"
                  />
                </div>
                {requiresReference && (
                  <div className="space-y-1.5">
                    <label className="text-[11px] font-mono uppercase tracking-wider text-fg-subtle">Referans fiyat</label>
                    <input
                      type="number"
                      step="0.0001"
                      min="0"
                      value={referencePrice}
                      onChange={(e) => setReferencePrice(e.target.value)}
                      placeholder="başlangıç fiyatı"
                      className="w-full rounded-lg border border-border-default bg-bg-elevated px-3 py-2 text-sm font-mono text-fg outline-none focus:border-accent transition-colors"
                    />
                  </div>
                )}
              </div>

              <footer className="flex items-center justify-end gap-2 pt-2 border-t border-border-default">
                <button
                  type="button"
                  onClick={onClose}
                  className="px-3 py-2 rounded-lg text-xs font-medium text-fg-muted hover:text-fg hover:bg-surface transition-colors bg-transparent border border-border-default cursor-pointer"
                >
                  İptal
                </button>
                <button
                  type="submit"
                  disabled={create.isPending}
                  className="px-4 py-2 rounded-lg text-xs font-semibold text-white bg-accent hover:bg-accent-bright transition-colors border-none cursor-pointer disabled:opacity-50"
                >
                  {create.isPending ? 'Oluşturuluyor…' : 'Alarmı oluştur'}
                </button>
              </footer>
            </form>
          </motion.div>
        </>
      )}
    </AnimatePresence>
  );
}
