import { useTranslation } from 'react-i18next';
import { Bookmark } from 'lucide-react';
import PopoverHeader from './PopoverHeader';
import { useWatchlists } from '../../../../shared/hooks/useWatchlist';

export default function WatchlistConfigSection({ config, onChange }) {
  const { t } = useTranslation();
  const { data: lists } = useWatchlists();
  const items = lists || [];
  return (
    <>
      <PopoverHeader Icon={Bookmark} title={t('widgetSettings.watchlistHeader')} />
      {items.length === 0
        ? <p className="text-[11px] text-fg-subtle leading-relaxed">
            <span dangerouslySetInnerHTML={{ __html: t('widgetSettings.createListHint') }} />
          </p>
        : <div className="space-y-1.5">
            {items.map((l) => {
              const active = config?.watchlistId === l.id || (!config?.watchlistId && l.isDefault);
              return (
                <button
                  key={l.id}
                  type="button"
                  onClick={() => onChange({ ...config, watchlistId: l.id })}
                  className={`w-full flex items-center justify-between gap-2 px-2.5 py-2 rounded-lg font-display text-[12px] font-semibold transition-all cursor-pointer border
                    ${active
                      ? 'border-accent bg-accent/15 text-accent'
                      : 'border-border-default bg-transparent text-fg-muted hover:border-accent/40 hover:text-fg hover:bg-surface/50'}`}
                >
                  <span className="truncate">{l.name}</span>
                  <span className="font-mono text-[10px] tabular-nums text-fg-subtle">{l.itemCount ?? 0}</span>
                </button>
              );
            })}
          </div>}
    </>
  );
}
