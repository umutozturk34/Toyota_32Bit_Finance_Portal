import { useCallback, useEffect, useRef, useState } from 'react';
import {
  ACTION_SETTLE_MS,
  CLOSE_SETTLE_MS,
  POLL_MAX_FRAMES,
  POLL_MAX_FRAMES_SLOW,
  RECT_CUSHION,
  ROUTE_SETTLE_MS,
  SETTLE_MAX_FRAMES,
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
  // True while a step's rect is still settling (scrolling into view / a drawer sliding in). External re-measures
  // (scroll, resize, the post-step rAF syncs) must NOT commit a half-settled rect during this window, or the ring
  // flashes at an intermediate position — the settle routine is the sole writer until it stabilises.
  const settlingRef = useRef(false);

  useEffect(() => {
    stepIdRef.current = step?.id ?? null;
  }, [step]);
  const candidatesRef = useRef([]);

  const measure = useCallback(() => {
    if (settlingRef.current) return;
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
    settlingRef.current = false;

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

    // Commit the spotlight rect only once it stops moving. Two motions settle here: scrolling a below-the-fold
    // target into view (re-scrolled each frame, since slow content above it — e.g. the chart — keeps pushing it
    // as it loads), and a drawer sliding in from the right (a fixed target is waited out, never scrolled). This
    // is why the ring no longer flashes at an intermediate position (mid-slide off to the right) and snaps left:
    // nothing is committed until the rect is stable for two frames. A static target settles immediately.
    const settleAndCommit = (el) => {
      const fixedAncestor = isInFixedAncestor(el);
      settlingRef.current = true;
      let settleFrames = 0;
      let stable = 0;
      let last = null;
      const tick = () => {
        if (cancelled) { settlingRef.current = false; return; }
        let r = el.getBoundingClientRect();
        if (r.width < 4 || r.height < 8) {
          settleFrames += 1;
          if (settleFrames >= SETTLE_MAX_FRAMES) {
            settlingRef.current = false;
            setRectInfo({ rect: null, stepId: step.id, status: 'failed' });
            return;
          }
          pollHandleRef.current = requestAnimationFrame(tick);
          return;
        }
        const steady = last
          && Math.abs(r.top - last.top) < 1 && Math.abs(r.left - last.left) < 1
          && Math.abs(r.width - last.width) < 1 && Math.abs(r.height - last.height) < 1;
        // Smooth-scroll a below/above-fold target into view, but only once the rect has SETTLED (not mid-scroll)
        // so the scroll plays out as one smooth glide instead of the old instant jump re-issued every frame. Skip
        // when scrolling can't help (a target taller than the viewport, already pinned to the top). If late
        // content (e.g. the chart) later pushes it out, the next settled frame nudges it again, still smoothly.
        if (steady && !fixedAncestor && typeof el.scrollIntoView === 'function') {
          const vh = window.innerHeight || document.documentElement.clientHeight || 0;
          const needsScroll = vh && (r.top < -1 || (r.bottom > vh && r.top > 1));
          if (needsScroll && settleFrames < SETTLE_MAX_FRAMES) {
            el.scrollIntoView({ block: r.height > vh - 80 ? 'start' : 'center', behavior: 'smooth' });
            stable = 0;
            last = r;
            settleFrames += 1;
            pollHandleRef.current = requestAnimationFrame(tick);
            return;
          }
        }
        stable = steady ? stable + 1 : 0;
        last = r;
        if (stable >= 2 || settleFrames >= SETTLE_MAX_FRAMES) {
          settlingRef.current = false;
          setRectInfo({ rect: cushionRect(r), stepId: step.id, status: 'found' });
          attachResizeObserver(el);
          return;
        }
        settleFrames += 1;
        pollHandleRef.current = requestAnimationFrame(tick);
      };
      pollHandleRef.current = requestAnimationFrame(tick);
    };

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
        settleAndCommit(el);
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
      settlingRef.current = false;
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
