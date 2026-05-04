import { useState, useMemo } from 'react';
import { motion } from 'framer-motion';
import { AlertCircle, Eye, Check, Loader2, Trash2 } from 'lucide-react';
import {
  useWatchlist,
  useAddWatchlistItem,
  useRemoveWatchlistItem,
} from '../../shared/hooks/useWatchlist';
import AddPriceAlertModal from './AddPriceAlertModal';
import { toast } from '../../shared/components/Toast';

export default function AssetActionsBar({ marketType, assetCode, currentPrice }) {
  const [alertOpen, setAlertOpen] = useState(false);
  const watchlist = useWatchlist();
  const addItem = useAddWatchlistItem();
  const removeItem = useRemoveWatchlistItem();

  const tracked = useMemo(
    () => (watchlist.data ?? []).find(
      (i) => i.marketType === marketType && i.assetCode === assetCode
    ),
    [watchlist.data, marketType, assetCode]
  );

  const toggleWatch = async () => {
    try {
      if (tracked) {
        await removeItem.mutateAsync(tracked.id);
        toast.success(`${assetCode} takip listesinden çıkarıldı`);
      } else {
        await addItem.mutateAsync({
          marketType,
          assetCode,
          note: null,
          deltaThreshold: null,
        });
        toast.success(`${assetCode} takip listesine eklendi`);
      }
    } catch (err) {
      toast.error(err?.response?.data?.error?.message ?? 'İşlem başarısız');
    }
  };

  const watchPending = addItem.isPending || removeItem.isPending;

  return (
    <>
      <div className="flex items-center gap-2">
        <motion.button
          type="button"
          whileTap={{ scale: 0.96 }}
          onClick={toggleWatch}
          disabled={watchlist.isLoading || watchPending}
          className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold border transition-colors cursor-pointer disabled:opacity-50 ${
            tracked
              ? 'border-accent/40 bg-accent/10 text-accent'
              : 'border-border-default bg-bg-elevated text-fg hover:border-border-hover hover:bg-surface'
          }`}
        >
          {watchPending ? (
            <Loader2 className="h-3.5 w-3.5 animate-spin" />
          ) : tracked ? (
            <Check className="h-3.5 w-3.5" />
          ) : (
            <Eye className="h-3.5 w-3.5" />
          )}
          {tracked ? 'Takipte' : 'Takip et'}
          {tracked && (
            <Trash2
              className="h-3 w-3 ml-1 opacity-60 hover:opacity-100"
              aria-label="Çıkar"
            />
          )}
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
    </>
  );
}
