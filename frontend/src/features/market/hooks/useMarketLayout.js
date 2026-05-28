import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useSearchParams } from 'react-router-dom';
import {
  useUserLayout,
  useUpdateOverviewLayout,
  DEFAULT_OVERVIEW_LAYOUT,
  MAX_PAGES,
  SCHEMA_VERSION,
} from '../../../shared/hooks/useUserLayout';
import { useWidgetDefinitions } from '../../../shared/hooks/useWidgetDefinitions';
import { newSectionId } from '../sections/sectionRegistry';
import { REMOVAL_ANIMATION_MS } from '../../../shared/constants/timings';

function newPageId() {
  return `page-${Math.random().toString(36).slice(2, 8)}`;
}

export function useMarketLayout() {
  const { t } = useTranslation();
  const [searchParams, setSearchParams] = useSearchParams();
  const [editMode, setEditMode] = useState(false);
  const [pendingTile, setPendingTile] = useState(null);
  const [localPages, setLocalPages] = useState(null);
  const userPickedPageId = searchParams.get('page');
  const setUserPickedPageId = useCallback((pageId) => {
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      if (pageId) next.set('page', pageId);
      else next.delete('page');
      return next;
    }, { replace: true });
  }, [setSearchParams]);
  const [deletingIds, setDeletingIds] = useState(() => new Set());
  const [popoverState, setPopoverState] = useState(null);
  const [galleryOpen, setGalleryOpen] = useState(true);

  const { isLoading: layoutLoading, overview: layout, error: layoutError, refetch: refetchLayout } = useUserLayout();
  const persistedPages = layout?.pages ?? null;

  const { byKind: widgetDefsByKind, isLoading: defsLoading, error: defsError, refetch: refetchDefs } = useWidgetDefinitions();
  const updateLayout = useUpdateOverviewLayout();

  const pages = editMode && localPages ? localPages : persistedPages;
  const activePageId = useMemo(() => {
    if (!pages || pages.length === 0) return null;
    if (userPickedPageId && pages.some((p) => p.id === userPickedPageId)) return userPickedPageId;
    return pages[0].id;
  }, [pages, userPickedPageId]);
  const activePage = useMemo(
    () => (pages && activePageId ? pages.find((p) => p.id === activePageId) : null),
    [pages, activePageId],
  );
  const persistedActivePage = useMemo(
    () => (persistedPages && activePageId ? persistedPages.find((p) => p.id === activePageId) : null),
    [persistedPages, activePageId],
  );

  const sections = activePage?.sections ?? [];
  const isDirty = editMode && localPages !== null && localPages !== persistedPages;

  const persistedRef = useRef(persistedPages);
  useEffect(() => { persistedRef.current = persistedPages; }, [persistedPages]);
  const updateLayoutRef = useRef(updateLayout);
  useEffect(() => { updateLayoutRef.current = updateLayout; }, [updateLayout]);

  const closePopover = useCallback(() => setPopoverState(null), []);

  const enterEditMode = useCallback(() => {
    setLocalPages(persistedRef.current ? persistedRef.current.map((p) => ({ ...p, sections: p.sections.map((s) => ({ ...s })) })) : null);
    setGalleryOpen(false);
    setEditMode(true);
  }, []);

  const saveAndExit = useCallback(() => {
    setLocalPages((prev) => {
      if (prev) updateLayoutRef.current.mutate({ schemaVersion: SCHEMA_VERSION, pages: prev });
      return null;
    });
    setEditMode(false);
    setPopoverState(null);
  }, []);

  const discardAndExit = useCallback(() => {
    setLocalPages(null);
    setPopoverState(null);
    setEditMode(false);
  }, []);

  const revertChanges = useCallback(() => {
    setLocalPages(persistedRef.current ? persistedRef.current.map((p) => ({ ...p, sections: p.sections.map((s) => ({ ...s })) })) : null);
  }, []);

  const updateActivePageSections = useCallback((mapper) => {
    setLocalPages((prev) => {
      if (!prev) return prev;
      return prev.map((p) => (p.id === activePageId ? { ...p, sections: mapper(p.sections) } : p));
    });
  }, [activePageId]);

  const handleDelete = useCallback((id) => {
    setDeletingIds((prev) => new Set(prev).add(id));
    setPopoverState((prev) => (prev?.sectionId === id ? null : prev));
    setTimeout(() => {
      updateActivePageSections((prev) => prev.filter((s) => s.sectionId !== id));
      setDeletingIds((prev) => {
        const next = new Set(prev);
        next.delete(id);
        return next;
      });
    }, REMOVAL_ANIMATION_MS);
  }, [updateActivePageSections]);

  const handleConfigChange = useCallback((id, config) => {
    if (editMode) {
      updateActivePageSections((prev) => prev.map((s) => (s.sectionId === id ? { ...s, config } : s)));
      return;
    }
    const base = persistedRef.current;
    if (!base || !activePageId) return;
    const next = base.map((p) => (p.id === activePageId
      ? { ...p, sections: p.sections.map((s) => (s.sectionId === id ? { ...s, config } : s)) }
      : p));
    updateLayoutRef.current.mutate({ schemaVersion: SCHEMA_VERSION, pages: next });
  }, [editMode, activePageId, updateActivePageSections]);

  const handleCanvasChange = useCallback((next) => {
    updateActivePageSections(() => next);
  }, [updateActivePageSections]);

  const handleOpenSettings = useCallback((sectionId, anchorEl) => {
    setPopoverState({ sectionId, anchorEl, autoFocusName: false });
  }, []);

  const insertSection = useCallback((kind, config, x, y, opts = {}) => {
    const def = widgetDefsByKind.get(kind);
    const size = def ? { w: def.defaults.w, h: def.defaults.h } : { w: 4, h: 6 };
    const finalConfig = { ...config };
    const newId = newSectionId(kind);
    const newEntry = { sectionId: newId, kind, w: size.w, h: size.h, config: finalConfig };
    setLocalPages((prev) => {
      const base = prev ?? persistedRef.current;
      if (!base) return prev;
      return base.map((p) => {
        if (p.id !== activePageId) return p;
        const existing = p.sections;
        if (kind === 'ASSET_CARDS' && !finalConfig.name) {
          finalConfig.name = t('widgetTray.assetCardsN', { n: existing.filter((s) => s.kind === 'ASSET_CARDS').length + 1 });
        }
        if (opts.placeAtTop) {
          const shifted = existing.map((s) => ({ ...s, y: (s.y ?? 0) + size.h }));
          return { ...p, sections: [{ ...newEntry, x: 0, y: 0 }, ...shifted] };
        }
        return {
          ...p,
          sections: [
            ...existing,
            {
              ...newEntry,
              x: Math.max(0, Math.min(12 - size.w, Math.round(x ?? 0))),
              y: Math.max(0, Math.round(y ?? 0)),
            },
          ],
        };
      });
    });
    return newId;
  }, [widgetDefsByKind, t, activePageId]);

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
    const defaults = DEFAULT_OVERVIEW_LAYOUT.pages.map((p) => ({
      ...p,
      sections: p.sections.map((s) => ({ ...s, config: { ...s.config } })),
    }));
    setLocalPages(defaults);
    setUserPickedPageId(defaults[0].id);
  }, [setUserPickedPageId]);

  const handleAddPage = useCallback(() => {
    setLocalPages((prev) => {
      const base = prev ?? persistedRef.current ?? [];
      if (base.length >= MAX_PAGES) return prev;
      const next = [
        ...base,
        { id: newPageId(), name: t('overviewPages.newPageName', { defaultValue: 'Yeni sayfa' }), sections: [] },
      ];
      setUserPickedPageId(next[next.length - 1].id);
      return next;
    });
  }, [t, setUserPickedPageId]);

  const handleRenamePage = useCallback((pageId, name) => {
    setLocalPages((prev) => {
      if (!prev) return prev;
      return prev.map((p) => (p.id === pageId ? { ...p, name } : p));
    });
  }, []);

  const handleDeletePage = useCallback((pageId) => {
    setLocalPages((prev) => {
      const base = prev ?? persistedRef.current ?? [];
      if (base.length <= 1) return prev;
      const next = base.filter((p) => p.id !== pageId);
      if (pageId === activePageId) setUserPickedPageId(next[0].id);
      return next;
    });
  }, [activePageId, setUserPickedPageId]);

  const handleSelectPage = useCallback((pageId) => {
    setUserPickedPageId(pageId);
    setPopoverState(null);
  }, [setUserPickedPageId]);

  const toggleGallery = useCallback(() => setGalleryOpen((o) => !o), []);

  return {
    editMode,
    isDirty,
    pages,
    activePageId,
    activePage,
    persistedActivePage,
    sections,
    pendingTile,
    deletingIds,
    popoverState,
    galleryOpen,
    widgetDefsByKind,
    updateLayout,
    layoutLoading,
    defsLoading,
    layoutError,
    defsError,
    refetchLayout,
    refetchDefs,
    layout,
    enterEditMode,
    saveAndExit,
    discardAndExit,
    revertChanges,
    resetToDefaults,
    handleDelete,
    handleConfigChange,
    handleCanvasChange,
    handleOpenSettings,
    handleTrayClick,
    handleTrayDragStart,
    handleTrayDragEnd,
    handleCanvasDrop,
    handleAddPage,
    handleRenamePage,
    handleDeletePage,
    handleSelectPage,
    toggleGallery,
    closePopover,
  };
}
