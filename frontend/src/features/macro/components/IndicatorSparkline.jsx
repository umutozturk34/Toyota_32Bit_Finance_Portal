import { useMemo } from 'react';
import { useMacroIndicatorHistory } from '../hooks/useMacroIndicators';

const SPARK_WIDTH = 100;
const SPARK_HEIGHT = 32;
const SPARK_DAYS = 90;

function toIsoDate(d) {
  return d.toISOString().slice(0, 10);
}

function buildPath(points, width, height) {
  if (points.length < 2) return '';
  const values = points.map((p) => Number(p.value));
  const min = Math.min(...values);
  const max = Math.max(...values);
  const span = max - min || 1;
  const stepX = width / (points.length - 1);
  return points
    .map((p, i) => {
      const x = i * stepX;
      const y = height - ((Number(p.value) - min) / span) * height;
      return `${i === 0 ? 'M' : 'L'}${x.toFixed(2)},${y.toFixed(2)}`;
    })
    .join(' ');
}

export default function IndicatorSparkline({ code, color = '#5E6AD2' }) {
  const today = useMemo(() => new Date(), []);
  const from = useMemo(() => {
    const d = new Date(today);
    d.setDate(d.getDate() - SPARK_DAYS);
    return toIsoDate(d);
  }, [today]);
  const to = useMemo(() => toIsoDate(today), [today]);

  const { data: points = [] } = useMacroIndicatorHistory(code, { from, to });

  const path = useMemo(() => buildPath(points, SPARK_WIDTH, SPARK_HEIGHT), [points]);
  const last = points[points.length - 1];
  const first = points[0];
  const isUp = last && first && Number(last.value) >= Number(first.value);
  const strokeColor = isUp ? color : '#ef4444';

  if (path === '') {
    return <div className="h-8 w-full opacity-40" />;
  }

  return (
    <svg
      viewBox={`0 0 ${SPARK_WIDTH} ${SPARK_HEIGHT}`}
      preserveAspectRatio="none"
      className="h-8 w-full"
      role="img"
      aria-label="trend"
    >
      <path d={path} fill="none" stroke={strokeColor} strokeWidth={1.5} strokeLinejoin="round" strokeLinecap="round" />
    </svg>
  );
}
