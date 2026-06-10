import {
  TrendingUp, Minus, ArrowDownUp, Pencil, Type, Star,
  Triangle, GitBranch,
} from 'lucide-react';

// Single source of truth for the chart's drawing/fib tool descriptors (previously duplicated across
// DrawingPanel, FibonacciPanel and ChartToolbar). The `id`s are the persisted drawing/fib `type` strings and
// MUST stay within the backend allow-list (TREND_LINE, HORIZONTAL_LINE, VERTICAL_LINE, FREEHAND, TEXT, ICON for
// drawings; RETRACEMENT, EXTENSION for fib). `color` is the tool's signature UI tint only — the colour actually
// drawn comes from the user-chosen `drawingColor` (DRAWING_COLORS), not from here.
export const DRAWING_TOOLS = [
  { id: 'TREND_LINE', labelKey: 'chart.drawingPanel.tools.trendline', Icon: TrendingUp, color: '#5E6AD2' },
  { id: 'HORIZONTAL_LINE', labelKey: 'chart.drawingPanel.tools.horizontal', Icon: Minus, color: '#f59e0b' },
  { id: 'VERTICAL_LINE', labelKey: 'chart.drawingPanel.tools.vertical', Icon: ArrowDownUp, color: '#06b6d4' },
  { id: 'FREEHAND', labelKey: 'chart.drawingPanel.tools.freehand', Icon: Pencil, color: '#10b981' },
  { id: 'TEXT', labelKey: 'chart.drawingPanel.tools.text', Icon: Type, color: '#8b5cf6' },
  { id: 'ICON', labelKey: 'chart.drawingPanel.tools.emoji', Icon: Star, color: '#f97316' },
];

export const FIB_TOOLS = [
  { id: 'RETRACEMENT', labelKey: 'chart.fibonacci.tools.retracement', Icon: Triangle, color: '#ec4899' },
  { id: 'EXTENSION', labelKey: 'chart.fibonacci.tools.extension', Icon: GitBranch, color: '#f97316' },
];

export const ICON_OPTIONS = [
  { id: '🚀', emoji: '🚀', labelKey: 'chart.drawingPanel.emojis.rocket' },
  { id: '📈', emoji: '📈', labelKey: 'chart.drawingPanel.emojis.trendUp' },
  { id: '📉', emoji: '📉', labelKey: 'chart.drawingPanel.emojis.trendDown' },
  { id: '💰', emoji: '💰', labelKey: 'chart.drawingPanel.emojis.money' },
  { id: '⚠️', emoji: '⚠️', labelKey: 'chart.drawingPanel.emojis.alert' },
  { id: '🔥', emoji: '🔥', labelKey: 'chart.drawingPanel.emojis.fire' },
  { id: '💎', emoji: '💎', labelKey: 'chart.drawingPanel.emojis.diamond' },
  { id: '🎯', emoji: '🎯', labelKey: 'chart.drawingPanel.emojis.target' },
  { id: '⭐', emoji: '⭐', labelKey: 'chart.drawingPanel.emojis.star' },
  { id: '🐂', emoji: '🐂', labelKey: 'chart.drawingPanel.emojis.bull' },
  { id: '🐻', emoji: '🐻', labelKey: 'chart.drawingPanel.emojis.bear' },
  { id: '🟢', emoji: '🟢', labelKey: 'chart.drawingPanel.emojis.green' },
  { id: '🔴', emoji: '🔴', labelKey: 'chart.drawingPanel.emojis.red' },
  { id: '📊', emoji: '📊', labelKey: 'chart.drawingPanel.emojis.chartUp' },
  { id: '🏦', emoji: '🏦', labelKey: 'chart.drawingPanel.emojis.bank' },
];

// Active-colour palette for new drawings; the first entry is the default.
export const DRAWING_COLORS = ['#5E6AD2', '#10b981', '#ef4444', '#f59e0b', '#06b6d4', '#8b5cf6', '#ec4899', '#eab308'];
export const DEFAULT_DRAWING_COLOR = DRAWING_COLORS[0];

// drawing/fib type → i18n label key, so the toolbar's active-tool banner shows a translated name instead of the
// raw enum (e.g. "Trend çizgisi" rather than "TREND_LINE").
export const TOOL_LABEL_KEYS = Object.fromEntries(
  [...DRAWING_TOOLS, ...FIB_TOOLS].map((tool) => [tool.id, tool.labelKey]),
);
