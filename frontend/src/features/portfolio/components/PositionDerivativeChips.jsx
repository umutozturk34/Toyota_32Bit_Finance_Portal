import { AlertTriangle } from 'lucide-react';

export default function PositionDerivativeChips({ meta, money, t, localeTag, entryDate }) {
  if (!meta) return null;
  const isLong = meta.direction === 'LONG';
  const formatExpiry = (iso) => {
    if (!iso) return null;
    return new Date(iso).toLocaleDateString(localeTag, { day: '2-digit', month: 'short', year: '2-digit' });
  };
  return (
    <div className="flex flex-wrap items-center gap-1.5 mt-1.5">
      <span className={`inline-flex items-center rounded px-1.5 py-0.5 text-[9px] font-bold uppercase tracking-wide border ${
        isLong
          ? 'bg-emerald-500/15 text-emerald-400 border-emerald-500/30'
          : 'bg-rose-500/15 text-rose-400 border-rose-500/30'
      }`}>
        {meta.direction}
      </span>
      {meta.contractKind && (
        <span className="inline-flex items-center rounded px-1.5 py-0.5 text-[9px] font-mono text-fg-muted bg-bg-elevated border border-border-default">
          {t(`portfolio.derivatives.${String(meta.contractKind).toLowerCase()}`, { defaultValue: meta.contractKind })}
        </span>
      )}
      {meta.contractSize != null && Number(meta.contractSize) !== 1 && (
        <span className="inline-flex items-center rounded px-1.5 py-0.5 text-[9px] font-mono text-fg-muted bg-bg-elevated border border-border-default">
          {Number(meta.contractSize).toLocaleString(localeTag)}×
        </span>
      )}
      {meta.lockedMarginTry != null && (
        <span className="inline-flex items-center gap-1 rounded px-1.5 py-0.5 text-[9px] text-accent bg-accent/10 border border-accent/30">
          <span className="font-bold uppercase tracking-wide">{t('portfolio.derivatives.margin', 'Teminat')}</span>
          <span className="font-mono">{money(meta.lockedMarginTry, 'TRY', { dateAt: entryDate })}</span>
        </span>
      )}
      {meta.expiryDate && (
        <span className="inline-flex items-center gap-1 rounded px-1.5 py-0.5 text-[9px] text-fg-muted bg-bg-elevated border border-border-default">
          <span className="font-bold uppercase tracking-wide">{t('portfolio.derivatives.expiry', 'Vade')}</span>
          <span className="font-mono">{formatExpiry(meta.expiryDate)}</span>
        </span>
      )}
      {meta.strikePrice != null && (
        <span className="inline-flex items-center gap-1 rounded px-1.5 py-0.5 text-[9px] text-fg-muted bg-bg-elevated border border-border-default">
          <span className="font-bold uppercase tracking-wide">{t('portfolio.derivatives.strike', 'Strike')}</span>
          <span className="font-mono">{money(meta.strikePrice, meta.currency, { natural: meta.currency })}</span>
        </span>
      )}
      {meta.maxLossTry != null && (
        <span className="inline-flex items-center gap-1 rounded px-1.5 py-0.5 text-[9px] text-rose-400 bg-rose-500/10 border border-rose-500/30">
          <span className="font-bold uppercase tracking-wide">{t('portfolio.derivatives.maxLoss', 'Max Kayıp')}</span>
          <span className="font-mono">{money(meta.maxLossTry, 'TRY', { dateAt: entryDate })}</span>
        </span>
      )}
      {meta.maxGainTry != null && (
        <span className="inline-flex items-center gap-1 rounded px-1.5 py-0.5 text-[9px] text-emerald-400 bg-emerald-500/10 border border-emerald-500/30">
          <span className="font-bold uppercase tracking-wide">{t('portfolio.derivatives.maxGain', 'Max Kazanç')}</span>
          <span className="font-mono">{money(meta.maxGainTry, 'TRY', { dateAt: entryDate })}</span>
        </span>
      )}
      {/* A SHORT option's downside is what the premium-only "max gain" hides: a short CALL is unlimited, a short
          PUT loses down to the strike. Surface it so a sold option doesn't look like pure upside. */}
      {meta.direction === 'SHORT' && meta.contractKind === 'OPTION' && (
        <span className="inline-flex items-center gap-1 rounded px-1.5 py-0.5 text-[9px] text-rose-400 bg-rose-500/10 border border-rose-500/40">
          <AlertTriangle className="h-2.5 w-2.5" />
          <span className="font-bold uppercase tracking-wide">{t('portfolio.derivatives.risk', 'Risk')}</span>
          <span>
            {meta.optionSide === 'CALL'
              ? t('portfolio.derivatives.unlimitedLoss', 'Sınırsız zarar')
              : t('portfolio.derivatives.lossToStrike', "Strike'a kadar zarar")}
          </span>
        </span>
      )}
      {meta.currency && meta.currency !== 'TRY' && (
        <span className="inline-flex items-center rounded px-1.5 py-0.5 text-[9px] font-mono text-fg-muted bg-bg-elevated border border-border-default">
          {meta.currency}
        </span>
      )}
    </div>
  );
}
