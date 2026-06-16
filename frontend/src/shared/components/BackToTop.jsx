import { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { ArrowUp } from 'lucide-react';
import { useTranslation } from 'react-i18next';

// Global "scroll to top" affordance: the app scrolls the window (see useScrollRestoration), so this watches
// window.scrollY and, once past a threshold, fades in a fixed button that smooth-scrolls back to the top.
// Mounted once at the layout root so every page gets it. z below modals/drawers (z-50) so it never covers them.
export default function BackToTop({ threshold = 500 }) {
  const { t } = useTranslation();
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    const onScroll = () => setVisible(window.scrollY > threshold);
    onScroll();
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => window.removeEventListener('scroll', onScroll);
  }, [threshold]);

  const label = t('common.backToTop', { defaultValue: 'Back to top' });

  return (
    <AnimatePresence>
      {visible && (
        <motion.button
          type="button"
          initial={{ opacity: 0, y: 12, scale: 0.9 }}
          animate={{ opacity: 1, y: 0, scale: 1 }}
          exit={{ opacity: 0, y: 12, scale: 0.9 }}
          transition={{ duration: 0.2, ease: [0.16, 1, 0.3, 1] }}
          onClick={() => window.scrollTo({ top: 0, behavior: 'smooth' })}
          aria-label={label}
          title={label}
          className="fixed bottom-5 right-5 z-40 flex h-11 w-11 items-center justify-center rounded-full border border-border-default bg-bg-elevated/90 text-accent shadow-lg backdrop-blur transition-all hover:-translate-y-0.5 hover:border-accent/50 hover:bg-bg-elevated cursor-pointer"
        >
          <ArrowUp className="h-5 w-5" strokeWidth={2.2} />
        </motion.button>
      )}
    </AnimatePresence>
  );
}
