import { useCallback, useEffect, useRef, useState } from 'react';
import {
  ACTION_SETTLE_MS,
  CLOSE_SETTLE_MS,
  POLL_MAX_FRAMES,
  POLL_MAX_FRAMES_SLOW,
  RECT_CUSHION,
  ROUTE_SETTLE_MS,
} from './constants';

function isInFixedAncestor(el) {
  let node = el;
  while (node && node !== document.body && node.nodeType === 1) {
    const pos = window.getComputedStyle(node).position;
    if (pos === 'fixed' || pos === 'sticky') return true;
    node = node.parentElement;
  }
  return false;
}

function hasAnimatingAncestor(el) {
  if (typeof Element === 'undefined' || typeof Element.prototype.getAnimations !== 'function') return false;
  let node = el;
  while (node && node !== document.body && node.nodeType === 1) {
    const animations = node.getAnimations();
    for (const a of animations) {
      if (a.playState === 'running') return true;
    }
    node = node.parentElement;
  }
  return false;
}

function cushionRect(r) {
  return {
    top: r.top - RECT_CUSHION,
    left: r.left - RECT_CUSHION,
    width: r.width + RECT_CUSHION * 2,
    height: r.height + RECT_CUSHION * 2,
    bottom: r.bottom + RECT_CUSHION,
    right: r.right + RECT_CUSHION,
  };
}

function clickSelector(sel) {
  const el = document.querySelector(sel);
  if (el && typeof el.click === 'function') {
    el.click();
  }
}

function clickSelectors(selectorOrList) {
  const list = Array.isArray(selectorOrList) ? selectorOrList : [selectorOrList];
  for (const sel of list) clickSelector(sel);
}

