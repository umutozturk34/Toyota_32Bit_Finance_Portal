import { useEffect, useMemo, useRef, useState, useTransition } from 'react';
import { useTranslation } from 'react-i18next';
import { motion } from 'framer-motion';
import { AnimatePresence } from 'framer-motion';
import { Calendar, ChevronLeft, ChevronRight } from 'lucide-react';
import { currentLocaleTag } from '../../utils/formatters';

function buildMonthLabels(locale, format) {
  const fmt = new Intl.DateTimeFormat(locale, { month: format });
  return Array.from({ length: 12 }, (_, i) => fmt.format(new Date(2024, i, 15)));
}

function buildWeekdayLabels(locale) {
  const fmt = new Intl.DateTimeFormat(locale, { weekday: 'narrow' });
  return Array.from({ length: 7 }, (_, i) => fmt.format(new Date(2024, 0, i + 1)));
}

const pad = (n) => String(n).padStart(2, '0');
const toIso = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
const fromIso = (s) => {
  if (!s) return null;
  const [y, m, d] = s.split('-').map(Number);
  return new Date(y, m - 1, d);
};
const sameDay = (a, b) => a && b && a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate();

function buildGrid(year, month) {
  const first = new Date(year, month, 1);
  const offset = (first.getDay() + 6) % 7;
  return Array.from({ length: 42 }, (_, i) => new Date(year, month, 1 - offset + i));
}

const NEXT_VIEW = { day: 'month', month: 'year', year: 'day' };

