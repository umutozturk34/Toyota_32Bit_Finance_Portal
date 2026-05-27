import { useCallback, useEffect, useMemo, useRef, useState, useSyncExternalStore } from 'react';
import { createPortal } from 'react-dom';
import { AnimatePresence, motion } from 'framer-motion';
import { useNavigate, useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { ArrowLeft, ArrowRight, X } from 'lucide-react';
import tourSteps from './tourSteps';
import {
  DEFAULT_PADDING,
  EASE_OUT_EXPO,
  TABLET_BREAKPOINT,
  TRANSITION_MS,
  Z_MASK,
  Z_OVERLAY,
  Z_TOOLTIP,
  Z_TOP_SKIP,
} from './tour/constants';
import {
  buildArrowPath,
  computeTooltipPosition,
  tooltipEnterOffset,
  tooltipExitOffset,
} from './tour/geometry';
import {
  getTooltipThemeServerSnapshot,
  readTooltipTheme,
  subscribeTooltipTheme,
} from './tour/theme';
import { SpotlightMask, SpotlightRing } from './tour/Spotlight';
import ArrowConnector from './tour/Arrow';
import { SummaryGrid, TooltipPointer } from './tour/TooltipParts';
import useTourTarget from './tour/useTourTarget';

export default function ProductTour({ open, onFinish, onSkip }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();

  const [stepIndex, setStepIndex] = useState(0);
  const [farewellOpen, setFarewellOpen] = useState(false);
  const [tooltipSize, setTooltipSize] = useState({ width: 320, height: 200 });
  const [viewport, setViewport] = useState(() => ({
    w: typeof window !== 'undefined' ? window.innerWidth : 0,
    h: typeof window !== 'undefined' ? window.innerHeight : 0,
  }));
  const tooltipTheme = useSyncExternalStore(subscribeTooltipTheme, readTooltipTheme, getTooltipThemeServerSnapshot);
  const tooltipRef = useRef(null);
  const scrollHandleRef = useRef(0);
  const directionRef = useRef('forward');

  const isMobileLayout = viewport.w > 0 && viewport.w < TABLET_BREAKPOINT;
  const step = tourSteps[stepIndex];
  const isLast = stepIndex === tourSteps.length - 1;

  useEffect(() => {
    if (!open) return undefined;
    if (!step) return undefined;
    if (!isMobileLayout) return undefined;
    if (!step.skipOnMobile) return undefined;
    if (isLast) {
      const finishHandle = requestAnimationFrame(() => onFinish?.());
      return () => cancelAnimationFrame(finishHandle);
    }
    const advanceHandle = requestAnimationFrame(() => {
      setStepIndex((s) => {
        let next = s + 1;
        while (next < tourSteps.length - 1 && tourSteps[next]?.skipOnMobile) {
          next += 1;
        }
        return Math.min(tourSteps.length - 1, next);
      });
    });
    return () => cancelAnimationFrame(advanceHandle);
  }, [open, step, isMobileLayout, isLast, onFinish]);

  const { rect, pollingDone, measure } = useTourTarget({
    open,
    step,
    pathname: location.pathname,
    navigate,
    directionRef,
  });

  useEffect(() => {
    if (!open) return undefined;
    document.body.dataset.tourActive = '1';
    return () => {
      delete document.body.dataset.tourActive;
    };
  }, [open]);


  useEffect(() => {
    if (!open) return undefined;
    const syncViewport = () => setViewport({ w: window.innerWidth, h: window.innerHeight });
    const syncHandle = requestAnimationFrame(syncViewport);
    const onResize = () => {
      syncViewport();
      measure();
    };
    const onScroll = () => {
      if (scrollHandleRef.current) return;
      scrollHandleRef.current = requestAnimationFrame(() => {
        scrollHandleRef.current = 0;
        measure();
      });
    };
    window.addEventListener('resize', onResize);
    window.addEventListener('scroll', onScroll, true);
    return () => {
      cancelAnimationFrame(syncHandle);
      window.removeEventListener('resize', onResize);
      window.removeEventListener('scroll', onScroll, true);
      if (scrollHandleRef.current) {
        cancelAnimationFrame(scrollHandleRef.current);
        scrollHandleRef.current = 0;
      }
    };
  }, [open, measure]);

  useEffect(() => {
    if (!open) return undefined;
    const onPopState = () => {
      onSkip?.();
    };
    window.addEventListener('popstate', onPopState);
    return () => window.removeEventListener('popstate', onPopState);
  }, [open, onSkip]);

  const isSummary = step?.kind === 'summary';
  const tooltipPos = useMemo(() => {
    const placement = step?.placement ?? 'auto';
    return computeTooltipPosition(rect, placement, viewport.w, viewport.h, isSummary, tooltipSize.height);
  }, [rect, step, viewport.w, viewport.h, isSummary, tooltipSize.height]);
  const resolvedPlacement = tooltipPos.placement;

  useEffect(() => {
    if (!tooltipRef.current) return undefined;
    let cancelled = false;
    const handle = requestAnimationFrame(() => {
      if (cancelled || !tooltipRef.current) return;
      const r = tooltipRef.current.getBoundingClientRect();
      if (r.width === 0 && r.height === 0) return;
      setTooltipSize((prev) => {
        if (Math.abs(prev.width - r.width) < 1 && Math.abs(prev.height - r.height) < 1) return prev;
        return { width: r.width, height: r.height };
      });
    });
    return () => {
      cancelled = true;
      cancelAnimationFrame(handle);
    };
  }, [tooltipPos.left, tooltipPos.top, tooltipPos.width, stepIndex, viewport.w, viewport.h]);

  const padding = (step?.padding ?? DEFAULT_PADDING) + (step?.spotlightPaddingExtra ?? 4);
  const arrowPath = useMemo(() => {
    if (!rect || isSummary) return null;
    const spotlightRect = {
      left: Math.max(0, rect.left - padding),
      top: Math.max(0, rect.top - padding),
      width: rect.width + padding * 2,
      height: rect.height + padding * 2,
      right: rect.right + padding,
      bottom: rect.bottom + padding,
    };
    const tooltipBox = {
      left: tooltipPos.left,
      top: tooltipPos.top,
      width: tooltipSize.width,
      height: tooltipSize.height,
    };
    return buildArrowPath(spotlightRect, tooltipBox, resolvedPlacement);
  }, [rect, padding, tooltipPos.left, tooltipPos.top, tooltipSize.width, tooltipSize.height, resolvedPlacement, isSummary]);

  const handleNext = useCallback(() => {
    if (isLast) {
      setFarewellOpen(true);
      window.setTimeout(() => setFarewellOpen(false), 2600);
      window.setTimeout(() => onFinish?.(), 3800);
      return;
    }
    directionRef.current = 'forward';
    setStepIndex((s) => {
      let next = s + 1;
      while (
        isMobileLayout
        && next < tourSteps.length - 1
        && tourSteps[next]?.skipOnMobile
      ) {
        next += 1;
      }
      return Math.min(tourSteps.length - 1, next);
    });
  }, [isLast, isMobileLayout, onFinish]);

  const handleBack = useCallback(() => {
    directionRef.current = 'back';
    setStepIndex((s) => {
      let prev = s - 1;
      while (isMobileLayout && prev > 0 && tourSteps[prev]?.skipOnMobile) {
        prev -= 1;
      }
      return Math.max(0, prev);
    });
  }, [isMobileLayout]);

  useEffect(() => {
    if (!open) return undefined;
    const onKey = (e) => {
      if (e.key === 'Escape') {
        e.preventDefault();
        e.stopPropagation();
        onSkip?.();
      } else if (e.key === 'ArrowRight') {
        handleNext();
      } else if (e.key === 'ArrowLeft') {
        handleBack();
      }
    };
    window.addEventListener('keydown', onKey, true);
    return () => window.removeEventListener('keydown', onKey, true);
  }, [open, onSkip, handleNext, handleBack]);

  if (!open) return null;

  const visibleSteps = isMobileLayout
    ? tourSteps.filter((s) => !s.skipOnMobile)
    : tourSteps;
  const visibleIndex = Math.max(0, visibleSteps.findIndex((s) => s.id === step?.id));
  const counterLabel = t('onboarding.tour.stepLabel', {
    num: String(visibleIndex + 1),
    total: String(visibleSteps.length),
  });
  const hasTarget = !!rect;
  const targetInView = !!rect && rect.bottom > 0 && rect.top < viewport.h && rect.right > 0 && rect.left < viewport.w;

  const overlay = (
    <motion.div
      className="fixed inset-0"
      animate={{ opacity: farewellOpen ? 0 : 1 }}
      transition={{ duration: 0.55, ease: EASE_OUT_EXPO }}
      style={{ zIndex: Z_OVERLAY, isolation: 'isolate' }}
      role="dialog"
      aria-modal="true"
    >
      {!isSummary && (
        <SpotlightMask
          rect={hasTarget && targetInView ? rect : null}
          padding={padding}
          viewportW={viewport.w}
          viewportH={viewport.h}
          fill={tooltipTheme.maskFill}
        />
      )}
      {isSummary && (
        <div
          aria-hidden="true"
          className="fixed inset-0"
          style={{ zIndex: Z_MASK, backgroundColor: tooltipTheme.summaryBackdrop }}
        />
      )}
      {!isSummary && hasTarget && targetInView && (
        <SpotlightRing
          rect={rect}
          padding={padding}
          viewportW={viewport.w}
          viewportH={viewport.h}
        />
      )}
      {!isSummary && targetInView && (
        <ArrowConnector
          path={arrowPath}
          viewportW={viewport.w}
          viewportH={viewport.h}
          stroke={tooltipTheme.arrowStroke}
          halo={tooltipTheme.arrowHalo}
        />
      )}

      <button
        type="button"
        onClick={onSkip}
        className="fixed top-3 right-3 sm:top-5 sm:right-5 inline-flex items-center gap-1.5 rounded-lg border border-border-default bg-bg-elevated/95 px-3 sm:px-2.5 py-2 sm:py-1.5 min-h-[40px] sm:min-h-0 text-[11px] text-fg-muted backdrop-blur-md transition-colors hover:text-fg hover:border-accent/40 cursor-pointer"
        style={{ zIndex: Z_TOP_SKIP }}
      >
        <X className="h-3 w-3" />
        {t('onboarding.skip')}
      </button>

      <AnimatePresence mode="wait">
        {(isSummary || !step?.selector || pollingDone) && (
        <motion.div
          key={step?.id ?? stepIndex}
          ref={tooltipRef}
          initial={{ opacity: 0, scale: 0.92, ...tooltipEnterOffset(resolvedPlacement) }}
          animate={{ opacity: 1, scale: 1, x: 0, y: 0 }}
          exit={{ opacity: 0, scale: 0.96, ...tooltipExitOffset(resolvedPlacement) }}
          transition={{
            opacity: { duration: TRANSITION_MS / 1000, ease: EASE_OUT_EXPO },
            scale: { duration: TRANSITION_MS / 1000, ease: EASE_OUT_EXPO },
            x: { duration: TRANSITION_MS / 1000, ease: EASE_OUT_EXPO },
            y: { duration: TRANSITION_MS / 1000, ease: EASE_OUT_EXPO },
          }}
          style={{
            position: 'fixed',
            top: tooltipPos.top,
            left: tooltipPos.left,
            width: tooltipPos.width,
            zIndex: Z_TOOLTIP,
          }}
        >
          <div
            className="relative rounded-2xl border-2 border-accent/30 p-5 shadow-2xl shadow-black/40 ring-1 ring-accent/20"
            style={{ backgroundColor: tooltipTheme.bg }}
          >
            <TooltipPointer placement={resolvedPlacement} bg={tooltipTheme.pointerBg} />
            <span aria-hidden="true" className="absolute top-0 left-4 right-4 h-[1px] bg-gradient-to-r from-transparent via-accent/50 to-transparent rounded-t-2xl" />

            <div className="flex items-center justify-between mb-3">
              <span className="text-[11px] text-accent font-medium">
                {counterLabel}
              </span>
              <div className="flex items-center gap-1">
                {visibleSteps.map((s, i) => {
                  const active = i === visibleIndex;
                  const done = i < visibleIndex;
                  const baseCls = 'h-1 rounded-full block';
                  const fillCls = active
                    ? 'bg-gradient-accent'
                    : done
                      ? 'bg-accent/50'
                      : 'bg-border-default';
                  return (
                    <motion.span
                      key={s.id}
                      layout
                      aria-hidden="true"
                      className={`${baseCls} ${fillCls}`}
                      animate={
                        active
                          ? {
                              width: 18,
                              boxShadow: [
                                '0 0 0 0 rgba(99,102,241,0.0)',
                                '0 0 10px 1px rgba(99,102,241,0.55)',
                                '0 0 0 0 rgba(99,102,241,0.0)',
                              ],
                            }
                          : { width: 4, boxShadow: '0 0 0 0 rgba(0,0,0,0)' }
                      }
                      transition={
                        active
                          ? {
                              width: { duration: 0.35, ease: EASE_OUT_EXPO },
                              boxShadow: { duration: 1.8, ease: 'easeInOut', repeat: Infinity },
                            }
                          : { width: { duration: 0.35, ease: EASE_OUT_EXPO } }
                      }
                    />
                  );
                })}
              </div>
            </div>

            {isSummary ? (
              <div className="relative flex items-center justify-center">
                <motion.span
                  aria-hidden="true"
                  className="absolute -left-1 -top-1 text-base"
                  animate={{
                    scale: [0, 1.2, 0.8, 1.2, 0],
                    rotate: [0, 90, 180, 270, 360],
                    opacity: [0, 1, 1, 1, 0],
                  }}
                  transition={{ delay: 0.4, duration: 2.4, repeat: Infinity, repeatDelay: 1.6, ease: 'easeInOut' }}
                >
                  ✨
                </motion.span>
                <motion.span
                  aria-hidden="true"
                  className="absolute -right-2 top-1 text-sm"
                  animate={{
                    scale: [0, 1.1, 0.7, 1.1, 0],
                    rotate: [0, -90, -180, -270, -360],
                    opacity: [0, 1, 1, 1, 0],
                  }}
                  transition={{ delay: 0.9, duration: 2.4, repeat: Infinity, repeatDelay: 1.6, ease: 'easeInOut' }}
                >
                  ✨
                </motion.span>
                <motion.h3
                  initial={{ opacity: 0, y: -10, scale: 0.9 }}
                  animate={{
                    opacity: 1,
                    y: 0,
                    scale: 1,
                    backgroundPosition: ['0% 50%', '200% 50%'],
                  }}
                  transition={{
                    opacity: { duration: 0.55, ease: EASE_OUT_EXPO },
                    y: { duration: 0.55, ease: EASE_OUT_EXPO },
                    scale: { duration: 0.55, ease: EASE_OUT_EXPO },
                    backgroundPosition: { delay: 0.6, duration: 3.6, repeat: Infinity, ease: 'linear' },
                  }}
                  className="font-display text-2xl sm:text-3xl font-bold leading-tight tracking-tight text-center bg-gradient-to-r from-accent via-accent-bright via-fuchsia-400 via-accent-bright to-accent bg-clip-text text-transparent"
                  style={{ backgroundSize: '200% 100%' }}
                >
                  {t(step?.titleKey ?? '')}
                </motion.h3>
              </div>
            ) : (
              <h3 className="font-display text-[17px] font-semibold text-fg leading-tight tracking-tight">
                {t(step?.titleKey ?? '')}
              </h3>
            )}
            {isSummary ? (
              <motion.p
                initial={{ opacity: 0, y: 8 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: 0.14, duration: 0.4, ease: EASE_OUT_EXPO }}
                className="mt-3 text-[13px] text-fg-muted leading-[1.6] text-center"
              >
                {t(step?.descKey ?? '')}
              </motion.p>
            ) : (
              <p className="mt-2 text-[13px] text-fg-muted leading-[1.55]">
                {t(step?.descKey ?? '')}
              </p>
            )}

            {isSummary && <SummaryGrid t={t} />}
            {isSummary && (
              <motion.p
                initial={{ opacity: 0, y: 6 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: 0.7, duration: 0.45, ease: EASE_OUT_EXPO }}
                className="mt-4 text-[12px] text-fg-muted leading-[1.55] text-center"
              >
                {t('onboarding.tour.summary.closing', {
                  defaultValue: 'Hepsi bu! Artık platformu rahatça kullanmaya başlayabilirsin.',
                })}
              </motion.p>
            )}

            <div className="mt-5 flex items-center justify-between gap-3">
              <button
                type="button"
                onClick={handleBack}
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
                onClick={handleNext}
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
          </div>
        </motion.div>
        )}
      </AnimatePresence>

      <AnimatePresence>
        {farewellOpen && (
          <motion.div
            key="tour-farewell"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0, transition: { duration: 1.1, ease: [0.4, 0, 0.4, 1] } }}
            transition={{ duration: 0.6, ease: EASE_OUT_EXPO }}
            className="fixed inset-0 flex items-center justify-center"
            style={{ zIndex: Z_TOP_SKIP + 1, backgroundColor: tooltipTheme.summaryBackdrop }}
            aria-hidden="true"
          >
            <motion.div
              initial={{ opacity: 0, scale: 0.85, y: 24 }}
              animate={{ opacity: 1, scale: 1, y: 0 }}
              exit={{ opacity: 0, scale: 1.02, y: -8, transition: { duration: 1.1, ease: [0.4, 0, 0.4, 1] } }}
              transition={{ duration: 1.0, ease: EASE_OUT_EXPO }}
              className="relative flex flex-col items-center gap-3 px-8"
            >
              <motion.span
                aria-hidden="true"
                className="absolute -top-8 text-3xl"
                initial={{ scale: 0, rotate: 0, opacity: 0 }}
                animate={{ scale: [0, 1.3, 1], rotate: [0, 180, 360], opacity: [0, 1, 1] }}
                transition={{ duration: 1.2, ease: 'easeOut', delay: 0.2 }}
              >
                ✨
              </motion.span>
              <motion.h2
                className="text-4xl sm:text-6xl font-display font-bold tracking-tight text-center bg-gradient-to-r from-accent via-accent-bright via-fuchsia-400 via-accent-bright to-accent bg-clip-text text-transparent"
                style={{ backgroundSize: '200% 100%' }}
                animate={{ backgroundPosition: ['0% 50%', '200% 50%'] }}
                transition={{ duration: 3.2, repeat: Infinity, ease: 'linear' }}
              >
                {t('onboarding.tour.farewell.title', { defaultValue: 'Hoş geldin' })}
              </motion.h2>
              <motion.p
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: 0.6, duration: 0.8, ease: EASE_OUT_EXPO }}
                className="text-base sm:text-lg text-fg-muted text-center max-w-md leading-relaxed"
              >
                {t('onboarding.tour.farewell.subtitle', {
                  defaultValue: 'Keyifli takipler dileriz, hep yanındayız.',
                })}
              </motion.p>
              <motion.div
                aria-hidden="true"
                className="mt-4 h-[2px] w-32 rounded-full bg-gradient-to-r from-transparent via-accent to-transparent"
                initial={{ scaleX: 0, opacity: 0 }}
                animate={{ scaleX: 1, opacity: 1 }}
                transition={{ delay: 1.1, duration: 0.9, ease: EASE_OUT_EXPO }}
              />
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>
    </motion.div>
  );

  if (typeof document === 'undefined') return null;
  return createPortal(overlay, document.body);
}
