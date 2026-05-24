import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { motion } from 'framer-motion';
import { createPortal } from 'react-dom';
import { X, Check } from 'lucide-react';
import NewsConfigSection from './widget-config/NewsConfigSection';
import WatchlistConfigSection from './widget-config/WatchlistConfigSection';
import AssetCardsConfigSection from './widget-config/AssetCardsConfigSection';
import SingleAssetConfigSection from './widget-config/SingleAssetConfigSection';
import BenchmarkBeatersConfigSection from './widget-config/BenchmarkBeatersConfigSection';

const POPOVER_WIDTH = 400;
const POPOVER_MAX_HEIGHT = 460;
const GAP = 10;

function placeNear(anchor) {
  if (!anchor || typeof anchor.getBoundingClientRect !== 'function') {
    return { top: 80, left: 16, maxHeight: POPOVER_MAX_HEIGHT };
  }
  const rect = anchor.getBoundingClientRect();
  const vw = window.innerWidth;
  const vh = window.innerHeight;
  const rightSpace = vw - rect.right - GAP;
  const leftSpace = rect.left - GAP;
  const belowSpace = vh - rect.bottom - GAP;
  const aboveSpace = rect.top - GAP;
  let top;
  let left;
  let maxHeight;
  if (leftSpace >= POPOVER_WIDTH) {
    left = rect.left - POPOVER_WIDTH - GAP;
    top = Math.max(8, Math.min(rect.top, vh - POPOVER_MAX_HEIGHT - 8));
    maxHeight = Math.min(POPOVER_MAX_HEIGHT, vh - 16);
  } else if (rightSpace >= POPOVER_WIDTH) {
    left = rect.right + GAP;
    top = Math.max(8, Math.min(rect.top, vh - POPOVER_MAX_HEIGHT - 8));
    maxHeight = Math.min(POPOVER_MAX_HEIGHT, vh - 16);
  } else {
    left = Math.max(8, Math.min(rect.left, vw - POPOVER_WIDTH - 8));
    if (belowSpace >= aboveSpace) {
      top = rect.bottom + GAP;
      maxHeight = Math.min(POPOVER_MAX_HEIGHT, belowSpace);
    } else {
      maxHeight = Math.min(POPOVER_MAX_HEIGHT, aboveSpace);
      top = rect.top - maxHeight - GAP;
    }
  }
  return { top, left, maxHeight };
}

export default function WidgetSettingsPopover({ anchorEl, kind, config, autoFocusName = false, onChange, onClose }) {
  const { t } = useTranslation();
  const ref = useRef(null);
  const [pos, setPos] = useState(null);

  useLayoutEffect(() => {
    const recompute = () => setPos(placeNear(anchorEl));
    recompute();
    window.addEventListener('resize', recompute);
    window.addEventListener('scroll', recompute, true);
    return () => {
      window.removeEventListener('resize', recompute);
      window.removeEventListener('scroll', recompute, true);
    };
  }, [anchorEl]);

  useEffect(() => {
    const clickHandler = (e) => {
      if (ref.current?.contains(e.target)) return;
      if (anchorEl && anchorEl.contains(e.target)) return;
      onClose();
    };
    const escHandler = (e) => { if (e.key === 'Escape') onClose(); };
    document.addEventListener('mousedown', clickHandler);
    document.addEventListener('keydown', escHandler);
    return () => {
      document.removeEventListener('mousedown', clickHandler);
      document.removeEventListener('keydown', escHandler);
    };
  }, [anchorEl, onClose]);

  if (!pos) return null;

  return createPortal(
    <motion.div
      ref={ref}
      initial={{ opacity: 0, x: -16 }}
      animate={{ opacity: 1, x: 0 }}
      exit={{ opacity: 0, x: -16 }}
      transition={{ duration: 0.2, ease: [0.16, 1, 0.3, 1] }}
      style={{
        position: 'fixed',
        top: pos.top,
        left: pos.left,
        width: `min(${POPOVER_WIDTH}px, calc(100vw - 16px))`,
        maxHeight: pos.maxHeight,
        zIndex: 100,
      }}
      className="flex flex-col rounded-xl border border-accent/50 bg-bg-deep shadow-2xl shadow-black/60"
    >
      <div className="absolute top-0 left-0 right-0 h-[1px] bg-gradient-to-r from-transparent via-accent/60 to-transparent rounded-t-xl" />
      <button
        type="button"
        onClick={onClose}
        aria-label={t('common.close')}
        title={t('widgetSettings.closeTooltip')}
        className="absolute top-2 right-2 z-10 flex items-center justify-center w-6 h-6 rounded-md border border-border-default bg-bg-deep/80 text-fg-muted hover:text-danger hover:border-danger/50 hover:bg-danger/10 transition-all cursor-pointer"
      >
        <X className="h-3 w-3" />
      </button>
      <div className="flex-1 min-h-0 flex flex-col p-3.5 pt-3 pr-9">
        {kind === 'NEWS' && <NewsConfigSection config={config} onChange={onChange} />}
        {kind === 'WATCHLIST' && <WatchlistConfigSection config={config} onChange={onChange} />}
        {kind === 'ASSET_CARDS' && <AssetCardsConfigSection config={config} onChange={onChange} autoFocusName={autoFocusName} />}
        {kind === 'SINGLE_ASSET' && <SingleAssetConfigSection config={config} onChange={onChange} autoFocusName={autoFocusName} />}
        {kind === 'BENCHMARK_BEATERS' && <BenchmarkBeatersConfigSection config={config} onChange={onChange} />}
      </div>
      <div className="shrink-0 flex items-center justify-end gap-2 px-3 py-2 border-t border-border-default/60 bg-bg-deep/30 rounded-b-xl">
        <button
          type="button"
          onClick={onClose}
          className="flex items-center gap-1.5 rounded-md border border-accent bg-accent text-white px-2.5 py-1 text-[11px] font-display font-semibold tracking-tight hover:bg-accent-bright shadow-md shadow-accent/20 transition-all cursor-pointer"
        >
          <Check className="h-3 w-3" />
          {t('widgetSettings.done')}
        </button>
      </div>
    </motion.div>,
    document.body,
  );
}
