import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { motion } from 'framer-motion';
import {
  BarChart3,
  Activity, Newspaper, ChevronRight,
} from 'lucide-react';
import { TrendingUp, TrendingDown, ArrowUpRight, ArrowDownRight, RefreshCw } from '../../shared/components/AnimatedIcons';
import { unifiedMarketService } from '../../shared/services/unifiedMarketService';
import { newsService } from '../news/newsService';
import { formatPriceTRY, getChangeClass, changeColors, changeBg } from '../../shared/utils/formatters';
import LoadingState from '../../shared/components/LoadingState';
import ErrorState from '../../shared/components/ErrorState';
import SearchSuggestions from '../../shared/components/SearchSuggestions';
import { containerVariants, cardVariants } from '../../shared/utils/animations';
import { ASSET_TYPE_LABELS, ASSET_TYPE_COLORS } from '../../shared/constants/assetTypes';

const TYPE_ROUTES = { STOCK: '/stocks', CRYPTO: '/crypto', FOREX: '/forex', FUND: '/funds', COMMODITY: '/commodities' };

function assetRoute(asset) {
  return (TYPE_ROUTES[asset.type] || '/market') + '/' + asset.code;
}

function IndexCard({ asset, index, navigate }) {
  const cls = getChangeClass(asset.changePercent);
  const isUp = asset.changePercent > 0;
  const glowColor = isUp ? '#10b981' : '#ef4444';

  return (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.5, delay: 0.1 + index * 0.08, ease: [0.16, 1, 0.3, 1] }}
      whileHover={{ y: -2 }}
      onClick={() => navigate(`/stocks/${asset.code}`)}
      className="group relative rounded-2xl border border-border-default bg-bg-elevated backdrop-blur-md p-5 cursor-pointer card-hover transition-all duration-300 hover:border-border-hover overflow-hidden"
    >
      <div
        className="pointer-events-none absolute -top-16 -right-16 w-32 h-32 rounded-full blur-[60px] opacity-0 group-hover:opacity-100 transition-opacity duration-500"
        style={{ background: `radial-gradient(circle, ${glowColor}20 0%, transparent 70%)` }}
        aria-hidden="true"
      />
      <div
        className="absolute inset-0 opacity-[0.04] group-hover:opacity-[0.08] transition-opacity duration-300"
        style={{
          background: `linear-gradient(135deg, ${glowColor} 0%, transparent 60%)`,
        }}
      />
      <div className="relative">
        <div className="flex items-center justify-between mb-3">
          <p className="text-sm font-bold text-fg">{asset.code.replace('.IS', '')}</p>
          <span className="text-[10px] text-fg-subtle">Endeks</span>
        </div>
        <p className="font-mono text-2xl font-bold text-fg tracking-tight">{formatPriceTRY(asset.price)}</p>
        {asset.changePercent != null && (
          <div className={`mt-3 inline-flex items-center gap-1 rounded-lg px-2.5 py-1 text-xs font-semibold ${changeBg[cls]} ${changeColors[cls]}`}>
            {isUp ? <ArrowUpRight className="h-3 w-3" /> : <ArrowDownRight className="h-3 w-3" />}
            {Math.abs(asset.changePercent).toFixed(2)}%
          </div>
        )}
      </div>
    </motion.div>
  );
}

function AssetRow({ asset, onClick }) {
  const cls = getChangeClass(asset.changePercent);
  const color = ASSET_TYPE_COLORS[asset.type] || '#6366f1';

  return (
    <div
      onClick={onClick}
      className="flex items-center gap-2.5 rounded-xl px-3 py-2 cursor-pointer hover:bg-surface/50 transition-all group"
    >
      {asset.image ? (
        <img src={asset.image} alt={asset.code} className="w-6 h-6 rounded-full shrink-0 ring-1 ring-border-default" />
      ) : (
        <span className="w-6 h-6 rounded-full shrink-0 flex items-center justify-center text-[8px] font-bold text-white shadow-sm" style={{ backgroundColor: color }}>
          {(asset.name || asset.code).slice(0, 2).toUpperCase()}
        </span>
      )}
      <span className="text-[13px] font-medium text-fg truncate flex-1 group-hover:text-accent transition-colors">{asset.code.replace('.IS', '')}</span>
      <span className="text-[13px] font-mono font-bold text-fg tabular-nums">{formatPriceTRY(asset.price)}</span>
      {asset.changePercent != null && (
        <span className={`text-[11px] font-mono font-semibold tabular-nums min-w-[52px] text-right ${changeColors[cls]}`}>
          {asset.changePercent > 0 ? '+' : ''}{asset.changePercent.toFixed(2)}%
        </span>
      )}
    </div>
  );
}

