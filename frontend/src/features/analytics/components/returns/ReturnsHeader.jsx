import { CCY_SYMBOL } from '../../returnsConstants';

export default function ReturnsHeader({ t, ccy, setCcy, setPage }) {
  return (
    <header className="pb-3 border-b border-border-default/40">
      <h1 className="font-display text-2xl font-bold text-fg tracking-tight leading-none">
        {t('analytics.returns.title', { defaultValue: 'Varlık Getirileri' })}
      </h1>
      <p className="mt-2 text-sm text-fg-muted max-w-2xl">
        {t('analytics.returns.subtitle')}
      </p>
      <div className="flex flex-wrap items-center gap-2 mt-2">
        <span className="text-[10px] font-mono uppercase tracking-[0.12em] text-fg-subtle">
          {t('analytics.returns.currencyLabel', { defaultValue: 'Para birimi' })}
        </span>
        {['TRY', 'USD', 'EUR'].map((c) => (
          <button
            key={c}
            type="button"
            onClick={() => { setCcy(c); setPage(0); }}
            className={`text-[11px] font-mono font-semibold rounded-md px-2.5 py-1 cursor-pointer border-none transition-colors ${
              ccy === c ? 'bg-accent/15 text-accent shadow-[inset_0_0_0_1px_rgba(99,102,241,0.4)]' : 'text-fg-muted hover:text-fg'
            }`}
          >
            {CCY_SYMBOL[c]} {c}
          </button>
        ))}
      </div>
      <p className="mt-2 text-[11px] text-fg-subtle leading-snug max-w-3xl">
        {t('analytics.returns.currencyHint', { defaultValue: 'Getiriler seçtiğin para biriminde; her tarih kendi günündeki kurla çevrilir. Varsayılan genel para birimi tercihinden gelir, burada serbestçe değiştirebilirsin.' })} {t('analytics.returns.usageHint')}
      </p>
    </header>
  );
}
