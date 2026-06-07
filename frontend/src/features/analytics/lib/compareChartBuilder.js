import { formatPrice } from '../../../shared/utils/formatters';

function rawKind(type, levelMode) {
  // Portfolio: plotted as a time-weighted-return INDEX that normalizes to a % from the window start, so it
  // sits on the same % axis as CPI/deposit and is directly comparable to inflation. The tooltip / info-bar
  // additionally surface its cumulative TL P&L (carried per-point as pnlTry) — "% to compare, ₺ to feel it".
  if (type === 'PORTFOLIO') return 'portfolio';
  // Level mode (homogeneous rate-vs-rate compare): rates are NOT compounded — they are plotted at their
  // actual % level, so format them as a rate (%X.XX) rather than a growth-index number.
  if (levelMode && (type === 'MACRO_RATE' || type === 'MACRO_DEPOSIT')) return 'rate';
  // Deposits AND policy/reference rates (MACRO_RATE) are compounded into a cumulative growth index (a
  // multiplier), not a rate or a currency value, so their plotted value formats as a plain index number.
  if (type === 'MACRO_INFLATION' || type === 'MACRO_DEPOSIT' || type === 'MACRO_RATE') return 'index';
  if (type === 'BOND') return 'rate';
  return 'price';
}

// Last data point at or before `ts` for a series whose `data` is sorted ascending by timestamp (data[i][0]);
// returns null when `ts` precedes the series' first point. Lets the tooltip show every series' value-in-force
// at the hovered date even when series sit on different grids (sparse macro vs daily asset).
function valueAsOf(data, ts) {
  if (!data || data.length === 0) return null;
  let lo = 0;
  let hi = data.length - 1;
  let ans = -1;
  while (lo <= hi) {
    const mid = (lo + hi) >> 1;
    if (data[mid][0] <= ts) { ans = mid; lo = mid + 1; } else { hi = mid - 1; }
  }
  return ans >= 0 ? data[ans] : null;
}

function formatRaw(kind, raw, currency) {
  if (!Number.isFinite(raw)) return '—';
  if (kind === 'rate') return `%${raw.toFixed(2)}`;
  if (kind === 'index') return raw.toLocaleString('tr-TR', { maximumFractionDigits: 2 });
  return raw.toLocaleString('tr-TR', { style: 'currency', currency, maximumFractionDigits: 2 });
}

// Signed TL for the portfolio's cumulative P&L shown next to its return %. Uses the shared formatter so it
// matches the info-bar exactly — 2 decimals ("−₺11,13" not the rounded "−₺11"); tiny values collapse to ₺0,00.
function formatPnl(val, currency) {
  if (!Number.isFinite(val)) return '';
  return `${val > 0 ? '+' : ''}${formatPrice(val, { currency, minDecimals: 2, maxDecimals: 2 })}`;
}

// Leading-split skip — mirrors backend ScenarioService.pickBaselineIndex (SPLIT_DETECTION_LOW/HIGH +
// BASELINE_PROBE_WINDOW, ScenarioService.java:46-48) so Compare anchors its baseline on the SAME post-cliff
// limb the inflation-beater uses, keeping the two surfaces' trailing-return consistent. A fund whose first
// in-window candle sits just before a launch-week crash or an unadjusted split (e.g. PKZ 1.006 → 0.16, an
// ~84% one-day step) would otherwise be based on the pre-cliff value, so Compare reported a ~6.4x-too-small
// return that disagreed with the beater. Scans the leading probe window from startIdx and returns the index
// of the first point AFTER the LAST split-like step (ratio > HIGH or < LOW); only meaningful for raw price
// series — index/rate/portfolio lines never split.
const SPLIT_DETECTION_LOW = 0.2;
const SPLIT_DETECTION_HIGH = 5;
const BASELINE_PROBE_WINDOW = 10;

export function skipLeadingSplit(sortedPoints, startIdx) {
  const span = sortedPoints.length - startIdx;
  if (span < 2) return startIdx;
  const scanLimit = Math.min(span - 1, Math.max(BASELINE_PROBE_WINDOW, Math.floor(span / 3)));
  let jumpIdx = startIdx;
  for (let k = 0; k < scanLimit; k += 1) {
    const i = startIdx + k;
    const cur = Number(sortedPoints[i].value);
    const next = Number(sortedPoints[i + 1].value);
    if (cur > 0 && next > 0) {
      const ratio = next / cur;
      if (ratio > SPLIT_DETECTION_HIGH || ratio < SPLIT_DETECTION_LOW) jumpIdx = i + 1;
    }
  }
  return jumpIdx;
}

