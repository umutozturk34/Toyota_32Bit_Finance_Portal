import { useId, useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { useMacroIndicatorHistory } from '../hooks/useMacroIndicators';
import { SPARK_DAYS } from '../constants';
import { computeChange } from '../utils';

const SPARK_WIDTH = 100;
const SPARK_HEIGHT = 32;
const SPARK_PAD = 2;
const SUCCESS = '#10b981';
const DANGER = '#ef4444';

function toIsoDate(d) {
  return d.toISOString().slice(0, 10);
}

function buildPath(points, width, height) {
  if (points.length < 2) return { line: '', area: '', lastDot: null };
  const values = points.map((p) => Number(p.value));
  const min = Math.min(...values);
  const max = Math.max(...values);
  const span = max - min || 1;
  const innerW = width - 2 * SPARK_PAD;
  const innerH = height - 2 * SPARK_PAD;
  const stepX = innerW / (points.length - 1);
  const coords = points.map((p, i) => ({
    x: SPARK_PAD + i * stepX,
    y: SPARK_PAD + innerH - ((Number(p.value) - min) / span) * innerH,
  }));
  const line = coords.map(({ x, y }, i) => `${i === 0 ? 'M' : 'L'}${x.toFixed(2)},${y.toFixed(2)}`).join(' ');
  const area = `${line} L${(coords[coords.length - 1].x).toFixed(2)},${height} L${SPARK_PAD.toFixed(2)},${height} Z`;
  return { line, area, lastDot: coords[coords.length - 1] };
}

export default function IndicatorSparkline({ code, color, points: pointsProp }) {
  const { t } = useTranslation();
  const today = useMemo(() => new Date(), []);
  const from = useMemo(() => {
    const d = new Date(today);
    d.setDate(d.getDate() - SPARK_DAYS);
    return toIsoDate(d);
  }, [today]);
  const to = useMemo(() => toIsoDate(today), [today]);
  const enabled = !pointsProp;
  const { data: fetched = [] } = useMacroIndicatorHistory(enabled ? code : null, { from, to });
  const points = pointsProp || fetched;

  const change = useMemo(() => computeChange(points), [points]);
  const baseColor = color || (change?.direction === 'down' ? DANGER : SUCCESS);
  const gradientId = useId();
  const { line, area, lastDot } = useMemo(() => buildPath(points, SPARK_WIDTH, SPARK_HEIGHT), [points]);

  if (!line) return <div className="h-8 w-full opacity-40" aria-hidden />;

  return (
    <svg
      viewBox={`0 0 ${SPARK_WIDTH} ${SPARK_HEIGHT}`}
      preserveAspectRatio="none"
      className="h-8 w-full"
      role="img"
      aria-label={t('marketOverview.macro.sparkline90d')}
    >
      <defs>
        <linearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor={baseColor} stopOpacity="0.32" />
          <stop offset="100%" stopColor={baseColor} stopOpacity="0" />
        </linearGradient>
      </defs>
      <path d={area} fill={`url(#${gradientId})`} stroke="none" />
      <path d={line} fill="none" stroke={baseColor} strokeWidth={1.4} strokeLinejoin="round" strokeLinecap="round" />
      {lastDot && (
        <circle cx={lastDot.x} cy={lastDot.y} r={1.6} fill={baseColor} stroke="var(--color-bg-base)" strokeWidth={0.6} />
      )}
    </svg>
  );
}
