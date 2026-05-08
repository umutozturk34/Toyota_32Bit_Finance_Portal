import { useDeferredValue, useEffect, useMemo, useRef, useState } from 'react';
import GridLayout, { useContainerWidth, verticalCompactor } from 'react-grid-layout';
import OverviewWidgetCard from './OverviewWidgetCard';
import { definitionFor } from './sections/sectionRegistry';
import 'react-grid-layout/css/styles.css';

function WidgetSkeleton() {
  return (
    <div className="h-full rounded-xl border border-border-default border-t-2 border-t-border-default/60 bg-bg-elevated/40 overflow-hidden relative">
      <div className="absolute inset-0 bg-gradient-to-r from-transparent via-bg-elevated/40 to-transparent skeleton-shimmer" aria-hidden="true" />
    </div>
  );
}

function buildWidgetDataMap(widgets) {
  const map = new Map();
  for (const w of widgets) map.set(w.sectionId, w.data ?? null);
  return map;
}

const GRID_CONFIG = { cols: 12, rowHeight: 28, margin: [16, 16], containerPadding: [0, 0] };

function newsItemCount(widgets, sectionId) {
  return widgets.find((w) => w.sectionId === sectionId)?.data?.items?.length ?? 0;
}

function adjustNewsHeight(layoutItem, count) {
  if (count <= 0) return layoutItem;
  const itemHeightPx = 68;
  const headerPx = 50;
  const rowUnit = 28 + 16;
  const desired = Math.max(6, Math.min(40, Math.ceil((count * itemHeightPx + headerPx) / rowUnit)));
  return { ...layoutItem, h: desired };
}

/**
 * @typedef {Object} OverviewWidgetCanvasProps
 * @property {Array<Object>} sections
 * @property {Array<Object>} widgets
 * @property {boolean} editMode
 * @property {Set<string>} [deletingIds]
 * @property {string|null} [activePopoverSectionId]
 * @property {(id: string, anchorEl: HTMLElement) => void} [onOpenSettings]
 * @property {(next: Array) => void} onChange
 * @property {(id: string) => void} onDelete
 * @property {(id: string, config: Object) => void} onConfigChange
 * @property {(payload: {kind: string, config: Object, x: number, y: number, w: number, h: number}) => void} onDrop
 * @property {{w: number, h: number} | null} pendingDropSize
 */

/** @param {OverviewWidgetCanvasProps} props */
export default function OverviewWidgetCanvas({
  sections, widgets, editMode,
  deletingIds, activePopoverSectionId, onOpenSettings,
  onChange, onDelete, onConfigChange, onDrop, pendingDropSize,
}) {
  const { containerRef, width } = useContainerWidth();
  const dropDataRef = useRef(null);
  const [mountedCount, setMountedCount] = useState(0);

  useEffect(() => {
    if (mountedCount >= sections.length) return;
    const id = window.setTimeout(() => setMountedCount((c) => c + 1), mountedCount === 0 ? 0 : 150);
    return () => window.clearTimeout(id);
  }, [mountedCount, sections.length]);

  const widgetDataMap = useMemo(() => buildWidgetDataMap(widgets), [widgets]);

  const layout = useMemo(() => sections.map((s) => {
    const def = definitionFor(s.kind)?.defaultSize ?? { w: 4, h: 6, minW: 3, minH: 3 };
    const base = {
      i: s.sectionId,
      x: typeof s.x === 'number' ? s.x : 0,
      y: typeof s.y === 'number' ? s.y : 0,
      w: typeof s.w === 'number' ? s.w : def.w,
      h: typeof s.h === 'number' ? s.h : def.h,
      minW: def.minW ?? 3,
      minH: def.minH ?? 3,
    };
    if (s.kind === 'NEWS') {
      const count = newsItemCount(widgets, s.sectionId);
      return adjustNewsHeight(base, count);
    }
    return base;
  }), [sections, widgets]);

  const deferredEditMode = useDeferredValue(editMode);
  const dragConfig = useMemo(
    () => ({ enabled: deferredEditMode, cancel: '.widget-no-drag', preventCollision: false }),
    [deferredEditMode],
  );
  const resizeConfig = useMemo(
    () => ({ enabled: deferredEditMode, handles: ['se'], preventCollision: false }),
    [deferredEditMode],
  );
  const dropConfig = useMemo(
    () => ({ enabled: deferredEditMode }),
    [deferredEditMode],
  );
  const droppingItem = useMemo(() => {
    const size = pendingDropSize || { w: 4, h: 6 };
    return { i: '__dropping-elem__', w: size.w, h: size.h };
  }, [pendingDropSize]);

  const persistChange = (newLayout) => {
    if (!editMode || !Array.isArray(newLayout) || newLayout.length === 0) return;
    const map = new Map(newLayout.map((l) => [l.i, l]));
    let changed = false;
    const next = sections.map((s) => {
      const item = map.get(s.sectionId);
      if (!item) return s;
      if (item.x === s.x && item.y === s.y && item.w === s.w && item.h === s.h) return s;
      changed = true;
      return { ...s, x: item.x, y: item.y, w: item.w, h: item.h };
    });
    if (changed) onChange(next);
  };

  const handleDrop = (newLayout, item) => {
    if (!editMode || !item) return;
    onDrop?.({ x: item.x, y: item.y, w: item.w, h: item.h });
  };

  const handleDragOver = (e) => {
    if (!editMode) return;
    if (e.dataTransfer?.types?.includes('application/x-widget-kind')) {
      e.preventDefault();
    }
  };

  return (
    <div
      ref={containerRef}
      className="overview-canvas"
      onDragOver={handleDragOver}
      style={{ minHeight: width > 0 ? undefined : 600 }}
    >
      {width > 0 && (
        <GridLayout
          layout={layout}
          width={width}
          gridConfig={GRID_CONFIG}
          dragConfig={dragConfig}
          resizeConfig={resizeConfig}
          dropConfig={dropConfig}
          droppingItem={droppingItem}
          compactor={verticalCompactor}
          onDragStop={persistChange}
          onResizeStop={persistChange}
          onDrop={handleDrop}
        >
          {sections.map((section, i) => (
            <div key={section.sectionId} className="h-full">
              {i < mountedCount
                ? (
                  <div className="h-full widget-fade-in">
                    <OverviewWidgetCard
                      section={section}
                      widgetData={widgetDataMap.get(section.sectionId) ?? null}
                      editMode={editMode}
                      draggable={editMode}
                      deleting={deletingIds?.has(section.sectionId) ?? false}
                      popoverActive={activePopoverSectionId === section.sectionId}
                      onOpenSettings={onOpenSettings}
                      onDelete={onDelete}
                      onConfigChange={onConfigChange}
                    />
                  </div>
                )
                : <WidgetSkeleton />}
            </div>
          ))}
        </GridLayout>
      )}
    </div>
  );
}
