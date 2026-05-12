import { X } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import SearchSuggestions from '../form/SearchSuggestions';
import { ASSET_TYPE_COLORS } from '../../constants/assetTypes';

export default function CompareBar({ compareAsset, onSelect, onClear, excludeCodes = [] }) {
  const { t } = useTranslation();
  if (compareAsset) {
    const color = ASSET_TYPE_COLORS[compareAsset.type] || '#8b5cf6';
    return (
      <div className="flex items-center gap-3">
        <div className="flex items-center gap-2 rounded-lg border border-accent/30 bg-accent/5 px-3 py-2">
          {compareAsset.image && (/^https?:\/\//i.test(compareAsset.image)
            ? <img src={compareAsset.image} alt={compareAsset.code} className="w-5 h-5 rounded" />
            : <span className="text-base leading-none">{compareAsset.image}</span>)}
          <span className="text-sm font-semibold text-fg">{compareAsset.code}</span>
          {compareAsset.name && <span className="text-xs text-fg-muted truncate max-w-[150px]">{compareAsset.name}</span>}
          <span
            className="text-[10px] font-bold uppercase px-1.5 py-0.5 rounded"
            style={{ backgroundColor: color + '18', color }}
          >
            {t(`assets.labels.${compareAsset.type}`, { defaultValue: compareAsset.type })}
          </span>
        </div>
        <button
          onClick={onClear}
          className="flex items-center gap-1.5 rounded-lg border border-danger/30 px-3 py-2 text-sm font-medium text-danger hover:bg-danger/10 transition-colors cursor-pointer bg-transparent"
        >
          <X className="w-4 h-4" /> {t('common.remove')}
        </button>
      </div>
    );
  }

  return (
    <div className="w-72">
      <SearchSuggestions
        placeholder={t('compareBar.placeholder')}
        navigateOnSelect={false}
        onSelect={onSelect}
        excludeCodes={excludeCodes}
      />
    </div>
  );
}
