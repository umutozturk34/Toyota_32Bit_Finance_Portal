import { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { X, Zap } from 'lucide-react';
import { useAddWatchlistItem, useAddToFavorites } from '../../shared/hooks/useWatchlist';
import { toast } from '../../shared/components/Toast';

const MARKET_OPTIONS = [
  { value: 'CRYPTO', label: 'Crypto' },
  { value: 'STOCK', label: 'Stock' },
  { value: 'FOREX', label: 'Forex' },
  { value: 'FUND', label: 'Fund' },
  { value: 'COMMODITY', label: 'Emtia' },
  { value: 'BOND', label: 'Bond' },
];

export default function AddWatchlistItemModal({
  isOpen,
  onClose,
  watchlistId,
  defaultMarketType,
  defaultAssetCode,
}) {
  const add = useAddWatchlistItem();
  const addToFavorites = useAddToFavorites();
  const [marketType, setMarketType] = useState(defaultMarketType ?? 'CRYPTO');
  const [assetCode, setAssetCode] = useState(defaultAssetCode ?? '');
  const [note, setNote] = useState('');
  const [deltaThreshold, setDeltaThreshold] = useState('');

  useEffect(() => {
    if (isOpen) {
      setMarketType(defaultMarketType ?? 'CRYPTO');
      setAssetCode(defaultAssetCode ?? '');
      setNote('');
      setDeltaThreshold('');
    }
  }, [isOpen, defaultMarketType, defaultAssetCode]);

  const submit = async (e) => {
    e.preventDefault();
    if (!assetCode.trim()) return toast.error('Asset kodu gerekli');
    let numericThreshold = null;
    if (deltaThreshold !== '') {
      const parsed = Number.parseFloat(deltaThreshold);
      if (Number.isNaN(parsed) || parsed <= 0) {
        return toast.error('% eşiği geçerli pozitif sayı olmalı');
      }
      numericThreshold = parsed;
    }
    const payload = {
      marketType,
      assetCode: assetCode.trim().toUpperCase(),
      note: note.trim() || null,
      deltaThreshold: numericThreshold,
    };
    try {
      if (watchlistId != null) {
        await add.mutateAsync({ watchlistId, ...payload });
      } else {
        await addToFavorites.mutateAsync(payload);
      }
      toast.success('Takip listesine eklendi');
      onClose();
    } catch (err) {
      toast.error(err?.response?.data?.error?.message ?? 'Ekleme başarısız');
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
                <Zap className="h-4 w-4 text-warning" />
                <h2 className="text-sm font-bold text-fg tracking-tight font-display">Takip listesine ekle</h2>
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
                <label className="text-[11px] font-semibold uppercase tracking-wide text-fg-muted">Pazar</label>
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
                <label className="text-[11px] font-semibold uppercase tracking-wide text-fg-muted">Asset kodu</label>
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
                <label className="text-[11px] font-semibold uppercase tracking-wide text-fg-muted">Not (opsiyonel)</label>
                <input
                  type="text"
                  value={note}
                  onChange={(e) => setNote(e.target.value)}
                  maxLength={255}
                  placeholder="ETF spot dönemi"
                  className="w-full rounded-lg border border-border-default bg-bg-elevated px-3 py-2 text-sm text-fg outline-none focus:border-accent transition-colors"
                />
              </div>

              <div className="space-y-1.5">
                <label className="text-[11px] font-semibold uppercase tracking-wide text-fg-muted">% değişim eşiği (opsiyonel)</label>
                <input
                  type="number"
                  step="0.1"
                  min="0"
                  value={deltaThreshold}
                  onChange={(e) => setDeltaThreshold(e.target.value)}
                  placeholder="varsayılan 5"
                  className="w-full rounded-lg border border-border-default bg-bg-elevated px-3 py-2 text-sm font-mono text-fg outline-none focus:border-accent transition-colors"
                />
                <p className="text-[10px] text-fg-subtle leading-relaxed">
                  Boş bırakırsan varsayılan global eşik kullanılır. Bu eşiği aşan değişimlerde bildirim atılır.
                </p>
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
                  disabled={add.isPending || addToFavorites.isPending}
                  className="px-4 py-2 rounded-lg text-xs font-semibold text-white bg-accent hover:bg-accent-bright transition-colors border-none cursor-pointer disabled:opacity-50"
                >
                  {add.isPending || addToFavorites.isPending ? 'Ekleniyor…' : 'Ekle'}
                </button>
              </footer>
            </form>
          </motion.div>
        </>
      )}
    </AnimatePresence>
  );
}
