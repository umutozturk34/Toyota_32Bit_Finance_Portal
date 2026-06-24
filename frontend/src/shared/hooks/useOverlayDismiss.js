import { useEffect } from 'react';

/**
 * Shared behaviour for any overlay (modal, drawer, confirm dialog) while it is open: closes on Escape and locks
 * background scroll (so the page no longer scrolls under the panel), restoring the prior overflow on close. One
 * hook so every overlay behaves the same instead of each re-implementing (or omitting) these.
 *
 * @param open    whether the overlay is currently shown
 * @param onClose called on Escape
 */
export default function useOverlayDismiss(open, onClose) {
  useEffect(() => {
    if (!open) return undefined;
    const onKey = (e) => {
      if (e.key === 'Escape') {
        e.stopPropagation();
        onClose?.();
      }
    };
    window.addEventListener('keydown', onKey);
    const prevOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      window.removeEventListener('keydown', onKey);
      document.body.style.overflow = prevOverflow;
    };
  }, [open, onClose]);
}
