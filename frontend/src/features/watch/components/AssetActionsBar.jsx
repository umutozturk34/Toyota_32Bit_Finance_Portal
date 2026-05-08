import { useState, useMemo } from 'react';
import { AlertCircle, Eye, Star, Loader2 } from 'lucide-react';
import {
  useAddToFavorites,
  useWatchlists,
  useWatchlistItems,
  useRemoveWatchlistItem,
} from '../../../shared/hooks/useWatchlist';
import AddPriceAlertModal from '../components/AddPriceAlertModal';
import AddWatchlistItemModal from '../components/AddWatchlistItemModal';
import { toast } from '../../../shared/components/feedback/Toast';
import { extractApiError } from '../../../shared/utils/apiError';

export default function AssetActionsBar({ marketType, assetCode, currentPrice }) {
  const [alertOpen, setAlertOpen] = useState(false);
  const [watchModalOpen, setWatchModalOpen] = useState(false);
  const lists = useWatchlists();
  const defaultList = useMemo(
    () => (lists.data ?? []).find((w) => w.isDefault) ?? null,
    [lists.data],
  );
  const favoritesItems = useWatchlistItems(defaultList?.id ?? null);
  const favoriteEntry = useMemo(
    () => (favoritesItems.data ?? []).find(
      (it) => it.marketType === marketType && it.assetCode === assetCode,
    ),
    [favoritesItems.data, marketType, assetCode],
  );
  const isFavorite = favoriteEntry != null;
  const addToFavorites = useAddToFavorites();
  const removeWatchlistItem = useRemoveWatchlistItem(defaultList?.id);

  const toggleFavorite = async () => {
    try {
      if (isFavorite) {
        await removeWatchlistItem.mutateAsync(favoriteEntry.id);
        toast.success(`${assetCode} favorilerden çıkarıldı`);
      } else {
        await addToFavorites.mutateAsync({
          marketType,
          assetCode,
          note: null,
          deltaThreshold: null,
        });
        toast.success(`${assetCode} favorilere eklendi`);
      }
    } catch (err) {
      toast.error(extractApiError(err, 'İşlem başarısız'));
    }
  };

  const pending = addToFavorites.isPending || removeWatchlistItem.isPending;

  return (
    <>
      <div className="flex items-center gap-2">
        <motion.button
          type="button"
          whileTap={{ scale: 0.96 }}
          onClick={toggleFavorite}
          disabled={pending}
          title={isFavorite ? 'Favorilerden çıkar' : 'Favorilere ekle'}
          className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold border transition-colors cursor-pointer disabled:opacity-50 ${
            isFavorite
              ? 'border-warning/60 bg-warning/15 text-warning hover:bg-warning/20'
              : 'border-warning/40 bg-warning/5 text-warning hover:bg-warning/10'
          }`}
        >
          {pending ? (
            <Loader2 className="h-3.5 w-3.5 animate-spin" />
          ) : (
            <Star className={`h-3.5 w-3.5 ${isFavorite ? 'fill-warning' : ''}`} />
          )}
          {isFavorite ? 'Favorilerde' : 'Favorilere ekle'}
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
