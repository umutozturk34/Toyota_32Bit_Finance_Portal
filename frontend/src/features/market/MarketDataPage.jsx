import { useTranslation } from 'react-i18next';
import { motion, AnimatePresence } from 'framer-motion';
import { Activity, LayoutGrid, Save, RotateCcw, ToggleRight, ToggleLeft, ChevronUp, ChevronDown, Banknote, BarChart3 } from 'lucide-react';
import BankRatesPanel from '../bankRates/BankRatesPanel';
import MacroIndicatorsPanel from '../macro/MacroIndicatorsPanel';
import { RefreshCw } from '../../shared/components/feedback/AnimatedIcons';
import LoadingState from '../../shared/components/feedback/LoadingState';
import ErrorState from '../../shared/components/feedback/ErrorState';
import Spinner from '../../shared/components/feedback/Spinner';
import SearchSuggestions from '../../shared/components/form/SearchSuggestions';
import { MAX_PAGES } from '../../shared/hooks/useUserLayout';
import { useMarketOverview } from '../../shared/hooks/useMarketOverview';
import { useWatchlists } from '../../shared/hooks/useWatchlist';
import OverviewLayout from './components/OverviewLayout';
import WidgetSettingsPopover from './components/WidgetSettingsPopover';
import OverviewPageTabs from './components/OverviewPageTabs';
import MarketCanvas from './MarketCanvas';
import MarketWidgetGallery from './MarketWidgetGallery';
import { useMarketTabs } from './hooks/useMarketTabs';
import { useMarketLayout } from './hooks/useMarketLayout';

