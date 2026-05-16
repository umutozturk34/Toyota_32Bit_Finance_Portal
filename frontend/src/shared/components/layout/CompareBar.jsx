import { X, Plus } from 'lucide-react';
import { useState } from 'react';
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
      {canAddMore && !adding && (
        <button
          onClick={() => setAdding(true)}
          className="flex items-center gap-1.5 rounded-lg border border-dashed border-accent/40 px-3 py-2 text-sm font-medium text-accent hover:bg-accent/10 transition-colors cursor-pointer bg-transparent"
        >
          <Plus className="w-4 h-4" />
          {compareAssets.length === 0 ? t('compareBar.placeholder') : t('compareBar.addMore', { defaultValue: '+ Compare' })}
        </button>
      )}
      {canAddMore && adding && (
        <div className="w-72">
          <SearchSuggestions
            placeholder={t('compareBar.placeholder')}
            navigateOnSelect={false}
            autoFocus
            onSelect={(asset) => {
              onAdd(asset);
              setAdding(false);
            }}
            excludeCodes={allExcludes}
          />
          <button
            onClick={() => setAdding(false)}
            className="mt-1 text-[11px] text-fg-muted hover:text-fg cursor-pointer bg-transparent border-none"
          >
            {t('common.cancel')}
          </button>
        </div>
      )}
    </div>
  );
}
