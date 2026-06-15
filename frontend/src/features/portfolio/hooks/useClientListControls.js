import { useCallback, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';

const PAGE_SIZE = 8;

/**
 * Client-side search + status + P&L-sign filtering, sorting and pagination over an already-loaded list. The
 * fixed-income endpoints return the whole holding set in one shot (a portfolio holds few bonds/deposits), so there
 * is nothing to page server-side — this keeps all the controls in memory. Each consumer supplies how to read a
 * row's searchable text, its open/closed status, its P&L, plus the named sort comparators (ascending; direction is
 * applied here). Changing any filter resets to page 0 so the user never lands on a now-empty page.
 *
 * Pass {@code urlKey} (e.g. {@code 'b'} for bonds, {@code 'd'} for deposits) to mirror the controls into the URL
 * query string under that prefix ({@code ?b.q=akbnk&b.st=open&b.sort=maturity}), so a filtered view survives a
 * refresh and is shareable. Two lists on one page stay independent via distinct prefixes. Omit it to keep the
 * controls purely in memory (the original behaviour).
 *
 * @returns control state + `pageItems` (the current page slice) + `totalPages`/`total`.
 */
export function useClientListControls(items, { searchOf, statusOf, pnlOf, sorters, defaultSort, pageSize = PAGE_SIZE, urlKey = null }) {
  const [searchParams, setSearchParams] = useSearchParams();
  // Read the initial value of a control from the URL (when a prefix is set), else fall back to the default. Lazy
  // `useState` initialisers call this once on mount, so a refreshed/shared URL restores the view.
  const fromUrl = (name, fallback) => (urlKey ? (searchParams.get(`${urlKey}.${name}`) ?? fallback) : fallback);

  const [search, setSearchRaw] = useState(() => fromUrl('q', ''));
  const [status, setStatusRaw] = useState(() => fromUrl('st', 'all')); // all | open | closed
  const [pnl, setPnlRaw] = useState(() => fromUrl('kz', 'all')); // all | profit | loss
  const [sort, setSortRaw] = useState(() => fromUrl('sort', defaultSort || ''));
  const [direction, setDirectionRaw] = useState(() => fromUrl('dir', 'desc'));
  const [page, setPageRaw] = useState(() => {
    const p = parseInt(fromUrl('p', '0'), 10);
    return Number.isFinite(p) && p > 0 ? p : 0;
  });

  // Write (or clear, when at its default) one control into the URL under the prefix, replacing history so the
  // back button isn't spammed. A functional update merges with the other list's params on the same page.
  const writeUrl = useCallback((name, value, def) => {
    if (!urlKey) return;
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      const key = `${urlKey}.${name}`;
      if (value == null || value === '' || value === def) next.delete(key);
      else next.set(key, String(value));
      return next;
    }, { replace: true });
  }, [urlKey, setSearchParams]);

  // Filter changes reset to page 0 (and clear the page param) so the user never lands on a now-empty page.
  const onFilter = (setter, name, def) => (v) => {
    setter(v);
    setPageRaw(0);
    writeUrl(name, v, def);
    writeUrl('p', 0, '0');
  };
  const setDirection = (v) => { setDirectionRaw(v); writeUrl('dir', v, 'desc'); };
  const setPage = (p) => { setPageRaw(p); writeUrl('p', p, '0'); };

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
    search, setSearch: onFilter(setSearchRaw, 'q', ''),
    status, setStatus: onFilter(setStatusRaw, 'st', 'all'),
    pnl, setPnl: onFilter(setPnlRaw, 'kz', 'all'),
    sort, setSort: onFilter(setSortRaw, 'sort', defaultSort || ''),
    direction, setDirection,
    page: safePage, setPage,
    totalPages, pageItems, total: processed.length,
  };
}