export default function DatePickerPopover({
  value, onChange, onMonthChange, minDate, maxDate, highlightedDates, loading,
}) {
  const { t } = useTranslation();
  const localeTag = currentLocaleTag();
  const WEEKDAYS = useMemo(() => buildWeekdayLabels(localeTag), [localeTag]);
  const MONTHS = useMemo(() => buildMonthLabels(localeTag, 'long'), [localeTag]);
  const MONTHS_SHORT = useMemo(() => buildMonthLabels(localeTag, 'short'), [localeTag]);
  const PREV_LABEL = useMemo(() => ({
    day: t('datePicker.prev.day'),
    month: t('datePicker.prev.month'),
    year: t('datePicker.prev.year'),
  }), [t]);
  const NEXT_LABEL = useMemo(() => ({
    day: t('datePicker.next.day'),
    month: t('datePicker.next.month'),
    year: t('datePicker.next.year'),
  }), [t]);
  const [open, setOpen] = useState(false);
  const [view, setView] = useState('day');
  const ref = useRef(null);

  const selected = useMemo(() => fromIso(value), [value]);
  const today = useMemo(() => new Date(), []);
  const min = useMemo(() => fromIso(minDate), [minDate]);
  const max = useMemo(() => fromIso(maxDate), [maxDate]);
  const initial = selected || today;
  const [cursor, setCursor] = useState({ year: initial.getFullYear(), month: initial.getMonth() });
  const [, startTransition] = useTransition();

  useEffect(() => {
    if (selected) setCursor({ year: selected.getFullYear(), month: selected.getMonth() });
  }, [value]);

  useEffect(() => {
    if (typeof onMonthChange !== 'function') return;
    startTransition(() => onMonthChange(cursor.year, cursor.month));
  }, [cursor.year, cursor.month]);

  useEffect(() => {
    if (!open) return undefined;
    const close = (e) => { if (ref.current && !ref.current.contains(e.target)) setOpen(false); };
    document.addEventListener('mousedown', close);
    return () => document.removeEventListener('mousedown', close);
  }, [open]);

  useEffect(() => {
    if (!open) setView('day');
  }, [open]);

  const grid = useMemo(() => buildGrid(cursor.year, cursor.month), [cursor.year, cursor.month]);
  const outOfRange = (d) => (max && d > max) || (min && d < min);

  const shift = (delta) => {
    if (view === 'day') {
      const next = new Date(cursor.year, cursor.month + delta, 1);
      setCursor({ year: next.getFullYear(), month: next.getMonth() });
    } else if (view === 'month') {
      setCursor((p) => ({ ...p, year: p.year + delta }));
    } else {
      setCursor((p) => ({ ...p, year: p.year + delta * 12 }));
    }
  };

  const pick = (d) => {
    if (outOfRange(d)) return;
    onChange(toIso(d));
    setOpen(false);
  };

  const display = selected
    ? selected.toLocaleDateString(localeTag, { day: '2-digit', month: 'long', year: 'numeric' })
    : t('datePicker.placeholder');

  const highlights = highlightedDates instanceof Set ? highlightedDates : null;
  const dataCount = highlights?.size ?? 0;

  const headerLabel = view === 'year'
    ? `${cursor.year - 11} – ${cursor.year + 12}`
    : view === 'month'
    ? cursor.year
    : `${MONTHS[cursor.month]} ${cursor.year}`;

  return (
    <div ref={ref} className="relative">
      <button
        type="button"
        onClick={() => setOpen((p) => !p)}
        className="w-full flex items-center justify-between rounded-lg border border-border-default bg-bg-base px-3 py-2.5 text-sm text-fg font-mono outline-none focus:ring-1 focus:ring-accent/50 transition-colors hover:border-border-hover cursor-pointer"
      >
        <span className={selected ? 'text-fg' : 'text-fg-subtle'}>{display}</span>
        <Calendar className="h-3.5 w-3.5 text-fg-muted" />
      </button>

      <AnimatePresence>
        {open && (
          <motion.div
            initial={{ opacity: 0, y: -2 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -2 }}
            transition={{ duration: 0.1, ease: 'easeOut' }}
            className="absolute z-50 left-0 right-0 mt-1.5 rounded-xl border border-border-default p-3 space-y-2"
            style={{ backgroundColor: 'var(--color-bg-base, #0f0f17)', boxShadow: '0 12px 40px -8px rgba(0,0,0,0.6)' }}
          >
            <div className="flex items-center justify-between gap-1">
              <NavBtn onClick={() => shift(-1)} title={PREV_LABEL[view]}>
                <ChevronLeft className="h-3.5 w-3.5" />
              </NavBtn>
              <button
                type="button"
                onClick={() => setView((v) => NEXT_VIEW[v])}
                className="flex items-center gap-1.5 px-2.5 py-1 rounded-md text-xs font-medium tracking-tight text-fg hover:text-accent hover:bg-surface transition-colors bg-transparent border-none cursor-pointer flex-1 justify-center"
                title={t('datePicker.toggleView')}
              >
                {headerLabel}
                {loading && <span className="h-1 w-1 rounded-full bg-fg-muted animate-pulse" />}
              </button>
              <NavBtn onClick={() => shift(1)} title={NEXT_LABEL[view]}>
                <ChevronRight className="h-3.5 w-3.5" />
              </NavBtn>
            </div>

            {view === 'day' && (
              <>
                <div className="grid grid-cols-7 gap-0.5">
                  {WEEKDAYS.map((d) => (
                    <span key={d} className="text-center text-[10px] text-fg-subtle font-medium py-1">{d}</span>
                  ))}
                  {grid.map((date, idx) => {
                    const inMonth = date.getMonth() === cursor.month;
                    const disabled = outOfRange(date);
                    const isSelected = sameDay(date, selected);
                    const isToday = sameDay(date, today);
                    const iso = toIso(date);
                    const hasData = highlights ? highlights.has(iso) : false;

                    let cls = 'relative flex items-center justify-center h-8 rounded-md text-xs font-mono transition-colors border-none cursor-pointer ';
                    if (disabled) cls += 'text-fg-subtle/40 cursor-not-allowed bg-transparent';
                    else if (isSelected) cls += 'bg-accent text-white';
                    else if (!inMonth) cls += 'text-fg-subtle bg-transparent hover:bg-surface';
                    else if (isToday) cls += 'text-accent bg-accent/10 hover:bg-accent/20';
                    else cls += 'text-fg bg-transparent hover:bg-surface';

                    return (
                      <button type="button" key={idx} onClick={() => !disabled && pick(date)} disabled={disabled} className={cls}>
                        {date.getDate()}
                        {hasData && (
                          <span className={`absolute bottom-1 left-1/2 -translate-x-1/2 w-1 h-1 rounded-full ${isSelected ? 'bg-white' : 'bg-success'}`} />
                        )}
                      </button>
                    );
                  })}
                </div>
                <div className="flex items-center gap-1.5 text-[10px] text-fg-subtle pt-1 border-t border-border-default">
                  <span className={`w-1 h-1 rounded-full ${dataCount > 0 ? 'bg-success' : 'bg-fg-subtle/40'}`} />
                  <span>{dataCount > 0 ? t('datePicker.dataDays', { count: dataCount }) : t('datePicker.noDataMonth')}</span>
                </div>
              </>
            )}

            {view === 'month' && (
              <div className="grid grid-cols-3 gap-1 py-1">
                {MONTHS_SHORT.map((m, i) => {
                  const start = new Date(cursor.year, i, 1);
                  const end = new Date(cursor.year, i + 1, 0);
                  const disabled = (max && start > max) || (min && end < min);
                  const current = cursor.month === i;
                  let cls = 'text-xs py-2.5 rounded-md transition-colors border-none cursor-pointer font-medium tracking-wide ';
                  if (disabled) cls += 'text-fg-subtle/40 cursor-not-allowed bg-transparent';
                  else if (current) cls += 'bg-accent text-white';
                  else cls += 'text-fg bg-transparent hover:bg-surface';
                  return (
                    <button key={m} type="button" disabled={disabled} onClick={() => { setCursor((p) => ({ ...p, month: i })); setView('day'); }} className={cls}>
                      {m}
                    </button>
                  );
                })}
              </div>
            )}

            {view === 'year' && (
              <div className="grid grid-cols-4 gap-1 py-1 max-h-60 overflow-y-auto pr-1" style={{ scrollbarWidth: 'thin' }}>
                {Array.from({ length: 24 }, (_, i) => cursor.year - 11 + i).map((yr) => {
                  const start = new Date(yr, 0, 1);
                  const end = new Date(yr, 11, 31);
                  const disabled = (max && start > max) || (min && end < min);
                  const current = cursor.year === yr;
                  let cls = 'text-[11px] py-2 rounded-md transition-colors border-none cursor-pointer font-mono font-medium tracking-tight ';
                  if (disabled) cls += 'text-fg-subtle/40 cursor-not-allowed bg-transparent';
                  else if (current) cls += 'bg-accent text-white';
                  else cls += 'text-fg bg-transparent hover:bg-surface';
                  return (
                    <button key={yr} type="button" disabled={disabled} onClick={() => { setCursor((p) => ({ ...p, year: yr })); setView('month'); }} className={cls}>
                      {yr}
                    </button>
                  );
                })}
              </div>
            )}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

function NavBtn({ onClick, title, children }) {
  return (
    <button
      type="button"
      onClick={onClick}
      title={title}
      className="flex items-center justify-center w-7 h-7 rounded-md text-fg-muted hover:text-fg hover:bg-surface transition-colors bg-transparent border-none cursor-pointer"
    >
      {children}
    </button>
  );
}
