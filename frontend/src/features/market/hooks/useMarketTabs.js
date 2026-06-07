import { useCallback } from 'react';
import { useSearchParams } from 'react-router-dom';

export function useMarketTabs() {
  const [searchParams, setSearchParams] = useSearchParams();
  const tabParam = searchParams.get('tab');
  const activeTab = tabParam === 'rates' ? 'rates'
    : tabParam === 'macro' ? 'macro'
    : tabParam === 'returns' ? 'returns'
    : 'overview';

  const setActiveTab = useCallback((next) => {
    const params = new URLSearchParams(searchParams);
    if (next === 'overview') params.delete('tab');
    else params.set('tab', next);
    setSearchParams(params, { replace: true });
  }, [searchParams, setSearchParams]);

  return { activeTab, setActiveTab };
}
