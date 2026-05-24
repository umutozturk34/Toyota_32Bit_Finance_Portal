import { useCallback, useMemo, useRef, useState } from 'react';

export function usePositionSelection(positions, getId = (p) => p.id) {
    const [selectedIds, setSelectedIds] = useState(() => new Set());
    const anchorRef = useRef(null);

    const toggle = useCallback((id, event) => {
        setSelectedIds((prev) => {
            const next = new Set(prev);
            if (event?.shiftKey && anchorRef.current && anchorRef.current !== id) {
                const ids = positions.map(getId);
                const a = ids.indexOf(anchorRef.current);
                const b = ids.indexOf(id);
                if (a >= 0 && b >= 0) {
                    const [lo, hi] = a < b ? [a, b] : [b, a];
                    const target = !next.has(id);
                    for (let i = lo; i <= hi; i++) {
                        if (target) next.add(ids[i]); else next.delete(ids[i]);
                    }
                }
            } else if (next.has(id)) {
                next.delete(id);
            } else {
                next.add(id);
            }
            return next;
        });
        anchorRef.current = id;
    }, [positions, getId]);

    const clear = useCallback(() => {
        setSelectedIds(new Set());
        anchorRef.current = null;
    }, []);

    const toggleAll = useCallback(() => {
        setSelectedIds((prev) =>
            prev.size === positions.length && positions.length > 0
                ? new Set()
                : new Set(positions.map(getId)));
    }, [positions, getId]);

    return useMemo(() => ({
        count: selectedIds.size,
        allSelected: positions.length > 0 && selectedIds.size === positions.length,
        isSelected: (id) => selectedIds.has(id),
        selectedArray: positions.filter((p) => selectedIds.has(getId(p))),
        toggle,
        clear,
        toggleAll,
    }), [selectedIds, positions, getId, toggle, clear, toggleAll]);
}
