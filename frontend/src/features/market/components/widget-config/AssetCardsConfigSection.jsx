import { useTranslation } from 'react-i18next';
import { X, Search, Layers, GripVertical } from 'lucide-react';
import {
  DndContext, closestCenter, KeyboardSensor, PointerSensor, useSensor, useSensors,
} from '@dnd-kit/core';
import {
  arrayMove, SortableContext, useSortable, verticalListSortingStrategy, sortableKeyboardCoordinates,
} from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import PopoverHeader from './PopoverHeader';
import SearchSuggestions from '../../../../shared/components/form/SearchSuggestions';

const MAX_ASSET_CHIPS = 12;

function SortableChip({ id, code, type, onRemove }) {
  const { t } = useTranslation();
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({ id });
  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
    opacity: isDragging ? 0.5 : 1,
  };
  return (
    <span
      ref={setNodeRef}
      style={style}
      className="inline-flex items-center gap-1 font-mono text-[11px] uppercase tracking-[0.06em] font-semibold pl-1 pr-1.5 py-1 rounded-md border border-accent/40 bg-accent/10 text-accent select-none"
    >
      <button
        type="button"
        {...attributes}
        {...listeners}
        className="flex items-center hover:text-fg transition-colors bg-transparent border-none cursor-grab active:cursor-grabbing p-0"
        aria-label={t('widgetSettings.drag')}
      >
        <GripVertical className="h-3 w-3 opacity-60" />
      </button>
      <span>{code.replace('.IS', '')}</span>
      <span className="text-[8px] text-accent/60 ml-0.5 normal-case tracking-wider">{type}</span>
      <button
        type="button"
        onClick={onRemove}
        className="flex items-center hover:text-danger transition-colors bg-transparent border-none cursor-pointer p-0 ml-0.5"
        aria-label={t('widgetSettings.removeChip', { code })}
      >
        <X className="h-2.5 w-2.5" />
      </button>
    </span>
  );
}

export default function AssetCardsConfigSection({ config, onChange, autoFocusName }) {
  const { t } = useTranslation();
  const codes = Array.isArray(config?.assetCodes) ? config.assetCodes : [];
  const name = typeof config?.name === 'string' ? config.name : '';
  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 4 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  );
  const ids = codes.map((c) => `${c.type}-${c.code}`);
  const remove = (idx) => onChange({ ...config, assetCodes: codes.filter((_, i) => i !== idx) });
  const add = (asset) => {
    if (codes.length >= MAX_ASSET_CHIPS) return;
    if (codes.some((c) => c.code === asset.code && c.type === asset.type)) return;
    onChange({ ...config, assetCodes: [...codes, asset] });
  };
  const handleDragEnd = (event) => {
    const { active, over } = event;
    if (!over || active.id === over.id) return;
    const oldIndex = ids.indexOf(active.id);
    const newIndex = ids.indexOf(over.id);
    onChange({ ...config, assetCodes: arrayMove(codes, oldIndex, newIndex) });
  };
  const full = codes.length >= MAX_ASSET_CHIPS;
  return (
    <div className="flex flex-col h-full min-h-0">
      <PopoverHeader Icon={Layers} title={t('widgetSettings.assetCardHeader')} />
      <div className="shrink-0 mb-3">
        <div className={`rounded-lg border bg-bg-base/50 px-3 py-2 transition-opacity ${full ? 'opacity-50 pointer-events-none border-border-default' : 'border-border-default'}`}>
          <div className="flex items-center gap-1.5 mb-2 px-1">
            <Search className="h-3.5 w-3.5 text-fg-subtle" />
            <span className="font-mono text-[10px] tracking-[0.16em] uppercase text-fg-subtle">{t('widgetSettings.searchHint')}</span>
          </div>
          <SearchSuggestions placeholder={t('widgetSettings.searchPlaceholder')} navigateOnSelect={false} onSelect={add} />
        </div>
        {full && (
          <p className="font-mono text-[9px] tracking-[0.14em] text-warning uppercase mt-1.5">{t('widgetSettings.maxAssets')}</p>
        )}
      </div>
      <div className="shrink-0 flex items-center justify-between mb-1.5">
        <span className="font-mono text-[9px] tracking-[0.18em] uppercase text-fg-subtle">{t('widgetSettings.pinnedAssets')}</span>
        <span className={`font-mono text-[9px] tabular-nums ${full ? 'text-warning' : 'text-fg-subtle'}`}>{codes.length}/{MAX_ASSET_CHIPS}</span>
      </div>
      <div className="flex-1 min-h-0 overflow-y-auto pr-1 -mr-1 mb-3 scrollbar-auto-hide">
        {codes.length === 0
          ? <p className="text-[11px] text-fg-subtle leading-relaxed">
              {t('widgetSettings.emptyHint')}
            </p>
          : <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
              <SortableContext items={ids} strategy={verticalListSortingStrategy}>
                <div className="flex flex-wrap gap-1.5">
                  {codes.map((c, i) => (
                    <SortableChip
                      key={`${c.type}-${c.code}`}
                      id={`${c.type}-${c.code}`}
                      code={c.code}
                      type={c.type}
                      onRemove={() => remove(i)}
                    />
                  ))}
                </div>
              </SortableContext>
            </DndContext>}
      </div>
      <div className="shrink-0">
        <label className="block font-mono text-[9px] tracking-[0.18em] uppercase text-fg-subtle mb-1">{t('widgetSettings.panelNameLabel')}</label>
        <input
          type="text"
          value={name}
          onChange={(e) => onChange({ ...config, name: e.target.value })}
          placeholder={t('widgetSettings.panelNamePlaceholder')}
          maxLength={40}
          autoFocus={autoFocusName}
          className="w-full font-display text-[13px] font-semibold px-2.5 py-1.5 rounded-md border border-border-default bg-bg-base/60 text-fg placeholder:text-fg-subtle focus:border-accent focus:bg-bg-base focus:outline-none focus:ring-2 focus:ring-accent/20 transition-all"
        />
      </div>
    </div>
  );
}
