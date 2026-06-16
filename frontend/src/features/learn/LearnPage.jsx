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
const TERM_CATEGORIES = ['technical', 'returnsRisk', 'bonds', 'deposits', 'viop', 'general'];

// A signature hue per section gives the page a sense of place — the rail, the section marker and each card's
// top edge share it, so you always know which topic you're in. Drawn from the app's harmonised palette.
const CATEGORY_ACCENT = {
  macroRates: '#818cf8',
  technical: '#60a5fa',
  returnsRisk: '#fbbf24',
  bonds: '#34d399',
  deposits: '#2dd4bf',
  viop: '#f472b6',
  general: '#38bdf8',
};

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

  const countFor = useCallback(
    (cat) => (cat === 'macroRates' ? macroMatches.length : termMatches.filter((term) => term.category === cat).length),
    [macroMatches, termMatches],
  );

  const categories = useMemo(() => {
    const out = [];
    if (macroMatches.length) out.push('macroRates');
    for (const c of TERM_CATEGORIES) {
      if (termMatches.some((term) => term.category === c)) out.push(c);
    }
    return out;
  }, [macroMatches, termMatches]);

  const totalCount = macroMatches.length + termMatches.length;

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

  const renderCards = (children) => (
    <motion.div variants={containerVariants(0.04)} initial="hidden" animate="show"
      className="grid grid-cols-1 md:grid-cols-2 gap-4">
      {children}
    </motion.div>
  );

  return (
    <div className="py-6 space-y-6">
      {/* Hero — a quiet "academy" masthead with a radial glow behind the mark and a live count of what's inside. */}
      <div className="relative overflow-hidden rounded-3xl border border-border-default bg-bg-elevated/60 px-5 py-5 sm:px-6 sm:py-6 backdrop-blur">
        <div aria-hidden className="pointer-events-none absolute -right-16 -top-20 h-48 w-48 rounded-full bg-accent/20 blur-3xl" />
        <div className="relative flex items-center gap-4">
          <span className="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl bg-accent/10 text-accent ring-1 ring-inset ring-accent/25 shadow-[0_0_24px_-6px_var(--accent-glow,rgba(99,102,241,0.5))]">
            <GraduationCap className="h-6 w-6" />
          </span>
          <div className="min-w-0">
            <h1 className="text-xl sm:text-2xl font-bold tracking-tight text-fg leading-tight">{t('learn.title')}</h1>
            <p className="mt-0.5 text-xs sm:text-sm text-fg-muted">{t('learn.subtitle')}</p>
          </div>
          <span className="ml-auto hidden shrink-0 rounded-full border border-border-default bg-bg-base/60 px-3 py-1 text-[11px] font-semibold text-fg-muted sm:inline-flex">
            <span className="tabular-nums text-fg">{totalCount}</span>
            <span className="ml-1">{t('learn.countLabel', { defaultValue: 'konu' })}</span>
          </span>
        </div>

        <div className="relative mt-4 max-w-md">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-fg-muted" />
          <input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder={t('learn.searchPlaceholder')}
            className="w-full rounded-xl border border-border-default bg-bg-base/80 py-2.5 pl-9 pr-9 text-sm text-fg placeholder:text-fg-subtle outline-none transition-colors focus:border-accent/60 focus:ring-2 focus:ring-accent/15"
          />
          {query && (
            <button onClick={() => setQuery('')} aria-label={t('common.clear', { defaultValue: 'clear' })}
              className="absolute right-2.5 top-1/2 -translate-y-1/2 border-none bg-transparent p-1 text-fg-muted hover:text-fg cursor-pointer">
              <X className="h-3.5 w-3.5" />
            </button>
          )}
        </div>
      </div>

      {/* Sticky scroll-spy nav on the left (desktop) / sticky chips on top (mobile). */}
      <div className="lg:grid lg:grid-cols-[200px_1fr] lg:gap-8">
        <nav aria-label={t('learn.title')} className="hidden lg:block">
          <ul className="sticky top-4 space-y-1">
            {categories.map((cat) => {
              const active = activeCat === cat;
              const color = CATEGORY_ACCENT[cat] || '#818cf8';
              return (
                <li key={cat}>
                  <button
                    onClick={() => jump(cat)}
                    className={`group flex w-full items-center gap-2.5 rounded-lg px-3 py-2 text-left text-xs font-semibold transition-colors border-none cursor-pointer ${
                      active ? 'bg-surface/70 text-fg' : 'bg-transparent text-fg-muted hover:text-fg hover:bg-surface/40'
                    }`}
                  >
                    <span className="h-4 w-1 shrink-0 rounded-full transition-all"
                      style={{ backgroundColor: active ? color : 'transparent', boxShadow: active ? `0 0 8px ${color}` : 'none' }} />
                    <span className="flex-1 truncate">{t(`learn.categories.${cat}`)}</span>
                    <span className="tabular-nums text-[10px] font-bold text-fg-subtle">{countFor(cat)}</span>
                  </button>
                </li>
              );
            })}
          </ul>
        </nav>

        <div className="min-w-0">
          {/* Mobile category nav WRAPS to multiple rows instead of a horizontal-scroll strip (which read poorly). */}
          <div className="mb-4 flex flex-wrap gap-1.5 lg:hidden">
            {categories.map((cat) => {
              const active = activeCat === cat;
              const color = CATEGORY_ACCENT[cat] || '#818cf8';
              return (
                <button key={cat} onClick={() => jump(cat)}
                  className={`inline-flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-[11px] font-semibold border-none cursor-pointer transition-colors ${
                    active ? 'bg-surface/70 text-fg' : 'bg-surface/50 text-fg-muted hover:text-fg'
                  }`}>
                  <span className="h-1.5 w-1.5 rounded-full" style={{ backgroundColor: color }} />
                  {t(`learn.categories.${cat}`)}
                </button>
              );
            })}
          </div>

          <div className="space-y-10">
            {macroMatches.length > 0 && (
              <Section ref={(el) => { sectionRefs.current.macroRates = el; }} cat="macroRates"
                title={t('learn.categories.macroRates')} color={CATEGORY_ACCENT.macroRates} count={macroMatches.length}>
                {renderCards(macroMatches.map((ind) => (
                  <TermCard key={ind.code || ind.label} color={CATEGORY_ACCENT.macroRates}>
                    <h3 className="text-sm font-bold text-fg">{t(`marketOverview.macro.${ind.label}`, { defaultValue: ind.label })}</h3>
                    <p className="mt-2 text-[13px] leading-relaxed text-fg-muted">
                      {t(`marketOverview.macro.descriptions.${ind.label}`, { defaultValue: t('learn.macroFallback') })}
                    </p>
                    {ind.code && <LearnMacroChart code={ind.code} color={MACRO_COLOR[ind.category] || '#6366f1'} />}
                  </TermCard>
                )))}
              </Section>
            )}

            {TERM_CATEGORIES.map((category) => {
              const terms = termMatches.filter((term) => term.category === category);
              if (terms.length === 0) return null;
              const color = CATEGORY_ACCENT[category] || '#818cf8';
              return (
                <Section key={category} ref={(el) => { sectionRefs.current[category] = el; }} cat={category}
                  title={t(`learn.categories.${category}`)} color={color} count={terms.length}>
                  {renderCards(terms.map((term) => (
                    <TermCard key={term.key} color={color} focused={focusTerm === term.key}
                      cardRef={(el) => { termRefs.current[term.key] = el; }}>
                      <h3 className="text-sm font-bold text-fg">{t(`learn.terms.${term.key}.title`)}</h3>
                      <p className="mt-1 text-xs font-semibold" style={{ color }}>{t(`learn.terms.${term.key}.short`)}</p>
                      <p className="mt-2 text-[13px] leading-relaxed text-fg-muted">{t(`learn.terms.${term.key}.long`)}</p>
                      {term.chart && <LearnTermChart chart={term.chart} />}
                    </TermCard>
                  )))}
                </Section>
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

// Section wrapper: a colour-keyed marker dot + glowing rule sets the topic's identity before its cards.
const Section = ({ ref, cat, title, color, count, children }) => (
  <section ref={ref} data-cat={cat} className="scroll-mt-6 space-y-3">
    <div className="flex items-center gap-2.5">
      <span className="h-2 w-2 rounded-full" style={{ backgroundColor: color, boxShadow: `0 0 10px ${color}` }} />
      <h2 className="text-sm font-bold uppercase tracking-wider text-fg">{title}</h2>
      <span className="tabular-nums text-[11px] font-semibold text-fg-subtle">{count}</span>
      <span className="ml-1 h-px flex-1 bg-gradient-to-r from-border-default to-transparent" />
    </div>
    {children}
  </section>
);

// Glass term card: a category-coloured top edge, a soft lift and an accent-tinted glow on hover — the "card
// glow / transparency" the rest of the app uses, applied to the learning surfaces.
function TermCard({ color, focused, cardRef, children }) {
  return (
    <motion.article
      ref={cardRef}
      variants={cardVariants}
      style={{ '--card-accent': color }}
      className={`group relative scroll-mt-6 overflow-hidden rounded-2xl border bg-bg-elevated/70 p-4 backdrop-blur transition-all duration-200 hover:-translate-y-0.5 hover:border-accent/40 hover:shadow-[0_12px_32px_-16px_var(--card-accent)] ${
        focused ? 'border-accent/60 ring-1 ring-accent/30' : 'border-border-default'
      }`}
    >
      <span aria-hidden className="absolute inset-x-0 top-0 h-0.5 opacity-60 transition-opacity group-hover:opacity-100"
        style={{ background: `linear-gradient(90deg, ${color}, transparent 70%)` }} />
      {children}
    </motion.article>
  );
}
