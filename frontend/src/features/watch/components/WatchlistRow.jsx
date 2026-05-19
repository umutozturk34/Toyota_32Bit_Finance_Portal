import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useSortable } from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import { GripVertical, TrendingUp, TrendingDown, Trash2, Pencil } from 'lucide-react';
import AssetBadge from '../../../shared/components/asset/AssetBadge';
import { useAssetDetailPrefetch } from '../../../shared/hooks/useAssetDetailPrefetch';
import { formatPercent, getChangeClass, changeColors, changeBg } from '../../../shared/utils/formatters';
import { useMoney } from '../../../shared/hooks/useMoney';
import { priceCurrencyOf } from '../../../shared/utils/priceCurrency';
import { assetRoute } from '../lib/watchConstants';

export default function WatchlistRow({ item, onRemove, onEdit, draggable }) {
  const { t } = useTranslation();
  const { format: money } = useMoney();
  const localeTag = t('common.localeTag');
  const navigate = useNavigate();
  const route = assetRoute(item.marketType, item.assetCode);
  const prefetch = useAssetDetailPrefetch();
  const triggerPrefetch = () => prefetch(item.marketType, item.assetCode);
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id: item.id,
    disabled: !draggable,
  });
  const style = draggable
    ? {
        transform: CSS.Transform.toString(transform),
        transition,
        zIndex: isDragging ? 30 : 'auto',
      }
    : undefined;

  return (
    <div
      ref={setNodeRef}
      style={style}
      onClick={route ? () => navigate(route) : undefined}
      onMouseEnter={triggerPrefetch}
      onFocus={triggerPrefetch}
      className={`relative group grid grid-cols-[auto_auto_1fr_auto_auto] gap-3 items-center px-4 py-3 border-b border-border-default last:border-b-0 transition-colors duration-150 ${
        route ? 'cursor-pointer hover:bg-accent/5' : ''
      } ${isDragging
        ? 'bg-accent/8 shadow-[0_18px_40px_-18px_rgba(99,102,241,0.55),inset_0_0_0_1px_rgba(99,102,241,0.4)] rounded-lg border-b-transparent'
        : ''}`}
    >
      {draggable ? (
        <button
          type="button"
          {...attributes}
          {...listeners}
          onClick={(e) => e.stopPropagation()}
          className={`flex items-center justify-center w-6 h-6 cursor-grab active:cursor-grabbing bg-transparent border-none touch-none transition-colors ${
            isDragging ? 'text-accent' : 'text-fg-subtle hover:text-fg'
          }`}
          title={t('watchlistRow.dragToReorder')}
        >
          <GripVertical className="h-4 w-4" />
        </button>
      ) : (
        <span className="w-6" />
      )}
      <AssetBadge
        assetType={item.marketType}
        assetCode={item.assetCode}
        assetImage={item.image}
        size="md"
      />
      <div className="flex flex-col justify-center min-w-0 leading-tight">
        <div className="flex items-center gap-2 min-w-0">
          <span className="text-sm font-semibold text-fg truncate group-hover:text-accent transition-colors leading-tight">
            {item.assetName || item.assetCode}
          </span>
          {item.deltaThreshold != null && (
            <span className="text-[10px] font-mono text-accent shrink-0 leading-none">
              ±{Number(item.deltaThreshold).toLocaleString(localeTag, { maximumFractionDigits: 4 })}%
            </span>
          )}
        </div>
        <div className="flex items-center gap-2 text-[11px] text-fg-muted mt-0.5 leading-none">
          <span className="font-mono">{item.assetCode}</span>
          {item.note && (
            <>
              <span className="text-fg-subtle">·</span>
              <span className="truncate">{item.note}</span>
            </>
          )}
        </div>
      </div>
      <div className="flex flex-col items-end justify-center leading-tight">
        <div className="text-sm font-mono font-semibold text-fg tabular-nums leading-none">
          {item.currentPrice != null ? money(item.currentPrice, priceCurrencyOf(item)) : '—'}
        </div>
        {item.changePercent != null && (() => {
          const cls = getChangeClass(item.changePercent);
          const isUp = item.changePercent > 0;
          const isDown = item.changePercent < 0;
          const ChangeIcon = isUp ? TrendingUp : isDown ? TrendingDown : null;
          return (
            <div className={`mt-1 inline-flex items-center gap-1 rounded px-1.5 py-0.5 text-[10px] font-mono font-semibold tabular-nums leading-none ${changeBg[cls]} ${changeColors[cls]}`}>
              {ChangeIcon && <ChangeIcon className="h-3 w-3" />}
              <span>{formatPercent(item.changePercent)}</span>
              {item.changeAmount != null && (
                <span className="font-normal opacity-70">
                  ({isUp ? '+' : ''}{Number(item.changeAmount).toLocaleString(localeTag, { maximumFractionDigits: 2 })})
                </span>
              )}
            </div>
          );
        })()}
      </div>
      <div className="flex items-center gap-0.5 min-w-[64px] justify-end opacity-0 group-hover:opacity-100 transition-opacity">
        <button
          type="button"
          onClick={(e) => {
            e.preventDefault();
            e.stopPropagation();
            onEdit?.(item);
          }}
          className="flex items-center justify-center w-8 h-8 rounded-lg text-fg-muted hover:text-accent hover:bg-accent/5 bg-transparent border-none cursor-pointer"
          title={t('common.edit')}
        >
          <Pencil className="h-3.5 w-3.5" />
        </button>
        <button
          type="button"
          onClick={(e) => {
            e.preventDefault();
            e.stopPropagation();
            onRemove(item.id);
          }}
          className="flex items-center justify-center w-8 h-8 rounded-lg text-fg-muted hover:text-danger hover:bg-danger/5 bg-transparent border-none cursor-pointer"
          title={t('watchlistRow.removeFromList')}
        >
          <Trash2 className="h-4 w-4" />
        </button>
      </div>
    </div>
  );
}
