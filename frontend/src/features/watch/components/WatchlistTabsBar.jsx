import { useTranslation } from 'react-i18next';
import { ListPlus, Star, Trash2 } from 'lucide-react';
import { watchlistName } from '../../../shared/utils/watchlistName';

export default function WatchlistTabsBar({ lists, activeId, onSelect, onCreate, onDelete }) {
  const { t } = useTranslation();
  return (
    <div className="flex flex-wrap items-center gap-2">
      {lists.map((list) => {
        const active = list.id === activeId;
        return (
          <div
            key={list.id}
            className={`relative inline-flex items-stretch rounded-lg border transition-colors shrink-0 overflow-hidden ${
              active
                ? 'border-accent/50 bg-accent/10 shadow-accent/20'
                : 'border-border-default bg-bg-elevated hover:border-accent/40 hover:bg-accent/5'
            }`}
          >
            <button
              type="button"
              onClick={() => onSelect(list.id)}
              className={`inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold bg-transparent border-none cursor-pointer min-w-0 ${
                active ? 'text-accent' : 'text-fg-muted hover:text-fg'
              }`}
              title={watchlistName(t, list)}
            >
              {list.isDefault && <Star className="h-3 w-3 text-warning fill-warning shrink-0" />}
              <span className="truncate max-w-[140px]">{watchlistName(t, list)}</span>
              <span className={`text-[10px] font-mono shrink-0 ${active ? 'text-accent/70' : 'text-fg-subtle'}`}>
                {list.itemCount}
              </span>
            </button>
            {!list.isDefault && (
              <button
                type="button"
                onClick={(e) => {
                  e.stopPropagation();
                  onDelete(list);
                }}
                className={`flex items-center justify-center px-1.5 border-l shrink-0 bg-transparent cursor-pointer ${
                  active ? 'border-accent/30 text-accent/70 hover:text-danger' : 'border-border-default text-fg-muted hover:text-danger hover:bg-danger/5'
                }`}
                title={t('watch.deleteListTitle')}
              >
                <Trash2 className="h-3 w-3" />
              </button>
            )}
          </div>
        );
      })}
      <button
        type="button"
        onClick={onCreate}
        data-tour="watchlist-create"
        className="inline-flex items-center gap-1 px-3 py-1.5 rounded-lg text-xs font-semibold border border-dashed border-border-default text-fg-muted hover:border-accent hover:text-accent hover:bg-accent/5 transition-colors shrink-0 cursor-pointer bg-transparent"
      >
        <ListPlus className="h-3.5 w-3.5" />
        {t('watch.newListCta')}
      </button>
    </div>
  );
}
