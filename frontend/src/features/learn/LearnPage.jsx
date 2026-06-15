import { useMemo, useState, useEffect, useRef, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { useSearchParams } from 'react-router-dom';
import { motion } from 'framer-motion';
import { GraduationCap, Search, X } from 'lucide-react';
import { containerVariants, cardVariants } from '../../shared/utils/animations';
import { LEARN_TERMS } from './learnTerms';
import LearnTermChart from './LearnTermChart';
import LearnMacroChart from './LearnMacroChart';
import { useMacroIndicators } from '../macro/hooks/useMacroIndicators';

// Accent per macro category so the live charts carry the same colour language as the market overview.
const MACRO_COLOR = { RATES: '#5E6AD2', INFLATION: '#f59e0b', DEPOSIT: '#10b981' };
const TERM_CATEGORIES = ['technical', 'returnsRisk', 'bonds', 'viop', 'general'];

// Financial-literacy hub (/learn): every macro indicator the app tracks (live EVDS data) plus the jargon terms
// used across the app, grouped by topic, with a sticky scroll-spy nav to jump between sections.
export default function LearnPage() {
  const { t } = useTranslation();
  const [params, setParams] = useSearchParams();
  const [query, setQuery] = useState('');
  const [activeCat, setActiveCat] = useState('macroRates');
  const focusTerm = params.get('term');
  const sectionRefs = useRef({});
  const termRefs = useRef({});

  const { data: macroList = [] } = useMacroIndicators({ prominentOnly: true });

  const q = query.trim().toLocaleLowerCase('tr');
  const macroMatches = useMemo(() => (Array.isArray(macroList) ? macroList : []).filter((ind) => {
    if (!q) return true;
    const name = t(`marketOverview.macro.${ind.label}`, { defaultValue: ind.label });
    const desc = t(`marketOverview.macro.descriptions.${ind.label}`, { defaultValue: '' });
    return `${name} ${desc}`.toLocaleLowerCase('tr').includes(q);
  }), [macroList, q, t]);

  const termMatches = useMemo(() => LEARN_TERMS.filter((term) => {
    if (!q) return true;
    const hay = `${t(`learn.terms.${term.key}.title`)} ${t(`learn.terms.${term.key}.short`)} ${t(`learn.terms.${term.key}.long`)}`;
    return hay.toLocaleLowerCase('tr').includes(q);
  }), [q, t]);

  const categories = useMemo(() => {
    const out = [];
    if (macroMatches.length) out.push('macroRates');
    for (const c of TERM_CATEGORIES) {
      if (termMatches.some((term) => term.category === c)) out.push(c);
    }
    return out;
  }, [macroMatches, termMatches]);

  // Scroll-spy: the section nearest the top of the viewport becomes the active nav entry.
  useEffect(() => {
    const obs = new IntersectionObserver(
      (entries) => {
        const top = entries.filter((e) => e.isIntersecting).sort((a, b) => a.boundingClientRect.top - b.boundingClientRect.top)[0];
        if (top?.target?.dataset?.cat) setActiveCat(top.target.dataset.cat);
      },
      { rootMargin: '-12% 0px -75% 0px', threshold: 0 },
    );
    Object.values(sectionRefs.current).forEach((el) => el && obs.observe(el));
    return () => obs.disconnect();
  }, [categories]);

  // Deep-linked term (from an inline TermInfo badge): scroll it into view once, then clear the param.
  useEffect(() => {
    if (!focusTerm) return;
    const el = termRefs.current[focusTerm];
    if (el) {
      el.scrollIntoView({ behavior: 'smooth', block: 'center' });
      const next = new URLSearchParams(params);
      next.delete('term');
      setParams(next, { replace: true });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [focusTerm]);

  const jump = useCallback((cat) => {
    setActiveCat(cat);
    sectionRefs.current[cat]?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }, []);

  const noResults = macroMatches.length === 0 && termMatches.length === 0;

  return (
    <div className="py-6 space-y-6">
      <div className="flex items-center gap-3">
        <span className="flex h-11 w-11 items-center justify-center rounded-2xl bg-accent/10 ring-1 ring-inset ring-accent/20">
          <GraduationCap className="h-5 w-5 text-accent" />
        </span>
        <div className="min-w-0">
          <h1 className="text-lg sm:text-xl font-bold text-fg leading-tight">{t('learn.title')}</h1>
          <p className="text-xs sm:text-sm text-fg-muted">{t('learn.subtitle')}</p>
        </div>
      </div>

      <div className="relative max-w-md">
        <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-fg-muted" />
        <input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder={t('learn.searchPlaceholder')}
          className="w-full rounded-xl border border-border-default bg-bg-base py-2.5 pl-9 pr-9 text-sm text-fg placeholder:text-fg-subtle outline-none focus:border-accent/60"
        />
        {query && (
          <button onClick={() => setQuery('')} aria-label={t('common.clear', { defaultValue: 'clear' })}
            className="absolute right-2.5 top-1/2 -translate-y-1/2 border-none bg-transparent p-1 text-fg-muted hover:text-fg cursor-pointer">
            <X className="h-3.5 w-3.5" />
          </button>
        )}
      </div>

      {/* Sticky scroll-spy nav on the left (desktop) / sticky chips on top (mobile). */}
      <div className="lg:grid lg:grid-cols-[180px_1fr] lg:gap-8">
        <nav aria-label={t('learn.title')} className="hidden lg:block">
          <ul className="sticky top-4 space-y-1">
            {categories.map((cat) => (
              <li key={cat}>
                <button
                  onClick={() => jump(cat)}
                  className={`w-full rounded-lg px-3 py-2 text-left text-xs font-semibold transition-colors border-none cursor-pointer ${
                    activeCat === cat ? 'bg-accent/15 text-accent' : 'bg-transparent text-fg-muted hover:text-fg hover:bg-surface/60'
                  }`}
                >
                  {t(`learn.categories.${cat}`)}
                </button>
              </li>
            ))}
          </ul>
        </nav>

        <div className="min-w-0">
          {/* Mobile category nav WRAPS to multiple rows instead of a horizontal-scroll strip (which read poorly). */}
          <div className="mb-4 flex flex-wrap gap-1.5 lg:hidden">
            {categories.map((cat) => (
              <button key={cat} onClick={() => jump(cat)}
                className={`rounded-lg px-3 py-1.5 text-[11px] font-semibold border-none cursor-pointer transition-colors ${
                  activeCat === cat ? 'bg-accent/15 text-accent' : 'bg-surface/60 text-fg-muted hover:text-fg'
                }`}>
                {t(`learn.categories.${cat}`)}
              </button>
            ))}
          </div>

          <div className="space-y-10">
            {macroMatches.length > 0 && (
              <section ref={(el) => { sectionRefs.current.macroRates = el; }} data-cat="macroRates" className="scroll-mt-6 space-y-3">
                <h2 className="text-xs font-bold uppercase tracking-wider text-accent">{t('learn.categories.macroRates')}</h2>
                <motion.div variants={containerVariants(0.04)} initial="hidden" animate="show" className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  {macroMatches.map((ind) => (
                    <motion.article key={ind.code || ind.label} variants={cardVariants}
                      className="rounded-2xl border border-border-default bg-bg-elevated p-4">
                      <h3 className="text-sm font-bold text-fg">{t(`marketOverview.macro.${ind.label}`, { defaultValue: ind.label })}</h3>
                      <p className="mt-2 text-[13px] leading-relaxed text-fg-muted">
                        {t(`marketOverview.macro.descriptions.${ind.label}`, { defaultValue: t('learn.macroFallback') })}
                      </p>
                      {ind.code && <LearnMacroChart code={ind.code} color={MACRO_COLOR[ind.category] || '#6366f1'} />}
                    </motion.article>
                  ))}
                </motion.div>
              </section>
            )}

            {TERM_CATEGORIES.map((category) => {
              const terms = termMatches.filter((term) => term.category === category);
              if (terms.length === 0) return null;
              return (
                <section key={category} ref={(el) => { sectionRefs.current[category] = el; }} data-cat={category} className="scroll-mt-6 space-y-3">
                  <h2 className="text-xs font-bold uppercase tracking-wider text-accent">{t(`learn.categories.${category}`)}</h2>
                  <motion.div variants={containerVariants(0.04)} initial="hidden" animate="show" className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    {terms.map((term) => (
                      <motion.article
                        key={term.key}
                        ref={(el) => { termRefs.current[term.key] = el; }}
                        variants={cardVariants}
                        className={`scroll-mt-6 rounded-2xl border bg-bg-elevated p-4 transition-colors ${
                          focusTerm === term.key ? 'border-accent/60 ring-1 ring-accent/30' : 'border-border-default'
                        }`}
                      >
                        <h3 className="text-sm font-bold text-fg">{t(`learn.terms.${term.key}.title`)}</h3>
                        <p className="mt-1 text-xs font-medium text-accent/90">{t(`learn.terms.${term.key}.short`)}</p>
                        <p className="mt-2 text-[13px] leading-relaxed text-fg-muted">{t(`learn.terms.${term.key}.long`)}</p>
                        {term.chart && <LearnTermChart chart={term.chart} />}
                      </motion.article>
                    ))}
                  </motion.div>
                </section>
              );
            })}

            {noResults && (
              <div className="flex flex-col items-center justify-center gap-2 py-16 text-center">
                <Search className="h-7 w-7 text-fg-subtle" />
                <p className="text-sm text-fg-muted">{t('learn.noResults')}</p>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
