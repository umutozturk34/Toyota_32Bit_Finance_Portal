import { Shield, BarChart3, Briefcase, LineChart, TrendingUp, Zap, Activity, Lock, Layers } from 'lucide-react';
import { formatPriceTRY, formatPercent } from '../../../shared/utils/formatters';

export const easeOut = [0.16, 1, 0.3, 1];

export const FEATURE_DEFS = [
  {
    icon: Shield,
    key: 'security',
    glowPos: 'top-left',
    glowColor: { dark: 'rgba(99,102,241,0.18)', light: 'rgba(0,82,255,0.12)' },
  },
  {
    icon: BarChart3,
    key: 'marketData',
    glowPos: 'top-right',
    glowColor: { dark: 'rgba(16,185,129,0.16)', light: 'rgba(16,163,127,0.12)' },
  },
  {
    icon: LineChart,
    key: 'charting',
    glowPos: 'bottom-left',
    glowColor: { dark: 'rgba(245,158,11,0.14)', light: 'rgba(234,138,0,0.10)' },
  },
  {
    icon: Briefcase,
    key: 'portfolio',
    glowPos: 'bottom-right',
    glowColor: { dark: 'rgba(168,85,247,0.16)', light: 'rgba(109,40,217,0.10)' },
  },
];

export const GLOW_POSITIONS = {
  'top-left': { top: '-40%', left: '-40%' },
  'top-right': { top: '-40%', right: '-40%' },
  'bottom-left': { bottom: '-40%', left: '-40%' },
  'bottom-right': { bottom: '-40%', right: '-40%' },
};

export const STAT_DEFS = [
  { value: '4', key: 'assetClasses', icon: Layers },
  { value: '<1s', key: 'latency', icon: Zap },
  { value: '24/7', key: 'uptime', icon: Activity },
  { value: '2FA', key: 'security', icon: Lock },
];

export const CARD_POSITIONS = [
  { position: 'top-[12%] left-[8%]', delay: 0, duration: 5, y: [-8, 8, -8] },
  { position: 'top-[18%] right-[6%]', delay: 1, duration: 4, y: [6, -6, 6] },
  { position: 'bottom-[18%] left-[12%]', delay: 0.5, duration: 4.5, y: [-5, 10, -5] },
];

const STATIC_CARDS = [
  { label: 'BIST 100', price: '9.847,32 ₺', change: '+2.14%', changeColor: 'text-success', iconBg: 'from-emerald-500 to-teal-500' },
  { label: 'BTC', price: '67.241,00 ₺', change: '+1.82%', changeColor: 'text-success', iconBg: 'from-amber-500 to-orange-500' },
  { label: 'USD/TRY', price: '38,42 ₺', change: '-0.31%', changeColor: 'text-danger', iconBg: 'from-blue-500 to-cyan-500' },
];

export function buildFloatingCards(overview) {
  if (!overview) return STATIC_CARDS;
  const picks = [];
  if (overview.indices?.[0]) picks.push(overview.indices[0]);
  const firstMovers = overview.movers?.[0];
  if (firstMovers?.gainers?.[0]) picks.push(firstMovers.gainers[0]);
  if (firstMovers?.losers?.[0]) picks.push(firstMovers.losers[0]);
  if (picks.length === 0) return STATIC_CARDS;

  const gradients = ['from-emerald-500 to-teal-500', 'from-amber-500 to-orange-500', 'from-blue-500 to-cyan-500'];
  return picks.slice(0, 3).map((asset, i) => {
    const pct = asset.changePercent ?? 0;
    return {
      label: asset.name || asset.code,
      price: formatPriceTRY(asset.price),
      change: formatPercent(pct),
      changeColor: pct >= 0 ? 'text-success' : 'text-danger',
      iconBg: gradients[i],
    };
  });
}