// Money base a single compounded deposit/rate index is rebased to at the window start (see buildOption), so
// its line reads as "100.000 → 224.000" instead of the raw carried-over index (~1,25) — which only looks
// arbitrary because the index resets to 1.0 at the widened fetch start, well before the window.
const INDEX_MONEY_BASE = 100000;

export function buildOption(seriesData, normalize, isDark, targetCurrency, commonStartDate, levelMode, indexMode) {
  const muted = isDark ? '#6b6b7a' : '#94a3b8';
  const grid = isDark ? 'rgba(255,255,255,0.04)' : 'rgba(0,0,0,0.04)';
  const tooltipBg = isDark ? 'rgba(12,12,20,0.96)' : 'rgba(255,255,255,0.98)';
  const tooltipFg = isDark ? '#e2e2ea' : '#1a1a2e';
  const single = seriesData.length === 1;

  // First pass: sort each series once and find its effective baseline date — the common start advanced past
  // any leading split-like cliff (fund launch crash / unadjusted split) for price series. The SHARED baseline
  // is the LATEST of these across all series; EVERY series is then normalized AND trimmed from that one date,
  // so they all begin together at 0% (a real apples-to-apples compare). Previously each series anchored
  // independently — a longer-history asset showed a pre-start tail and per-series split-skips desynced the
  // 0% point, so e.g. THYAO started a few days before ASELSAN instead of from the same shared origin.
  const prepared = seriesData.map(({ indicator: ind, points, color }) => {
    if (!points || points.length === 0) return null;
    const sortedPoints = [...points].sort((a, b) => String(a.date).localeCompare(String(b.date)));
    const kind = rawKind(ind.type, levelMode);
    const startIdx = commonStartDate ? sortedPoints.findIndex((p) => p.date >= commonStartDate) : 0;
    const safeStart = startIdx >= 0 ? startIdx : 0;
    const skipIdx = kind === 'price' ? skipLeadingSplit(sortedPoints, safeStart) : safeStart;
    return {
      ind, color, sortedPoints, kind,
      effectiveStartDate: sortedPoints[skipIdx]?.date,
      lastDate: sortedPoints[sortedPoints.length - 1]?.date,
    };
  }).filter(Boolean);

  // Shared window = the INTERSECTION [latest effective start, earliest last point], so EVERY series both
  // starts at 0% on the same date AND ends on the same date — a fully aligned apples-to-apples compare over
  // one window (not each series running to its own private start/end).
  let sharedBaselineDate = commonStartDate || null;
  let sharedEndDate = null;
  for (const p of prepared) {
    if (p.effectiveStartDate && (!sharedBaselineDate || p.effectiveStartDate > sharedBaselineDate)) {
      sharedBaselineDate = p.effectiveStartDate;
    }
    if (p.lastDate && (sharedEndDate === null || p.lastDate < sharedEndDate)) {
      sharedEndDate = p.lastDate;
    }
  }

  const series = prepared.map(({ ind, color, sortedPoints, kind }) => {
    const found = sharedBaselineDate ? sortedPoints.findIndex((p) => p.date >= sharedBaselineDate) : 0;
    const baseIdx = found >= 0 ? found : 0;
    // Cut the tail at the shared end so every series stops on the same date.
    let endIdx = sortedPoints.length;
    if (sharedEndDate !== null) {
      const over = sortedPoints.findIndex((p) => p.date > sharedEndDate);
      if (over >= 0) endIdx = over;
    }
    const basePoint = Number(sortedPoints[baseIdx]?.value);
    // Trim to the shared [start, end] window: all begin at the same date/0% and end on the same date.
    const visiblePoints = sortedPoints.slice(baseIdx, Math.max(baseIdx, endIdx));
    const data = visiblePoints.map((p) => {
      const raw = Number(p.value);
      const pct = basePoint !== 0 ? ((raw - basePoint) / Math.abs(basePoint)) * 100 : 0;
      // A compounded deposit/rate index (kind 'index') resets to 1.0 at the widened fetch start (~18mo before
      // the window), so its raw value at the window start is an arbitrary ~1,25 — a confusing baseline. When it
      // is NOT being normalized to % (single-series view), rebase it to a 100.000 money base at the shared
      // baseline so the line reads "100.000 → 224.000". Pure ratio rescale → the return % is unchanged; every
      // other series keeps its raw value.
      const displayValue = (kind === 'index' && !normalize && basePoint)
        ? (raw / basePoint) * INDEX_MONEY_BASE
        : raw;
      // INDEX MODE (macro-vs-macro only): rebase every series to a COMMON index of 100 at the shared start, so
      // deposits/rates read as a growth multiple (100 → 105) instead of disparate compounded-index levels
      // (1,01 vs 2,54). ASSET MODE keeps % change from the real starting price (the asset's price is its unit).
      // Index = % change + 100; line shape / comparison are identical either way.
      const plotted = normalize ? (indexMode ? pct + 100 : pct) : displayValue;
      // 5th slot carries the portfolio's cumulative TL P&L at this point (null for every other series).
      return [new Date(p.date).getTime(), plotted, displayValue, pct, p.pnlTry != null ? Number(p.pnlTry) : null];
    });
    // Step lines for sparse/published-snapshot data: CPI is monthly, policy rate /
    // deposit rates are weekly or monthly. The published value is the canonical
    // reading for the full period until the next reading, so render as a step
    // function — horizontal line carries the last-known value until the next data
    // point, eliminating empty gaps when zoomed in.
    const isSparse = kind === 'index' || kind === 'rate';
    return {
      name: ind.displayName || ind.code,
      type: 'line',
      smooth: !isSparse && data.length < 200,
      step: isSparse ? 'end' : undefined,
      showSymbol: false,
      connectNulls: true,
      // Full data + lttb + dataZoom filterMode:'filter' = viewport-adaptive: ECharts draws ~viewport width at
      // any zoom, and zooming in re-samples the (now smaller) visible window so DAILY detail returns — TINY
      // ranges show every real point. Pre-trimming here would have capped that detail, so the data stays full.
      sampling: 'lttb',
      data,
      itemStyle: { color },
      lineStyle: { width: 2, color },
      areaStyle: single ? {
        color: {
          type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [
            { offset: 0, color: `${color}55` },
            { offset: 1, color: `${color}00` },
          ],
        },
      } : null,
      _kind: kind,
    };
  }).filter(Boolean);

  const totalPoints = series.reduce((acc, s) => acc + (s.data?.length || 0), 0);
  const showZoom = totalPoints >= 2;
  // Entry animation is the dominant jank on heavy compares: 6 assets × ~30y ≈ tens of thousands of
  // points animated over 1.1s with a staggered delay froze the chart. lttb sampling already trims what
  // is DRAWN, but ECharts still animates the sampled set per frame. So animate only light compares (the
  // "few assets / short range" case stays smooth and pretty); render heavy ones instantly.
  const animate = totalPoints <= 3000;
  series.forEach((s, idx) => {
    if (animate) {
      s.animationDuration = 1100;
      s.animationEasing = 'cubicOut';
      s.animationDelay = idx * 180;
    } else {
      s.animationDuration = 0;
    }
  });

  return {
    backgroundColor: 'transparent',
    animation: animate,
    animationThreshold: 100000,
    grid: { left: 8, right: 12, top: single ? 16 : 32, bottom: showZoom ? 64 : 32, containLabel: true },
    legend: !single ? {
      type: 'scroll',
      top: 4,
      textStyle: { color: muted, fontSize: 10, fontFamily: 'ui-monospace,monospace' },
      icon: 'circle',
      itemWidth: 8,
      itemHeight: 8,
    } : undefined,
    dataZoom: showZoom ? [
      { type: 'inside', filterMode: 'filter', zoomOnMouseWheel: true, moveOnMouseMove: true,
        moveOnMouseWheel: false, preventDefaultMouseMove: true },
      { type: 'slider', height: 18, bottom: 8, filterMode: 'filter',
        borderColor: 'transparent', backgroundColor: 'transparent',
        dataBackground: { lineStyle: { color: '#6366f160', width: 1 }, areaStyle: { color: '#6366f120' } },
        selectedDataBackground: { lineStyle: { color: '#6366f1', width: 1 }, areaStyle: { color: '#6366f140' } },
        fillerColor: 'rgba(99,102,241,0.12)',
        handleStyle: { color: '#6366f1', borderColor: '#6366f1' },
        moveHandleStyle: { color: '#6366f1', opacity: 0.4 },
        showDetail: false, brushSelect: false, textStyle: { color: muted, fontSize: 9 } },
    ] : undefined,
    tooltip: {
      trigger: 'axis',
      confine: true,
      position: (point, _params, _dom, _rect, size) => {
        const x = Math.max(8, Math.min(point[0] - size.contentSize[0] / 2, size.viewSize[0] - size.contentSize[0] - 8));
        return [x, 8];
      },
      backgroundColor: tooltipBg, borderWidth: 0,
      textStyle: { color: tooltipFg, fontSize: 11 },
      formatter: (params) => {
        if (!params?.length) return '';
        // Take the hovered timestamp, then look up EVERY series' value-in-force at that date from its own full
        // data (last point <= ts) instead of echarts' `params` — which on a time axis only includes the
        // series that happen to own a point near the cursor. With sparse macro lines on a different grid than
        // daily asset lines, that left rows missing / showing a neighbouring date's value and made the tooltip
        // flicker on fast moves. The as-of lookup yields a complete, consistent cross-section at one date.
        const ts = params[0].value[0];
        const date = new Date(ts).toLocaleDateString('tr-TR', { day: '2-digit', month: 'short', year: 'numeric' });
        const rows = series.map((seriesDef) => {
          const pt = valueAsOf(seriesDef.data, ts);
          if (!pt) return '';
          const kind = seriesDef._kind || 'price';
          const color = seriesDef.itemStyle?.color;
          const raw = Number(pt[2]);
          const pct = Number(pt[3]);
          const sign = pct > 0 ? '+' : '';
          const pctColor = pct > 0 ? '#10b981' : pct < 0 ? '#ef4444' : tooltipFg;
          const pctFmt = `${sign}${pct.toFixed(2)}%`;
          let valueSpans;
          if (kind === 'portfolio') {
            // Return % is the comparison primary; the cumulative TL P&L rides along as the secondary
            // so you still feel the money even while the axis is in %.
            const tl = formatPnl(Number(pt[4]), targetCurrency);
            valueSpans = `<span style="font-weight:700;font-size:12px;color:${pctColor}">${pctFmt}</span>`
              + (tl ? `<span style="font-size:10px;font-weight:600;color:${tooltipFg};opacity:0.7">${tl}</span>` : '');
          } else {
            // Normalized: the primary number is the common 100-based index (every series visibly starts at
            // 100, so the growth multiplier reads directly); the native level differs per series and is
            // dropped. Level mode / single-series still show the native level (price/rate/index).
            const idx = Number(pt[1]);
            const primary = (normalize && indexMode && !levelMode)
              ? idx.toLocaleString('tr-TR', { maximumFractionDigits: idx >= 1000 ? 0 : 2 })
              : formatRaw(kind, raw, targetCurrency);
            // Level mode plots the actual rate, so the level IS the value — the % from baseline is noise there.
            const pctSpan = levelMode ? '' : `<span style="font-size:10px;font-weight:600;color:${pctColor};opacity:0.9">${pctFmt}</span>`;
            valueSpans = `<span style="font-weight:700;color:${color}">${primary}</span>${pctSpan}`;
          }
          return `<div style="display:flex;justify-content:space-between;gap:14px;align-items:center;padding:3px 0;font-family:ui-monospace,monospace;font-size:11px">
            <span style="display:flex;align-items:center;gap:6px;min-width:0">
              <span style="width:6px;height:6px;border-radius:50%;background:${color};flex-shrink:0"></span>
              <span style="color:${tooltipFg};opacity:0.85">${seriesDef.name}</span>
            </span>
            <span style="display:flex;align-items:baseline;gap:8px;flex-shrink:0">${valueSpans}</span>
          </div>`;
        }).filter(Boolean).join('');
        return `<div style="padding:6px 4px;min-width:240px">
          <div style="font-size:10px;color:${tooltipFg};opacity:0.65;margin-bottom:6px">${date}</div>
          ${rows}
        </div>`;
      },
    },
    xAxis: {
      type: 'time',
      axisLine: { show: false }, axisTick: { show: false },
      axisLabel: { color: muted, fontSize: 10 }, splitLine: { show: false },
    },
    yAxis: {
      type: 'value', scale: true,
      axisLine: { show: false }, axisTick: { show: false },
      axisLabel: {
        color: muted, fontSize: 10,
        formatter: (val) => {
          if (normalize && indexMode) {
            // Macro-only compare: indexed to 100 at the window start. Adaptive precision so a near-flat
            // series still shows movement instead of collapsing every tick to "100".
            const span = Math.abs(val - 100);
            const dec = span >= 10 ? 0 : span >= 1 ? 1 : 2;
            return val.toLocaleString('tr-TR', { minimumFractionDigits: dec, maximumFractionDigits: dec });
          }
          if (normalize) {
            // Asset compare: % change from the real starting price. Adaptive precision so a near-flat series
            // (e.g. a USD position in a USD frame, ~0%) doesn't collapse every tick to "0%"/"-0%".
            const mag = Math.abs(val);
            const dec = mag >= 10 ? 0 : mag >= 1 ? 1 : 2;
            const sign = val > 0 ? '+' : '';
            return `${sign}${val.toFixed(dec)}%`;
          }
          if (levelMode) return `%${val.toFixed(0)}`;
          return val.toLocaleString('tr-TR');
        },
      },
      splitLine: { lineStyle: { color: grid, type: 'dashed' } },
    },
    series,
  };
}
