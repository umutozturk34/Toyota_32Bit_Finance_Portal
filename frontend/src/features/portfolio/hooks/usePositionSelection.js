import { useCallback, useMemo, useRef, useState } from 'react';

/**
 * Selection state for the positions table. Tracks ids on the current page (cheap toggle/range
 * semantics) AND a separate metadata map keyed by id so cross-page selections survive page
 * changes — BulkDeleteDialog needs each row's {id, assetType} to route to spot vs derivative
 * delete mutations, but the visible {@code positions} list is filtered to the current page.
 */
export function usePositionSelection(positions, getId = (p) => p.id) {
    const [selectedIds, setSelectedIds] = useState(() => new Set());
    const [selectedMeta, setSelectedMeta] = useState(() => new Map());
    const anchorRef = useRef(null);

    // Keeps the meta map in sync with the id Set. Adding pulls minimal {id, assetType} info
    // from the current `positions` page; removing drops the entry.
    const remember = useCallback((id, posObj) => {
        setSelectedMeta((prev) => {
            if (prev.has(id)) return prev;
            const next = new Map(prev);
            next.set(id, { id, assetType: posObj?.assetType });
            return next;
        });
    }, []);

    const forget = useCallback((id) => {
        setSelectedMeta((prev) => {
            if (!prev.has(id)) return prev;
            const next = new Map(prev);
            next.delete(id);
            return next;
        });
    }, []);

    const toggle = useCallback((id, event) => {
        setSelectedIds((prev) => {
            const next = new Set(prev);
            const idsOnPage = positions.map(getId);
            const posById = new Map(positions.map((p) => [getId(p), p]));
            const a = anchorRef.current ? idsOnPage.indexOf(anchorRef.current) : -1;
            const b = idsOnPage.indexOf(id);
            // Range-select only when BOTH anchor and target are on the current page; an off-page anchor
            // (after pagination) falls through to a plain single toggle instead of silently doing nothing.
            if (event?.shiftKey && anchorRef.current !== id && a >= 0 && b >= 0) {
                const [lo, hi] = a < b ? [a, b] : [b, a];
                const target = !next.has(id);
                for (let i = lo; i <= hi; i++) {
                    const rangeId = idsOnPage[i];
                    if (target) {
                        next.add(rangeId);
                        remember(rangeId, posById.get(rangeId));
                    } else {
                        next.delete(rangeId);
                        forget(rangeId);
                    }
                }
            } else if (next.has(id)) {
                next.delete(id);
                forget(id);
            } else {
                next.add(id);
                remember(id, posById.get(id));
            }
            return next;
        });
        anchorRef.current = id;
    }, [positions, getId, remember, forget]);

    const clear = useCallback(() => {
        setSelectedIds(new Set());
        setSelectedMeta(new Map());
        anchorRef.current = null;
    }, []);

    const toggleAll = useCallback(() => {
        const pageIds = positions.map(getId);
        const fullyChecked = selectedIds.size >= pageIds.length
            && pageIds.every((id) => selectedIds.has(id));
        if (fullyChecked) {
            // Already covers the whole page → clear ONLY this page's ids (preserve cross-page
            // selections made earlier). Without this, hitting toggle on a re-visited page would
            // wipe a previously-built cross-page selection.
            setSelectedIds((prev) => {
                const next = new Set(prev);
                pageIds.forEach((id) => next.delete(id));
                return next;
            });
            setSelectedMeta((prev) => {
                const next = new Map(prev);
                pageIds.forEach((id) => next.delete(id));
                return next;
            });
            return;
        }
        setSelectedIds((prev) => {
            const next = new Set(prev);
            pageIds.forEach((id) => next.add(id));
            return next;
        });
        setSelectedMeta((prev) => {
            const next = new Map(prev);
            positions.forEach((p) => next.set(getId(p), { id: getId(p), assetType: p.assetType }));
            return next;
        });
    }, [positions, getId, selectedIds]);

    // Replace the entire selection with the supplied item list. Used by the "select all across
    // pages" affordance: after fetching every page's positions, callers pass the full item array
    // here so both the id set AND the meta map span every lot — BulkDeleteDialog can then route
    // each deletion to the right mutation (spot vs derivative) without re-fetching.
    const replaceWith = useCallback((items) => {
        setSelectedIds(new Set(items.map((it) => it.id)));
        setSelectedMeta(new Map(items.map((it) => [it.id, { id: it.id, assetType: it.assetType }])));
        anchorRef.current = null;
    }, []);

    return useMemo(() => ({
        count: selectedIds.size,
        allSelected: positions.length > 0 && positions.every((p) => selectedIds.has(getId(p))),
        selectedIdSet: selectedIds,
        // Every selected row (across pages) with the minimum metadata BulkDelete needs.
        selectedItems: Array.from(selectedMeta.values()),
        isSelected: (id) => selectedIds.has(id),
        selectedArray: positions.filter((p) => selectedIds.has(getId(p))),
        toggle,
        clear,
        toggleAll,
        replaceWith,
    }), [selectedIds, selectedMeta, positions, getId, toggle, clear, toggleAll, replaceWith]);
}