export default function MarketDataPage() {
  const { t } = useTranslation();
  const { activeTab, setActiveTab } = useMarketTabs();
  const {
    editMode, isDirty, pages, activePageId, sections, persistedActivePage,
    pendingTile, deletingIds, popoverState, galleryOpen,
    updateLayout, layoutLoading, defsLoading, layoutError, defsError, layout, widgetDefsByKind,
    refetchLayout, refetchDefs,
    enterEditMode, saveAndExit, discardAndExit, revertChanges, resetToDefaults,
    handleDelete, handleConfigChange, handleCanvasChange, handleOpenSettings,
    handleTrayClick, handleTrayDragStart, handleTrayDragEnd, handleCanvasDrop,
    handleAddPage, handleRenamePage, handleDeletePage, handleSelectPage,
    toggleGallery, closePopover,
  } = useMarketLayout();

  const { isLoading: dataLoading, error, refetch, isFetching, widgets } = useMarketOverview(activePageId);
  const { data: watchlists = [] } = useWatchlists({ enabled: editMode });

  if (layoutLoading || defsLoading) return <LoadingState message={t('marketOverview.loading')} />;
  if (defsError || layoutError || !layout || widgetDefsByKind.size === 0) {
    return <ErrorState message={t('marketOverview.error')} onRetry={() => { refetch(); refetchDefs(); refetchLayout(); }} />;
  }

  const popoverSection = popoverState ? sections.find((s) => s.sectionId === popoverState.sectionId) : null;

  const header = (
    <div className="flex items-center justify-between gap-3 flex-wrap">
      <div className="flex items-center gap-2.5 min-w-0">
        <span className="flex items-center justify-center w-9 h-9 rounded-xl bg-gradient-accent text-white shadow-lg shadow-accent/25 shrink-0">
          <Activity className="h-4 w-4" />
        </span>
        <div className="min-w-0">
          <h1 className="font-display text-xl font-bold tracking-tight text-fg leading-none">{t('marketOverview.title')}</h1>
          <div className="relative flex items-center gap-2 mt-1 min-h-[18px] sm:min-w-[180px]">
            <AnimatePresence mode="wait" initial={false}>
              {editMode
                ? (
                  <motion.span
                    key="edit-state"
                    initial={{ opacity: 0, y: 4 }}
                    animate={{ opacity: 1, y: 0 }}
                    exit={{ opacity: 0, y: -4 }}
                    transition={{ duration: 0.16, ease: [0.16, 1, 0.3, 1] }}
                    className={`font-display text-[12px] font-semibold tracking-tight ${isDirty ? 'text-accent' : 'text-fg-muted'}`}
                  >
                    {isDirty ? t('marketOverview.unsavedChanges') : t('marketOverview.editMode')}
                  </motion.span>
                )
                : (
                  <motion.div
                    key="live-state"
                    initial={{ opacity: 0, y: 4 }}
                    animate={{ opacity: 1, y: 0 }}
                    exit={{ opacity: 0, y: -4 }}
                    transition={{ duration: 0.16, ease: [0.16, 1, 0.3, 1] }}
                    className="flex items-center gap-2"
                  >
                    <span className="font-display text-[12px] font-semibold tracking-tight text-fg-muted">{t('marketOverview.liveData')}</span>
                    <span className="inline-flex items-center gap-1 rounded-md bg-danger/12 border border-danger/40 px-1.5 py-0.5">
                      <span className="relative flex w-1.5 h-1.5">
                        <span className="absolute inset-0 rounded-full bg-danger opacity-60 animate-ping" />
                        <span className="relative block w-1.5 h-1.5 rounded-full bg-danger shadow-[0_0_6px_rgba(248,113,113,0.8)]" />
                      </span>
                      <span className="font-mono text-[9px] tracking-[0.18em] uppercase font-bold text-danger">{t('common.live')}</span>
                    </span>
                  </motion.div>
                )}
            </AnimatePresence>
          </div>
        </div>
      </div>
      <div className="flex items-center gap-1.5 flex-wrap">
        {updateLayout.isPending && (
          <span className="flex items-center gap-1 font-mono text-[10px] tracking-wider uppercase text-accent/80">
            <Spinner size="xs" tone="inherit" />
            {t('marketOverview.saving')}
          </span>
        )}
        <button
          onClick={editMode ? discardAndExit : enterEditMode}
          data-tour="widget-edit"
          className={`relative flex items-center justify-center gap-1.5 rounded-lg border px-3 py-1.5 text-[12px] font-display font-semibold tracking-tight transition-colors duration-200 cursor-pointer w-[112px] shrink-0 ${
            editMode
              ? 'border-accent bg-accent text-white hover:bg-accent-bright shadow-lg shadow-accent/30'
              : 'border-accent/40 bg-accent/8 text-accent hover:border-accent hover:bg-accent/15'
          }`}
          title={editMode ? (isDirty ? t('marketOverview.editToggleDirty') : t('marketOverview.editToggleClean')) : t('marketOverview.editToggleOff')}
          aria-pressed={editMode}
        >
          <span className="relative inline-flex items-center justify-center w-3.5 h-3.5">
            <AnimatePresence initial={false} mode="wait">
              <motion.span
                key={editMode ? 'on' : 'off'}
                initial={{ opacity: 0, rotate: -90, scale: 0.6 }}
                animate={{ opacity: 1, rotate: 0, scale: 1 }}
                exit={{ opacity: 0, rotate: 90, scale: 0.6 }}
                transition={{ duration: 0.16, ease: [0.16, 1, 0.3, 1] }}
                className="absolute inset-0 flex items-center justify-center"
              >
                {editMode
                  ? <ToggleRight className="h-3.5 w-3.5" strokeWidth={2.4} />
                  : <ToggleLeft className="h-3.5 w-3.5" strokeWidth={2.4} />}
              </motion.span>
            </AnimatePresence>
          </span>
          {t('marketOverview.edit')}
        </button>
        <button
          onClick={refetch}
          className={`flex items-center gap-1.5 rounded-lg border border-border-default bg-bg-elevated px-3 py-1.5 text-[12px] font-display font-semibold tracking-tight text-fg-muted hover:text-fg hover:border-border-hover transition-opacity duration-150 cursor-pointer shrink-0 ${editMode ? 'opacity-0 pointer-events-none invisible' : 'opacity-100'}`}
          aria-hidden={editMode}
          tabIndex={editMode ? -1 : 0}
        >
          <RefreshCw className={`h-3.5 w-3.5 ${isFetching ? 'animate-spin' : ''}`} />
          {t('marketOverview.refresh')}
        </button>
        <span className={`w-px h-5 bg-border-default/60 mx-0.5 shrink-0 ${editMode ? '' : 'invisible'}`} aria-hidden="true" />
        <button
          onClick={resetToDefaults}
          className={`flex items-center gap-1 rounded-lg border border-border-default bg-bg-elevated px-2 py-1.5 text-[11px] font-display font-semibold tracking-tight text-fg-muted hover:text-fg hover:border-border-hover transition-opacity duration-150 cursor-pointer shrink-0 ${editMode ? 'opacity-100' : 'opacity-0 pointer-events-none invisible'}`}
          title={t('marketOverview.resetTitle')}
          aria-hidden={!editMode}
          tabIndex={editMode ? 0 : -1}
        >
          <LayoutGrid className="h-3 w-3" />
          {t('marketOverview.reset')}
        </button>
        <button
          onClick={revertChanges}
          disabled={!editMode || !isDirty}
          className={`flex items-center gap-1 rounded-lg border px-2 py-1.5 text-[11px] font-display font-semibold tracking-tight transition-opacity duration-150 shrink-0 ${
            editMode
              ? (isDirty
                ? 'border-border-default bg-bg-elevated text-fg-muted hover:text-fg hover:border-border-hover cursor-pointer opacity-100'
                : 'border-border-default bg-bg-elevated text-fg-subtle cursor-not-allowed opacity-50')
              : 'border-border-default bg-bg-elevated text-fg-subtle opacity-0 pointer-events-none invisible'
          }`}
          title={t('marketOverview.revertTitle')}
          aria-hidden={!editMode}
          tabIndex={editMode ? 0 : -1}
        >
          <RotateCcw className="h-3 w-3" />
          {t('marketOverview.revert')}
        </button>
        <button
          onClick={saveAndExit}
          disabled={!editMode || !isDirty || updateLayout.isPending}
          className={`flex items-center gap-1 rounded-lg border px-2 py-1.5 text-[11px] font-display font-semibold tracking-tight transition-opacity duration-150 shrink-0 ${
            editMode
              ? (isDirty && !updateLayout.isPending
                ? 'border-accent bg-accent text-white hover:bg-accent-bright shadow-md shadow-accent/30 cursor-pointer opacity-100'
                : 'border-border-default bg-bg-elevated text-fg-subtle cursor-not-allowed opacity-50')
              : 'border-border-default bg-bg-elevated text-fg-subtle opacity-0 pointer-events-none invisible'
          }`}
          aria-hidden={!editMode}
          tabIndex={editMode ? 0 : -1}
        >
          <Save className="h-3 w-3" />
          {t('marketOverview.save')}
        </button>
        <button
          onClick={toggleGallery}
          className={`flex items-center gap-1 rounded-lg border px-2 py-1.5 text-[11px] font-display font-semibold tracking-tight transition-opacity duration-150 cursor-pointer shrink-0 ${
            editMode
              ? (galleryOpen
                ? 'border-accent/60 bg-accent/15 text-accent opacity-100'
                : 'border-border-default bg-bg-elevated text-fg-muted hover:text-fg hover:border-border-hover opacity-100')
              : 'border-border-default bg-bg-elevated text-fg-muted opacity-0 pointer-events-none invisible'
          }`}
          title={galleryOpen ? t('marketOverview.galleryHide') : t('marketOverview.galleryShow')}
          aria-pressed={galleryOpen}
          aria-hidden={!editMode}
          tabIndex={editMode ? 0 : -1}
        >
          {galleryOpen ? <ChevronUp className="h-3 w-3" /> : <ChevronDown className="h-3 w-3" />}
          {t('marketOverview.gallery')}
        </button>
      </div>
      <div data-tour="market-search" className="w-full max-w-md"><SearchSuggestions variant="hero" placeholder={t('marketOverview.searchPlaceholder')} /></div>
      <div data-tour="market-tabs" className="inline-flex items-center gap-1 rounded-xl border border-border-default bg-bg-elevated backdrop-blur-md p-1 self-start" style={{ willChange: 'backdrop-filter', transform: 'translate3d(0,0,0)' }}>
        <button
          onClick={() => setActiveTab('overview')}
          data-tour="market-overview-tab"
          className={`relative flex items-center gap-1.5 rounded-lg px-4 py-2 text-xs font-medium transition-all border-none cursor-pointer ${
            activeTab === 'overview' ? 'bg-accent/15 text-accent' : 'bg-transparent text-fg-muted hover:text-fg'
          }`}
        >
          <Activity className="h-3.5 w-3.5" />
          {t('marketOverview.tabOverview', 'Genel Bakış')}
        </button>
        <button
          onClick={() => setActiveTab('rates')}
          data-tour="market-rates-tab"
          className={`relative flex items-center gap-1.5 rounded-lg px-4 py-2 text-xs font-medium transition-all border-none cursor-pointer ${
            activeTab === 'rates' ? 'bg-accent/15 text-accent' : 'bg-transparent text-fg-muted hover:text-fg'
          }`}
        >
          <Banknote className="h-3.5 w-3.5" />
          {t('marketOverview.tabRates', 'Kurlar')}
        </button>
        <button
          onClick={() => setActiveTab('macro')}
          data-tour="market-macro-tab"
          className={`relative flex items-center gap-1.5 rounded-lg px-4 py-2 text-xs font-medium transition-all border-none cursor-pointer ${
            activeTab === 'macro' ? 'bg-accent/15 text-accent' : 'bg-transparent text-fg-muted hover:text-fg'
          }`}
        >
          <BarChart3 className="h-3.5 w-3.5" />
          {t('marketOverview.tabMacro', 'Göstergeler')}
        </button>
      </div>
    </div>
  );

  const pageTabs = activeTab === 'overview' && pages && pages.length > 0
    ? (
      <OverviewPageTabs
        pages={pages}
        activePageId={activePageId}
        editMode={editMode}
        canAdd={pages.length < MAX_PAGES}
        onSelect={handleSelectPage}
        onRename={handleRenamePage}
        onDelete={handleDeletePage}
        onAdd={handleAddPage}
      />
    )
    : null;

  const tray = (
    <MarketWidgetGallery
      visible={editMode && galleryOpen}
      sections={sections}
      watchlists={watchlists}
      onAdd={handleTrayClick}
      onDragStart={handleTrayDragStart}
      onDragEnd={handleTrayDragEnd}
    />
  );

  const grid = activeTab === 'rates'
    ? <div data-tour="market-rates-content"><BankRatesPanel /></div>
    : activeTab === 'macro'
    ? <div data-tour="market-macro-content"><MacroIndicatorsPanel /></div>
    : (
      <MarketCanvas
        sections={sections}
        widgets={widgets}
        editMode={editMode}
        deletingIds={deletingIds}
        popoverState={popoverState}
        pendingTile={pendingTile}
        dataLoading={dataLoading}
        isPageInPersisted={persistedActivePage != null}
        error={error}
        onEnterEditMode={enterEditMode}
        onOpenSettings={handleOpenSettings}
        onCanvasChange={handleCanvasChange}
        onDelete={handleDelete}
        onConfigChange={handleConfigChange}
        onCanvasDrop={handleCanvasDrop}
        onRefetch={refetch}
      />
    );

  return (
    <>
      <OverviewLayout header={header} pageTabs={pageTabs} editBar={tray} grid={grid} />
      <AnimatePresence>
        {popoverState && popoverSection && (
          <WidgetSettingsPopover
            key={popoverState.sectionId}
            anchorEl={popoverState.anchorEl}
            kind={popoverSection.kind}
            config={popoverSection.config}
            autoFocusName={popoverState.autoFocusName}
            onChange={(next) => handleConfigChange(popoverSection.sectionId, next)}
            onClose={closePopover}
          />
        )}
      </AnimatePresence>
    </>
  );
}
