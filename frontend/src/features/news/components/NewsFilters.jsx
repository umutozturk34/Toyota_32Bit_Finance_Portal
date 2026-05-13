import { useQuery } from '@tanstack/react-query';
import { STALE } from '../../../shared/constants/query';
import { useTranslation } from 'react-i18next';
import { Newspaper } from 'lucide-react';
import { newsService } from '../services/newsService';
import { CATEGORY_CONFIG } from '../lib/newsConfig';
import FilterTabs from '../../../shared/components/form/FilterTabs';

export default function NewsFilters({ activeTab, onTabChange }) {
    const { t } = useTranslation();
    const { data: categoryCounts = [] } = useQuery({
        queryKey: ['newsCategories'],
        queryFn: newsService.getCategories,
        staleTime: STALE.MEDIUM,
    });

    const items = categoryCounts.map(c => {
        const cfg = CATEGORY_CONFIG[c.type] || {};
        return { type: c.type, count: c.count, label: cfg.labelKey ? t(cfg.labelKey) : c.type };
    });

    const totalCount = categoryCounts.reduce((sum, c) => sum + Number(c.count), 0);

    return (
        <FilterTabs
            items={items}
            activeId={activeTab}
            onSelect={onTabChange}
            allCount={totalCount}
            layoutId="news-category"
        />
    );
}
