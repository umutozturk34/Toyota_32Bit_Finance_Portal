import { useCallback, useEffect, useRef, useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Activity, LayoutGrid, Save, RotateCcw, ToggleRight, ToggleLeft, Loader2, ChevronUp, ChevronDown } from 'lucide-react';
import { RefreshCw } from '../../shared/components/AnimatedIcons';
import LoadingState from '../../shared/components/LoadingState';
import ErrorState from '../../shared/components/ErrorState';
import SearchSuggestions from '../../shared/components/SearchSuggestions';
import { useUserLayout, useUpdateOverviewLayout, DEFAULT_OVERVIEW_LAYOUT } from '../../shared/hooks/useUserLayout';
import { useMarketOverview } from '../../shared/hooks/useMarketOverview';
import { useWidgetDefinitions } from '../../shared/hooks/useWidgetDefinitions';
import { useWatchlists } from '../../shared/hooks/useWatchlist';
import { newSectionId } from './sections/sectionRegistry';
import OverviewLayout from './OverviewLayout';
import OverviewWidgetCanvas from './OverviewWidgetCanvas';
import WidgetTray from './WidgetTray';
import WidgetSettingsPopover from './WidgetSettingsPopover';

const REMOVAL_ANIMATION_MS = 200;

export default function MarketDataPage() {
  const [editMode, setEditMode] = useState(false);
  const [pendingTile, setPendingTile] = useState(null);
  const [localSections, setLocalSections] = useState(null);
  const [deletingIds, setDeletingIds] = useState(() => new Set());
  const [popoverState, setPopoverState] = useState(null);
  const [galleryOpen, setGalleryOpen] = useState(true);
  const { isLoading: layoutLoading, overview: layout } = useUserLayout();
  const { isLoading: dataLoading, error, refetch, isFetching, widgets } = useMarketOverview();
  const { byKind: widgetDefsByKind } = useWidgetDefinitions();
  const { data: watchlists = [] } = useWatchlists({ enabled: editMode });
  const updateLayout = useUpdateOverviewLayout();

  const persistedSections = layout.sections;
  const sections = editMode && localSections ? localSections : persistedSections;
  const isDirty = editMode && localSections !== null && localSections !== persistedSections;

  const persistedRef = useRef(persistedSections);
  useEffect(() => { persistedRef.current = persistedSections; }, [persistedSections]);
  const updateLayoutRef = useRef(updateLayout);
  useEffect(() => { updateLayoutRef.current = updateLayout; }, [updateLayout]);

  const closePopover = useCallback(() => setPopoverState(null), []);

  const enterEditMode = useCallback(() => {
    setLocalSections(persistedRef.current);
    setGalleryOpen(false);
    setEditMode(true);
  }, []);

  const saveAndExit = useCallback(() => {
    setLocalSections((prev) => {
      if (prev) updateLayoutRef.current.mutate({ schemaVersion: 3, sections: prev });
      return null;
    });
    setEditMode(false);
    setPopoverState(null);
  }, []);

  const discardAndExit = useCallback(() => {
    setLocalSections(null);
    setPopoverState(null);
    setEditMode(false);
  }, []);

  const revertChanges = useCallback(() => {
    setLocalSections(persistedRef.current);
  }, []);

  const handleDelete = useCallback((id) => {
    setDeletingIds((prev) => new Set(prev).add(id));
    setPopoverState((prev) => (prev?.sectionId === id ? null : prev));
    setTimeout(() => {
      setLocalSections((prev) => (prev ? prev.filter((s) => s.sectionId !== id) : prev));
      setDeletingIds((prev) => {
        const next = new Set(prev);
        next.delete(id);
        return next;
      });
    }, REMOVAL_ANIMATION_MS);
  }, []);

  const handleConfigChange = useCallback((id, config) => {
    setLocalSections((prev) => (prev ? prev.map((s) => (s.sectionId === id ? { ...s, config } : s)) : prev));
  }, []);

  const handleCanvasChange = useCallback((next) => setLocalSections(next), []);

  const handleOpenSettings = useCallback((sectionId, anchorEl) => {
    setPopoverState({ sectionId, anchorEl, autoFocusName: false });
  }, []);

  const insertSection = useCallback((kind, config, x, y, opts = {}) => {
    const def = widgetDefsByKind.get(kind);
    const size = def ? { w: def.defaults.w, h: def.defaults.h } : { w: 4, h: 6 };
    const finalConfig = { ...config };
    const newId = newSectionId(kind);
    const newEntry = { sectionId: newId, kind, w: size.w, h: size.h, config: finalConfig };
    setLocalSections((prev) => {
      const base = prev ?? persistedRef.current;
      if (kind === 'ASSET_CARDS' && !finalConfig.name) {
        finalConfig.name = `Asset Kartları ${base.filter((s) => s.kind === 'ASSET_CARDS').length + 1}`;
      }
      if (opts.placeAtTop) {
        const shifted = base.map((s) => ({ ...s, y: (s.y ?? 0) + size.h }));
        return [{ ...newEntry, x: 0, y: 0 }, ...shifted];
      }
      return [
        ...base,
        {
          ...newEntry,
          x: Math.max(0, Math.min(12 - size.w, Math.round(x ?? 0))),
          y: Math.max(0, Math.round(y ?? 0)),
        },
      ];
    });
    return newId;
  }, [widgetDefsByKind]);

  const handleTrayClick = useCallback((tile, anchorEl) => {
    const newId = insertSection(tile.kind, tile.config, 0, 0, { placeAtTop: true });
    if (tile.kind === 'ASSET_CARDS' && anchorEl) {
      setPopoverState({ sectionId: newId, anchorEl, autoFocusName: true });
    }
  }, [insertSection]);

  const handleTrayDragStart = useCallback((tile) => setPendingTile(tile), []);
  const handleTrayDragEnd = useCallback(() => setPendingTile(null), []);

  const handleCanvasDrop = useCallback(({ x, y }) => {
    setPendingTile((tile) => {
      if (tile) insertSection(tile.kind, tile.config, x, y);
      return null;
    });
  }, [insertSection]);

  const resetToDefaults = useCallback(() => {
    setLocalSections(DEFAULT_OVERVIEW_LAYOUT.sections.map((s) => ({ ...s, config: { ...s.config } })));
  }, []);

  const toggleGallery = useCallback(() => setGalleryOpen((o) => !o), []);

  if (layoutLoading || dataLoading) return <LoadingState message="Piyasa özeti yükleniyor..." />;
  if (error) return <ErrorState message="Piyasa verileri yüklenemedi" onRetry={refetch} />;

  const popoverSection = popoverState ? sections.find((s) => s.sectionId === popoverState.sectionId) : null;

  const header = (
    <div className="flex items-center justify-between gap-3 flex-wrap">
      <div className="flex items-center gap-2.5 min-w-0">
        <span className="flex items-center justify-center w-9 h-9 rounded-xl bg-gradient-accent text-white shadow-lg shadow-accent/25 shrink-0">
          <Activity className="h-4 w-4" />
        </span>
        <div className="min-w-0">
          <h1 className="font-display text-xl font-bold tracking-tight text-fg leading-none">Piyasa Özeti</h1>
          <div className="relative flex items-center gap-2 mt-1 min-h-[18px] min-w-[180px]">
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
                    {isDirty ? 'Kaydedilmemiş değişiklikler' : 'Düzenleme modu'}
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
                    <span className="font-display text-[12px] font-semibold tracking-tight text-fg-muted">Canlı piyasa verileri</span>
                    <span className="inline-flex items-center gap-1 rounded-md bg-danger/12 border border-danger/40 px-1.5 py-0.5">
                      <span className="relative flex w-1.5 h-1.5">
                        <span className="absolute inset-0 rounded-full bg-danger opacity-60 animate-ping" />
                        <span className="relative block w-1.5 h-1.5 rounded-full bg-danger shadow-[0_0_6px_rgba(248,113,113,0.8)]" />
                      </span>
                      <span className="font-mono text-[9px] tracking-[0.18em] uppercase font-bold text-danger">Live</span>
                    </span>
                  </motion.div>
                )}
            </AnimatePresence>
          </div>
        </div>
      </div>
      <div className="flex items-center gap-1.5 flex-nowrap">
        {updateLayout.isPending && (
          <span className="flex items-center gap-1 font-mono text-[10px] tracking-wider uppercase text-accent/80">
            <Loader2 className="h-3 w-3 animate-spin" />
            Kaydediliyor
          </span>
        )}
        <button
          onClick={editMode ? discardAndExit : enterEditMode}
          className={`relative flex items-center justify-center gap-1.5 rounded-lg border px-3 py-1.5 text-[12px] font-display font-semibold tracking-tight transition-colors duration-200 cursor-pointer w-[112px] shrink-0 ${
            editMode
              ? 'border-accent bg-accent text-white hover:bg-accent-bright shadow-lg shadow-accent/30'
              : 'border-accent/40 bg-accent/8 text-accent hover:border-accent hover:bg-accent/15'
          }`}
          title={editMode ? (isDirty ? 'Düzenleme modunu kapat (kaydedilmemiş değişiklikler kaybolur)' : 'Düzenleme modunu kapat') : 'Düzenleme modunu aç'}
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
          Düzenle
        </button>
        <button
          onClick={refetch}
          className={`flex items-center gap-1.5 rounded-lg border border-border-default bg-bg-elevated px-3 py-1.5 text-[12px] font-display font-semibold tracking-tight text-fg-muted hover:text-fg hover:border-border-hover transition-opacity duration-150 cursor-pointer shrink-0 ${editMode ? 'opacity-0 pointer-events-none invisible' : 'opacity-100'}`}
          aria-hidden={editMode}
          tabIndex={editMode ? -1 : 0}
        >
          <RefreshCw className={`h-3.5 w-3.5 ${isFetching ? 'animate-spin' : ''}`} />
          Yenile
        </button>
        <span className={`w-px h-5 bg-border-default/60 mx-0.5 shrink-0 ${editMode ? '' : 'invisible'}`} aria-hidden="true" />
        <button
          onClick={resetToDefaults}
          className={`flex items-center gap-1 rounded-lg border border-border-default bg-bg-elevated px-2 py-1.5 text-[11px] font-display font-semibold tracking-tight text-fg-muted hover:text-fg hover:border-border-hover transition-opacity duration-150 cursor-pointer shrink-0 ${editMode ? 'opacity-100' : 'opacity-0 pointer-events-none invisible'}`}
          title="Varsayılan layout'a dön"
          aria-hidden={!editMode}
          tabIndex={editMode ? 0 : -1}
        >
          <LayoutGrid className="h-3 w-3" />
          Varsayılan
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
          title="Bu oturumdaki tüm değişiklikleri geri al"
          aria-hidden={!editMode}
          tabIndex={editMode ? 0 : -1}
        >
          <RotateCcw className="h-3 w-3" />
          Geri al
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
          Kaydet
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
          title={galleryOpen ? 'Widget galerisini gizle' : 'Widget galerisini göster'}
          aria-pressed={galleryOpen}
          aria-hidden={!editMode}
          tabIndex={editMode ? 0 : -1}
        >
          {galleryOpen ? <ChevronUp className="h-3 w-3" /> : <ChevronDown className="h-3 w-3" />}
          Galeri
        </button>
      </div>
      <div className="w-full max-w-md"><SearchSuggestions variant="hero" placeholder="Hisse, kripto, döviz, fon ara..." /></div>
    </div>
  );

  const tray = (
    <AnimatePresence initial={false}>
      {editMode && galleryOpen && (
        <motion.div
          key="gallery-sidebar"
          initial={{ opacity: 0, x: -12 }}
          animate={{ opacity: 1, x: 0 }}
          exit={{ opacity: 0, x: -12 }}
          transition={{ duration: 0.18, ease: [0.16, 1, 0.3, 1] }}
        >
          <WidgetTray sections={sections} watchlists={watchlists} onAdd={handleTrayClick} onDragStart={handleTrayDragStart} onDragEnd={handleTrayDragEnd} />
        </motion.div>
      )}
    </AnimatePresence>
  );

  const grid = sections.length === 0
    ? (
      <div className="rounded-xl border-2 border-dashed border-accent/30 bg-bg-elevated/40 px-6 py-16 text-center">
        <p className="font-display text-base font-bold text-fg mb-1">Boş tuval</p>
        <p className="font-mono text-[10px] tracking-[0.16em] uppercase text-fg-muted mb-5">
          {editMode ? 'Yukardaki galeriden widget sürükle' : 'Düzenle ile widget ekle'}
        </p>
        {!editMode && (
          <button
            onClick={enterEditMode}
            className="inline-flex items-center gap-2 rounded-lg border-2 border-accent bg-accent text-white px-4 py-2 text-xs font-mono font-bold tracking-[0.14em] uppercase hover:bg-accent-bright transition-all cursor-pointer shadow-lg shadow-accent/30"
          >
            <LayoutGrid className="h-3.5 w-3.5" />
            Düzene Geç
          </button>
        )}
      </div>
    )
    : <OverviewWidgetCanvas
        sections={sections}
        widgets={widgets}
        editMode={editMode}
        deletingIds={deletingIds}
        activePopoverSectionId={popoverState?.sectionId ?? null}
        onOpenSettings={handleOpenSettings}
        onChange={handleCanvasChange}
        onDelete={handleDelete}
        onConfigChange={handleConfigChange}
        onDrop={handleCanvasDrop}
        pendingDropSize={pendingTile ? { w: pendingTile.w, h: pendingTile.h } : null}
      />;

  return (
    <>
      <OverviewLayout header={header} editBar={tray} grid={grid} />
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
