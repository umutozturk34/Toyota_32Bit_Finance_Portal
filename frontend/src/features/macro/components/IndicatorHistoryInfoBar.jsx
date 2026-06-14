import { Info } from 'lucide-react';

export default function InfoBar({ selected, t }) {
  if (!selected || selected.length === 0) return null;
  return (
    <div className="rounded-lg border border-border-default/40 bg-bg-base/30 p-3 space-y-1.5">
      <div className="flex items-center gap-1.5 text-[9px] font-mono uppercase tracking-[0.16em] text-fg-muted">
        <Info className="h-3 w-3" />
        {t('marketOverview.macro.indicatorInfo', { defaultValue: 'Bilgi' })}
      </div>
      {selected.map(({ indicator: ind, color }) => {
        // type/category/frequency/unit resolve under marketOverview.macro.enum; maturity uses the maturity<M>
        // convention (DepositMatrix/YieldCurvePanel); currency codes (TRY/USD/EUR) are display-safe verbatim —
        // none of these last two live in the enum namespace, so routing them through it rendered the raw value.
        const tags = [{ kind: 'enum', value: ind.type }];
        if (ind.category) tags.push({ kind: 'enum', value: ind.category });
        if (ind.frequency) tags.push({ kind: 'enum', value: ind.frequency });
        if (ind.unit) tags.push({ kind: 'enum', value: ind.unit });
        if (ind.currency) tags.push({ kind: 'currency', value: ind.currency });
        if (ind.maturity) tags.push({ kind: 'maturity', value: ind.maturity });
        const friendlyName = ind.label
          ? t(`marketOverview.macro.${ind.label}`, { defaultValue: ind.name })
          : ind.name;
        const showFriendly = friendlyName && friendlyName !== ind.code;
        return (
          <div key={`${ind.type}-${ind.code}`} className="flex items-baseline gap-2 text-[11px] flex-wrap">
            <span className="h-1.5 w-1.5 rounded-full shrink-0 mt-1" style={{ background: color }} />
            <span className="font-mono text-[10px] text-fg-muted uppercase tracking-[0.12em] shrink-0">{ind.code}</span>
            {showFriendly && (
              <>
                <span className="text-fg-subtle hidden sm:inline">·</span>
                <span className="text-fg-muted truncate min-w-0 max-w-[200px] sm:max-w-none">
                  {friendlyName}
                </span>
              </>
            )}
            <span className="sm:ml-auto flex items-center flex-wrap gap-1.5 text-[10px] font-mono text-fg-subtle tracking-[0.04em]">
              {tags.map(({ kind, value }, i) => {
                const label = kind === 'maturity'
                  ? t(`marketOverview.macro.maturity${value}`, { defaultValue: value })
                  : kind === 'currency'
                    ? value
                    : t(`marketOverview.macro.enum.${value}`, { defaultValue: value });
                return (
                  <span key={`${value}-${i}`}>
                    {i > 0 && <span className="mr-1.5">·</span>}{label}
                  </span>
                );
              })}
            </span>
          </div>
        );
      })}
    </div>
  );
}
