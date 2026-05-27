import { useTranslation } from 'react-i18next';
import { PlusCircle } from 'lucide-react';
import { motion } from 'framer-motion';
import SearchSuggestions from '../../../shared/components/form/SearchSuggestions';
import Card from '../../../shared/components/card';

export default function PositionSearchBar() {
  const { t } = useTranslation();
  return (
    <Card
      as={motion.div}
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.3, ease: [0.16, 1, 0.3, 1] }}
      variant="elevated"
      radius="2xl"
      padding="md"
      backdropBlur
      className="relative z-50 overflow-visible"
    >
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 mb-3">
        <div className="flex items-center gap-2">
          <span className="flex items-center justify-center w-7 h-7 rounded-lg bg-accent/10 text-accent">
            <PlusCircle className="h-3.5 w-3.5" />
          </span>
          <div>
            <h3 className="text-sm font-semibold text-fg leading-tight">
              {t('portfolio.search.title')}
            </h3>
            <p className="text-[11px] text-fg-muted leading-tight mt-0.5">
              {t('portfolio.search.subtitle')}
            </p>
          </div>
        </div>
      </div>
      <SearchSuggestions
        placeholder={t('portfolio.search.placeholder')}
        excludeTypes={['MACRO', 'BOND']}
      />
    </Card>
  );
}
