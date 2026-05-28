import { ChevronLeft, ChevronRight } from 'lucide-react';
import { useTranslation } from 'react-i18next';

export default function Pagination({ page, totalPages, onPageChange }) {
  const { t } = useTranslation();
  if (totalPages <= 1) return null;

  const pages = buildPageNumbers(page, totalPages);

  return (
    <div className="flex flex-wrap items-center justify-center gap-1.5 pt-2">
      <button
        onClick={() => onPageChange(page - 1)}
        disabled={page === 0}
        aria-label={t('common.previousPage')}
        className="flex items-center justify-center w-10 h-10 sm:w-8 sm:h-8 rounded-lg border border-border-default bg-bg-elevated text-fg-muted hover:text-fg hover:bg-surface disabled:opacity-30 disabled:cursor-default transition-colors cursor-pointer"
      >
        <ChevronLeft className="h-3.5 w-3.5" />
      </button>

      {pages.map((p, i) =>
        p === '...' ? (
          <span key={`dot-${i}`} className="w-8 text-center text-xs text-fg-subtle">...</span>
        ) : (
          <button
            key={p}
            onClick={() => onPageChange(p)}
            className={`w-10 h-10 sm:w-8 sm:h-8 rounded-lg text-xs font-semibold transition-colors cursor-pointer border ${
              p === page
                ? 'bg-accent/15 text-accent border-accent/30'
                : 'border-border-default bg-bg-elevated text-fg-muted hover:text-fg hover:bg-surface'
            }`}
          >
            {p + 1}
          </button>
        )
      )}

      <button
        onClick={() => onPageChange(page + 1)}
        disabled={page >= totalPages - 1}
        aria-label={t('common.nextPage')}
        className="flex items-center justify-center w-10 h-10 sm:w-8 sm:h-8 rounded-lg border border-border-default bg-bg-elevated text-fg-muted hover:text-fg hover:bg-surface disabled:opacity-30 disabled:cursor-default transition-colors cursor-pointer"
      >
        <ChevronRight className="h-3.5 w-3.5" />
      </button>
    </div>
  );
}

function buildPageNumbers(current, total) {
  if (total <= 7) return Array.from({ length: total }, (_, i) => i);
  const pages = [];
  pages.push(0);
  if (current > 2) pages.push('...');
  for (let i = Math.max(1, current - 1); i <= Math.min(total - 2, current + 1); i++) {
    pages.push(i);
  }
  if (current < total - 3) pages.push('...');
  pages.push(total - 1);
  return pages;
}
