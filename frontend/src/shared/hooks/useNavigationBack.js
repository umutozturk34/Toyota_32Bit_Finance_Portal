import { useCallback } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import useNavigationStore from '../stores/useNavigationStore';

export default function useNavigationBack(fallbackRoute) {
  const navigate = useNavigate();
  const location = useLocation();
  const consumeOrigin = useNavigationStore((s) => s.consumeOrigin);

  return useCallback(() => {
    const origin = consumeOrigin();
    const here = location.pathname + (location.search || '');
    if (origin?.route && origin.route !== here) {
      const currentLength = typeof window !== 'undefined' ? window.history.length : 0;
      const delta = currentLength - (origin.historyLength || 0);
      if (delta > 0 && delta < currentLength) {
        navigate(-delta);
        return;
      }
      navigate(origin.route, { replace: true });
      return;
    }
    const canGoBack = typeof window !== 'undefined' && window.history.length > 1;
    if (canGoBack) {
      navigate(-1);
      return;
    }
    if (fallbackRoute) {
      navigate(fallbackRoute, { replace: true });
    }
  }, [navigate, consumeOrigin, fallbackRoute, location.pathname, location.search]);
}
