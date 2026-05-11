import { useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import useNavigationStore from '../stores/useNavigationStore';

export default function useNavigationBack(fallbackRoute) {
  const navigate = useNavigate();
  const consumeOrigin = useNavigationStore((s) => s.consumeOrigin);

  return useCallback(() => {
    const origin = consumeOrigin();
    if (origin?.route) {
      navigate(origin.route);
      return;
    }
    const canGoBack = typeof window !== 'undefined' && window.history.length > 1;
    if (canGoBack) {
      navigate(-1);
      return;
    }
    if (fallbackRoute) {
      navigate(fallbackRoute);
    }
  }, [navigate, consumeOrigin, fallbackRoute]);
}
