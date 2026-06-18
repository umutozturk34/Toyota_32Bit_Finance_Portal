import { motion } from 'framer-motion';
import { EASE } from '../../../../shared/utils/animations';
import {
  ChevronLeft, ChevronRight, ArrowUpRight, ShieldAlert,
} from 'lucide-react';
import Card from '../../../../shared/components/card';
import { formatPercent } from '../../utils';
import { assetRoute } from '../../../watch/lib/watchConstants';
import { ANALYTICS_TO_MARKET_TYPE, TYPE_BADGE, RISK_STYLE, PAGE_SIZE } from '../../returnsConstants';
import Th from '../ReturnsTh';

export default function ReturnsTable({
  t,
  sortKey, sortDir, ccy, period, typeFilter, riskFilter, search, page,
  pageRows, safePage, totalPages, setPage,
  metaFor, nameFor, money, moneyDelta, navigate,
}) {
  return (
    <Card variant="elevated" radius="xl" padding="none" backdropBlur className="overflow-hidden">
      <div className="overflow-x-auto">
        <table className="w-full text-xs sm:text-sm min-w-[640px]">
          <thead className="bg-bg-elevated/40">
            <tr>
              <Th>#</Th>
              <Th>{t('analytics.instrument', { defaultValue: 'Enstrüman' })}</Th>
              <Th align="right" active={sortKey === 'price'} dir={sortDir}>{t('analytics.returns.price', { defaultValue: 'Fiyat' })}</Th>
              <Th align="right" active={sortKey === 'return'} dir={sortDir}>{t('analytics.returns.return', { defaultValue: 'Getiri' })}</Th>
              <Th align="right" active={sortKey === 'delta'} dir={sortDir}>{t('analytics.returns.deltaTry', { defaultValue: '₺ Değişim' })}</Th>
              <Th align="right" active={sortKey === 'vol'} dir={sortDir} title={t('analytics.returns.riskInfo')}>
                {t('analytics.returns.risk', { defaultValue: 'Risk' })}
              </Th>
            </tr>
          </thead>
          <motion.tbody
            key={`${sortKey}-${sortDir}-${ccy}-${period}-${[...typeFilter].sort().join(',')}-${[...riskFilter].sort().join(',')}-${search.trim()}-${page}`}
            initial={{ opacity: 0, y: 6 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.26, ease: EASE.standard }}
          >
            {pageRows.map((r, idx) => {
              const badge = TYPE_BADGE[r.type] || { label: r.type, color: '#6366f1' };
              const risk = r.riskLevel ? RISK_STYLE[r.riskLevel] : null;
              const rank = safePage * PAGE_SIZE + idx + 1;
              const detail = assetRoute(ANALYTICS_TO_MARKET_TYPE[r.type], r.code);
              const icon = r.type === 'CRYPTO' ? metaFor(r.type, r.code)?.image : null;
              return (
                <tr
                  key={`${r.type}|${r.code}`}
                  onClick={() => { if (detail) navigate(detail); }}
                  className="group border-t border-border-default/40 hover:bg-bg-elevated/40 transition-colors cursor-pointer"
                  title={t('analytics.returns.openDetail', { defaultValue: 'Detayı aç' })}
                >
                  <td className="py-3 px-2 sm:px-3 font-mono text-xs tabular-nums">
                    <span className={rank <= 3 ? 'text-warning font-bold' : 'text-fg-muted'}>{rank}</span>
                  </td>
                  <td className="py-3 px-2 sm:px-3">
                    <div className="flex items-center gap-2 flex-wrap">
                      {icon && <img src={icon} alt="" loading="lazy" className="w-5 h-5 rounded-full ring-1 ring-border-default shrink-0" />}
                      <span className="text-fg font-semibold">{nameFor(r)}</span>
                      <span
                        className="inline-flex items-center text-[10px] font-mono font-semibold tracking-[0.04em] rounded px-1.5 py-0.5"
                        style={{ background: `${badge.color}1f`, color: badge.color, boxShadow: `inset 0 0 0 1px ${badge.color}40` }}
                      >
                        {t(`assets.labels.${r.type}`, { defaultValue: badge.label })}
                      </span>
                      <ArrowUpRight className="h-3 w-3 text-fg-subtle opacity-0 group-hover:opacity-100 transition-opacity" />
                    </div>
                    <div className="text-[10px] font-mono uppercase tracking-[0.12em] text-fg-subtle mt-0.5">{r.code}</div>
                  </td>
                  <td className="py-3 px-2 sm:px-3 text-right font-mono text-[11px] tabular-nums whitespace-nowrap">
                    <span className="text-fg-subtle">{money(r.priceThen)}</span>
                    <span className="text-fg-subtle mx-1">→</span>
                    <span className="text-fg">{money(r.priceNow)}</span>
                  </td>
                  <td className="py-3 px-2 sm:px-3 text-right font-mono font-bold tabular-nums">
                    <span className={r.returnPct >= 0 ? 'text-success' : 'text-danger'}>
                      {formatPercent(r.returnPct)}
                    </span>
                  </td>
                  <td className="py-3 px-2 sm:px-3 text-right font-mono tabular-nums whitespace-nowrap">
                    <span className={r.returnTry == null ? 'text-fg-subtle' : r.returnTry >= 0 ? 'text-success' : 'text-danger'}>
                      {moneyDelta(r.returnTry)}
                    </span>
                  </td>
                  <td className="py-3 px-2 sm:px-3 text-right">
                    {risk ? (
                      <div className="inline-flex flex-col items-end gap-0.5">
                        <span className={`inline-flex items-center gap-1 text-[10px] font-mono uppercase tracking-[0.12em] rounded-md px-2 py-0.5 border ${risk.badge}`}>
                          <ShieldAlert className="h-3 w-3" />
                          {t(risk.key)}
                        </span>
                        {r.volatility != null && (
                          <span className="text-[10px] font-mono text-fg-muted tabular-nums" title={t('analytics.returns.riskInfo')}>
                            σ {r.volatility.toLocaleString('tr-TR', { maximumFractionDigits: 1 })}%
                          </span>
                        )}
                      </div>
                    ) : (
                      <span className="text-fg-subtle font-mono text-xs">—</span>
                    )}
                  </td>
                </tr>
              );
            })}
            {pageRows.length === 0 && (
              <tr>
                <td colSpan={6} className="py-6 text-center text-xs text-fg-muted font-mono italic">
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
              onClick={() => setPage(Math.max(0, safePage - 1))}
              disabled={safePage === 0}
              className="h-7 w-7 flex items-center justify-center rounded-md text-fg-muted hover:text-fg hover:bg-bg-elevated transition-colors border-none cursor-pointer bg-transparent disabled:opacity-30 disabled:cursor-not-allowed"
            >
              <ChevronLeft className="h-3.5 w-3.5" />
            </button>
            <button
              type="button"
              onClick={() => setPage(Math.min(totalPages - 1, safePage + 1))}
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
