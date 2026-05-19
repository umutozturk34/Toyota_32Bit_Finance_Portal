import { useTranslation } from 'react-i18next';
import { motion } from 'framer-motion';
import { ArrowDownRight, ArrowUpRight, Minus } from 'lucide-react';
import Card from '../../../shared/components/card';
import IndicatorSparkline from './IndicatorSparkline';

const UNIT_FORMATTERS = {
  PERCENT: (v) => `%${Number(v).toFixed(2)}`,
  INDEX: (v) => Number(v).toLocaleString('tr-TR', { maximumFractionDigits: 2 }),
  NUMBER: (v) => Number(v).toLocaleString('tr-TR', { maximumFractionDigits: 2 }),
};

function formatValue(value, unit) {
  if (value == null) return '—';
  const formatter = UNIT_FORMATTERS[unit] || UNIT_FORMATTERS.NUMBER;
  return formatter(value);
}

function formatDate(dateIso) {
  if (!dateIso) return null;
  return new Date(dateIso).toLocaleDateString('tr-TR', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
  });
}

export default function IndicatorCard({ indicator, onOpen }) {
  const { t } = useTranslation();
  const label = t(`marketOverview.macro.${indicator.label}`, { defaultValue: indicator.label });
  const formattedValue = formatValue(indicator.lastValue, indicator.unit);
  const formattedDate = formatDate(indicator.lastDate);

  return (
    <Card
      as={motion.button}
      type="button"
      onClick={() => onOpen?.(indicator)}
      variant="elevated"
      radius="xl"
      padding="md"
      interactive
      backdropBlur
      className="w-full text-left flex flex-col gap-3 cursor-pointer"
      whileHover={{ y: -2 }}
      whileTap={{ scale: 0.98 }}
    >
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <p className="text-[10px] font-mono uppercase tracking-wide text-fg-muted">
            {t(`marketOverview.macro.category${indicator.category.charAt(0)}${indicator.category.slice(1).toLowerCase()}`,
              { defaultValue: indicator.category })}
          </p>
          <h3 className="text-sm font-semibold text-fg truncate">{label}</h3>
        </div>
        <TrendBadge value={indicator.lastValue} />
      </div>

      <p className="text-xl sm:text-2xl font-bold font-mono text-fg leading-none">
        {formattedValue}
      </p>

      <IndicatorSparkline code={indicator.code} />

      {formattedDate && (
        <p className="text-[10px] text-fg-muted font-mono">
          {t('marketOverview.macro.lastUpdated')}: {formattedDate}
        </p>
      )}
    </Card>
  );
}

function TrendBadge({ value }) {
  if (value == null) {
    return <Minus className="h-3 w-3 text-fg-muted" />;
  }
  const positive = Number(value) >= 0;
  const Icon = positive ? ArrowUpRight : ArrowDownRight;
  const tone = positive ? 'text-success' : 'text-danger';
  return <Icon className={`h-4 w-4 ${tone}`} />;
}
