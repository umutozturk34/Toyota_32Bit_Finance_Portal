import { motion } from 'framer-motion';
import { ArrowLeft, ArrowRight } from 'lucide-react';
import { EASE_OUT_EXPO } from './constants';

export default function TooltipNav({ t, stepIndex, isSummary, isLast, onBack, onNext }) {
  return (
    <div className="mt-5 landscape:mt-3 flex items-center justify-between gap-3">
      <button
        type="button"
        onClick={onBack}
        disabled={stepIndex === 0}
        className={`inline-flex items-center gap-1 px-2 min-h-[44px] sm:min-h-0 text-[12px] font-medium transition-colors bg-transparent border-none cursor-pointer ${
          stepIndex === 0
            ? 'invisible'
            : 'text-fg-muted hover:text-fg'
        }`}
      >
        <ArrowLeft className="h-3.5 w-3.5" />
        {t('onboarding.back')}
      </button>

      <motion.button
        type="button"
        onClick={onNext}
        initial={isSummary ? { opacity: 0, y: 10, scale: 0.92 } : false}
        animate={isSummary
          ? {
              opacity: 1,
              y: 0,
              scale: 1,
              boxShadow: [
                '0 6px 18px -4px rgba(99,102,241,0.4)',
                '0 12px 36px -4px rgba(99,102,241,0.7)',
                '0 6px 18px -4px rgba(99,102,241,0.4)',
              ],
            }
          : undefined}
        whileHover={{
          y: -2,
          scale: isSummary ? 1.05 : 1,
          boxShadow: '0 14px 32px -8px rgba(99,102,241,0.65), 0 4px 12px -2px rgba(99,102,241,0.45)',
        }}
        whileTap={{ y: 0, scale: 0.97 }}
        transition={isSummary
          ? {
              opacity: { delay: 0.95, duration: 0.4, ease: EASE_OUT_EXPO },
              y: { delay: 0.95, duration: 0.4, ease: EASE_OUT_EXPO },
              scale: { delay: 0.95, duration: 0.4, ease: EASE_OUT_EXPO },
              boxShadow: { delay: 1.4, duration: 2.4, repeat: Infinity, ease: 'easeInOut' },
            }
          : { type: 'spring', stiffness: 360, damping: 26 }}
        className={`relative inline-flex items-center gap-1.5 overflow-hidden rounded-lg bg-gradient-accent ${isSummary ? 'px-5 py-3 text-[13px]' : 'px-4 py-2.5 sm:py-2 text-[12px]'} min-h-[44px] sm:min-h-0 font-semibold text-white shadow-md shadow-accent/25 border-none cursor-pointer`}
      >
        <span
          aria-hidden="true"
          className="pointer-events-none absolute inset-0 rounded-lg shimmer opacity-70 mix-blend-screen"
        />
        <span className="relative z-[1] inline-flex items-center gap-1.5">
          {isSummary
            ? t('onboarding.tour.summary.cta', { defaultValue: 'Başla' })
            : isLast
              ? t('onboarding.tour.finish')
              : t('onboarding.continue')}
          <motion.span
            aria-hidden="true"
            animate={isSummary ? { x: [0, 4, 0] } : undefined}
            transition={isSummary ? { duration: 1.4, repeat: Infinity, ease: 'easeInOut' } : undefined}
            className="inline-flex"
          >
            <ArrowRight className="h-3.5 w-3.5" />
          </motion.span>
        </span>
      </motion.button>
    </div>
  );
}
