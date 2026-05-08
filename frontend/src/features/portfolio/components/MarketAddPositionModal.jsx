import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { X, Wallet } from 'lucide-react';
import { Loader2 } from '../../../shared/components/AnimatedIcons';
import PositionFormModal from './PositionFormModal';
import { usePortfolioList } from '../hooks/usePortfolioData';

export default function MarketAddPositionModal({ assetType, assetCode, assetName, assetImage, currentPrice, onClose, onComplete }) {
  const navigate = useNavigate();
  const { data: portfolios, isLoading } = usePortfolioList();
  const portfolioId = portfolios?.[0]?.id ?? null;

  if (isLoading) {
    return (
      <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
        <div className="absolute inset-0 modal-overlay backdrop-blur-sm" onClick={onClose} />
        <div className="relative flex items-center justify-center w-12 h-12 rounded-full bg-bg-elevated border border-border-default">
          <Loader2 className="h-5 w-5 animate-spin text-accent" />
        </div>
      </div>
    );
  }

  if (!portfolioId) {
    return (
      <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          className="absolute inset-0 modal-overlay backdrop-blur-sm"
          onClick={onClose}
        />
        <motion.div
          initial={{ opacity: 0, scale: 0.95 }}
          animate={{ opacity: 1, scale: 1 }}
          className="relative w-full max-w-sm rounded-2xl border border-border-default modal-panel p-6"
        >
          <button
            onClick={onClose}
            className="absolute top-3 right-3 flex items-center justify-center w-7 h-7 rounded-lg text-fg-muted hover:text-fg hover:bg-surface transition-colors bg-transparent border-none cursor-pointer"
          >
            <X className="h-3.5 w-3.5" />
          </button>
          <div className="flex flex-col items-center gap-4 py-4">
            <div className="flex items-center justify-center w-14 h-14 rounded-2xl bg-accent/10">
              <Wallet className="h-7 w-7 text-accent" />
            </div>
            <div className="text-center space-y-1">
              <p className="text-sm font-semibold text-fg">Portföy bulunamadı</p>
              <p className="text-xs text-fg-muted">Pozisyon ekleyebilmek için önce portföyünüzü oluşturmanız gerekiyor.</p>
            </div>
            <button
              onClick={() => { onClose(); navigate('/portfolio'); }}
              className="flex items-center gap-2 rounded-lg bg-accent px-5 py-2 text-sm font-semibold text-white transition-all hover:bg-accent-bright border-none cursor-pointer"
            >
              <Wallet className="h-4 w-4" />
              Portföye Git
            </button>
          </div>
        </motion.div>
      </div>
    );
  }

  return (
    <PositionFormModal
      mode="add"
      portfolioId={portfolioId}
      asset={{ type: assetType, code: assetCode, name: assetName, image: assetImage, currentPrice }}
      onClose={onClose}
      onComplete={onComplete}
    />
  );
}
