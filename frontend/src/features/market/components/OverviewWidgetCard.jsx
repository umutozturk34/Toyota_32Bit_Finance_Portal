import { memo, useMemo } from 'react';
import { X, Sliders, GripVertical } from 'lucide-react';
import { definitionFor } from '../sections/sectionRegistry';

/** @param {{Icon: any, label: string, onClick: (e: any) => void, tone?: string, active?: boolean}} props */
function ActionDot({ Icon, label, onClick, tone = 'default', active = false }) {
  const tones = {
    default: 'border-border-default text-fg-muted hover:text-fg hover:border-fg-subtle hover:bg-surface',
    danger: 'border-danger/40 text-danger/80 hover:text-danger hover:border-danger hover:bg-danger/10',
    accent: active
      ? 'border-accent bg-accent/15 text-accent shadow-[inset_0_0_8px_-2px_var(--color-accent)]'
      : 'border-accent/50 text-accent hover:text-accent-bright hover:border-accent-bright hover:bg-accent/10',
  };
  return (
    <button
      type="button"
      onClick={onClick}
      onPointerDown={(e) => e.stopPropagation()}
      title={label}
      aria-label={label}
      className={`widget-no-drag flex items-center justify-center w-7 h-7 rounded-lg border bg-bg-deep/85 backdrop-blur-md transition-colors duration-150 cursor-pointer shadow-lg shadow-black/40 ${tones[tone]}`}
    >
      <Icon className="h-3.5 w-3.5" />
    </button>
  );
}

const EMPTY_STYLE = Object.freeze({});

/**
 * @typedef {Object} OverviewWidgetCardProps
 * @property {{sectionId: string, kind: string, config?: Object}} section
 * @property {Object|null} widgetData
 * @property {boolean} editMode
 * @property {boolean} [draggable]
 * @property {boolean} [deleting]
 * @property {boolean} [popoverActive]
 * @property {(id: string, anchorEl: HTMLElement) => void} [onOpenSettings]
 * @property {(id: string) => void} onDelete
 * @property {(id: string, config: Object) => void} onConfigChange
 */

/** @param {OverviewWidgetCardProps} props */
function OverviewWidgetCard({
  section, widgetData, editMode, draggable = true,
  deleting = false, popoverActive = false, onOpenSettings,
  onDelete, onConfigChange,
}) {
  const def = definitionFor(section.kind);
  const Component = def?.Component;
  const dragActive = editMode && draggable && !deleting;
  const editStyle = useMemo(() => {
    if (!editMode) return EMPTY_STYLE;
    if (deleting) return { outline: '1.5px solid rgba(248,113,113,0.7)', outlineOffset: '2px' };
    if (popoverActive) return { outline: '1.5px solid rgba(99,102,241,0.8)', outlineOffset: '2px' };
    return { outline: '1.5px dashed rgba(99,102,241,0.5)', outlineOffset: '2px' };
  }, [editMode, deleting, popoverActive]);
  if (!def) return null;

  return (
    <div
      style={editStyle}
      className={`relative h-full transition-opacity duration-200 ease-out ${deleting ? 'pointer-events-none opacity-0' : 'opacity-100'} ${editMode ? 'is-edit' : ''}`}
    >
      <div className="relative h-full">
        <div className="h-full">
          {def.configurable
            ? <Component
                data={widgetData}
                {...(section.config || {})}
                editMode={editMode}
                config={section.config || {}}
                onConfigChange={(next) => onConfigChange?.(section.sectionId, next)}
              />
            : <Component data={widgetData} {...(section.config || {})} />}
        </div>
        <div
          className={`absolute inset-0 z-30 cursor-grab active:cursor-grabbing transition-opacity duration-150 ${editMode ? 'opacity-100' : 'opacity-0 pointer-events-none'}`}
          aria-hidden="true"
        />
        <div
          className={`absolute top-2.5 left-2.5 z-40 flex items-center justify-center w-7 h-7 rounded-lg border bg-bg-deep/85 shadow-lg pointer-events-none border-accent/55 text-accent transition-[opacity,transform] duration-150 ease-out ${dragActive ? 'opacity-100 scale-100' : 'opacity-0 scale-90'}`}
          aria-hidden="true"
        >
          <GripVertical className="h-3.5 w-3.5" />
        </div>
        <div
          className={`absolute top-2.5 right-2.5 z-40 flex items-center gap-1.5 transition-[opacity,transform] duration-150 ease-out ${editMode ? 'opacity-100 scale-100 pointer-events-auto' : 'opacity-0 scale-90 pointer-events-none'}`}
        >
          {def.configurable && (
            <ActionDot
              Icon={Sliders}
              label="Ayarlar"
              tone="accent"
              active={popoverActive}
              onClick={(e) => onOpenSettings?.(section.sectionId, e.currentTarget)}
            />
          )}
          <ActionDot Icon={X} label="Sil" tone="danger" onClick={() => onDelete?.(section.sectionId)} />
        </div>
      </div>
    </div>
  );
}

export default memo(OverviewWidgetCard);
