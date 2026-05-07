import { useState } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { useSortable } from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import { GripVertical, EyeOff, X, Sliders } from 'lucide-react';
import WidgetSettingsPopover from './WidgetSettingsPopover';
import { definitionFor } from './sections/sectionRegistry';

function ActionDot({ Icon, label, onClick, tone = 'default' }) {
  const tones = {
    default: 'border-border-default text-fg-muted hover:text-fg hover:border-fg-subtle',
    danger: 'border-danger/40 text-danger/80 hover:text-danger hover:border-danger',
    accent: 'border-accent/40 text-accent/80 hover:text-accent hover:border-accent',
  };
  return (
    <button
      type="button"
      onClick={onClick}
      title={label}
      aria-label={label}
      className={`flex items-center justify-center w-6 h-6 rounded-md border bg-bg-elevated/95 backdrop-blur-sm transition-colors cursor-pointer ${tones[tone]}`}
    >
      <Icon className="h-3 w-3" />
    </button>
  );
}

/**
 * @typedef {Object} OverviewWidgetCardProps
 * @property {{sectionId: string, kind: string, visible: boolean, order: number, config?: Object}} section
 * @property {Object|null} widgetData
 * @property {boolean} editMode
 * @property {(id: string) => void} onHide
 * @property {(id: string) => void} onRemove
 * @property {(id: string, config: Object) => void} onConfigChange
 */

/** @param {OverviewWidgetCardProps} props */
export default function OverviewWidgetCard({ section, widgetData, editMode, onHide, onRemove, onConfigChange }) {
  const [settingsOpen, setSettingsOpen] = useState(false);
  const def = definitionFor(section.kind);
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id: section.sectionId,
    disabled: !editMode,
  });
  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
    opacity: isDragging ? 0 : 1,
  };
  if (!def) return null;
  const Component = def.Component;

  return (
    <motion.div
      ref={setNodeRef}
      style={style}
      layout
      className="relative"
    >
      <div
        className={`relative rounded-xl transition-colors duration-150 ${
          editMode
            ? 'ring-1 ring-dashed ring-accent/50 bg-accent/3 shadow-[inset_0_0_24px_-12px_var(--color-accent)]/40'
            : ''
        }`}
      >
        {editMode && (
          <>
            <div className="absolute top-1.5 left-1.5 z-10 flex items-center gap-1 pointer-events-auto">
              <button
                type="button"
                {...attributes}
                {...listeners}
                aria-label={`${def.label} sürükle`}
                className="flex items-center justify-center w-6 h-6 rounded-md border border-accent/40 bg-bg-elevated/95 backdrop-blur-sm text-accent cursor-grab active:cursor-grabbing hover:border-accent transition-colors"
              >
                <GripVertical className="h-3 w-3" />
              </button>
              <span className="font-mono text-[9px] tracking-[0.2em] uppercase text-accent/90 px-1.5 py-0.5 rounded bg-bg-elevated/95 backdrop-blur-sm border border-accent/30">
                {def.label}
              </span>
            </div>
            <div className="absolute top-1.5 right-1.5 z-10 flex items-center gap-1 pointer-events-auto">
              {def.configurable && (
                <div className="relative">
                  <ActionDot Icon={Sliders} label="Ayarlar" tone="accent" onClick={() => setSettingsOpen((o) => !o)} />
                  <AnimatePresence>
                    {settingsOpen && (
                      <WidgetSettingsPopover
                        sectionId={section.sectionId}
                        kind={section.kind}
                        config={section.config}
                        onChange={(next) => onConfigChange(section.sectionId, next)}
                        onClose={() => setSettingsOpen(false)}
                      />
                    )}
                  </AnimatePresence>
                </div>
              )}
              <ActionDot Icon={EyeOff} label="Gizle" onClick={() => onHide(section.sectionId)} />
              <ActionDot Icon={X} label="Kaldır" tone="danger" onClick={() => onRemove(section.sectionId)} />
            </div>
          </>
        )}
        <div className={editMode ? 'pointer-events-none select-none [&_*]:pointer-events-none' : ''}>
          <Component data={widgetData} {...(section.config || {})} />
        </div>
      </div>
    </motion.div>
  );
}
