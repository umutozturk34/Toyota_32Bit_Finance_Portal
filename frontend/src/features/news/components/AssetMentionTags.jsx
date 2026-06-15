import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import AssetMentionTag from './AssetMentionTag';

/**
 * The row of asset tags an article mentions, capped at {@code limit} so a market wrap naming a dozen assets doesn't
 * blow the card into many rows. The overflow collapses behind a "+N more" pill the user can click to reveal the
 * rest (expand-only — once opened it stays open). Click is stopped from bubbling so opening tags never triggers the
 * parent card's navigation. Renders nothing when there are no mentions.
 */
export default function AssetMentionTags({ assets, date, limit = 6, lite = false, onNavigate, className = '' }) {
  const { t } = useTranslation();
  const [expanded, setExpanded] = useState(false);
  if (!Array.isArray(assets) || assets.length === 0) return null;

  const shown = expanded ? assets : assets.slice(0, limit);
  const hidden = assets.length - shown.length;

  return (
    <div className={`flex flex-wrap items-center gap-1.5 ${className}`}>
      {shown.map((a) => (
        <AssetMentionTag key={`${a.type}:${a.code}`} code={a.code} type={a.type} date={date} lite={lite} onNavigate={onNavigate} />
      ))}
      {hidden > 0 && (
        <button
          type="button"
          onClick={(e) => { e.stopPropagation(); setExpanded(true); }}
          className="inline-flex items-center rounded-md border border-border-default bg-bg-elevated/70 px-1.5 py-0.5 text-[10px] font-semibold text-fg-muted hover:border-accent/40 hover:text-accent transition-colors cursor-pointer"
        >
          {t('news.tags.more', { count: hidden })}
        </button>
      )}
    </div>
  );
}
