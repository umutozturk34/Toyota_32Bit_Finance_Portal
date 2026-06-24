import { Info } from 'lucide-react';

export default function CompareNotices({
  levelMode,
  normalize,
  selected,
  currencyReconciledNotice,
  forceTryFrame,
  targetCurrency,
  macroUnitLoadFailed,
  t,
}) {
  return (
    <>
      {levelMode && (
        <div className="flex items-center gap-2 text-[10px] font-mono text-fg-subtle italic">
          <Info className="h-3 w-3" />
          {t('analytics.annualHintCompare', {
            defaultValue: 'Yıllık oran gösteriliyor (endeksler için YoY değişim) — bileşik büyüme için Kümülatif\'e geç',
          })}
        </div>
      )}

      {normalize && (
        <div className="flex items-center gap-2 text-[10px] font-mono text-amber-500 italic">
          <Info className="h-3 w-3" />
          {t('analytics.normalizedHintCompare', {
            defaultValue: 'Her seri başlangıçta 100.000\'e endekslendi',
          })}
        </div>
      )}
      {selected.some((s) => s.type === 'PORTFOLIO') && (
        <div className="flex items-center gap-2 text-[10px] font-mono text-fg-subtle italic">
          <Info className="h-3 w-3" />
          {t('analytics.portfolioSeriesHint', {
            defaultValue: 'Portföy serisi zaman-ağırlıklı getiridir (nakit giriş/çıkışlarından bağımsız)',
          })}
        </div>
      )}
      {currencyReconciledNotice && (
        <div className="flex items-center gap-2 text-[10px] font-mono text-amber-500 italic">
          <Info className="h-3 w-3 shrink-0" />
          {forceTryFrame
            ? t('analytics.inflationFrameHint', {
                defaultValue: 'Enflasyon TRY endeksi — seriler TRY bazında karşılaştırılıyor',
              })
            : t('analytics.mixedCurrencyHint', {
                currency: targetCurrency,
                defaultValue: 'Farklı para birimlerindeki seriler {{currency}} bazında karşılaştırılıyor — para birimini değiştirebilirsin',
              })}
        </div>
      )}
      {macroUnitLoadFailed && (
        <div className="flex items-center gap-2 text-[10px] font-mono text-amber-500 italic">
          <Info className="h-3 w-3 shrink-0" />
          {t('analytics.macroUnitLoadError', {
            defaultValue: 'Makro indikatör bilgisi yüklenemedi — bu oran serisi güvenli şekilde gizlendi; sayfayı yenileyip tekrar dene',
          })}
        </div>
      )}
    </>
  );
}
