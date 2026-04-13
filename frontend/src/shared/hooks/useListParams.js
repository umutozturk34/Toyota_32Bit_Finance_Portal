import { useCallback, useMemo } from 'react';
import { useSearchParams } from 'react-router-dom';

export default function useListParams({ defaultDirection = 'desc', defaultSize = 8, prefix = '' } = {}) {
  const [searchParams, setSearchParams] = useSearchParams();
  const p = prefix ? `${prefix}.` : '';

  const search = searchParams.get(`${p}q`) || '';
  const sort = searchParams.get(`${p}sort`) || '';
  const direction = searchParams.get(`${p}dir`) || defaultDirection;
  const page = Number(searchParams.get(`${p}page`)) || 0;
  const filter = searchParams.get(`${p}filter`) || null;

  const update = useCallback((updates) => {
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      Object.entries(updates).forEach(([k, v]) => {
        const key = `${p}${k}`;
        if (v === null || v === undefined || v === '' || v === 0) next.delete(key);
        else next.set(key, String(v));
      });
      return next;
    }, { replace: true });
  }, [setSearchParams, p]);

  const handleSearch = useCallback((v) => update({ q: v, page: 0 }), [update]);
  const handleSort = useCallback((v) => update({ sort: v, page: 0 }), [update]);
  const handleDirection = useCallback((v) => update({ dir: v, page: 0 }), [update]);
  const handlePage = useCallback((v) => update({ page: v }), [update]);
  const handleFilter = useCallback((v) => update({ filter: v, page: 0 }), [update]);

  const params = useMemo(() => ({
    ...(search && { search }),
    ...(sort && { sort, direction }),
    page, size: defaultSize,
  }), [search, sort, direction, page, defaultSize]);

  return {
    search, sort, direction, page, size: defaultSize, filter,
    setSearch: handleSearch, setSort: handleSort, setDirection: handleDirection,
    setPage: handlePage, setFilter: handleFilter, params,
  };
}
