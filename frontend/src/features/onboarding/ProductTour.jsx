import { useCallback, useEffect, useMemo, useRef, useState, useSyncExternalStore } from 'react';
import { createPortal } from 'react-dom';
import { AnimatePresence, motion } from 'framer-motion';
import { useNavigate, useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import tourSteps from './tourSteps';
import {
  DEFAULT_PADDING,
  EASE_OUT_EXPO,
  TABLET_BREAKPOINT,
  TRANSITION_MS,
  Z_MASK,
  Z_OVERLAY,
  Z_TOOLTIP,
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
import { SummaryGrid, SummaryTitle, TooltipPointer, TooltipProgress } from './tour/TooltipParts';
import TooltipNav from './tour/TooltipNav';
import SkipButton from './tour/SkipButton';
import useTourTarget from './tour/useTourTarget';

export default function ProductTour({ open, onFinish, onSkip }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();

  const [stepIndex, setStepIndex] = useState(() => {
    try {
      const saved = parseInt(sessionStorage.getItem('onboarding:tourStep') ?? '', 10);
      return Number.isInteger(saved) && saved > 0 && saved < tourSteps.length ? saved : 0;
    } catch { return 0; }
  });
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

  // Persist the tour position so a remount — or the full-page reload keycloak.onTokenExpired triggers via
  // doLogin() — resumes the same step instead of restarting at 0. Mirrors OnboardingGate's phase persistence;
  // the key is cleared by OnboardingGate when the tour finishes.
  useEffect(() => {
    try { sessionStorage.setItem('onboarding:tourStep', String(stepIndex)); } catch { /* ignore */ }
  }, [stepIndex]);

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
    const sync = () => {
      setViewport({ w: window.innerWidth, h: window.innerHeight });
      measure();
    };
    const r1 = requestAnimationFrame(sync);
    const t1 = setTimeout(sync, 80);
    const t2 = setTimeout(sync, 200);
    return () => {
      cancelAnimationFrame(r1);
      clearTimeout(t1);
      clearTimeout(t2);
    };
  }, [open, stepIndex, measure]);


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
      onFinish?.();
      return;
    }
    directionRef.current = 'forward';
    setStepIndex((s) => {
      let next = s + 1;
      if (isMobileLayout) {
        while (next < tourSteps.length - 1 && tourSteps[next]?.skipOnMobile) {
          next += 1;
        }
      }
      return Math.min(tourSteps.length - 1, next);
    });
  }, [isLast, isMobileLayout, onFinish]);

  const handleBack = useCallback(() => {
    directionRef.current = 'back';
    setStepIndex((s) => {
      let prev = s - 1;
      if (isMobileLayout) {
        while (prev > 0 && tourSteps[prev]?.skipOnMobile) {
          prev -= 1;
        }
      }
      return Math.max(0, prev);
    });
  }, [isMobileLayout]);

  const handleSkip = useCallback(() => {
    onSkip?.();
  }, [onSkip]);

  useEffect(() => {
    if (!open) return undefined;
    const onKey = (e) => {
      if (e.key === 'Escape') {
        e.preventDefault();
        e.stopPropagation();
        handleSkip();
      } else if (e.key === 'ArrowRight') {
        handleNext();
      } else if (e.key === 'ArrowLeft') {
        handleBack();
      }
    };
    window.addEventListener('keydown', onKey, true);
    return () => window.removeEventListener('keydown', onKey, true);
  }, [open, handleSkip, handleNext, handleBack]);

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
          key={step?.id ?? stepIndex}
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

      <SkipButton t={t} onSkip={handleSkip} />

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
            left: `max(12px, min(${tooltipPos.left}px, calc(100vw - ${tooltipPos.width}px - 12px)))`,
            width: `min(${tooltipPos.width}px, calc(100vw - 24px))`,
            maxWidth: 'calc(100vw - 24px)',
            maxHeight: 'calc(100dvh - 24px)',
            zIndex: Z_TOOLTIP,
          }}
        >
          <div
            className="relative rounded-2xl border-2 border-accent/30 p-4 landscape:p-3 sm:p-5 sm:landscape:p-4 shadow-2xl shadow-black/40 ring-1 ring-accent/20 overflow-y-auto overflow-x-hidden"
            style={{ backgroundColor: tooltipTheme.bg, maxHeight: 'calc(100dvh - 24px)' }}
          >
            <TooltipPointer placement={resolvedPlacement} bg={tooltipTheme.pointerBg} />
            <span aria-hidden="true" className="absolute top-0 left-4 right-4 h-[1px] bg-gradient-to-r from-transparent via-accent/50 to-transparent rounded-t-2xl" />

            <div className="flex items-center justify-between gap-2 mb-3 landscape:mb-2">
              <span className="text-[10px] sm:text-[11px] text-accent font-medium whitespace-nowrap shrink-0">
                {counterLabel}
              </span>
              <TooltipProgress visibleSteps={visibleSteps} visibleIndex={visibleIndex} />
            </div>

            {isSummary ? (
              <SummaryTitle t={t} titleKey={step?.titleKey} />
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
                className="mt-3 landscape:mt-2 text-[13px] landscape:text-[12px] text-fg-muted leading-[1.6] landscape:leading-[1.45] text-center"
              >
                {t(step?.descKey ?? '')}
              </motion.p>
            ) : (
              <p className="mt-2 landscape:mt-1.5 text-[13px] landscape:text-[12px] text-fg-muted leading-[1.55] landscape:leading-[1.4]">
                {t(step?.descKey ?? '')}
              </p>
            )}

            {isSummary && <SummaryGrid t={t} />}
            {isSummary && (
              <motion.p
                initial={{ opacity: 0, y: 6 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: 0.7, duration: 0.45, ease: EASE_OUT_EXPO }}
                className="mt-4 landscape:mt-2.5 text-[12px] landscape:text-[11px] text-fg-muted leading-[1.55] landscape:leading-[1.4] text-center"
              >
                {t('onboarding.tour.summary.closing', {
                  defaultValue: 'Hepsi bu! Artık platformu rahatça kullanmaya başlayabilirsin.',
                })}
              </motion.p>
            )}

            <TooltipNav
              t={t}
              stepIndex={stepIndex}
              isSummary={isSummary}
              isLast={isLast}
              onBack={handleBack}
              onNext={handleNext}
            />
          </div>
        </motion.div>
        )}
      </AnimatePresence>
    </motion.div>
  );

  if (typeof document === 'undefined') return null;
  return createPortal(overlay, document.body);
}
