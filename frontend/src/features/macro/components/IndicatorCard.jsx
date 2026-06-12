import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { motion } from 'framer-motion';
import { ArrowDownRight, ArrowUpRight, Minus } from 'lucide-react';
import Card from '../../../shared/components/card';
import IndicatorSparkline from './IndicatorSparkline';
import { useMacroIndicatorHistory } from '../hooks/useMacroIndicators';
import { SPARK_DAYS } from '../constants';
import { changeBadgeText, computeChange, formatDate, formatValue, themeFor } from '../utils';

const SUCCESS = '#10b981';
const DANGER = '#ef4444';

function toIsoDate(d) {
  return d.toISOString().slice(0, 10);
}

export default function IndicatorCard({ indicator, onOpen, dense = false }) {
  const { t } = useTranslation();
  const today = useMemo(() => new Date(), []);
  const from = useMemo(() => {
    const d = new Date(today);
    d.setDate(d.getDate() - SPARK_DAYS);
    return toIsoDate(d);
  }, [today]);
  const to = useMemo(() => toIsoDate(today), [today]);

  const { data: points = [] } = useMacroIndicatorHistory(indicator.code, { from, to });
  const change = useMemo(() => computeChange(points), [points]);
  const theme = themeFor(indicator.category);

  const localeTag = t('common.localeTag');
  const label = t(`marketOverview.macro.${indicator.label}`, { defaultValue: indicator.label });
  const formattedValue = formatValue(indicator.lastValue, indicator.unit, localeTag);
  // Inflation indicators store a cumulative INDEX level (which only ever rises); the figure users expect
  // is the derived year-over-year rate, so lead with that and keep the raw index + monthly rate secondary.
  const showInflationRate = indicator.category === 'INFLATION' && indicator.yoyChangePct != null;
  const primaryValue = showInflationRate
    ? formatValue(indicator.yoyChangePct, 'PERCENT', localeTag)
    : formattedValue;
  const formattedDate = formatDate(indicator.lastDate, localeTag);
  const changeText = changeBadgeText(change, indicator.unit);
  const freqLabel = t(`marketOverview.macro.enum.${indicator.frequency}`,
    { defaultValue: indicator.frequency });
  const isDown = change?.direction === 'down';

  return (
    <Card
      as={motion.button}
      type="button"
      onClick={() => onOpen?.(indicator)}
      variant="elevated"
      radius="xl"
      padding={dense ? 'sm' : 'md'}
      interactive
      backdropBlur
      className="group/card relative w-full text-left flex flex-col gap-3 cursor-pointer overflow-hidden"
      whileHover={{ y: -2 }}
      whileTap={{ scale: 0.98 }}
      style={{ '--accent-soft': theme.soft }}
    >
      <span
        className="pointer-events-none absolute left-0 top-3 bottom-3 w-[2px] rounded-full"
        style={{ background: `linear-gradient(180deg, ${theme.accent}, ${theme.accent}40)` }}
      />
      <div className="flex items-start justify-between gap-2 pl-2">
        <div className="min-w-0">
          <div className="flex items-center gap-1.5">
            <span
              className="h-1.5 w-1.5 rounded-full shrink-0"
              style={{ background: theme.accent, boxShadow: `0 0 8px ${theme.glow}` }}
            />
            <p className="text-[9px] font-mono uppercase tracking-[0.14em] text-fg-muted">
              {t(`marketOverview.macro.enum.${indicator.category}`, { defaultValue: indicator.category })}
            </p>
          </div>
          <h3 className={`mt-1 font-semibold text-fg truncate ${dense ? 'text-xs' : 'text-sm'}`}>{label}</h3>
          {!dense && (() => {
            const desc = t(`marketOverview.macro.descriptions.${indicator.label}`, { defaultValue: '' });
            return desc ? <p className="mt-0.5 text-[10px] text-fg-subtle leading-snug line-clamp-2">{desc}</p> : null;
          })()}
        </div>
        <ChangeChip text={changeText} down={isDown} theme={theme} />
      </div>

      <p className={`pl-2 font-bold font-mono tabular-nums text-fg leading-none ${dense ? 'text-lg' : 'text-2xl'}`}>
        {primaryValue}
        {showInflationRate && (
          <span className="ml-1.5 align-middle text-[10px] font-medium text-fg-subtle">
            {t('marketOverview.macro.yoy')}
          </span>
        )}
      </p>

      {showInflationRate && (
        <p className="pl-2 -mt-1.5 flex flex-wrap items-center gap-x-3 gap-y-0.5 text-[10px] font-mono text-fg-subtle">
          <span>{t('marketOverview.macro.index')}: {formattedValue}</span>
          {indicator.momChangePct != null && (
            <span>{t('marketOverview.macro.mom')}: {formatValue(indicator.momChangePct, 'PERCENT', localeTag)}</span>
          )}
        </p>
      )}

      <div className="pl-2">
        <IndicatorSparkline code={indicator.code} color={theme.accent} points={points} baselineValue={indicator.lastValue} />
      </div>

      <div className="pl-2 flex items-center justify-between text-[10px] font-mono text-fg-subtle">
        <span>{formattedDate}</span>
        <span className="uppercase tracking-[0.12em]">{freqLabel}</span>
      </div>
    </Card>
  );
}

function ChangeChip({ text, down, theme }) {
  if (!text) return <Minus className="h-3 w-3 text-fg-muted" />;
  const tone = down ? DANGER : SUCCESS;
  const Icon = down ? ArrowDownRight : ArrowUpRight;
  return (
    <span
      className="inline-flex items-center gap-1 rounded-md px-1.5 py-0.5 text-[10px] font-mono font-semibold tabular-nums"
      style={{ background: `${tone}1a`, color: tone, boxShadow: `0 0 0 1px ${tone}33 inset` }}
    >
      <Icon className="h-2.5 w-2.5" />
      {text}
      <span className="sr-only">{theme.accent}</span>
    </span>
  );
}
