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
  // True once the user manually wheels/touches during a step — the settle routine then stops auto-scrolling so
  // it never yanks the page back up to the spotlight target while the user is reading/scrolling further down.
  const userScrolledRef = useRef(false);

  useEffect(() => {
    stepIdRef.current = step?.id ?? null;
  }, [step]);
  const candidatesRef = useRef([]);

  // Detect a MANUAL scroll (wheel/touch only — a programmatic scrollIntoView never fires these), reset per step,
  // so settleAndCommit can yield to the user instead of fighting them back to the target ("jumps to the top
  // while scrolling"). The first auto-scroll of each new step still runs because this resets to false on entry.
  useEffect(() => {
    userScrolledRef.current = false;
    if (!open) return undefined;
    const mark = () => { userScrolledRef.current = true; };
    window.addEventListener('wheel', mark, { passive: true });
    window.addEventListener('touchmove', mark, { passive: true });
    return () => {
      window.removeEventListener('wheel', mark);
      window.removeEventListener('touchmove', mark);
    };
  }, [open, step]);

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

    // After committing, keep the spotlight aligned to the target as late content (e.g. lazy-loaded news images)
    // reflows it — but DEBOUNCED and THRESHOLDED so minor shifts don't re-commit every frame, which used to make
    // the ring + tooltip jitter continuously ("loop") on image-heavy pages.
    const attachResizeObserver = (element) => {
      if (resizeObserverRef.current) {
        resizeObserverRef.current.disconnect();
        resizeObserverRef.current = null;
      }
      if (typeof ResizeObserver === 'undefined') return;
      let committed = element.getBoundingClientRect();
      let debounce = 0;
      const ro = new ResizeObserver(() => {
        if (cancelled) return;
        if (debounce) cancelAnimationFrame(debounce);
        debounce = requestAnimationFrame(() => {
          if (cancelled) return;
          const r2 = element.getBoundingClientRect();
          if (r2.width <= 0 || r2.height <= 0) return;
          const moved = Math.abs(r2.top - committed.top) > 6 || Math.abs(r2.left - committed.left) > 6
            || Math.abs(r2.width - committed.width) > 6 || Math.abs(r2.height - committed.height) > 6;
          if (moved) {
            committed = r2;
            setRectInfo({ rect: cushionRect(r2), stepId: step.id, status: 'found' });
          }
        });
      });
      ro.observe(element);
      resizeObserverRef.current = ro;
    };

    // Bring the target into view, then commit the spotlight rect once it stops moving. The scroll is issued ONCE,
    // up-front, as a smooth glide (not gated on the rect first being stable — that deadlocked on dynamic pages
    // like the news grid where loading images meant the rect never settled, so the page never scrolled). We then
    // wait out the smooth scroll, settle for two stable frames, and commit. A fixed-ancestor target (a sliding
    // drawer) is waited out, never scrolled.
    const SCROLL_WAIT_FRAMES = 32; // ~530ms — long enough for a smooth scrollIntoView to finish
    const settleAndCommit = (el) => {
      const fixedAncestor = isInFixedAncestor(el);
      const canScroll = !fixedAncestor && typeof el.scrollIntoView === 'function';
      settlingRef.current = true;
      let settleFrames = 0;
      let stable = 0;
      let last = null;
      let scrollCount = 0;
      let scrolledAt = -1;
      const tick = () => {
        if (cancelled) { settlingRef.current = false; return; }
        const r = el.getBoundingClientRect();
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

        const vh = window.innerHeight || document.documentElement.clientHeight || 0;
        // Out of view = top above the fold, or bottom below it while not already pinned to the top (so a target
        // taller than the viewport, scrolled to start, isn't seen as perpetually "out of view").
        const outOfView = vh && (r.top < -1 || (r.bottom > vh && r.top > 1));
        const scrolling = scrolledAt >= 0 && (settleFrames - scrolledAt) < SCROLL_WAIT_FRAMES;

        if (scrolling) { // let the smooth scroll play out before measuring stability
          settleFrames += 1;
          pollHandleRef.current = requestAnimationFrame(tick);
          return;
        }
        if (canScroll && outOfView && scrollCount < 2 && !userScrolledRef.current) { // glide once; never fight a manual scroll
          el.scrollIntoView({ block: r.height > vh - 80 ? 'start' : 'center', behavior: 'smooth' });
          scrollCount += 1;
          scrolledAt = settleFrames;
          last = null;
          settleFrames += 1;
          pollHandleRef.current = requestAnimationFrame(tick);
          return;
        }

        const steady = last
          && Math.abs(r.top - last.top) < 1 && Math.abs(r.left - last.left) < 1
          && Math.abs(r.width - last.width) < 1 && Math.abs(r.height - last.height) < 1;
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
