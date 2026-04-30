import { useEffect, useMemo, useRef, useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Calendar, ChevronLeft, ChevronRight } from 'lucide-react';

const WEEKDAYS = ['Pt', 'Sa', 'Ça', 'Pe', 'Cu', 'Ct', 'Pz'];
const MONTHS = ['Ocak', 'Şubat', 'Mart', 'Nisan', 'Mayıs', 'Haziran', 'Temmuz', 'Ağustos', 'Eylül', 'Ekim', 'Kasım', 'Aralık'];

function toIsoDate(date) {
  const offset = date.getTimezoneOffset();
  return new Date(date.getTime() - offset * 60_000).toISOString().slice(0, 10);
}

function fromIsoDate(iso) {
  if (!iso) return null;
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(y, m - 1, d);
}

function isSameDay(a, b) {
  if (!a || !b) return false;
  return a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate();
}

function buildMonthGrid(year, month) {
  const first = new Date(year, month, 1);
  const offset = (first.getDay() + 6) % 7;
  const start = new Date(year, month, 1 - offset);
  const days = [];
  for (let i = 0; i < 42; i += 1) {
    const d = new Date(start.getFullYear(), start.getMonth(), start.getDate() + i);
    days.push(d);
  }
  return days;
}

export default function DatePickerPopover({ value, onChange, maxDate, highlightedDates }) {
  const [open, setOpen] = useState(false);
  const containerRef = useRef(null);
  const selected = useMemo(() => fromIsoDate(value), [value]);
  const today = new Date();
  const max = maxDate ? fromIsoDate(maxDate) : null;
  const initialMonth = selected || today;
  const [cursor, setCursor] = useState({ year: initialMonth.getFullYear(), month: initialMonth.getMonth() });

  useEffect(() => {
    if (selected) setCursor({ year: selected.getFullYear(), month: selected.getMonth() });
  }, [value]);

  useEffect(() => {
    if (!open) return undefined;
    const handler = (e) => {
      if (containerRef.current && !containerRef.current.contains(e.target)) setOpen(false);
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, [open]);

  const grid = useMemo(() => buildMonthGrid(cursor.year, cursor.month), [cursor]);

  const shiftMonth = (delta) => {
    setCursor((prev) => {
      const next = new Date(prev.year, prev.month + delta, 1);
      return { year: next.getFullYear(), month: next.getMonth() };
    });
  };

  const handleSelect = (date) => {
    if (max && date > max) return;
    onChange(toIsoDate(date));
    setOpen(false);
  };

  const displayLabel = selected
    ? selected.toLocaleDateString('tr-TR', { day: '2-digit', month: 'long', year: 'numeric' })
    : 'Tarih seçin';

  const highlights = highlightedDates instanceof Set ? highlightedDates : null;

  return (
    <div ref={containerRef} className="relative">
      <button
        type="button"
        onClick={() => setOpen((prev) => !prev)}
        className="w-full flex items-center justify-between rounded-lg border border-border-default bg-bg-base px-3 py-2.5 text-sm text-fg font-mono outline-none focus:ring-1 focus:ring-accent/50 transition-all cursor-pointer"
      >
        <span className={selected ? 'text-fg' : 'text-fg-subtle'}>{displayLabel}</span>
        <Calendar className="h-3.5 w-3.5 text-fg-muted" />
      </button>

      <AnimatePresence>
        {open && (
          <motion.div
            initial={{ opacity: 0, y: -4, scale: 0.98 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: -4, scale: 0.98 }}
            transition={{ duration: 0.12 }}
            className="absolute z-50 left-0 right-0 mt-1.5 rounded-xl border border-border-default bg-bg-elevated shadow-2xl backdrop-blur-md p-3 space-y-2"
          >
            <div className="flex items-center justify-between px-1">
              <button
                type="button"
                onClick={() => shiftMonth(-1)}
                className="flex items-center justify-center w-7 h-7 rounded-md text-fg-muted hover:text-fg hover:bg-surface transition-colors bg-transparent border-none cursor-pointer"
              >
                <ChevronLeft className="h-3.5 w-3.5" />
              </button>
              <span className="text-xs font-semibold text-fg">
                {MONTHS[cursor.month]} {cursor.year}
              </span>
              <button
                type="button"
                onClick={() => shiftMonth(1)}
                className="flex items-center justify-center w-7 h-7 rounded-md text-fg-muted hover:text-fg hover:bg-surface transition-colors bg-transparent border-none cursor-pointer"
              >
                <ChevronRight className="h-3.5 w-3.5" />
              </button>
            </div>

            <div className="grid grid-cols-7 gap-0.5">
              {WEEKDAYS.map((d) => (
                <span key={d} className="text-center text-[10px] text-fg-subtle font-medium py-1">{d}</span>
              ))}
              {grid.map((date) => {
                const inMonth = date.getMonth() === cursor.month;
                const disabled = max && date > max;
                const isSelected = isSameDay(date, selected);
                const isToday = isSameDay(date, today);
                const iso = toIsoDate(date);
                const hasData = highlights ? highlights.has(iso) : false;

                let cls = 'relative flex items-center justify-center h-8 rounded-md text-xs font-mono transition-all border-none cursor-pointer ';
                if (disabled) cls += 'text-fg-subtle/40 cursor-not-allowed bg-transparent';
                else if (isSelected) cls += 'bg-accent text-white';
                else if (!inMonth) cls += 'text-fg-subtle bg-transparent hover:bg-surface';
                else if (isToday) cls += 'text-accent bg-accent/10 hover:bg-accent/20';
                else cls += 'text-fg bg-transparent hover:bg-surface';

                return (
                  <button
                    type="button"
                    key={iso}
                    onClick={() => !disabled && handleSelect(date)}
                    disabled={disabled}
                    className={cls}
                  >
                    {date.getDate()}
                    {hasData && !isSelected && (
                      <span className="absolute bottom-1 w-1 h-1 rounded-full bg-success" />
                    )}
                    {hasData && isSelected && (
                      <span className="absolute bottom-1 w-1 h-1 rounded-full bg-white" />
                    )}
                  </button>
                );
              })}
            </div>

            {highlights && highlights.size > 0 && (
              <div className="flex items-center gap-1.5 text-[10px] text-fg-subtle pt-1 border-t border-border-default">
                <span className="w-1 h-1 rounded-full bg-success" />
                <span>geçmiş fiyat verisi mevcut</span>
              </div>
            )}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
