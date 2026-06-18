import { motion, AnimatePresence } from 'framer-motion';
import { X } from 'lucide-react';
import { compareTypeLabel } from '../../lib/compareSeriesUtils';

export default function CompareChipBar({ seriesData, selected, removeAsset, t }) {
  return (
    <div className="flex items-center gap-1.5 flex-wrap min-h-[28px]">
      <AnimatePresence>
        {seriesData.map(({ indicator: ind, color }) => (
          <motion.span
            key={`${ind.type}-${ind.code}`}
            initial={{ opacity: 0, scale: 0.9 }}
            animate={{ opacity: 1, scale: 1 }}
            exit={{ opacity: 0, scale: 0.9 }}
            className="inline-flex items-center gap-1.5 rounded-md pl-2 pr-1 py-1 text-xs font-mono"
            style={{ background: `${color}14`, boxShadow: `inset 0 0 0 1px ${color}40` }}
          >
            <span className="h-1.5 w-1.5 rounded-full" style={{ background: color }} />
            <span className="text-fg font-semibold tracking-tight text-[11px]">{ind.displayName}</span>
            <span className="text-fg-subtle">·</span>
            <span className="text-fg-muted uppercase tracking-[0.12em] text-[10px]">{compareTypeLabel(t, ind.type)}</span>
            <button
              type="button"
              onClick={() => removeAsset(ind.code, ind.type)}
              className="ml-0.5 h-4 w-4 flex items-center justify-center rounded-sm text-fg-subtle hover:text-fg hover:bg-bg-elevated cursor-pointer border-none bg-transparent"
            >
              <X className="h-2.5 w-2.5" />
            </button>
          </motion.span>
        ))}
      </AnimatePresence>
      {selected.length === 0 && (
        <span className="text-xs text-fg-subtle font-mono italic px-1 py-1">
          {t('analytics.compareEmpty', { defaultValue: 'Karşılaştırmak için aşağıdan ara ve seç.' })}
        </span>
      )}
    </div>
  );
}
