import { useEffect } from 'react';
import { createPortal } from 'react-dom';
import { motion, AnimatePresence } from 'framer-motion';
import { useTranslation } from 'react-i18next';
import { useLocation } from 'react-router-dom';
import { X } from 'lucide-react';
import useAppStore from '../../stores/useAppStore';
import useMediaQuery from '../../hooks/useMediaQuery';
import SearchSuggestions from '../form/SearchSuggestions';

// Global search overlay: a centered command palette on desktop and a full-screen sheet on touch widths.
// Both share the existing SearchSuggestions engine (debounce, recent searches, asset routing); this
// component only owns the shell — backdrop, Escape/scroll-lock, and the responsive placement. It sits
// at z-[65]: above the sidebar/drawer (≤60) yet below BaseModal (70), so a macro preview opened from a
// result still layers on top instead of being trapped behind the palette.
export default function CommandPalette() {
  const { t } = useTranslation();
  const isOpen = useAppStore((s) => s.searchOpen);
  const close = useAppStore((s) => s.closeSearch);
  const isMobile = useMediaQuery('(max-width: 639px)');
  const { pathname } = useLocation();

  // Close on navigation. A result that routes (or "Compare" from a macro preview opened inside the palette)
  // changes the route but leaves the palette state open, so it lingered over the destination and the back
  // button couldn't dismiss it. Closing on any pathname change clears it for every navigation path.
  useEffect(() => {
    close();
  }, [pathname, close]);

  useEffect(() => {
    if (!isOpen) return undefined;
    const onKey = (e) => { if (e.key === 'Escape') close(); };
    window.addEventListener('keydown', onKey);
    const prevOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      window.removeEventListener('keydown', onKey);
      document.body.style.overflow = prevOverflow;
    };
  }, [isOpen, close]);

  return createPortal(
    <AnimatePresence>
      {isOpen && (
        <div className={`fixed inset-0 z-[65] flex flex-col items-center px-3 ${isMobile ? 'pt-[max(0.75rem,env(safe-area-inset-top))]' : 'pt-[12vh] sm:pt-[14vh]'}`}>
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="absolute inset-0 modal-overlay backdrop-blur-sm"
            onClick={close}
          />
          <motion.div
            initial={{ opacity: 0, scale: 0.97, y: -8 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.97, y: -8 }}
            transition={{ type: 'spring', stiffness: 320, damping: 30 }}
            className={`relative w-full ${isMobile ? 'max-w-none' : 'max-w-xl'}`}
          >
            {isMobile && (
              <div className="flex justify-end mb-2">
                <button
                  type="button"
                  onClick={close}
                  className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-bg-elevated border border-border-default text-[13px] font-medium text-fg-muted hover:text-fg transition-colors cursor-pointer"
                >
                  <X className="h-4 w-4" />
                  {t('common.close')}
                </button>
              </div>
            )}
            <SearchSuggestions
              variant="hero"
              autoFocus
              onAfterSelect={close}
              placeholder={t('marketOverview.searchPlaceholder')}
            />
          </motion.div>
        </div>
      )}
    </AnimatePresence>,
    document.body,
  );
}
