import { useState, useRef, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { Info } from 'lucide-react';
import { isKnownTerm } from '../../features/learn/learnTerms';

// Inline "ⓘ" affordance that explains a financial term in place. Reads the term text from i18n
// (learn.terms.<key>.{title,short}) and deep-links to the full /learn page. Drop it next to any jargon term
// (chart indicators, bond fields, returns/risk) so the explanation lives where the user meets the word.
export default function TermInfo({ term, className = '', size = 13 }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const ref = useRef(null);

  useEffect(() => {
    if (!open) return undefined;
    const onDoc = (e) => { if (ref.current && !ref.current.contains(e.target)) setOpen(false); };
    const onEsc = (e) => { if (e.key === 'Escape') setOpen(false); };
    document.addEventListener('mousedown', onDoc);
    document.addEventListener('keydown', onEsc);
    return () => {
      document.removeEventListener('mousedown', onDoc);
      document.removeEventListener('keydown', onEsc);
    };
  }, [open]);

  if (!isKnownTerm(term)) return null;
  const title = t(`learn.terms.${term}.title`);
  const short = t(`learn.terms.${term}.short`);

  return (
    <span ref={ref} className={`relative inline-flex align-middle ${className}`}>
      <button
        type="button"
        aria-label={t('learn.explainTerm', { term: title })}
        onClick={(e) => { e.stopPropagation(); e.preventDefault(); setOpen((v) => !v); }}
        className="inline-flex items-center justify-center text-fg-subtle hover:text-accent transition-colors bg-transparent border-none cursor-pointer p-0"
      >
        <Info style={{ width: size, height: size }} />
      </button>
      {open && (
        <span className="absolute left-1/2 top-full z-50 mt-1.5 w-64 -translate-x-1/2 rounded-xl border border-border-default bg-bg-elevated p-3 text-left shadow-xl">
          <span className="mb-1 block text-xs font-bold text-fg">{title}</span>
          <span className="block text-[11px] leading-relaxed text-fg-muted">{short}</span>
          <button
            type="button"
            onClick={(e) => { e.stopPropagation(); setOpen(false); navigate(`/learn?term=${term}`); }}
            className="mt-2 inline-flex items-center gap-1 border-none bg-transparent p-0 text-[11px] font-semibold text-accent hover:text-accent-bright cursor-pointer"
          >
            {t('learn.learnMore')} →
          </button>
        </span>
      )}
    </span>
  );
}
