import { X } from 'lucide-react';
import { ASSET_TYPE_COLORS } from '../../../constants/assetTypes';
import { assetCodeLabel } from '../../../utils/assetCode';
import { commodityLabel } from '../../../utils/commodityName';

// Pure presentation for one recent-search row. The host owns all state and behaviour; this component
// only renders the row and forwards clicks. SecondaryIcon (when a secondaryAction is supplied) lets a
// recently-viewed asset be added straight from the recent panel, mirroring the live-results row.
export default function RecentSearchRow({
  t,
  item,
  secondaryAction,
  SecondaryIcon,
  onSelect,
  onSecondaryAction,
  onRemove,
}) {
  const typeColor = ASSET_TYPE_COLORS[item.type] || '#8b5cf6';
  return (
    <div
      className="group w-full flex items-center hover:bg-surface/50 transition-colors"
    >
      <button
        onClick={() => onSelect(item)}
        className="flex-1 min-w-0 flex items-center gap-3 px-4 py-2.5 text-left bg-transparent border-none cursor-pointer"
      >
        <span
          className="flex items-center justify-center w-8 h-8 rounded-lg text-[10px] font-bold shrink-0"
          style={{ backgroundColor: typeColor + '18', color: typeColor }}
        >
          {assetCodeLabel(item.type, item.code).slice(0, 3).toUpperCase()}
        </span>
        <div className="flex-1 min-w-0">
          <span className="block text-sm font-semibold text-fg truncate">
            {commodityLabel(t, item.type, item.code, item.name || assetCodeLabel(item.type, item.code))}
          </span>
          <span className="block text-[11px] text-fg-subtle font-mono truncate">
            {assetCodeLabel(item.type, item.code)}
          </span>
        </div>
        <span
          className="shrink-0 rounded px-1.5 py-0.5 text-[9px] font-bold uppercase tracking-wider"
          style={{ backgroundColor: typeColor + '18', color: typeColor }}
        >
          {t(`assets.labels.${item.type}`, { defaultValue: item.type })}
        </span>
      </button>
      {secondaryAction && (!secondaryAction.shouldShow || secondaryAction.shouldShow(item)) && (
        <button
          onClick={(e) => { e.stopPropagation(); onSecondaryAction(item); }}
          title={secondaryAction.label}
          aria-label={secondaryAction.label}
          className="shrink-0 flex items-center justify-center h-8 w-8 rounded-lg bg-accent/10 text-accent hover:bg-accent/20 transition-colors cursor-pointer border-none"
        >
          {SecondaryIcon && <SecondaryIcon className="h-4 w-4" />}
        </button>
      )}
      <button
        onClick={(e) => { e.stopPropagation(); onRemove(item); }}
        aria-label={t('searchSuggestions.removeRecent', { defaultValue: 'Kaldır' })}
        className="shrink-0 mr-2 flex items-center justify-center h-7 w-7 rounded-md text-fg-subtle hover:text-rose-400 hover:bg-rose-500/10 transition-colors cursor-pointer border-none bg-transparent opacity-0 group-hover:opacity-100 group-focus-within:opacity-100 focus-visible:opacity-100 [@media(hover:none)]:opacity-60"
      >
        <X className="h-3.5 w-3.5" />
      </button>
    </div>
  );
}
