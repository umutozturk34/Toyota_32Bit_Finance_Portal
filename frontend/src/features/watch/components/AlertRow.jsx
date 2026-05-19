import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { RotateCcw, Trash2, Pencil } from 'lucide-react';
import AssetBadge from '../../../shared/components/asset/AssetBadge';
import { useAssetDetailPrefetch } from '../../../shared/hooks/useAssetDetailPrefetch';
import { assetRoute, DIRECTION_META } from '../lib/watchConstants';
import { currencySymbolOf } from '../../../shared/utils/priceCurrency';

export default function AlertRow({ alert, onDelete, onReactivate, onEdit }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const dir = DIRECTION_META[alert.direction] ?? DIRECTION_META.ABOVE;
  const { Icon, tint } = dir;
  const localeTag = t('common.localeTag');
  const isFired = !alert.active && alert.triggeredAt;
  const route = assetRoute(alert.marketType, alert.assetCode);
  const prefetch = useAssetDetailPrefetch();
  const triggerPrefetch = () => prefetch(alert.marketType, alert.assetCode);
  const isPercent = alert.direction === 'CHANGE_PCT_UP' || alert.direction === 'CHANGE_PCT_DOWN';
  const thresholdSymbol = isPercent ? '%' : currencySymbolOf(alert.currency);
  const thresholdValue = Number(alert.threshold).toLocaleString(localeTag, { maximumFractionDigits: isPercent ? 2 : 2 });

  return (
    <div
      onClick={route ? () => navigate(route) : undefined}
      onMouseEnter={triggerPrefetch}
      onFocus={triggerPrefetch}
      className={`group grid grid-cols-[auto_1fr_auto_auto_auto_auto] gap-4 items-center px-4 py-3 transition-colors ${
        route ? 'cursor-pointer hover:bg-accent/5' : ''
      } ${isFired ? 'opacity-60' : ''}`}
    >
      <AssetBadge
        assetType={alert.marketType}
        assetCode={alert.assetCode}
        assetImage={alert.image}
        size="md"
      />
      <div className="min-w-0">
        <div className="text-sm font-semibold text-fg truncate group-hover:text-accent transition-colors">
          {alert.assetName || alert.assetCode}
        </div>
        <div className="text-[11px] text-fg-muted font-mono">{alert.assetCode}</div>
      </div>
      <div className={`flex items-center gap-1 text-[11px] font-medium ${tint}`}>
        <Icon className="h-3.5 w-3.5" />
        <span>{t(dir.labelKey)}</span>
      </div>
      <span className="text-sm font-mono font-semibold text-fg tabular-nums min-w-[90px] text-right">
        {isPercent ? `${thresholdValue}${thresholdSymbol}` : `${thresholdSymbol}${thresholdValue}`}
      </span>
      <span className={`text-[10px] font-semibold uppercase tracking-wide px-2 py-0.5 rounded shrink-0 min-w-[80px] text-center ${
        isFired ? 'bg-fg-subtle/10 text-fg-subtle' : 'bg-success/10 text-success'
      }`}>
        {isFired ? t('alertRow.fired') : t('alertRow.active')}
      </span>
      <div className="flex items-center gap-1 min-w-[110px] justify-end opacity-0 group-hover:opacity-100 transition-opacity">
        {isFired && (
          <button
            type="button"
            onClick={(e) => {
              e.preventDefault();
              e.stopPropagation();
              onReactivate(alert.id);
            }}
            className="flex items-center justify-center w-8 h-8 rounded-lg text-fg-muted hover:text-accent hover:bg-accent/10 bg-transparent border-none cursor-pointer"
            title={t('alertRow.reactivate')}
          >
            <RotateCcw className="h-4 w-4" />
          </button>
        )}
        <button
          type="button"
          onClick={(e) => {
            e.preventDefault();
            e.stopPropagation();
            onEdit?.(alert);
          }}
          className="flex items-center justify-center w-8 h-8 rounded-lg text-fg-muted hover:text-accent hover:bg-accent/5 bg-transparent border-none cursor-pointer"
          title={t('common.edit')}
        >
          <Pencil className="h-3.5 w-3.5" />
        </button>
        <button
          type="button"
          onClick={(e) => {
            e.preventDefault();
            e.stopPropagation();
            onDelete(alert.id);
          }}
          className="flex items-center justify-center w-8 h-8 rounded-lg text-fg-muted hover:text-danger hover:bg-danger/5 bg-transparent border-none cursor-pointer"
          title={t('common.delete')}
        >
          <Trash2 className="h-4 w-4" />
        </button>
      </div>
    </div>
  );
}
