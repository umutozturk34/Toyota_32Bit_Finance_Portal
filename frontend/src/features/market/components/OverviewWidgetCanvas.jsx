import { useDeferredValue, useEffect, useMemo, useState } from 'react';
import GridLayout, { useContainerWidth } from 'react-grid-layout';
import OverviewWidgetCard from './OverviewWidgetCard';
import WidgetSkeleton from './WidgetSkeleton';
import { useWidgetDefinitions } from '../../../shared/hooks/useWidgetDefinitions';
import 'react-grid-layout/css/styles.css';

function buildWidgetDataMap(widgets) {
  const map = new Map();
  for (const w of widgets) map.set(w.sectionId, w.data ?? null);
  return map;
}

const GRID_CONFIG = { cols: 12, rowHeight: 28, margin: [16, 16], containerPadding: [0, 0] };

const FRONTEND_MIN = {
  ASSET_CARDS: { w: 6, h: 3 },
  MOVERS: { w: 3, h: 4 },
  WATCHLIST: { w: 3, h: 4 },
  NEWS: { w: 3, h: 6 },
  BENCHMARK_BEATERS: { w: 3, h: 4 },
};

export default function OverviewWidgetCanvas({
  sections, widgets, editMode,
  deletingIds, activePopoverSectionId, onOpenSettings,
  onChange, onDelete, onConfigChange, onDrop, pendingDropSize,
}) {
  const { containerRef, width } = useContainerWidth();
  const [mountedCount, setMountedCount] = useState(0);
  const { byKind, limits } = useWidgetDefinitions();

  useEffect(() => {
    if (mountedCount >= sections.length) return;
    const id = window.setTimeout(() => setMountedCount((c) => c + 1), mountedCount === 0 ? 0 : 150);
    return () => window.clearTimeout(id);
  }, [mountedCount, sections.length]);

  const widgetDataMap = useMemo(() => buildWidgetDataMap(widgets), [widgets]);

  const mountSlotBySectionId = useMemo(() => {
    const ordered = [...sections].sort((a, b) => {
      const ay = typeof a.y === 'number' ? a.y : 0;
      const by = typeof b.y === 'number' ? b.y : 0;
      if (ay !== by) return ay - by;
      const ax = typeof a.x === 'number' ? a.x : 0;
      const bx = typeof b.x === 'number' ? b.x : 0;
      return ax - bx;
    });
    const news = ordered.filter((s) => s.kind === 'NEWS');
    const others = ordered.filter((s) => s.kind !== 'NEWS');
    const map = new Map();
    [...others, ...news].forEach((s, slot) => map.set(s.sectionId, slot));
    return map;
  }, [sections]);

  const layout = useMemo(() => sections.map((s) => {
    const def = byKind.get(s.kind);
    if (!def) return null;
    const frontMin = FRONTEND_MIN[s.kind];
    const minW = frontMin ? Math.max(def.min.w, frontMin.w) : def.min.w;
    const minH = frontMin ? Math.max(def.min.h, frontMin.h) : def.min.h;
    const desiredW = typeof s.w === 'number' ? s.w : def.defaults.w;
    const desiredH = typeof s.h === 'number' ? s.h : def.defaults.h;
    const base = {
      i: s.sectionId,
      x: typeof s.x === 'number' ? s.x : 0,
      y: typeof s.y === 'number' ? s.y : 0,
      w: Math.max(desiredW, minW),
      h: Math.max(desiredH, minH),
      minW,
      minH,
      maxW: def.max.w,
      maxH: def.max.h,
    };
    return base;
  }).filter(Boolean), [sections, byKind]);

  const deferredEditMode = useDeferredValue(editMode);
  const dragConfig = useMemo(
    () => ({ enabled: deferredEditMode, cancel: '.widget-no-drag', preventCollision: true }),
    [deferredEditMode],
  );
  const resizeConfig = useMemo(
    () => ({ enabled: deferredEditMode, handles: ['se'], preventCollision: true }),
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

  const isMobile = width > 0 && width < 768;

  const mobileOrderedSections = useMemo(() => {
    const sortByPosition = (a, b) => {
      const ay = typeof a.y === 'number' ? a.y : 0;
      const by = typeof b.y === 'number' ? b.y : 0;
      if (ay !== by) return ay - by;
      const ax = typeof a.x === 'number' ? a.x : 0;
      const bx = typeof b.x === 'number' ? b.x : 0;
      return ax - bx;
    };
    const others = sections.filter((s) => s.kind !== 'NEWS').sort(sortByPosition);
    const news = sections.filter((s) => s.kind === 'NEWS').sort(sortByPosition);
    return [...others, ...news];
  }, [sections]);

  return (
    <div
      ref={containerRef}
      className={`overview-canvas${pendingDropSize ? ' is-dropping' : ''}`}
      onDragOver={handleDragOver}
      style={{ minHeight: width > 0 ? undefined : 600 }}
    >
      {isMobile && (
        <div className="flex flex-col gap-4 pb-4">
          {mobileOrderedSections.map((section, i) => {
            const slot = mountSlotBySectionId.get(section.sectionId) ?? i;
            return (
              <div key={section.sectionId} className="h-full">
                {slot < mountedCount
                  ? (
                    <div className="h-full widget-fade-in">
                      <OverviewWidgetCard
                        section={section}
                        widgetData={widgetDataMap.get(section.sectionId) ?? null}
                        editMode={editMode}
                        draggable={false}
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
            );
          })}
        </div>
      )}
      {!isMobile && width > 0 && (
        <GridLayout
          layout={layout}
          width={width}
          gridConfig={GRID_CONFIG}
          maxRows={limits?.maxLayoutRows || undefined}
          dragConfig={dragConfig}
          resizeConfig={resizeConfig}
          dropConfig={dropConfig}
          droppingItem={droppingItem}
          compactor={null}
          onDragStop={persistChange}
          onResizeStop={persistChange}
          onDrop={handleDrop}
        >
          {sections.map((section, i) => {
            const slot = mountSlotBySectionId.get(section.sectionId) ?? i;
            return (
              <div key={section.sectionId} className="h-full">
                {slot < mountedCount
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
            );
          })}
        </GridLayout>
      )}
    </div>
  );
}
