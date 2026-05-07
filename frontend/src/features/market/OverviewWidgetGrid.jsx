import { useMemo, useState } from 'react';
import {
  DndContext, DragOverlay, closestCenter, KeyboardSensor, PointerSensor,
  useSensor, useSensors,
} from '@dnd-kit/core';
import {
  arrayMove, SortableContext, rectSortingStrategy, sortableKeyboardCoordinates,
} from '@dnd-kit/sortable';
import OverviewWidgetCard from './OverviewWidgetCard';
import { definitionFor } from './sections/sectionRegistry';

function findData(widgets, sectionId) {
  return widgets.find((w) => w.sectionId === sectionId)?.data ?? null;
}

/**
 * @typedef {Object} OverviewWidgetGridProps
 * @property {Array<{sectionId: string, kind: string, visible: boolean, order: number, config?: Object}>} sections
 * @property {Array<{sectionId: string, kind: string, data: Object|null}>} widgets
 * @property {boolean} editMode
 * @property {string} slot
 * @property {(next: Array) => void} onChange
 */

/** @param {OverviewWidgetGridProps} props */
export default function OverviewWidgetGrid({ sections, widgets, editMode, slot, onChange }) {
  const [activeId, setActiveId] = useState(null);
  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 5 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  );

  const slotSections = useMemo(
    () => sections
      .filter((s) => s.visible)
      .filter((s) => definitionFor(s.kind)?.slot === slot)
      .sort((a, b) => a.order - b.order),
    [sections, slot],
  );
  const ids = useMemo(() => slotSections.map((s) => s.sectionId), [slotSections]);

  const setVisibility = (id, visible) => {
    onChange(sections.map((s) => (s.sectionId === id ? { ...s, visible } : s)));
  };
  const setConfig = (id, config) => {
    onChange(sections.map((s) => (s.sectionId === id ? { ...s, config } : s)));
  };
  const handleDragEnd = (event) => {
    setActiveId(null);
    const { active, over } = event;
    if (!over || active.id === over.id) return;
    const oldIndex = ids.indexOf(active.id);
    const newIndex = ids.indexOf(over.id);
    const reordered = arrayMove(slotSections, oldIndex, newIndex);
    const idToOrder = new Map();
    reordered.forEach((s, i) => idToOrder.set(s.sectionId, i));
    const baseOrder = Math.min(...reordered.map((s) => s.order));
    const next = sections.map((s) =>
      idToOrder.has(s.sectionId) ? { ...s, order: baseOrder + idToOrder.get(s.sectionId) } : s,
    );
    onChange(next);
  };

  if (slotSections.length === 0) {
    if (editMode) {
      return (
        <div className="rounded-xl border border-dashed border-border-default bg-bg-elevated/30 px-4 py-6 text-center">
          <p className="font-mono text-[10px] tracking-[0.2em] uppercase text-fg-subtle">
            ▸ Bu bölgede widget yok
          </p>
        </div>
      );
    }
    return null;
  }

  const activeSection = activeId ? slotSections.find((s) => s.sectionId === activeId) : null;
  const activeData = activeId ? findData(widgets, activeId) : null;
  const ActiveComponent = activeSection ? definitionFor(activeSection.kind)?.Component : null;

  const gridClassFor = (slotName) => {
    if (slotName === 'strip') return 'block';
    if (slotName === 'news') return 'block';
    return 'grid grid-cols-1 lg:grid-cols-2 gap-3 auto-rows-min';
  };

  return (
    <DndContext
      sensors={sensors}
      collisionDetection={closestCenter}
      onDragStart={(e) => setActiveId(e.active.id)}
      onDragEnd={handleDragEnd}
      onDragCancel={() => setActiveId(null)}
    >
      <SortableContext items={ids} strategy={rectSortingStrategy}>
        <div className={gridClassFor(slot)}>
          {slotSections.map((section) => (
            <OverviewWidgetCard
              key={section.sectionId}
              section={section}
              widgetData={findData(widgets, section.sectionId)}
              editMode={editMode}
              onHide={(id) => setVisibility(id, false)}
              onRemove={(id) => setVisibility(id, false)}
              onConfigChange={setConfig}
            />
          ))}
        </div>
      </SortableContext>
      <DragOverlay dropAnimation={{ duration: 180, easing: 'cubic-bezier(0.16, 1, 0.3, 1)' }}>
        {ActiveComponent && (
          <div
            className="rounded-xl shadow-2xl shadow-accent/20 ring-2 ring-accent border border-accent/40 bg-bg-elevated"
            style={{ transform: 'scale(0.6)', transformOrigin: 'top left', opacity: 0.92, maxWidth: 320 }}
          >
            <ActiveComponent data={activeData} {...(activeSection?.config || {})} />
          </div>
        )}
      </DragOverlay>
    </DndContext>
  );
}
