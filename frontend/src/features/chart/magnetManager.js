const __DEV__ = import.meta.env?.DEV ?? false;
const timeToNum = (t) => {
  if (t && typeof t === 'object' && t.year != null) {
    return t.year * 10000 + t.month * 100 + t.day;
  }
  return Number(t);
};
const sanitizeCandles = (arr) => {
  if (!arr || arr.length === 0) return [];
  return arr
    .map(c => ({
      time: timeToNum(c.time),
      rawTime: c.time,
      open: Number(c.open),
      high: Number(c.high),
      low: Number(c.low),
      close: Number(c.close),
    }))
    .filter(c =>
      !isNaN(c.time) && !isNaN(c.open) &&
      !isNaN(c.high) && !isNaN(c.low) && !isNaN(c.close)
    );
};
const findNearestCandle = (candles, targetTime) => {
  if (!candles || candles.length === 0) return null;
  const len = candles.length;
  let lo = 0;
  let hi = len - 1;
  if (targetTime <= candles[0].time) return { index: 0, candle: candles[0] };
  if (targetTime >= candles[hi].time) return { index: hi, candle: candles[hi] };
  while (lo <= hi) {
    const mid = (lo + hi) >>> 1;
    const t = candles[mid].time;
    if (t === targetTime) return { index: mid, candle: candles[mid] };
    if (t < targetTime) lo = mid + 1;
    else hi = mid - 1;
  }
  const prev = lo - 1;
  if (lo >= len) return { index: prev, candle: candles[prev] };
  if (prev < 0) return { index: lo, candle: candles[lo] };
  const diffLo = Math.abs(candles[lo].time - targetTime);
  const diffPrev = Math.abs(candles[prev].time - targetTime);
  return diffPrev <= diffLo
    ? { index: prev, candle: candles[prev] }
    : { index: lo, candle: candles[lo] };
};
const snapToOHLC = (candle, cursorPrice, mode) => {
  const candidates =
    mode === 'strong'
      ? [candle.open, candle.high, candle.low, candle.close]
      : [candle.high, candle.low]; 
  let best = candidates[0];
  let bestDist = Math.abs(cursorPrice - best);
  for (let i = 1; i < candidates.length; i++) {
    const d = Math.abs(cursorPrice - candidates[i]);
    if (d < bestDist) {
      bestDist = d;
      best = candidates[i];
    }
  }
  return { price: best, distance: bestDist };
};
const ensureSorted = (arr) => {
  if (!arr || arr.length <= 1) return arr || [];
  for (let i = 1; i < arr.length; i++) {
    if (arr[i].time < arr[i - 1].time) {
      return arr.slice().sort((a, b) => a.time - b.time);
    }
  }
  return arr; 
};
const createEmitter = () => {
  const listeners = {};
  return {
    on(event, fn) {
      (listeners[event] ||= []).push(fn);
      return () => {
        listeners[event] = listeners[event].filter(f => f !== fn);
      };
    },
    emit(event, payload) {
      const fns = listeners[event];
      if (fns) for (let i = 0; i < fns.length; i++) fns[i](payload);
    },
    removeAll() {
      Object.keys(listeners).forEach(k => delete listeners[k]);
    },
  };
};
export const createMagnetManager = (config = {}) => {
  let candles = ensureSorted(sanitizeCandles(config.candles || []));
  let toPixel = config.toPixel || (() => null);
  let fromPixel = config.fromPixel || (() => null);
  let mode = 'off';       
  let enabled = false;     
  let rafId = null;
  let lastMouse = null;    
  let lastSnap = null;     
  let destroyed = false;
  const emitter = createEmitter();
  const shouldSnap = () =>
    !destroyed && mode !== 'off' && enabled && candles.length > 0;
  const releaseSnap = () => {
    if (lastSnap !== null) {
      lastSnap = null;
      emitter.emit('release', null);
    }
  };
  const computeSnap = (mouseX, mouseY) => {
    if (!shouldSnap()) {
      releaseSnap();
      return null;
    }
    let raw;
    try { raw = fromPixel(mouseX, mouseY); } catch { raw = null; }
    if (!raw || raw.time == null || raw.price == null) {
      releaseSnap();
      return null;
    }
    const match = findNearestCandle(candles, timeToNum(raw.time));
    if (!match) {
      releaseSnap();
      return null;
    }
    const { candle } = match;
    const snappedTime = candle.rawTime;
    const { price: snappedPrice } = snapToOHLC(candle, raw.price, mode);
    let pixel;
    try { pixel = toPixel(snappedTime, snappedPrice); } catch { pixel = null; }
    if (!pixel) {
      releaseSnap();
      return null;
    }
    const result = {
      chartCoords: { time: snappedTime, price: snappedPrice },
      pixel,
      candle,
      rawCoords: raw,
    };
    lastSnap = result;
    emitter.emit('snap', result);
    return result;
  };
  const flushPending = () => {
    rafId = null;
    if (!shouldSnap()) {
      releaseSnap();
      return;
    }
    if (lastMouse) {
      computeSnap(lastMouse.x, lastMouse.y);
    }
  };
  const scheduleRAF = () => {
    if (rafId || destroyed) return;
    rafId = requestAnimationFrame(flushPending);
  };
  const cancelRAF = () => {
    if (rafId) {
      cancelAnimationFrame(rafId);
      rafId = null;
    }
  };
  const api = {
        getMode: () => mode,
        cycleMode() {
      const next = mode === 'off' ? 'weak' : mode === 'weak' ? 'strong' : 'off';
      api.setMode(next);
      return next;
    },
        setMode(m) {
      if (m === mode) return;
      if (__DEV__) console.debug('[Magnet] setMode', m);
      mode = m;
      emitter.emit('modeChange', mode);
      if (lastMouse && shouldSnap()) {
        computeSnap(lastMouse.x, lastMouse.y);
      } else if (mode === 'off') {
        releaseSnap();
      }
    },
        isActive: () => shouldSnap(),
        setToolActive(active) {
      if (__DEV__) console.debug('[Magnet] setToolActive', !!active);
      const prev = enabled;
      enabled = !!active;
      if (prev && !enabled) {
        cancelRAF();
        releaseSnap();
      } else if (!prev && enabled && lastMouse && mode !== 'off') {
        computeSnap(lastMouse.x, lastMouse.y);
      }
    },
        setCandles(c) {
      candles = ensureSorted(sanitizeCandles(c || []));
      if (__DEV__) console.debug('[Magnet] setCandles', candles.length);
      if (lastMouse && shouldSnap()) {
        computeSnap(lastMouse.x, lastMouse.y);
      }
    },
        setConverters({ toPixel: tp, fromPixel: fp }) {
      if (tp) toPixel = tp;
      if (fp) fromPixel = fp;
      if (lastMouse && shouldSnap()) {
        computeSnap(lastMouse.x, lastMouse.y);
      }
    },
        update(x, y) {
      if (__DEV__) console.debug('[Magnet] update', { x: x|0, y: y|0, mode, enabled, candles: candles.length });
      lastMouse = { x, y };
      if (!shouldSnap()) return; 
      scheduleRAF();
    },
        snapImmediate(x, y) {
      let raw;
      try { raw = fromPixel(x, y); } catch { raw = null; }
      if (!shouldSnap()) return raw;
      if (!raw || raw.time == null || raw.price == null) return raw;
      const match = findNearestCandle(candles, timeToNum(raw.time));
      if (!match) return raw;
      const { candle } = match;
      const snappedTime = candle.rawTime;
      const { price: snappedPrice } = snapToOHLC(candle, raw.price, mode);
      return { time: snappedTime, price: snappedPrice };
    },
        clear() {
      cancelRAF();
      lastMouse = null;
      releaseSnap();
    },
        getSnap: () => lastSnap,
        on: (event, fn) => emitter.on(event, fn),
        destroy() {
      destroyed = true;
      cancelRAF();
      lastMouse = null;
      lastSnap = null;
      emitter.removeAll();
    },
  };
  return api;
};
export const drawSnapIndicator = (ctx, snap, size, opts = {}) => {
  if (!snap || !snap.pixel) return;
  const { x, y } = snap.pixel;
  const color = opts.color || '#5E6AD2';
  const { width, height } = size;
  ctx.save();
  ctx.strokeStyle = `${color}66`; 
  ctx.lineWidth = 1;
  ctx.setLineDash([3, 3]);
  ctx.beginPath();
  ctx.moveTo(x, 0);
  ctx.lineTo(x, height);
  ctx.stroke();
  ctx.beginPath();
  ctx.moveTo(0, y);
  ctx.lineTo(width, y);
  ctx.stroke();
  ctx.setLineDash([]);
  ctx.beginPath();
  ctx.arc(x, y, 8, 0, Math.PI * 2);
  ctx.fillStyle = `${color}26`; 
  ctx.fill();
  ctx.beginPath();
  ctx.arc(x, y, 4, 0, Math.PI * 2);
  ctx.fillStyle = color;
  ctx.fill();
  ctx.strokeStyle = '#ffffff';
  ctx.lineWidth = 1.5;
  ctx.stroke();
  ctx.restore();
};
export default createMagnetManager;
