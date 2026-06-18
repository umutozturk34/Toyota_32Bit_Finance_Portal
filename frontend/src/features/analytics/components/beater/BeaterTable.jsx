import { motion } from 'framer-motion';
import { EASE } from '../../../../shared/utils/animations';
import { TrendingDown, Trophy, ChevronLeft, ChevronRight, GitCompare } from 'lucide-react';
import Card from '../../../../shared/components/card';
import Th from '../BeaterTh';
import { formatPercent } from '../../utils';
import { TYPE_BADGE } from '../../inflationBeaterConstants';

export default function BeaterTable({
  t, sortKey, sortDir, verdictFilter, typeFilter, search, page,
  pageEntries, onCompare, metaFor, nameFor,
  safePage, totalPages, onPageChange,
}) {
  return (
    <Card variant="elevated" radius="xl" padding="none" backdropBlur className="overflow-hidden">
      <div className="overflow-x-auto">
        <table className="w-full text-xs sm:text-sm min-w-[560px]">
          <thead className="bg-bg-elevated/40">
            <tr>
              <Th sortKey="rank" activeSort={sortKey} dir={sortDir}>#</Th>
              <Th>
                {t('analytics.instrument', { defaultValue: 'Enstrüman' })}
              </Th>
              <Th align="right" sortKey="nominal" activeSort={sortKey} dir={sortDir} title={t('analytics.nominalReturnTooltip', { defaultValue: 'Mutlak yüzde değişim' })}>
                {t('analytics.nominalReturn', { defaultValue: 'Nominal' })}
              </Th>
              <Th align="right" sortKey="excess" activeSort={sortKey} dir={sortDir} title={t('analytics.excessReturnTooltip', { defaultValue: 'Nominal − Gösterge' })}>
                {t('analytics.excessReturn', { defaultValue: 'Gösterge Üzeri' })}
              </Th>
              <Th align="right">{t('analytics.verdict', { defaultValue: 'Sonuç' })}</Th>
            </tr>
          </thead>
          <motion.tbody
            key={`${sortKey}-${sortDir}-${verdictFilter}-${[...typeFilter].sort().join(',')}-${search.trim()}-${page}`}
            initial={{ opacity: 0, y: 6 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.26, ease: EASE.standard }}
          >
            {pageEntries.map((entry) => (
              <tr
                key={`${entry.type}|${entry.code}`}
                onClick={() => onCompare?.(entry)}
                className="group border-t border-border-default/40 hover:bg-bg-elevated/40 transition-colors cursor-pointer"
                title={t('analytics.openInCompare', { defaultValue: 'Compare’de aç' })}
              >
                <td className="py-3 px-2 sm:px-3 font-mono text-xs tabular-nums">
                  <span className={entry._displayRank <= 3 ? 'text-warning font-bold' : 'text-fg-muted'}>
                    {entry._displayRank}
                  </span>
                </td>
                <td className="py-3 px-2 sm:px-3">
                  {(() => {
                    const badge = TYPE_BADGE[entry.type] || { label: entry.type, color: '#6366f1' };
                    // Crypto shows its CoinGecko logo (same as the Returns page); everything else keeps the
                    // colored initials avatar so each asset still has an at-a-glance visual identity.
                    const icon = entry.type === 'CRYPTO' ? metaFor(entry.type, entry.code)?.image : null;
                    const initials = (entry.code || '').replace('.IS', '').slice(0, 2).toUpperCase();
                    return (
                      <>
                        <div className="flex items-center gap-2 flex-wrap">
                          {icon ? (
                            <img
                              src={icon}
                              alt=""
                              loading="lazy"
                              className="w-6 h-6 rounded-full ring-1 ring-border-default shrink-0"
                            />
                          ) : (
                            <span
                              className="w-6 h-6 rounded-full shrink-0 flex items-center justify-center text-[9px] font-bold text-white shadow-sm"
                              style={{ backgroundColor: badge.color }}
                              aria-hidden
                            >
                              {initials}
                            </span>
                          )}
                          <span className="text-fg font-semibold">{nameFor(entry)}</span>
                          <span
                            className="inline-flex items-center text-[10px] font-mono font-semibold tracking-[0.04em] rounded px-1.5 py-0.5"
                            style={{ background: `${badge.color}1f`, color: badge.color, boxShadow: `inset 0 0 0 1px ${badge.color}40` }}
                          >
                            {t(`assets.labels.${entry.type}`, { defaultValue: badge.label })}
                          </span>
                          <GitCompare className="h-3 w-3 text-fg-subtle opacity-0 group-hover:opacity-100 transition-opacity" />
                        </div>
                        <div className="text-[10px] font-mono uppercase tracking-[0.12em] text-fg-subtle mt-0.5">
                          {entry.code}
                        </div>
                      </>
                    );
                  })()}
                </td>
                <td className="py-3 px-2 sm:px-3 text-right font-mono tabular-nums">
                  <span className={Number(entry.nominalReturnPct) >= 0 ? 'text-success' : 'text-danger'}>
                    {formatPercent(entry.nominalReturnPct)}
                  </span>
                </td>
                <td className="py-3 px-2 sm:px-3 text-right font-mono font-bold tabular-nums">
                  <span className={Number(entry.excessReturnPct) >= 0 ? 'text-success' : 'text-danger'}>
                    {formatPercent(entry.excessReturnPct)}
                  </span>
                </td>
                <td className="py-3 px-2 sm:px-3 text-right">
                  {entry.beatsBenchmark ? (
                    <span className="inline-flex items-center gap-1 text-[10px] font-mono uppercase tracking-[0.14em] rounded-md px-2 py-0.5 bg-success/15 text-success">
                      <Trophy className="h-3 w-3" />
                      {t('analytics.beats', { defaultValue: 'Yendi' })}
                    </span>
                  ) : (
                    <span className="inline-flex items-center gap-1 text-[10px] font-mono uppercase tracking-[0.14em] rounded-md px-2 py-0.5 bg-danger/10 text-danger">
                      <TrendingDown className="h-3 w-3" />
                      {t('analytics.lost', { defaultValue: 'Kaybetti' })}
                    </span>
                  )}
                </td>
              </tr>
            ))}
            {pageEntries.length === 0 && (
              <tr>
                <td colSpan={5} className="py-6 text-center text-xs text-fg-muted font-mono italic">
                  {t('analytics.noMatch', { defaultValue: 'Eşleşme yok' })}
                </td>
              </tr>
            )}
          </motion.tbody>
        </table>
      </div>

      {totalPages > 1 && (
        <div className="flex items-center justify-between gap-2 px-3 py-2 border-t border-border-default/40">
          <span className="text-[10px] font-mono uppercase tracking-[0.14em] text-fg-subtle tabular-nums">
            {safePage + 1} / {totalPages}
          </span>
          <div className="flex items-center gap-1">
            <button
              type="button"
              onClick={() => onPageChange(Math.max(0, safePage - 1))}
              disabled={safePage === 0}
              className="h-7 w-7 flex items-center justify-center rounded-md text-fg-muted hover:text-fg hover:bg-bg-elevated transition-colors border-none cursor-pointer bg-transparent disabled:opacity-30 disabled:cursor-not-allowed"
            >
              <ChevronLeft className="h-3.5 w-3.5" />
            </button>
            <button
              type="button"
              onClick={() => onPageChange(Math.min(totalPages - 1, safePage + 1))}
              disabled={safePage >= totalPages - 1}
              className="h-7 w-7 flex items-center justify-center rounded-md text-fg-muted hover:text-fg hover:bg-bg-elevated transition-colors border-none cursor-pointer bg-transparent disabled:opacity-30 disabled:cursor-not-allowed"
            >
              <ChevronRight className="h-3.5 w-3.5" />
            </button>
          </div>
        </div>
      )}
    </Card>
  );
}
