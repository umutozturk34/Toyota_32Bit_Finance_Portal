import { useTranslation } from 'react-i18next';
import { Newspaper } from 'lucide-react';
import PopoverHeader from './PopoverHeader';

const NEWS_CATEGORY_VALUES = ['CRYPTO', 'BORSA_ISTANBUL', 'BORSA_SIRKETLERI', 'TAHVIL_BONO', 'PARITE', 'EMTIA', 'GENEL_FINANS'];

export default function NewsConfigSection({ config, onChange }) {
  const { t } = useTranslation();
  const selected = new Set(config?.categories || []);
  const ordered = Array.isArray(config?.categories) ? config.categories : [];
  const toggle = (value) => {
    if (selected.has(value)) {
      onChange({ ...config, categories: ordered.filter((c) => c !== value) });
    } else {
      onChange({ ...config, categories: [...ordered, value] });
    }
  };
  return (
    <>
      <PopoverHeader Icon={Newspaper} title={t('widgetSettings.newsHeader')} />
      <p className="font-mono text-[9px] tracking-[0.16em] uppercase text-fg-subtle mb-2">{t('widgetSettings.priorityHint')}</p>
      <div className="flex flex-wrap gap-1.5">
        {NEWS_CATEGORY_VALUES.map((value) => {
          const active = selected.has(value);
          const rank = active ? ordered.indexOf(value) + 1 : null;
          return (
            <button
              key={value}
              type="button"
              onClick={() => toggle(value)}
              className={`relative font-display text-[11px] tracking-tight font-semibold px-2.5 py-1 rounded-md border transition-all cursor-pointer
                ${active
                  ? 'border-accent bg-accent/15 text-accent shadow-[inset_0_0_10px_-3px_var(--color-accent)]'
                  : 'border-dashed border-border-default bg-transparent text-fg-muted hover:border-accent/50 hover:text-accent hover:bg-accent/5'}`}
            >
              {active && <span className="absolute -top-1.5 -left-1.5 w-4 h-4 rounded-full bg-accent text-bg-deep text-[9px] font-mono font-bold flex items-center justify-center">{rank}</span>}
              {t(`widgetSettings.newsCategory.${value}`)}
            </button>
          );
        })}
      </div>
      <p className="font-mono text-[9px] tracking-[0.14em] text-fg-subtle uppercase mt-3 leading-relaxed">
        {t('widgetSettings.allCategoriesHint')}
      </p>
    </>
  );
}
