import { motion } from 'framer-motion';
import { ArrowLeft, Landmark, TrendingUp, TrendingDown, Plus } from 'lucide-react';
import IconButton from '../../../shared/components/buttons/IconButton';
import MarketStatusBadge from '../../../shared/components/layout/MarketStatusBadge';

export default function BondDetailHeader({
  bond,
  t,
  localeTag,
  typeColor,
  priceChangePct,
  formatPrice,
  onBack,
  onAdd,
}) {
  return (
    <motion.div
      initial={{ opacity: 0, y: -8 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.3 }}
      className="flex items-center justify-between gap-3 flex-wrap"
    >
      <div className="flex items-center gap-3 min-w-0 flex-wrap">
        <IconButton
          variant="secondary"
          size={9}
          shape="square"
          icon={<ArrowLeft className="h-4 w-4" />}
          aria-label={t('common.back', { defaultValue: 'back' })}
          onClick={onBack}
        />
        <span className="flex items-center justify-center w-10 h-10 rounded-xl bg-accent/10 text-accent shrink-0">
          <Landmark className="h-5 w-5" />
        </span>
        <div className="min-w-0">
          <h1 className="text-base sm:text-xl font-bold text-fg leading-tight truncate">{bond.isinCode}</h1>
          <p className="text-[10px] sm:text-xs font-mono text-fg-muted mt-0.5 truncate">{bond.seriesCode}</p>
        </div>
        <MarketStatusBadge market="BOND" compact />
      </div>
      <div className="flex items-center gap-2 sm:gap-3 flex-wrap">
        <button
          type="button"
          onClick={onAdd}
          className="inline-flex items-center gap-1.5 rounded-lg bg-accent px-3 py-1.5 text-xs sm:text-sm font-semibold text-white hover:bg-accent-bright transition-colors cursor-pointer border-none"
        >
          <Plus className="h-3.5 w-3.5" />
          {t('portfolio.bonds.addAction', { defaultValue: 'Tahvil Ekle' })}
        </button>
        <span className={`rounded-lg border px-2 sm:px-3 py-1 text-[10px] sm:text-xs font-semibold tracking-wider ${typeColor}`}>
          {t(`market.bond.types.${bond.bondType}`, { defaultValue: bond.bondType })}
        </span>
        <div className="flex items-center gap-2">
          <span className="font-mono text-lg sm:text-2xl font-bold text-fg">{formatPrice(bond.baseIndex, localeTag)}</span>
          {priceChangePct != null && (
            <span className={`inline-flex items-center gap-1 rounded-lg px-2 py-1 text-xs sm:text-sm font-semibold ${priceChangePct >= 0 ? 'bg-success/10 text-success' : 'bg-danger/10 text-danger'}`}>
              {priceChangePct >= 0 ? <TrendingUp className="h-3.5 w-3.5" /> : <TrendingDown className="h-3.5 w-3.5" />}
              {priceChangePct >= 0 ? '+' : ''}{priceChangePct.toFixed(2)}%
            </span>
          )}
        </div>
      </div>
    </motion.div>
  );
}
