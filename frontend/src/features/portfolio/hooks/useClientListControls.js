import { useMemo, useState } from 'react';

const PAGE_SIZE = 8;

/**
 * Client-side search + status + P&L-sign filtering, sorting and pagination over an already-loaded list. The
 * fixed-income endpoints return the whole holding set in one shot (a portfolio holds few bonds/deposits), so there
 * is nothing to page server-side — this keeps all the controls in memory. Each consumer supplies how to read a
 * row's searchable text, its open/closed status, its P&L, plus the named sort comparators (ascending; direction is
 * applied here). Changing any filter resets to page 0 so the user never lands on a now-empty page.
 *
 * @returns control state + `pageItems` (the current page slice) + `totalPages`/`total`.
 */
export function useClientListControls(items, { searchOf, statusOf, pnlOf, sorters, defaultSort, pageSize = PAGE_SIZE }) {
  const [search, setSearchRaw] = useState('');
  const [status, setStatusRaw] = useState('all'); // all | open | closed
  const [pnl, setPnlRaw] = useState('all'); // all | profit | loss
  const [sort, setSortRaw] = useState(defaultSort || '');
  const [direction, setDirection] = useState('desc');
  const [page, setPage] = useState(0);

  const onPage0 = (setter) => (v) => { setter(v); setPage(0); };

  const processed = useMemo(() => {
    let rows = Array.isArray(items) ? items.slice() : [];
    if (status !== 'all') rows = rows.filter((r) => statusOf(r) === status);
    if (pnl !== 'all') {
      rows = rows.filter((r) => {
        const v = Number(pnlOf(r)) || 0;
        return pnl === 'profit' ? v > 0 : v < 0;
      });
    }
    const q = search.trim().toLowerCase();
    if (q) rows = rows.filter((r) => String(searchOf(r) ?? '').toLowerCase().includes(q));
    const cmp = sorters[sort];
    if (cmp) {
      const dir = direction === 'asc' ? 1 : -1;
      rows.sort((a, b) => cmp(a, b) * dir);
    }
    return rows;
  }, [items, status, pnl, search, sort, direction, statusOf, pnlOf, searchOf, sorters]);

  const totalPages = Math.max(1, Math.ceil(processed.length / pageSize));
  const safePage = Math.min(page, totalPages - 1);
  const pageItems = processed.slice(safePage * pageSize, safePage * pageSize + pageSize);

  return {
    search, setSearch: onPage0(setSearchRaw),
    status, setStatus: onPage0(setStatusRaw),
    pnl, setPnl: onPage0(setPnlRaw),
    sort, setSort: onPage0(setSortRaw),
    direction, setDirection,
    page: safePage, setPage,
    totalPages, pageItems, total: processed.length,
  };
}
