import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { motion } from 'framer-motion';
import { Crown, ArrowUpRight, ArrowDownRight } from 'lucide-react';
import { SERIES_COLORS } from '../constants';
import { formatPercent } from '../utils';
import { useMoney } from '../../../shared/hooks/useMoney';
import { instrumentDisplayName } from '../../../shared/utils/instrumentLabel';

export default function ScenarioRankingTable({ scenario }) {
  const { t } = useTranslation();
  const { format: money } = useMoney();
  const scenarioCurrency = scenario?.targetCurrency || 'TRY';
  const rows = useMemo(() => {
    if (!scenario?.series) return [];
    const indexed = scenario.series.map((s, idx) => ({ ...s, _color: SERIES_COLORS[idx % SERIES_COLORS.length] }));
    return [...indexed].sort((a, b) => {
      const av = Number(a.finalValue ?? -Infinity);
      const bv = Number(b.finalValue ?? -Infinity);
      return bv - av;
    });
  }, [scenario]);

  if (rows.length === 0) return null;

  return (
    <div className="rounded-xl border border-border-default/60 bg-bg-base/40 overflow-hidden">
      <div className="overflow-x-auto">
      <table className="w-full text-sm min-w-[480px]">
        <thead className="bg-bg-elevated/40">
          <tr>
            <Th>#</Th>
            <Th>{t('analytics.instrument', { defaultValue: 'Enstrüman' })}</Th>
            <Th align="right">{t('analytics.finalValue', { defaultValue: 'Son Değer' })}</Th>
            <Th align="right">{t('analytics.nominalReturn', { defaultValue: 'Nominal' })}</Th>
            <Th align="right">{t('analytics.realReturn', { defaultValue: 'Reel' })}</Th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row, idx) => {
            const nominal = Number(row.nominalReturnPct);
            const real = row.realReturnPct != null ? Number(row.realReturnPct) : null;
            const isWinner = idx === 0;
            const nominalDown = nominal < 0;
            const realDown = real != null && real < 0;
            return (
              <motion.tr
                key={`${row.instrument.type}|${row.instrument.code}`}
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                transition={{ delay: idx * 0.03 }}
                className="border-t border-border-default/40 hover:bg-bg-elevated/30 transition-colors"
              >
                <Td>
                  <div className="flex items-center gap-1">
                    {isWinner && <Crown className="h-3.5 w-3.5 text-amber-500" />}
                    <span className="font-mono text-xs tabular-nums text-fg-muted">{idx + 1}</span>
                  </div>
                </Td>
                <Td>
                  <div className="flex items-center gap-2">
                    <span className="h-2 w-2 rounded-full shrink-0" style={{ background: row._color }} />
                    <div>
                      <div className="text-fg font-semibold">
                        {instrumentDisplayName(t, row.instrument.type, row.instrument.code)}
                      </div>
                      <div className="text-[10px] font-mono uppercase tracking-[0.12em] text-fg-subtle flex items-center gap-1.5">
                        <span>{row.instrument.type}</span>
                        {row.nativeCurrency && row.nativeCurrency !== 'TRY' && (
                          <span className="px-1 py-0.5 rounded bg-accent/10 text-accent">
                            {row.nativeCurrency}
                          </span>
                        )}
                      </div>
                    </div>
                  </div>
                </Td>
                <Td align="right">
                  <span className="font-mono font-bold tabular-nums text-fg">
                    {money(row.finalValue, scenarioCurrency)}
                  </span>
                </Td>
                <Td align="right">
                  <ReturnChip value={nominal} down={nominalDown} />
                </Td>
                <Td align="right">
                  {real != null ? <ReturnChip value={real} down={realDown} /> : <span className="text-fg-subtle font-mono text-xs">—</span>}
                </Td>
              </motion.tr>
            );
          })}
        </tbody>
      </table>
      </div>
    </div>
  );
}

function Th({ children, align = 'left' }) {
  return (
    <th className={`text-xs font-display font-semibold text-fg-muted py-2.5 px-2 sm:px-3 ${align === 'right' ? 'text-right' : 'text-left'}`}>
      {children}
    </th>
  );
}

function Td({ children, align = 'left' }) {
  return <td className={`py-3 px-2 sm:px-3 ${align === 'right' ? 'text-right' : 'text-left'}`}>{children}</td>;
}

function ReturnChip({ value, down }) {
  const color = down ? '#ef4444' : '#10b981';
  const Icon = down ? ArrowDownRight : ArrowUpRight;
  return (
    <span
      className="inline-flex items-center gap-1 rounded-md px-2 py-0.5 text-xs font-mono font-bold tabular-nums"
      style={{ color, background: `${color}1a`, boxShadow: `inset 0 0 0 1px ${color}33` }}
    >
      <Icon className="h-3 w-3" />
      {formatPercent(value)}
    </span>
  );
}
