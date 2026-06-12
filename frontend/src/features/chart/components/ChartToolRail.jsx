import { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { useTranslation } from 'react-i18next';
import { MousePointer2, Undo2, Trash2 } from 'lucide-react';
import { DRAWING_TOOLS, FIB_TOOLS, ICON_OPTIONS, DRAWING_COLORS } from '../lib/drawingTools';

const ACCENT_HEX = '#5E6AD2';
const DANGER_HEX = '#ef4444';

// Single tinted icon button. `color` is always a hex so the active state can derive a translucent fill/border.
function RailButton({ active, color = ACCENT_HEX, title, onClick, children }) {
  return (
    <button
      type="button"
      onClick={onClick}
      title={title}
      aria-label={title}
      aria-pressed={active}
      className="shrink-0 inline-flex h-9 w-9 items-center justify-center rounded-md border cursor-pointer transition-all duration-150 hover:bg-surface"
      style={{
        background: active ? color : 'transparent',
        borderColor: active ? color : 'transparent',
        color: active ? '#fff' : 'var(--color-fg-muted)',
        boxShadow: active ? `0 2px 10px ${color}55` : 'none',
      }}
    >
      {children}
    </button>
  );
}

function Divider() {
  return <div className="shrink-0 bg-border-default h-5 w-px lg:h-px lg:w-5 lg:my-0.5" />;
}

/**
 * Always-on (non-collapsible) drawing tool rail — pick-and-use. Vertical on desktop, a horizontal scrolling strip
 * on mobile. Tool selection reuses the existing handlers; the colour swatch sets the active `drawingColor` that
 * new drawings inherit. Popovers are portaled to <body>: the rail itself is an overflow:auto scroll container on
 * mobile (which would clip an in-flow popover), so the colour/emoji panels must escape it.
 */
export default function ChartToolRail({
  activeTool,
  activeFibTool,
  isAnyToolActive,
  onSelectTool,
  onSelectFibTool,
  onCancelAll,
  showFib = true,
  drawingColor = ACCENT_HEX,
  setDrawingColor,
  selectedIcon,
  setSelectedIcon,
  iconSize,
  setIconSize,
  drawingsCount = 0,
  onUndo,
  onClear,
}) {
  const { t } = useTranslation();
  const [popover, setPopover] = useState(null); // { kind: 'color' | 'emoji', rect }
  const rootRef = useRef(null);
  const popRef = useRef(null);

  useEffect(() => {
    if (!popover) return undefined;
    const close = (e) => {
      if (rootRef.current?.contains(e.target) || popRef.current?.contains(e.target)) return;
      setPopover(null);
    };
    document.addEventListener('pointerdown', close);
    return () => document.removeEventListener('pointerdown', close);
  }, [popover]);

  const openPopover = (kind, e) => {
    const rect = e.currentTarget.getBoundingClientRect();
    setPopover((p) => (p?.kind === kind ? null : { kind, rect }));
  };

  const renderPopover = () => {
    if (!popover || typeof document === 'undefined') return null;
    const isEmoji = popover.kind === 'emoji';
    const width = isEmoji ? 200 : 150;
    const height = isEmoji ? 140 : 118;
    let left = Math.min(popover.rect.left, window.innerWidth - width - 8);
    left = Math.max(8, left);
    let top = popover.rect.bottom + 6;
    if (top + height > window.innerHeight - 8) top = Math.max(8, popover.rect.top - height - 6);
    return createPortal(
      <div
        ref={popRef}
        style={{ position: 'fixed', top, left, width }}
        className="z-[60] rounded-lg border border-border-strong bg-surface p-2 shadow-2xl"
      >
        {popover.kind === 'color' ? (
          <>
            <div className="grid grid-cols-4 gap-1.5">
              {DRAWING_COLORS.map((c) => (
                <button
                  key={c}
                  type="button"
                  onClick={() => { setDrawingColor?.(c); setPopover(null); }}
                  aria-label={c}
                  className="h-6 w-6 cursor-pointer rounded-full border-2"
                  style={{ background: c, borderColor: drawingColor === c ? 'var(--color-fg)' : 'transparent' }}
                />
              ))}
            </div>
            <label className="mt-2 flex cursor-pointer items-center gap-1.5 text-[10px] text-fg-muted">
              <input
                type="color"
                value={drawingColor}
                onChange={(e) => { setDrawingColor?.(e.target.value); setPopover(null); }}
                className="h-6 w-6 cursor-pointer rounded border-none bg-transparent p-0"
              />
              {t('chart.toolRail.custom')}
            </label>
          </>
        ) : (
          <>
            <div className="grid grid-cols-5 gap-1">
              {ICON_OPTIONS.map(({ id, emoji, labelKey }) => (
                <button
                  key={id}
                  type="button"
                  onClick={() => { setSelectedIcon?.(id); setPopover(null); }}
                  title={t(labelKey)}
                  aria-label={t(labelKey)}
                  className="flex h-8 w-8 items-center justify-center rounded-md text-base cursor-pointer hover:bg-surface"
                  style={{ background: selectedIcon === id ? 'rgba(94,106,210,0.18)' : 'transparent' }}
                >
                  {emoji}
                </button>
              ))}
            </div>
            {setIconSize && iconSize != null && (
              <div className="mt-2 flex items-center gap-2">
                <span className="w-9 shrink-0 font-mono text-[10px] tabular-nums text-fg-muted">{iconSize}px</span>
                <input
                  type="range"
                  min="12"
                  max="64"
                  step="2"
                  value={iconSize}
                  onChange={(e) => setIconSize(Number(e.target.value))}
                  className="h-1.5 flex-1 cursor-pointer"
                  style={{ accentColor: ACCENT_HEX }}
                />
              </div>
            )}
          </>
        )}
      </div>,
      document.body,
    );
  };

  return (
    <div
      ref={rootRef}
      data-tour="chart-drawing-rail"
      className="relative flex shrink-0 flex-row items-center gap-1 overflow-x-auto overscroll-x-contain border-b border-border-default bg-surface/40 p-1.5 scrollbar-hide lg:flex-col lg:overflow-x-visible lg:overflow-y-auto lg:border-b-0 lg:border-r"
    >
      <RailButton active={!isAnyToolActive} color={ACCENT_HEX} title={t('chart.toolRail.select')} onClick={onCancelAll}>
        <MousePointer2 className="h-4 w-4" />
      </RailButton>
      <Divider />

      {DRAWING_TOOLS.map(({ id, labelKey, Icon, color }) => (
        <RailButton key={id} active={activeTool === id} color={color} title={t(labelKey)} onClick={() => onSelectTool(id)}>
          <Icon className="h-4 w-4" />
        </RailButton>
      ))}

      {showFib && (
        <>
          <Divider />
          {FIB_TOOLS.map(({ id, labelKey, Icon, color }) => (
            <RailButton key={id} active={activeFibTool === id} color={color} title={t(labelKey)} onClick={() => onSelectFibTool(id)}>
              <Icon className="h-4 w-4" />
            </RailButton>
          ))}
        </>
      )}

      <Divider />

      <button
        type="button"
        onClick={(e) => openPopover('color', e)}
        title={t('chart.toolRail.color')}
        aria-label={t('chart.toolRail.color')}
        className="shrink-0 inline-flex h-9 w-9 items-center justify-center rounded-md border border-transparent cursor-pointer hover:bg-surface transition-colors"
      >
        <span className="h-4 w-4 rounded-full border border-border-strong" style={{ background: drawingColor }} />
      </button>

      {activeTool === 'ICON' && (
        <button
          type="button"
          onClick={(e) => openPopover('emoji', e)}
          title={t('chart.toolRail.emoji')}
          aria-label={t('chart.toolRail.emoji')}
          className="shrink-0 inline-flex h-9 w-9 items-center justify-center rounded-md border border-transparent cursor-pointer text-lg leading-none hover:bg-surface transition-colors"
        >
          {selectedIcon}
        </button>
      )}

      {drawingsCount > 0 && (
        <>
          <div className="hidden flex-1 lg:block" />
          <Divider />
          <RailButton color={ACCENT_HEX} title={t('chart.toolRail.undo')} onClick={onUndo}>
            <Undo2 className="h-4 w-4" />
          </RailButton>
          <RailButton color={DANGER_HEX} title={t('chart.toolRail.clear')} onClick={onClear}>
            <Trash2 className="h-4 w-4" />
          </RailButton>
        </>
      )}

      {renderPopover()}
    </div>
  );
}
