import { X, Plus, GitCompare } from 'lucide-react';
import { useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { useTranslation } from 'react-i18next';
import SearchSuggestions from '../form/SearchSuggestions';
import { ASSET_TYPE_COLORS } from '../../constants/assetTypes';

const COMPARE_PALETTE = ['#ef4444', '#10b981', '#f59e0b', '#06b6d4'];

export default function CompareBar({ compareAssets = [], onAdd, onRemove, excludeCodes = [], maxAssets = 4 }) {
  const { t } = useTranslation();
  const [adding, setAdding] = useState(false);
  const canAddMore = compareAssets.length < maxAssets;

  const chips = compareAssets.map((asset, idx) => {
    const accent = COMPARE_PALETTE[idx % COMPARE_PALETTE.length];
    const typeColor = ASSET_TYPE_COLORS[asset.type] || '#8b5cf6';
    return (
      <div
        key={`${asset.type}:${asset.code}`}
        className="flex items-center gap-2 rounded-lg border bg-accent/5 px-3 py-2"
        style={{ borderColor: accent + '55' }}
      >
        <span className="h-2 w-2 rounded-full shrink-0" style={{ backgroundColor: accent }} />
        {asset.image && (/^https?:\/\//i.test(asset.image)
          ? <img src={asset.image} alt={asset.code} className="w-5 h-5 rounded" />
          : <span className="text-base leading-none">{asset.image}</span>)}
        <span className="text-sm font-semibold text-fg">{asset.code}</span>
        {asset.name && <span className="text-xs text-fg-muted truncate max-w-[140px]">{asset.name}</span>}
        <span
          className="text-[10px] font-bold uppercase px-1.5 py-0.5 rounded"
          style={{ backgroundColor: typeColor + '18', color: typeColor }}
        >
          {t(`assets.labels.${asset.type}`, { defaultValue: asset.type })}
        </span>
        <button
          onClick={() => onRemove(asset)}
          className="text-fg-muted hover:text-danger transition-colors cursor-pointer bg-transparent border-none p-1"
          title={t('common.remove')}
        >
          <X className="w-3.5 h-3.5" />
        </button>
      </div>
    );
  });

  const allExcludes = [...excludeCodes, ...compareAssets.map(a => a.code)];

  return (
    <div className="flex items-center gap-2 flex-wrap">
      {chips}
      <AnimatePresence mode="wait" initial={false}>
        {canAddMore && !adding && (
          <motion.button
            key="add-btn"
            onClick={() => setAdding(true)}
            initial={{ opacity: 0, scale: 0.96 }}
            animate={{ opacity: 1, scale: 1 }}
            exit={{ opacity: 0, scale: 0.96 }}
            transition={{ duration: 0.14, ease: 'easeOut' }}
            whileHover={{ scale: 1.02 }}
            whileTap={{ scale: 0.98 }}
            className="group flex items-center gap-2 rounded-lg border border-dashed border-accent/40 px-3.5 py-2 text-sm font-medium text-accent hover:bg-accent/10 hover:border-accent/70 transition-colors cursor-pointer bg-transparent"
          >
            <GitCompare className="w-4 h-4 transition-transform group-hover:rotate-12" />
            <span>{compareAssets.length === 0
              ? t('compareBar.placeholder')
              : t('compareBar.addMore', { defaultValue: '+ Compare' })}</span>
            <span className="text-[10px] text-fg-muted opacity-0 group-hover:opacity-100 transition-opacity">
              {compareAssets.length}/{maxAssets}
            </span>
          </motion.button>
        )}
        {canAddMore && adding && (
          <motion.div
            key="panel"
            initial={{ opacity: 0, scale: 0.96, y: -4 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.96, y: -4 }}
            transition={{ duration: 0.18, ease: [0.16, 1, 0.3, 1] }}
            className="w-80 rounded-xl border border-accent/30 bg-bg-elevated shadow-lg shadow-accent/5 p-3 space-y-2.5"
          >
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <span className="flex items-center justify-center w-6 h-6 rounded-md bg-accent/10">
                  <GitCompare className="w-3.5 h-3.5 text-accent" />
                </span>
                <span className="text-xs font-semibold text-fg">
                  {t('compareBar.title', { defaultValue: 'Karşılaştır' })}
                </span>
              </div>
              <span className="text-[10px] text-fg-muted font-mono">
                {compareAssets.length}/{maxAssets}
              </span>
            </div>
            <SearchSuggestions
              placeholder={t('compareBar.searchPlaceholder', { defaultValue: 'Sembol ara...' })}
              navigateOnSelect={false}
              autoFocus
              onSelect={(asset) => {
                onAdd(asset);
                setAdding(false);
              }}
              excludeCodes={allExcludes}
            />
            <div className="flex items-center justify-end pt-0.5">
              <button
                type="button"
                onClick={() => setAdding(false)}
                className="text-[11px] font-medium text-fg-muted hover:text-fg px-2 py-1 rounded transition-colors cursor-pointer bg-transparent border-none"
              >
                {t('common.cancel')}
              </button>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
