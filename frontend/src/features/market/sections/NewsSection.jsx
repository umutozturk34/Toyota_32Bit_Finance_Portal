import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { Newspaper, ChevronRight } from 'lucide-react';

function formatDate(iso) {
  if (!iso) return '';
  return new Date(iso).toLocaleDateString('tr-TR', { day: 'numeric', month: 'short' });
}

function NewsRow({ article, onClick }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="w-full flex items-start gap-2 px-2 py-2 rounded-md hover:bg-surface/60 transition-colors cursor-pointer text-left border-none bg-transparent group"
    >
      {article.imageUrl
        ? <img src={article.imageUrl} alt="" loading="lazy" className="w-10 h-10 rounded-md object-cover shrink-0 ring-1 ring-border-default" />
        : <span className="w-10 h-10 rounded-md bg-bg-elevated flex items-center justify-center shrink-0">
            <Newspaper className="h-3.5 w-3.5 text-fg-subtle" />
          </span>}
      <div className="flex-1 min-w-0">
        <p className="text-[11px] font-semibold text-fg leading-tight line-clamp-2 group-hover:text-accent transition-colors">
          {article.title}
        </p>
        <div className="flex items-center gap-1.5 mt-1">
          {article.category && (
            <span className="text-[9px] font-mono uppercase tracking-wider text-accent bg-accent/10 rounded px-1.5 py-0.5">
              {article.category}
            </span>
          )}
          <span className="text-[9px] text-fg-subtle">{formatDate(article.publishedAt)}</span>
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
export default function NewsSection({ data }) {
  const navigate = useNavigate();
  const items = data?.items ?? [];
  const categoriesUsed = data?.categoriesUsed ?? [];

  return (
    <motion.aside className="relative rounded-xl border border-border-default bg-bg-elevated overflow-hidden hover:border-border-hover transition-colors h-full flex flex-col">
      <div className="absolute top-0 left-0 right-0 h-[1px] bg-gradient-to-r from-accent-secondary via-accent-secondary/30 to-transparent" />
      <button
        type="button"
        onClick={() => navigate('/news')}
        className="flex items-center gap-2 w-full px-3 py-2 border-b border-border-default bg-transparent border-x-0 cursor-pointer hover:bg-surface/40 transition-colors group shrink-0"
      >
        <span className="flex items-center justify-center w-6 h-6 rounded-md bg-accent-secondary/15">
          <Newspaper className="h-3 w-3 text-accent-secondary" />
        </span>
        <span className="text-[11px] font-bold text-fg uppercase tracking-wider">Haberler</span>
        {categoriesUsed.length > 0 && (
          <span className="font-mono text-[9px] tracking-wider text-fg-subtle uppercase">· {categoriesUsed.length} kategori</span>
        )}
        <ChevronRight className="h-3 w-3 text-fg-subtle ml-auto opacity-0 group-hover:opacity-100 transition-opacity" />
      </button>
      <div className="p-1.5 flex-1 overflow-y-auto" style={{ scrollbarWidth: 'thin' }}>
        {items.length === 0
          ? <p className="text-[11px] text-fg-subtle py-4 text-center">Haber yok</p>
          : <div className="space-y-0.5">
              {items.map((a) => <NewsRow key={a.id} article={a} onClick={() => navigate(`/news/${a.id}`)} />)}
            </div>
        }
      </div>
    </motion.aside>
  );
}