export default function useTourTarget({ open, step, pathname, navigate, directionRef }) {
  const [rectInfo, setRectInfo] = useState({ rect: null, stepId: null, status: 'idle' });
  const pollHandleRef = useRef(0);
  const targetRef = useRef(null);
  const prevCloseSelectorRef = useRef(null);
  const stepIdRef = useRef(null);
  const resizeObserverRef = useRef(null);

  useEffect(() => {
    stepIdRef.current = step?.id ?? null;
  }, [step]);
  const candidatesRef = useRef([]);

  const measure = useCallback(() => {
    let el = targetRef.current;
    if (el && !document.body.contains(el)) {
      el = null;
      targetRef.current = null;
    }
    if (!el) {
      for (const sel of candidatesRef.current) {
        const matches = document.querySelectorAll(sel);
        for (const candidate of matches) {
          const cr = candidate.getBoundingClientRect();
          if (cr.width > 0 && cr.height > 0) {
            el = candidate;
            targetRef.current = candidate;
            break;
          }
        }
        if (el) break;
      }
    }
    if (!el) return;
    const r = el.getBoundingClientRect();
    if (r.width === 0 && r.height === 0) return;
    if (hasAnimatingAncestor(el)) return;
    setRectInfo({ rect: cushionRect(r), stepId: stepIdRef.current, status: 'found' });
  }, []);

  useEffect(() => {
    if (!open) return undefined;
    if (!step) return undefined;

    const closePrev = prevCloseSelectorRef.current;
    prevCloseSelectorRef.current = step.closeSelector ?? null;

    if (closePrev) {
      clickSelectors(closePrev);
    }

    const needsNavigate = step.path && pathname !== step.path;
    if (needsNavigate) {
      navigate(step.path);
    }

    const skipScrollReset = !needsNavigate && !step.clickSelector;
    if (!skipScrollReset && typeof window !== 'undefined' && window.scrollTo) {
      window.scrollTo({ top: 0, left: 0, behavior: 'instant' });
    }

    let cancelled = false;
    let frames = 0;
    let routeTimer = 0;
    let closeTimer = 0;
    let actionTimer = 0;

    targetRef.current = null;

    const candidates = Array.isArray(step.selector)
      ? step.selector
      : (step.selector ? [step.selector] : []);
    candidatesRef.current = candidates;
    stepIdRef.current = step.id;

    const isTargetVisible = () => {
      for (const sel of candidates) {
        const found = document.querySelector(sel);
        if (found) {
          const r = found.getBoundingClientRect();
          if (r.width > 0 && r.height > 0) return true;
        }
      }
      return false;
    };

    const performAction = (onDone) => {
      if (!step.clickSelector) { onDone?.(); return; }
      if (isTargetVisible()) { onDone?.(); return; }
      const triggers = Array.isArray(step.clickSelector) ? step.clickSelector : [step.clickSelector];
      let idx = 0;
      const clickNext = () => {
        if (cancelled) return;
        if (idx >= triggers.length) { onDone?.(); return; }
        const sel = triggers[idx];
        idx += 1;
        clickSelector(sel);
        if (idx < triggers.length) {
          window.setTimeout(clickNext, ACTION_SETTLE_MS);
        } else {
          onDone?.();
        }
      };
      clickNext();
    };

    if (!step.selector) {
      candidatesRef.current = [];
      return () => {
        cancelled = true;
        cancelAnimationFrame(pollHandleRef.current);
      };
    }

    const maxFrames = step.slowLoad ? POLL_MAX_FRAMES_SLOW : POLL_MAX_FRAMES;

    const tryFind = () => {
      if (cancelled) return;
      let el = null;
      for (const sel of candidates) {
        const matches = document.querySelectorAll(sel);
        for (const candidate of matches) {
          const cr = candidate.getBoundingClientRect();
          if (cr.width > 0 && cr.height > 0) {
            el = candidate;
            break;
          }
        }
        if (!el && matches.length > 0) {
          el = matches[0];
        }
        if (el) break;
      }
      if (el) {
        targetRef.current = el;
        const r = el.getBoundingClientRect();
        if (r.width < 4 || r.height < 8) {
          frames += 1;
          if (frames >= maxFrames) {
            targetRef.current = null;
            setRectInfo({ rect: null, stepId: step.id, status: 'failed' });
            return;
          }
          pollHandleRef.current = requestAnimationFrame(tryFind);
          return;
        }
        const viewportH = window.innerHeight || document.documentElement.clientHeight || 0;
        const viewportW = window.innerWidth || document.documentElement.clientWidth || 0;
        const fixedAncestor = isInFixedAncestor(el);
        const fullyVisible = r.top >= 0 && r.bottom <= viewportH;
        const isSmallViewport = viewportW < 1024;
        const tallerThanViewport = r.height > viewportH - 80;
        const preferStartBlock = tallerThanViewport || (isSmallViewport && r.height > viewportH * 0.4);
        const needsScroll = !fullyVisible
          && !fixedAncestor
          && typeof el.scrollIntoView === 'function';
        const attachResizeObserver = (element) => {
          if (resizeObserverRef.current) {
            resizeObserverRef.current.disconnect();
            resizeObserverRef.current = null;
          }
          if (typeof ResizeObserver === 'undefined') return;
          const ro = new ResizeObserver(() => {
            if (cancelled) return;
            const r2 = element.getBoundingClientRect();
            if (r2.width > 0 && r2.height > 0) {
              setRectInfo({ rect: cushionRect(r2), stepId: step.id, status: 'found' });
            }
          });
          ro.observe(element);
          resizeObserverRef.current = ro;
        };
        const scheduleResizeCatch = () => {
          attachResizeObserver(el);
          actionTimer = window.setTimeout(() => {
            if (cancelled) return;
            const finalR = el.getBoundingClientRect();
            if (finalR.width > 0 && finalR.height > 0) {
              setRectInfo({ rect: cushionRect(finalR), stepId: step.id, status: 'found' });
            }
          }, 360);
        };
        if (needsScroll) {
          el.scrollIntoView({ block: preferStartBlock ? 'start' : 'center', behavior: 'instant' });
          const settled = el.getBoundingClientRect();
          setRectInfo({ rect: cushionRect(settled), stepId: step.id, status: 'found' });
          scheduleResizeCatch();
          return;
        }
        if (fixedAncestor) {
          actionTimer = window.setTimeout(() => {
            if (cancelled) return;
            requestAnimationFrame(() => {
              if (cancelled) return;
              const settled = el.getBoundingClientRect();
              if (settled.width > 0 && settled.height > 0) {
                setRectInfo({ rect: cushionRect(settled), stepId: step.id, status: 'found' });
              } else {
                setRectInfo({ rect: null, stepId: step.id, status: 'failed' });
              }
            });
          }, 420);
          return;
        }
        setRectInfo({ rect: cushionRect(r), stepId: step.id, status: 'found' });
        scheduleResizeCatch();
        return;
      }
      frames += 1;
      if (frames >= maxFrames) {
        targetRef.current = null;
        setRectInfo({ rect: null, stepId: step.id, status: 'failed' });
        return;
      }
      pollHandleRef.current = requestAnimationFrame(tryFind);
    };

    const startPolling = () => {
      if (cancelled) return;
      pollHandleRef.current = requestAnimationFrame(tryFind);
    };

    const runAfterAction = () => {
      if (cancelled) return;
      if (step.clickSelector) {
        performAction(() => {
          actionTimer = window.setTimeout(startPolling, ACTION_SETTLE_MS);
        });
      } else {
        startPolling();
      }
    };

    const runAfterRoute = () => {
      if (cancelled) return;
      if (needsNavigate) {
        routeTimer = window.setTimeout(runAfterAction, ROUTE_SETTLE_MS);
      } else {
        runAfterAction();
      }
    };

    if (closePrev) {
      closeTimer = window.setTimeout(runAfterRoute, CLOSE_SETTLE_MS);
    } else {
      runAfterRoute();
    }

    return () => {
      cancelled = true;
      if (routeTimer) window.clearTimeout(routeTimer);
      if (closeTimer) window.clearTimeout(closeTimer);
      if (actionTimer) window.clearTimeout(actionTimer);
      cancelAnimationFrame(pollHandleRef.current);
      if (resizeObserverRef.current) {
        resizeObserverRef.current.disconnect();
        resizeObserverRef.current = null;
      }
    };
  }, [open, step, pathname, navigate, directionRef]);

  useEffect(() => {
    if (!open) return undefined;
    return () => {
      const lingering = prevCloseSelectorRef.current;
      prevCloseSelectorRef.current = null;
      if (lingering) {
        clickSelectors(lingering);
      }
    };
  }, [open]);

  const currentStepId = step?.id ?? null;
  const rect = rectInfo.stepId === currentStepId && rectInfo.status === 'found' ? rectInfo.rect : null;
  const pollingDone = rectInfo.stepId === currentStepId && rectInfo.status !== 'idle';
  return { rect, pollingDone, measure };
}
