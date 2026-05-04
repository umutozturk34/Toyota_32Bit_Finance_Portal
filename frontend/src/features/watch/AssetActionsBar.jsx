import { useState } from 'react';
import { motion } from 'framer-motion';
import { AlertCircle, Eye, Star, Loader2 } from 'lucide-react';
import { useAddToFavorites } from '../../shared/hooks/useWatchlist';
import AddPriceAlertModal from './AddPriceAlertModal';
import AddWatchlistItemModal from './AddWatchlistItemModal';
import { toast } from '../../shared/components/Toast';

export default function AssetActionsBar({ marketType, assetCode, currentPrice }) {
  const [alertOpen, setAlertOpen] = useState(false);
  const [watchModalOpen, setWatchModalOpen] = useState(false);
  const addToFavorites = useAddToFavorites();

  const quickFavorite = async () => {
    try {
      await addToFavorites.mutateAsync({
        marketType,
        assetCode,
        note: null,
        deltaThreshold: null,
      });
      toast.success(`${assetCode} favorilere eklendi`);
    } catch (err) {
      toast.error(err?.response?.data?.error?.message ?? 'Ekleme başarısız');
    }
  };

  return (
    <>
      <div className="flex items-center gap-2">
        <motion.button
          type="button"
          whileTap={{ scale: 0.96 }}
          onClick={quickFavorite}
          disabled={addToFavorites.isPending}
          title="Favorilere ekle"
          className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold border transition-colors cursor-pointer disabled:opacity-50 border-warning/40 bg-warning/5 text-warning hover:bg-warning/10"
        >
          {addToFavorites.isPending ? (
            <Loader2 className="h-3.5 w-3.5 animate-spin" />
          ) : (
            <Star className="h-3.5 w-3.5" />
          )}
          Favorilere ekle
        </motion.button>

        <motion.button
          type="button"
          whileTap={{ scale: 0.96 }}
          onClick={() => setWatchModalOpen(true)}
          title="Başka bir listeye ekle"
          className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold border border-border-default bg-bg-elevated text-fg hover:border-border-hover hover:bg-surface transition-colors cursor-pointer"
        >
          <Eye className="h-3.5 w-3.5" />
          Listeye ekle
        </motion.button>

        <motion.button
          type="button"
          whileTap={{ scale: 0.96 }}
          onClick={() => setAlertOpen(true)}
          className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold text-white bg-accent hover:bg-accent-bright transition-colors border-none cursor-pointer"
        >
          <AlertCircle className="h-3.5 w-3.5" />
          Fiyat alarmı
        </motion.button>
      </div>

      <AddPriceAlertModal
        isOpen={alertOpen}
        onClose={() => setAlertOpen(false)}
        defaultMarketType={marketType}
        defaultAssetCode={assetCode}
        defaultReferencePrice={currentPrice}
      />
      <AddWatchlistItemModal
        isOpen={watchModalOpen}
        onClose={() => setWatchModalOpen(false)}
        defaultMarketType={marketType}
        defaultAssetCode={assetCode}
      />
    </>
  );
}