function MoversPanel({ type, gainers, losers, navigate }) {
  const color = ASSET_TYPE_COLORS[type] || '#6366f1';

  return (
    <motion.section
      variants={cardVariants}
      className="group relative rounded-2xl border border-border-default bg-bg-elevated backdrop-blur-md overflow-hidden card-hover transition-all duration-300 hover:border-border-hover"
    >
      <div
        className="pointer-events-none absolute -top-24 -left-24 w-48 h-48 rounded-full blur-[80px] opacity-0 group-hover:opacity-100 transition-opacity duration-500"
        style={{ background: `radial-gradient(circle, ${color}25 0%, transparent 70%)` }}
        aria-hidden="true"
      />
      <div
        className="pointer-events-none absolute -bottom-20 -right-20 w-40 h-40 rounded-full blur-[60px] opacity-0 group-hover:opacity-60 transition-opacity duration-700"
        style={{ background: `radial-gradient(circle, ${color}15 0%, transparent 70%)` }}
        aria-hidden="true"
      />

      <div
        className="absolute top-0 left-0 right-0 h-[2px]"
        style={{ background: `linear-gradient(90deg, ${color}, ${color}40 60%, transparent)` }}
      />

      <div className="p-4 pb-3">
        <button
          onClick={() => navigate(TYPE_ROUTES[type] || '/market')}
          className="flex items-center gap-2.5 group/title bg-transparent border-none cursor-pointer p-0 w-full"
        >
          <span
            className="flex items-center justify-center w-9 h-9 rounded-xl transition-transform duration-300 group-hover/title:scale-110"
            style={{ backgroundColor: color + '18', boxShadow: `0 0 20px ${color}15` }}
          >
            <BarChart3 className="h-4.5 w-4.5" style={{ color }} />
          </span>
          <span className="text-[15px] font-bold text-fg">{ASSET_TYPE_LABELS[type] || type}</span>
          <ChevronRight className="h-4 w-4 text-fg-subtle ml-auto opacity-0 group-hover/title:opacity-100 group-hover/title:translate-x-0.5 transition-all" />
        </button>
      </div>

      <div className="grid grid-cols-2">
        <div className="p-3 pt-0">
          <div className="flex items-center gap-1.5 px-3 pb-2.5 border-b border-border-default mb-2">
            <div className="relative w-1.5 h-1.5">
              <span className="absolute inset-0 rounded-full bg-success animate-ping opacity-40" />
              <span className="relative block w-1.5 h-1.5 rounded-full bg-success" />
            </div>
            <span className="text-[11px] font-semibold text-success">Yükselenler</span>
          </div>
          <div className="max-h-[280px] overflow-y-auto space-y-0.5" style={{ scrollbarWidth: 'thin' }}>
            {(gainers || []).map((a) => (
              <AssetRow key={a.code} asset={a} onClick={() => navigate(assetRoute(a))} />
            ))}
          </div>
          {(!gainers || gainers.length === 0) && (
            <p className="text-xs text-fg-subtle px-3 py-6 text-center">Veri yok</p>
          )}
        </div>

        <div className="p-3 pt-0 border-l border-border-default">
          <div className="flex items-center gap-1.5 px-3 pb-2.5 border-b border-border-default mb-2">
            <div className="relative w-1.5 h-1.5">
              <span className="absolute inset-0 rounded-full bg-danger animate-ping opacity-40" />
              <span className="relative block w-1.5 h-1.5 rounded-full bg-danger" />
            </div>
            <span className="text-[11px] font-semibold text-danger">Düşenler</span>
          </div>
          <div className="max-h-[280px] overflow-y-auto space-y-0.5" style={{ scrollbarWidth: 'thin' }}>
            {(losers || []).map((a) => (
              <AssetRow key={a.code} asset={a} onClick={() => navigate(assetRoute(a))} />
            ))}
          </div>
          {(!losers || losers.length === 0) && (
            <p className="text-xs text-fg-subtle px-3 py-6 text-center">Veri yok</p>
          )}
        </div>
      </div>
    </motion.section>
  );
}

