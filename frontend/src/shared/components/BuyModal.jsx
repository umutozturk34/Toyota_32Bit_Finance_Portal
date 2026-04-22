import { useState, useEffect, useMemo, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { motion, AnimatePresence } from 'framer-motion';
import { X, Wallet, ShieldCheck } from 'lucide-react';
import { ArrowDownRight, Loader2, Check, AlertCircle, RefreshCw, AlertTriangle } from './AnimatedIcons';
import { portfolioService } from '../../features/portfolio/portfolioService';
import { unifiedMarketService } from '../services/unifiedMarketService';
import { formatPriceTRY } from '../utils/formatters';
import { assetCodeLabel } from '../utils/assetCode';
import PercentageSlider from './PercentageSlider';
import useProcessingAnimation from '../hooks/useProcessingAnimation';

const PROCESSING_STEPS = [
  { label: 'İşlem doğrulanıyor...', duration: 800 },
  { label: 'Piyasa fiyatı kontrol ediliyor...', duration: 600 },
  { label: 'Portföy güncelleniyor...', duration: 700 },
];

const FRACTIONAL_TYPES = ['CRYPTO', 'FOREX', 'COMMODITY'];

export default function BuyModal({ assetType, assetCode, assetName, currentPrice: initialPrice, onClose, onComplete }) {
  const navigate = useNavigate();
  const isFractional = FRACTIONAL_TYPES.includes(assetType);
  const displayAssetCode = assetCodeLabel(assetType, assetCode);

  const { data: freshAsset } = useQuery({
    queryKey: ['marketAsset', assetType, assetCode],
    queryFn: () => unifiedMarketService.getByCode(assetType, assetCode),
    enabled: Boolean(assetType && assetCode),
    refetchOnMount: 'always',
    staleTime: 0,
  });
  const currentPrice = freshAsset?.price ?? initialPrice;
  const [inputMode, setInputMode] = useState('amount');
  const [amountTry, setAmountTry] = useState('');
  const [quantity, setQuantity] = useState('');
  const [sliderPercent, setSliderPercent] = useState(0);
  const [loading, setLoading] = useState(false);
  const [portfolioLoading, setPortfolioLoading] = useState(true);
  const [portfolioId, setPortfolioId] = useState(null);
  const [walletBalance, setWalletBalance] = useState(null);
  const [noPortfolio, setNoPortfolio] = useState(false);
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const { processingStep, runAnimation, reset: resetProcessing } = useProcessingAnimation();

  useEffect(() => {
    let cancelled = false;
    const init = async () => {
      setPortfolioLoading(true);
      try {
        const portfolios = await portfolioService.list();
        if (!cancelled) {
          if (!portfolios || portfolios.length === 0) {
            setNoPortfolio(true);
          } else {
            setPortfolioId(portfolios[0].id);
            setWalletBalance(portfolios[0].cashBalanceTry);
          }
        }
      } catch {
        if (!cancelled) setNoPortfolio(true);
      } finally {
        if (!cancelled) setPortfolioLoading(false);
      }
    };
    init();
    return () => { cancelled = true; };
  }, []);

  const computedQuantity = useMemo(() => {
    if (!currentPrice || currentPrice <= 0) return null;
    if (isFractional && inputMode === 'amount') {
      const amt = Number(amountTry);
      if (!amt || amt <= 0) return null;
      return amt / currentPrice;
    }
    const qty = Number(quantity);
    if (!qty || qty <= 0) return null;
    return qty;
  }, [currentPrice, amountTry, quantity, inputMode, isFractional]);

  const totalCost = useMemo(() => {
    if (!currentPrice || currentPrice <= 0) return null;
    if (isFractional && inputMode === 'amount') {
      const amt = Number(amountTry);
      return amt > 0 ? amt : null;
    }
    const qty = Number(quantity);
    return qty > 0 ? currentPrice * qty : null;
  }, [currentPrice, amountTry, quantity, inputMode, isFractional]);

  const handleSliderChange = useCallback((pct) => {
    if (!walletBalance || walletBalance <= 0) return;
    setSliderPercent(pct);
    setError(null);
    const amount = (walletBalance * pct) / 100;
    if (isFractional && inputMode === 'amount') {
      setAmountTry(pct === 0 ? '' : String(Math.floor(amount * 100) / 100));
    } else if (currentPrice > 0) {
      const qty = isFractional
        ? amount / currentPrice
        : Math.floor(amount / currentPrice);
      setQuantity(pct === 0 ? '' : String(qty));
    }
  }, [walletBalance, inputMode, isFractional, currentPrice]);

  const syncSliderFromInput = useCallback((val) => {
    if (!walletBalance || walletBalance <= 0) { setSliderPercent(0); return; }
    const cost = isFractional && inputMode === 'amount' ? Number(val) : Number(val) * (currentPrice || 0);
    const pct = Math.min(100, Math.round((cost / walletBalance) * 100));
    setSliderPercent(pct > 0 ? pct : 0);
  }, [walletBalance, inputMode, isFractional, currentPrice]);

  const handleModeSwitch = (mode) => {
    setInputMode(mode);
    setAmountTry('');
    setQuantity('');
    setSliderPercent(0);
    setError(null);
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    if (!portfolioId) return;

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
    }
    setConfirming(true);
  };

  const handleConfirm = async () => {
    setConfirming(false);
    setLoading(true);
    setError(null);

    const payload = {
      assetType,
      assetCode,
      side: 'BUY',
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
        runAnimation(PROCESSING_STEPS),
      ]);
      setSuccess(true);
      setTimeout(() => {
        onComplete?.();
        onClose();
      }, 1800);
    } catch (err) {
      resetProcessing();
      setError(err.response?.data?.message || 'Satın alma başarısız');
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
        <div className="absolute top-0 left-0 right-0 h-[1px] bg-gradient-to-r from-transparent via-success/40 to-transparent" />
        <div className="flex items-center justify-between mb-5">
          <div className="flex items-center gap-3">
            <div className="flex items-center justify-center w-9 h-9 rounded-lg bg-success/10">
              <ArrowDownRight className="h-4.5 w-4.5 text-success" />
            </div>
            <div>
              <h2 className="text-base font-semibold text-fg">Satın Al</h2>
              <p className="text-xs text-fg-muted">{assetName || displayAssetCode}</p>
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

        {portfolioLoading ? (
          <div className="flex items-center justify-center py-12">
            <Loader2 className="h-6 w-6 animate-spin text-accent" />
          </div>
        ) : noPortfolio ? (
          <div className="flex flex-col items-center justify-center gap-4 py-8">
            <div className="flex items-center justify-center w-14 h-14 rounded-2xl bg-accent/10">
              <Wallet className="h-7 w-7 text-accent" />
            </div>
            <div className="text-center space-y-1">
              <p className="text-sm font-semibold text-fg">Portföy bulunamadı</p>
              <p className="text-xs text-fg-muted">Alım yapabilmek için önce portföyünüzü oluşturmanız gerekiyor.</p>
            </div>
            <button
              onClick={() => { onClose(); navigate('/portfolio'); }}
              className="flex items-center gap-2 rounded-lg bg-accent px-5 py-2 text-sm font-semibold text-white transition-all hover:bg-accent-bright border-none cursor-pointer"
            >
              <Wallet className="h-4 w-4" />
              Portföye Git
            </button>
          </div>
        ) : success ? (
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
                  ? `${formatPriceTRY(Number(amountTry))} tutarında ${displayAssetCode} satın alındı`
                  : `${displayQuantity} adet ${displayAssetCode} satın alındı`}
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
                    ? <>{formatPriceTRY(Number(amountTry))} tutarında <span className="font-medium text-fg">{displayAssetCode}</span> satın alınacak</>
                    : <>{displayQuantity} adet <span className="font-medium text-fg">{displayAssetCode}</span> satın alınacak</>}
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
                    <span className="font-mono font-medium text-fg">{formatPriceTRY(currentPrice)}</span>
                  </div>
                  <div className="flex items-center justify-between text-xs border-t border-border-default pt-2">
                    <span className="text-fg-muted font-semibold">Tahmini Miktar</span>
                    <span className="font-mono font-bold text-success">{computedQuantity?.toLocaleString('tr-TR', { maximumFractionDigits: 6 })}</span>
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
                    <span className="font-mono font-medium text-fg">{formatPriceTRY(currentPrice)}</span>
                  </div>
                  <div className="flex items-center justify-between text-xs border-t border-border-default pt-2">
                    <span className="text-fg-muted font-semibold">Toplam Tutar</span>
                    <span className="font-mono font-bold text-success">{formatPriceTRY(totalCost)}</span>
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
                className="flex-1 flex items-center justify-center gap-2 rounded-lg py-2.5 text-sm font-semibold text-white bg-success hover:bg-success/90 transition-all border-none cursor-pointer"
              >
                <ArrowDownRight className="h-4 w-4" />
                Onayla
              </button>
            </div>
          </motion.div>
        ) : (
          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="rounded-lg border border-border-default bg-bg-base px-4 py-3 flex items-center justify-between">
              <span className="text-xs text-fg-muted font-medium">Güncel Fiyat</span>
              {currentPrice != null ? (
                <span className="text-base font-bold font-mono text-fg">{formatPriceTRY(currentPrice)}</span>
              ) : (
                <span className="text-xs text-danger">Fiyat bulunamadı</span>
              )}
            </div>

            <div className="flex items-center justify-between text-[11px] px-1 text-fg-subtle">
              <span>Alım komisyonu</span>
              <span className="font-mono">Alınmaz</span>
            </div>

            {walletBalance != null && (
              <div className="flex items-center justify-between text-xs px-1">
                <span className="text-fg-muted">Kullanılabilir Bakiye</span>
                <span className="font-mono font-medium text-fg">{formatPriceTRY(walletBalance)}</span>
              </div>
            )}

            {isFractional && (
              <div className="flex rounded-lg border border-border-default bg-bg-base p-0.5">
                <button
                  type="button"
                  onClick={() => handleModeSwitch('amount')}
                  className={`flex-1 rounded-md py-2 text-xs font-medium transition-all cursor-pointer border-none ${
                    inputMode === 'amount'
                      ? 'bg-accent text-white'
                      : 'bg-transparent text-fg-muted hover:text-fg'
                  }`}
                >
                  TRY Tutarı
                </button>
                <button
                  type="button"
                  onClick={() => handleModeSwitch('quantity')}
                  className={`flex-1 rounded-md py-2 text-xs font-medium transition-all cursor-pointer border-none ${
                    inputMode === 'quantity'
                      ? 'bg-accent text-white'
                      : 'bg-transparent text-fg-muted hover:text-fg'
                  }`}
                >
                  Adet
                </button>
              </div>
            )}

            <div className="space-y-1.5">
              <label className="text-xs font-medium text-fg-muted">
                {isFractional && inputMode === 'amount' ? 'Tutar (TRY)' : 'Miktar'}
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

            {walletBalance > 0 && (
              <div className="space-y-1">
                <div className="flex items-center justify-between text-[11px] text-fg-subtle px-0.5">
                  <span>%{sliderPercent}</span>
                  <span>{formatPriceTRY((walletBalance * sliderPercent) / 100)}</span>
                </div>
                <PercentageSlider
                  value={sliderPercent}
                  onChange={handleSliderChange}
                  color="success"
                />
              </div>
            )}

            {isFractional && inputMode === 'amount' && computedQuantity != null && (
              <div className="flex items-center justify-between text-xs px-1">
                <span className="text-fg-muted">Tahmini Miktar</span>
                <span className="font-mono font-medium text-fg">
                  ~{computedQuantity.toLocaleString('tr-TR', { maximumFractionDigits: 6 })} {displayAssetCode}
                </span>
              </div>
            )}

            {totalCost != null && (
              <div className="rounded-xl border border-success/30 bg-gradient-to-r from-success/5 to-transparent px-4 py-3 flex items-center justify-between">
                <span className="text-xs font-semibold text-success">Toplam Tutar</span>
                <span className="text-lg font-bold font-mono text-success">{formatPriceTRY(totalCost)}</span>
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
              disabled={loading || !currentPrice}
              className="w-full flex items-center justify-center gap-2 rounded-lg py-3 text-sm font-semibold text-white bg-success hover:bg-success/90 transition-all border-none cursor-pointer disabled:opacity-50"
            >
              <ArrowDownRight className="h-4 w-4" />
              Satın Al
            </button>
          </form>
        )}
      </motion.div>
    </div>
  );
}
