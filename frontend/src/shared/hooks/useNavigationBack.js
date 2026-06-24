import { useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import useNavigationStore from '../stores/useNavigationStore';

export default function useNavigationBack(fallbackRoute) {
  const navigate = useNavigate();
  const consumeOrigin = useNavigationStore((s) => s.consumeOrigin);

  return useCallback(() => {
    // Clear the one-shot origin marker (MainLayout re-records the previous path on the next push; scroll is
    // restored separately by useScrollRestoration), then step back through real browser history so a
    // detail→detail→detail chain traverses correctly. The old code navigated to origin.route with
    // {replace:true} — but MainLayout sets origin to the IMMEDIATE previous page, so that replaced the current
    // entry with a duplicate of it, making the NEXT back a no-op (stock → constituent → index → back → stuck).
    consumeOrigin();
    const canGoBack = typeof window !== 'undefined' && window.history.length > 1;
    if (canGoBack) {
      navigate(-1);
      return;
    }
    if (fallbackRoute) {
      navigate(fallbackRoute, { replace: true });
    }
  }, [navigate, consumeOrigin, fallbackRoute]);
}
