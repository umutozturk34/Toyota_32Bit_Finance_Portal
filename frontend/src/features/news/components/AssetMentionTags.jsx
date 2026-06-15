import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import AssetMentionTag from './AssetMentionTag';

/**
 * The row of asset tags an article mentions, capped at {@code limit} so a market wrap naming a dozen assets doesn't
 * blow the card into many rows. The overflow collapses behind a "+N more" pill; clicking it reveals the rest and
 * the pill becomes a "less" toggle to collapse back. Click is stopped from bubbling so toggling never triggers the
 * parent card's navigation. Renders nothing when there are no mentions.
 */
export default function AssetMentionTags({ assets, date, limit = 6, lite = false, onNavigate, className = '' }) {
  const { t } = useTranslation();
  const [expanded, setExpanded] = useState(false);
  if (!Array.isArray(assets) || assets.length === 0) return null;

  const collapsible = assets.length > limit;
  const shown = expanded ? assets : assets.slice(0, limit);
  const hidden = assets.length - shown.length;

  return (
    <div className={`flex flex-wrap items-center gap-1.5 ${className}`}>
      {shown.map((a) => (
        <AssetMentionTag key={`${a.type}:${a.code}`} code={a.code} type={a.type} date={date} lite={lite} onNavigate={onNavigate} />
      ))}
      {collapsible && (
        <button
          type="button"
          onClick={(e) => { e.stopPropagation(); setExpanded((v) => !v); }}
          className="inline-flex items-center rounded-md border border-border-default bg-bg-elevated/70 px-1.5 py-0.5 text-[10px] font-semibold text-fg-muted hover:border-accent/40 hover:text-accent transition-colors cursor-pointer"
        >
          {expanded ? t('news.tags.less') : t('news.tags.more', { count: hidden })}
        </button>
      )}
    </div>
  );
}
