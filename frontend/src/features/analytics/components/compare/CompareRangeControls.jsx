import { motion, AnimatePresence } from 'framer-motion';
import { CalendarRange } from 'lucide-react';
import { RANGES } from '../../../macro/constants';
import { rangeBoundsCalendar } from '../../lib/compareSeriesUtils';
import DatePickerPopover from '../../../../shared/components/form/DatePickerPopover';

export default function CompareRangeControls({
  rangeId,
  setRangeId,
  range,
  useExplicitBounds,
  setUseExplicitBounds,
  customFrom,
  setCustomFrom,
  customTo,
  setCustomTo,
  fxFloorDate,
  homogeneousRates,
  valueMode,
  setValueMode,
  t,
}) {
  const today = new Date().toLocaleDateString('sv-SE');
  return (
    <>
      <div className="flex flex-wrap items-center gap-1 pt-1">
        {RANGES.map((r) => (
          <button
            key={r.id}
            type="button"
            onClick={() => { setRangeId(r.id); setUseExplicitBounds(false); }}
            className={`text-[11px] font-mono font-semibold rounded-md px-2.5 py-1 transition-colors border-none cursor-pointer ${
              !useExplicitBounds && rangeId === r.id
                ? 'bg-accent/15 text-accent shadow-[inset_0_0_0_1px_rgba(99,102,241,0.4)]'
                : 'text-fg-muted hover:text-fg'
            }`}
          >
            {t(`marketOverview.macro.${r.labelKey}`, { defaultValue: r.id })}
          </button>
        ))}
        <button
          type="button"
          onClick={() => {
            const seed = rangeBoundsCalendar(range.id);
            // The ALL preset reaches back ~30y, before the FX floor — clamp the seed so the picker never opens
            // on a date below its own min (otherwise the input shows an out-of-range value).
            setCustomFrom((v) => v || (seed.from < fxFloorDate ? fxFloorDate : seed.from));
            setCustomTo((v) => v || seed.to);
            setUseExplicitBounds(true);
          }}
          className={`inline-flex items-center gap-1 text-[11px] font-mono font-semibold rounded-md px-2.5 py-1 transition-colors border-none cursor-pointer ${
            useExplicitBounds
              ? 'bg-accent/15 text-accent shadow-[inset_0_0_0_1px_rgba(99,102,241,0.4)]'
              : 'text-fg-muted hover:text-fg'
          }`}
        >
          <CalendarRange className="h-3 w-3" />
          {t('analytics.compare.rangeCustom', { defaultValue: 'Özel' })}
        </button>
      </div>

      <AnimatePresence initial={false}>
        {useExplicitBounds && (
          <motion.div
            initial={{ opacity: 0, height: 0 }}
            animate={{ opacity: 1, height: 'auto' }}
            exit={{ opacity: 0, height: 0 }}
            transition={{ duration: 0.18, ease: [0.16, 1, 0.3, 1] }}
            className="flex flex-wrap items-center gap-2 overflow-hidden"
          >
            <label className="flex items-center gap-2 text-[11px] font-mono text-fg-muted">
              {t('analytics.compare.startDate', { defaultValue: 'Başlangıç' })}
              <div className="w-44">
                <DatePickerPopover
                  large
                  value={customFrom}
                  minDate={fxFloorDate}
                  maxDate={customTo || today}
                  onChange={(iso) => { setCustomFrom(iso); setUseExplicitBounds(true); }}
                />
              </div>
            </label>
            <span className="text-fg-subtle text-xs">→</span>
            <label className="flex items-center gap-2 text-[11px] font-mono text-fg-muted">
              {t('analytics.compare.endDate', { defaultValue: 'Bitiş' })}
              <div className="w-44">
                <DatePickerPopover
                  large
                  value={customTo}
                  minDate={customFrom || fxFloorDate}
                  maxDate={today}
                  onChange={(iso) => { setCustomTo(iso); setUseExplicitBounds(true); }}
                />
              </div>
            </label>
          </motion.div>
        )}
      </AnimatePresence>

      {homogeneousRates && (
        <div className="flex items-center gap-1">
          <button
            type="button"
            onClick={() => setValueMode('level')}
            className={`text-[11px] font-mono font-semibold rounded-md px-2.5 py-1 transition-colors border-none cursor-pointer ${
              valueMode === 'level'
                ? 'bg-accent/15 text-accent shadow-[inset_0_0_0_1px_rgba(99,102,241,0.4)]'
                : 'text-fg-muted hover:text-fg'
            }`}
          >
            {t('analytics.compareAnnual', { defaultValue: 'Yıllık' })}
          </button>
          <button
            type="button"
            onClick={() => setValueMode('cumulative')}
            className={`text-[11px] font-mono font-semibold rounded-md px-2.5 py-1 transition-colors border-none cursor-pointer ${
              valueMode === 'cumulative'
                ? 'bg-accent/15 text-accent shadow-[inset_0_0_0_1px_rgba(99,102,241,0.4)]'
                : 'text-fg-muted hover:text-fg'
            }`}
          >
            {t('analytics.compareCumulative', { defaultValue: 'Kümülatif' })}
          </button>
        </div>
      )}
    </>
  );
}