export default function MarketDataPage() {
  const navigate = useNavigate();

  const { data: overview, isLoading, error, refetch, isFetching } = useQuery({
    queryKey: ['marketOverview'],
    queryFn: () => unifiedMarketService.getOverview(),
    staleTime: 30_000,
  });

  const { data: newsData } = useQuery({
    queryKey: ['news', 'dashboard'],
    queryFn: () => newsService.search({ size: 4 }),
    staleTime: 60_000,
  });

  const latestNews = newsData?.content || [];

  if (isLoading) return <LoadingState message="Piyasa verileri yükleniyor..." />;
  if (error) return <ErrorState message="Piyasa verileri yüklenemedi" onRetry={refetch} />;

  const indices = overview?.indices || [];
  const movers = overview?.movers || [];

  return (
    <div className="flex flex-col gap-6 py-6">
      <motion.div
        initial={{ opacity: 0, y: -12 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.5, ease: [0.16, 1, 0.3, 1] }}
        className="flex items-center justify-between"
      >
        <div className="flex items-center gap-3.5">
          <span className="flex items-center justify-center w-11 h-11 rounded-2xl bg-gradient-accent text-white shadow-lg shadow-accent/25">
            <Activity className="h-5 w-5" />
          </span>
          <div>
            <h1 className="text-2xl font-display font-bold tracking-tight text-fg">Piyasa Özeti</h1>
            <p className="text-xs text-fg-muted mt-0.5">Canlı piyasa verileri ve günün öne çıkanları</p>
          </div>
        </div>
        <button
          onClick={refetch}
          className="flex items-center gap-2 rounded-xl border border-border-default bg-bg-elevated backdrop-blur-md px-4 py-2.5 text-xs font-semibold text-fg-muted hover:text-fg hover:border-border-hover transition-all cursor-pointer"
        >
          <RefreshCw className={`h-3.5 w-3.5 ${isFetching ? 'animate-spin' : ''}`} />
          Yenile
        </button>
      </motion.div>

      <motion.div
        initial={{ opacity: 0, y: 8 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4, delay: 0.1, ease: [0.16, 1, 0.3, 1] }}
        className="max-w-xl"
      >
        <SearchSuggestions variant="hero" placeholder="Hisse, kripto, döviz veya fon ara..." />
      </motion.div>

      {indices.length > 0 && (
        <section>
          <button onClick={() => navigate('/stocks')} className="flex items-center gap-2 mb-4 group bg-transparent border-none cursor-pointer p-0">
            <BarChart3 className="h-4 w-4 text-fg-muted" />
            <span className="text-sm font-bold text-fg">BIST Endeksleri</span>
            <ChevronRight className="h-3.5 w-3.5 text-fg-subtle opacity-0 group-hover:opacity-100 group-hover:translate-x-0.5 transition-all" />
          </button>
          <div className="grid gap-4 grid-cols-2 sm:grid-cols-3 lg:grid-cols-5">
            {indices.map((asset, i) => (
              <IndexCard key={asset.code} asset={asset} index={i} navigate={navigate} />
            ))}
          </div>
        </section>
      )}

      <motion.div
        variants={containerVariants(0.1)}
        initial="hidden"
        animate="show"
        className="grid gap-5 grid-cols-1 lg:grid-cols-2"
      >
        {movers.map(({ type, gainers, losers }) => (
          <MoversPanel key={type} type={type} gainers={gainers} losers={losers} navigate={navigate} />
        ))}
      </motion.div>

      {latestNews.length > 0 && (
        <motion.section
          initial={{ opacity: 0, y: 16 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5, delay: 0.3, ease: [0.16, 1, 0.3, 1] }}
        >
          <div className="flex items-center justify-between mb-4">
            <button onClick={() => navigate('/news')} className="flex items-center gap-2 group bg-transparent border-none cursor-pointer p-0">
              <Newspaper className="h-4 w-4 text-fg-muted" />
              <span className="text-sm font-bold text-fg">Günün Haberleri</span>
              <ChevronRight className="h-3.5 w-3.5 text-fg-subtle opacity-0 group-hover:opacity-100 group-hover:translate-x-0.5 transition-all" />
            </button>
          </div>
          <div className="grid gap-4 grid-cols-1 sm:grid-cols-2 lg:grid-cols-4">
            {latestNews.map((article, i) => (
              <motion.div
                key={article.id}
                initial={{ opacity: 0, y: 12 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.4, delay: 0.35 + i * 0.06, ease: [0.16, 1, 0.3, 1] }}
                onClick={() => navigate(`/news/${article.id}`)}
                className="group rounded-2xl border border-border-default bg-bg-elevated backdrop-blur-md p-4 cursor-pointer card-hover transition-all hover:border-border-hover"
              >
                <p className="text-sm font-semibold text-fg line-clamp-2 leading-relaxed group-hover:text-accent transition-colors">{article.title}</p>
                <div className="flex items-center justify-between mt-3 pt-3 border-t border-border-default">
                  <span className="text-[11px] text-fg-subtle">
                    {article.publishedAt ? new Date(article.publishedAt).toLocaleDateString('tr-TR', { day: 'numeric', month: 'short' }) : ''}
                  </span>
                  {article.category && (
                    <span className="text-[10px] font-semibold text-accent bg-accent/10 rounded-lg px-2 py-0.5">{article.category}</span>
                  )}
                </div>
              </motion.div>
            ))}
          </div>
        </motion.section>
      )}
    </div>
  );
}
