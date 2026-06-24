import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { motion } from 'framer-motion';
import useChartRange from '../../../shared/hooks/useChartRange';
import { ArrowLeft, ExternalLink, Plus } from 'lucide-react';
import { TrendingUp, TrendingDown } from '../../../shared/components/feedback/AnimatedIcons';
import { useTheme } from '../../../shared/context/useTheme';
import { useBackfillStatus, isLotPending, useAssetLots } from '../hooks/usePortfolioData';
import { useMoney } from '../../../shared/hooks/useMoney';
import { useRateHistory } from '../../../shared/hooks/useRateHistory';
import PositionFormModal from './PositionFormModal';
import MarketOpenDerivativeModal from './MarketOpenDerivativeModal';
import IconButton from '../../../shared/components/buttons/IconButton';
import { resolveNativeCurrency } from '../lib/positionFormHelpers';
import { commodityLabel } from '../../../shared/utils/commodityName';
import { marketHref, lotDirection } from '../lib/assetDetailHelpers';
import { DirectionPanel } from './assetDetail/DirectionPanel';

export default function AssetDetail({ portfolioId, asset, onBack, onEditLot, onDeleteLot, onSellLot, onReopenLot, hasActiveDialog = false }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { isDark } = useTheme();
  const { format: money, formatCompact: moneyCompact } = useMoney();
  const nativeCurrency = resolveNativeCurrency({
    assetType: asset?.assetType,
    assetCode: asset?.assetCode,
  });
  const bigMoney = (v) => moneyCompact(v, 'TRY', 100_000, nativeCurrency);
  const { convertAt, convertBetween, currency: displayCurrency, frame } = useRateHistory();
  const [range, setRange] = useChartRange();
  const [addLotOpen, setAddLotOpen] = useState(false);

  const { data: lotData, isFetched: lotsFetched } = useAssetLots(portfolioId, asset.assetType, asset.assetCode);
  const lots = useMemo(() => lotData ?? [asset], [lotData, asset]);

  const backfill = useBackfillStatus(portfolioId);
  const isUpdating = isLotPending(backfill, asset.assetType, asset.assetCode);

  useEffect(() => {
    if (lotsFetched && lots.length === 0 && !isUpdating && !hasActiveDialog) {
      onBack?.();
    }
  }, [lotsFetched, lots.length, isUpdating, hasActiveDialog, onBack]);

  const isViop = asset.assetType === 'VIOP';
  const longLots = useMemo(() => lots.filter((l) => lotDirection(l) === 'LONG'), [lots]);
  const shortLots = useMemo(() => lots.filter((l) => lotDirection(l) === 'SHORT'), [lots]);
  // Split only a genuine same-symbol hedge: a VİOP symbol holding BOTH directions. A pure-direction or non-VİOP
  // asset keeps the single panel, so its behaviour is unchanged.
  const isMixed = isViop && longLots.length > 0 && shortLots.length > 0;

  const displayLabel = asset.assetCode;
  const displayBadge = asset.assetImage || null;
  const displayBadgeText = asset.assetCode.replace('.IS', '').slice(0, 3).toUpperCase();
  const anyLotOpen = lots.some((l) => l.exitDate == null);
  const rawAssetName = commodityLabel(t, asset.assetType, asset.assetCode,
    asset.assetName || t(`assets.labels.${asset.assetType}`, { defaultValue: asset.assetType }));
  const displaySub = anyLotOpen
    ? rawAssetName.replace(/\s·\sKAPALI$/, '')
    : rawAssetName;

  const panelProps = {
    portfolioId, asset, t, isDark, money, bigMoney, nativeCurrency, convertAt, convertBetween, displayCurrency, frame,
    range, setRange, isUpdating,
    onEditLot, onSellLot, onReopenLot, onDeleteLot,
  };

  const directionBadge = (dir, count) => (
    <div className={`flex items-center gap-2 ${dir === 'LONG' ? 'text-success' : 'text-danger'}`}>
      {dir === 'LONG' ? <TrendingUp className="h-4 w-4" /> : <TrendingDown className="h-4 w-4" />}
      <h2 className="text-sm font-bold tracking-wide">
        {t(`assetDetail.direction.${dir.toLowerCase()}`, { defaultValue: dir })}
      </h2>
      {count > 0 && (
        <span className="inline-flex items-center rounded-md bg-current/10 text-[10px] font-bold px-1.5 py-0.5">
          {t('assetDetail.lotCount', { count, defaultValue: '{{count}} lot' })}
        </span>
      )}
      <span className="h-px flex-1 bg-border-default/60" />
    </div>
  );

  return (
    <div className="space-y-5">
      <motion.div
        initial={{ opacity: 0, y: -16 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.5, ease: [0.16, 1, 0.3, 1] }}
        className="flex items-center justify-between gap-3 flex-wrap"
      >
        <div className="flex items-center gap-3 min-w-0">
          <IconButton
            variant="secondary"
            size={9}
            shape="square"
            icon={<ArrowLeft className="h-4 w-4" />}
            aria-label={t('common.back')}
            onClick={onBack}
            className="hover:-translate-x-0.5 shrink-0"
          />
          <div className="flex items-center gap-3 min-w-0">
            {displayBadge ? (
              /^https?:\/\//i.test(displayBadge)
                ? <img src={displayBadge} alt={displayLabel} width={40} height={40} loading="lazy" className="w-10 h-10 rounded-xl object-cover" />
                : <span className="flex items-center justify-center w-10 h-10 rounded-xl text-2xl">{displayBadge}</span>
            ) : (
              <span className="flex items-center justify-center w-10 h-10 rounded-xl bg-accent/10 text-sm font-bold text-accent">
                {displayBadgeText}
              </span>
            )}
            <div className="min-w-0">
              <div className="flex items-center gap-2 flex-wrap">
                <h1 className="text-lg sm:text-xl font-bold text-fg truncate max-w-[180px] sm:max-w-none">{displayLabel}</h1>
                {lots.length > 1 && !isMixed && (
                  <span className="inline-flex items-center rounded-md bg-accent/10 text-accent text-[10px] font-bold px-1.5 py-0.5 shrink-0">
                    {t('assetDetail.lotCount', { count: lots.length, defaultValue: '{{count}} lot' })}
                  </span>
                )}
              </div>
              <p className="text-xs text-fg-muted truncate">{displaySub}</p>
            </div>
          </div>
        </div>
        <div className="flex items-center gap-2 flex-wrap justify-end">
          <button
            type="button"
            onClick={() => navigate(marketHref(asset.assetType, asset.assetCode))}
            className="flex items-center gap-1.5 rounded-lg border border-border-default bg-bg-elevated px-2.5 sm:px-3 py-2 text-xs font-semibold text-fg-muted hover:text-accent hover:border-accent/40 hover:bg-accent/5 transition-all backdrop-blur-sm cursor-pointer"
            title={t('portfolio.positions.viewOnMarket', { defaultValue: 'Pazar detayı' })}
          >
            <ExternalLink className="h-3.5 w-3.5" />
            <span className="hidden sm:inline">{t('portfolio.positions.viewOnMarket', { defaultValue: 'Pazar detayı' })}</span>
          </button>
          <button
            onClick={() => setAddLotOpen(true)}
            className="flex items-center gap-2 rounded-lg bg-accent px-3 sm:px-4 py-2 text-sm font-semibold text-white hover:bg-accent-bright transition-all border-none cursor-pointer"
          >
            <Plus className="h-4 w-4" />
            {t('assetDetail.newLot')}
          </button>
        </div>
      </motion.div>

      {isMixed ? (
        <div className="space-y-8">
          <div className="space-y-3">
            {directionBadge('LONG', longLots.length)}
            <DirectionPanel {...panelProps} lots={longLots} direction="LONG" />
          </div>
          <div className="space-y-3">
            {directionBadge('SHORT', shortLots.length)}
            <DirectionPanel {...panelProps} lots={shortLots} direction="SHORT" />
          </div>
        </div>
      ) : (
        <DirectionPanel {...panelProps} lots={lots} direction={null} />
      )}

      {addLotOpen && asset.assetType === 'VIOP' && (
        <MarketOpenDerivativeModal
          assetCode={asset.assetCode}
          assetName={asset.assetName}
          currentPrice={asset.currentPriceTry}
          metadata={asset.metadata}
          onClose={() => setAddLotOpen(false)}
          onComplete={() => setAddLotOpen(false)}
        />
      )}
      {addLotOpen && asset.assetType !== 'VIOP' && (
        <PositionFormModal
          mode="add"
          portfolioId={portfolioId}
          asset={{
            type: asset.assetType,
            code: asset.assetCode,
            name: asset.assetName,
            image: asset.assetImage,
            currentPrice: asset.currentPriceTry,
          }}
          onClose={() => setAddLotOpen(false)}
          onComplete={() => setAddLotOpen(false)}
        />
      )}
    </div>
  );
}
