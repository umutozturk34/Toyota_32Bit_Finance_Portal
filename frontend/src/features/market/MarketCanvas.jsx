import { useTranslation } from 'react-i18next';
import { LayoutGrid } from 'lucide-react';
import LoadingState from '../../shared/components/feedback/LoadingState';
import ErrorState from '../../shared/components/feedback/ErrorState';
import OverviewWidgetCanvas from './components/OverviewWidgetCanvas';

export default function MarketCanvas({
  sections,
  widgets,
  editMode,
  deletingIds,
  popoverState,
  pendingTile,
  dataLoading,
  isPageInPersisted,
  error,
  onEnterEditMode,
  onOpenSettings,
  onCanvasChange,
  onDelete,
  onConfigChange,
  onCanvasDrop,
  onRefetch,
}) {
  const { t } = useTranslation();

  if (sections.length === 0) {
    return (
      <div className="rounded-xl border-2 border-dashed border-accent/30 bg-bg-elevated/40 px-6 py-16 text-center">
        <p className="font-display text-base font-bold text-fg mb-1">{t('marketOverview.emptyCanvas')}</p>
        <p className="font-mono text-[10px] tracking-[0.16em] uppercase text-fg-muted mb-5">
          {editMode ? t('marketOverview.emptyDragHint') : t('marketOverview.emptyEditHint')}
        </p>
        {!editMode && (
          <button
            onClick={onEnterEditMode}
            className="inline-flex items-center gap-2 rounded-lg border-2 border-accent bg-accent text-white px-4 py-2 text-xs font-mono font-bold tracking-[0.14em] uppercase hover:bg-accent-bright transition-all cursor-pointer shadow-lg shadow-accent/30"
          >
            <LayoutGrid className="h-3.5 w-3.5" />
            {t('marketOverview.startEdit')}
          </button>
        )}
      </div>
    );
  }

  if (dataLoading && isPageInPersisted) {
    return <LoadingState message={t('marketOverview.loading')} />;
  }

  if (error) {
    return <ErrorState message={t('marketOverview.error')} onRetry={onRefetch} />;
  }

  return (
    <OverviewWidgetCanvas
      sections={sections}
      widgets={widgets}
      editMode={editMode}
      deletingIds={deletingIds}
      activePopoverSectionId={popoverState?.sectionId ?? null}
      onOpenSettings={onOpenSettings}
      onChange={onCanvasChange}
      onDelete={onDelete}
      onConfigChange={onConfigChange}
      onDrop={onCanvasDrop}
      pendingDropSize={pendingTile ? { w: pendingTile.w, h: pendingTile.h } : null}
    />
  );
}
