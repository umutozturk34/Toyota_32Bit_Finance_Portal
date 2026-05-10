import { memo } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Newspaper, ChevronRight } from 'lucide-react';
import { getFallbackImage } from '../../news/lib/newsConfig';
import { currentLocaleTag } from '../../../shared/utils/formatters';

function formatDate(iso) {
  if (!iso) return '';
  return new Date(iso).toLocaleDateString(currentLocaleTag(), { day: 'numeric', month: 'short' });
}

function NewsRow({ article, onClick }) {
  const imgSrc = article.imageUrl || getFallbackImage(article.category, article.id);
  return (
    <button
      type="button"
      onClick={onClick}
      className="w-full flex items-start gap-2.5 p-2 rounded-lg hover:bg-surface/60 transition-colors cursor-pointer text-left border-none bg-transparent group"
    >
      {imgSrc
        ? <img src={imgSrc} alt="" loading="lazy" className="w-12 h-12 rounded-md object-cover shrink-0 ring-1 ring-border-default" />
        : <span className="w-12 h-12 rounded-md bg-surface flex items-center justify-center shrink-0">
            <Newspaper className="h-4 w-4 text-fg-subtle" />
          </span>}
      <div className="flex-1 min-w-0">
        <p className="font-display text-[12px] font-semibold text-fg leading-snug group-hover:text-accent transition-colors">
          {article.title}
        </p>
        <div className="flex items-center gap-1.5 mt-1.5 flex-wrap">
          {article.category && (
            <span className="font-mono text-[9px] uppercase tracking-[0.14em] text-accent bg-accent/10 rounded px-1.5 py-0.5">
              {article.category}
            </span>
          )}
          <span className="text-[10px] text-fg-subtle">{formatDate(article.publishedAt)}</span>
        </div>
      </div>
    </button>
  );
}

/**
 * @typedef {Object} NewsSectionProps
 * @property {{categoriesUsed: Array<string>, items: Array<Object>}|null} data
 */

/** @param {NewsSectionProps} props */
function NewsSectionImpl({ data }) {
  const navigate = useNavigate();
  const { t } = useTranslation();
  const items = data?.items ?? [];
  const categoriesUsed = data?.categoriesUsed ?? [];

  return (
    <aside className="group relative rounded-xl border border-border-default border-t-2 border-t-accent-secondary bg-bg-elevated overflow-hidden h-full flex flex-col card-hover transition-all duration-200 hover:border-border-hover">
      <button
        type="button"
        onClick={() => navigate('/news')}
        className="flex items-center gap-2 w-full p-3 cursor-pointer hover:bg-surface/30 transition-colors group/title bg-transparent border-x-0 border-t-0 border-b border-border-default shrink-0"
      >
        <span className="flex items-center justify-center w-7 h-7 rounded-lg bg-accent-secondary/15">
          <Newspaper className="h-3.5 w-3.5 text-accent-secondary" />
        </span>
        <span className="font-display text-[13px] font-bold text-fg">{t('nav.news')}</span>
        {categoriesUsed.length > 0 && (
          <span className="font-mono text-[9px] tracking-[0.14em] uppercase text-fg-subtle">· {categoriesUsed.join(', ')}</span>
        )}
        <ChevronRight className="h-3.5 w-3.5 text-fg-subtle ml-auto opacity-0 group-hover/title:opacity-100 group-hover/title:translate-x-0.5 transition-all" />
      </button>
      <div className="p-2 flex-1 overflow-y-auto" style={{ scrollbarWidth: 'thin' }}>
        {items.length === 0
          ? <p className="text-[11px] text-fg-subtle py-5 text-center">{t('news.empty')}</p>
          : <div className="space-y-0.5">
              {items.map((a) => <NewsRow key={a.id} article={a} onClick={() => navigate(`/news/${a.id}`)} />)}
            </div>
        }
      </div>
    </aside>
  );
}

export default memo(NewsSectionImpl);
